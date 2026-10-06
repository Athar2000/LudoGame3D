import {
  CapturedToken,
  COLOR_START_OFFSET,
  GameState,
  MoveResult,
  Player,
  Token,
  TOKEN_BASE,
  TOKEN_FINISHED,
  ValidMove,
} from "./types";

// Added by Athar
//
// This is a deliberate, field-for-field port of
// app/src/main/java/com/athar/ludo3d/domain/engine/LudoGameEngine.kt.
// Every rule, constant, and edge case below (SAFE_SQUARES, the 1..51/52..57/58
// position encoding, six-to-release, captures, exact-finish, triple-six
// forfeiture) is copied from the Kotlin engine, not reinterpreted — the two
// are meant to never drift apart. If you change a rule in one, change it in
// the other and re-run both test suites
// (app/src/test/.../LudoGameEngineTest.kt and this file's smoke test).
//
// This is what makes the client's own copy of the rules (used for instant
// move highlighting / local & AI play) and the server's copy (used here,
// with the Admin SDK, as the actual source of truth for online play) the
// same rules rather than two independent implementations that could
// silently disagree.

export const SAFE_SQUARES: ReadonlySet<number> = new Set([0, 8, 13, 21, 26, 34, 39, 47]);
export const TRACK_LENGTH = 51;
export const HOME_COLUMN_START = 52;
export const HOME_COLUMN_END = 57;
export const MAX_CONSECUTIVE_SIXES = 3;

function isInBase(t: Token): boolean {
  return t.position === TOKEN_BASE;
}
function isFinished(t: Token): boolean {
  return t.position === TOKEN_FINISHED;
}
function isOnSharedPath(t: Token): boolean {
  return t.position >= 1 && t.position <= 51;
}
function hasWon(p: Player): boolean {
  return p.tokens.every(isFinished);
}

export class InvalidGameActionError extends Error {}

export class LudoGameEngine {
  state: GameState;
  private readonly rng: () => number;

  constructor(initialState: GameState, rng: () => number = Math.random) {
    this.state = initialState;
    this.rng = rng;
  }

  private get currentPlayer(): Player {
    return this.state.players[this.state.currentPlayerIndex];
  }

  /** rollDice() — produces 1..6. Rejects rolling twice in one turn. */
  rollDice(): number {
    if (this.state.status !== "IN_PROGRESS") {
      throw new InvalidGameActionError("Game is not in progress");
    }
    if (this.state.hasRolledThisTurn) {
      throw new InvalidGameActionError("Dice already rolled this turn");
    }
    const value = 1 + Math.floor(this.rng() * 6);
    this.state = { ...this.state, diceValue: value, hasRolledThisTurn: true };
    return value;
  }

  /** getValidMoves() — every legal move for the CURRENT player given the CURRENT dice value. */
  getValidMoves(): ValidMove[] {
    const dice = this.state.diceValue;
    if (dice === null || dice === undefined) return [];
    const player = this.currentPlayer;
    const moves: ValidMove[] = [];

    for (const token of player.tokens) {
      if (isFinished(token)) continue;

      if (isInBase(token)) {
        if (dice === 6) {
          moves.push({
            tokenId: token.id,
            fromPosition: token.position,
            toPosition: 1,
            capturesOpponent: this.wouldCapture(player, 1),
          });
        }
        continue;
      }

      const destination = token.position + dice;
      if (destination > HOME_COLUMN_END + 1) continue; // cannot overshoot home

      const actualDestination = destination === HOME_COLUMN_END + 1 ? TOKEN_FINISHED : destination;
      if (actualDestination > TOKEN_FINISHED) continue;

      moves.push({
        tokenId: token.id,
        fromPosition: token.position,
        toPosition: actualDestination,
        capturesOpponent: this.wouldCapture(player, actualDestination),
      });
    }
    return moves;
  }

  /** moveToken() — applies a move that MUST be present in getValidMoves(). */
  moveToken(playerId: string, tokenId: number): MoveResult {
    const dice = this.state.diceValue;
    if (dice === null || dice === undefined) throw new InvalidGameActionError("No dice value to apply");

    const playerIndex = this.state.players.findIndex((p) => p.userId === playerId);
    if (playerIndex !== this.state.currentPlayerIndex) {
      throw new InvalidGameActionError("Not this player's turn");
    }

    const validMove = this.getValidMoves().find((m) => m.tokenId === tokenId);
    if (!validMove) {
      throw new InvalidGameActionError(`Illegal move: token ${tokenId} cannot move with dice=${dice}`);
    }

    const player = this.state.players[playerIndex];
    const updatedToken: Token = { id: tokenId, position: validMove.toPosition };

    const captured: CapturedToken[] = [];
    let newPlayers = this.state.players.slice();

    if (validMove.toPosition >= 1 && validMove.toPosition <= TRACK_LENGTH) {
      const globalSquare = this.toGlobalSquare(player, validMove.toPosition);
      if (!SAFE_SQUARES.has(globalSquare)) {
        newPlayers = newPlayers.map((opponent) => {
          if (opponent.userId === playerId) return opponent;
          const opponentTokens = opponent.tokens.map((t) => {
            if (isOnSharedPath(t) && this.toGlobalSquare(opponent, t.position) === globalSquare) {
              captured.push({ playerId: opponent.userId, tokenId: t.id });
              return { id: t.id, position: TOKEN_BASE };
            }
            return t;
          });
          return { ...opponent, tokens: opponentTokens };
        });
      }
    }

    const movingPlayer = newPlayers[playerIndex];
    newPlayers = newPlayers.slice();
    newPlayers[playerIndex] = {
      ...movingPlayer,
      tokens: movingPlayer.tokens.map((t) => (t.id === tokenId ? updatedToken : t)),
    };

    const winner = hasWon(newPlayers[playerIndex]) ? newPlayers[playerIndex] : null;
    const newSixStreak = dice === 6 ? this.state.consecutiveSixes + 1 : 0;
    const forfeitForTripleSix = newSixStreak >= MAX_CONSECUTIVE_SIXES;
    const extraTurn = (dice === 6 || captured.length > 0 || validMove.toPosition === TOKEN_FINISHED) && !forfeitForTripleSix;

    this.state = {
      ...this.state,
      players: newPlayers,
      winnerId: winner ? winner.userId : this.state.winnerId,
      status: winner ? "FINISHED" : this.state.status,
      hasRolledThisTurn: true,
      consecutiveSixes: newSixStreak,
    };

    if (!winner) {
      if (!extraTurn) {
        this.advanceTurn();
      } else {
        this.state = { ...this.state, diceValue: null, hasRolledThisTurn: false };
      }
    }

    return {
      playerId,
      tokenId,
      fromPosition: validMove.fromPosition,
      toPosition: validMove.toPosition,
      capturedTokens: captured,
      grantsExtraTurn: extraTurn && !winner,
      winnerId: winner ? winner.userId : null,
    };
  }

  isSafeCell(globalSquare: number): boolean {
    return SAFE_SQUARES.has(globalSquare);
  }

  wouldCapture(mover: Player, destinationLocalPos: number): boolean {
    if (destinationLocalPos < 1 || destinationLocalPos > TRACK_LENGTH) return false;
    const globalSquare = this.toGlobalSquare(mover, destinationLocalPos);
    if (SAFE_SQUARES.has(globalSquare)) return false;
    return this.state.players.some(
      (opponent) =>
        opponent.userId !== mover.userId &&
        opponent.tokens.some((t) => isOnSharedPath(t) && this.toGlobalSquare(opponent, t.position) === globalSquare)
    );
  }

  hasPlayerWon(playerId: string): boolean {
    const p = this.state.players.find((pl) => pl.userId === playerId);
    return p ? hasWon(p) : false;
  }

  /** nextTurn() — advances to the next non-finished player, clearing dice/roll state. */
  nextTurn(): void {
    if (this.state.status === "FINISHED") return;
    this.advanceTurn();
  }

  /** Call after rollDice() when getValidMoves() is empty, to auto-skip the turn. */
  skipTurnIfNoValidMoves(): boolean {
    if (this.state.diceValue === null) return false;
    if (this.getValidMoves().length > 0) return false;
    this.advanceTurn();
    return true;
  }

  private toGlobalSquare(player: Player, localPos: number): number {
    return mod(COLOR_START_OFFSET[player.color] + localPos - 1, 52);
  }

  private advanceTurn(): void {
    let nextIndex = this.state.currentPlayerIndex;
    do {
      nextIndex = (nextIndex + 1) % this.state.players.length;
    } while (hasWon(this.state.players[nextIndex]) && nextIndex !== this.state.currentPlayerIndex);

    this.state = {
      ...this.state,
      currentPlayerIndex: nextIndex,
      diceValue: null,
      hasRolledThisTurn: false,
      consecutiveSixes: 0,
      turnStartedAtMillis: 0,
    };
  }
}

function mod(a: number, n: number): number {
  return ((a % n) + n) % n;
}
