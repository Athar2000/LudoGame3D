// Added by Athar
// Mirrors app/src/main/java/com/athar/ludo3d/domain/model/{Token,Player,GameState}.kt
// exactly, field-for-field, so the JSON this reads from and writes to
// Realtime Database is byte-compatible with what the Kotlin client expects.

export type PlayerColor = "RED" | "GREEN" | "YELLOW" | "BLUE";
export type GameStatus = "WAITING" | "IN_PROGRESS" | "PAUSED" | "FINISHED";

export const COLOR_START_OFFSET: Record<PlayerColor, number> = {
  RED: 0,
  GREEN: 13,
  YELLOW: 26,
  BLUE: 39,
};

export const TOKEN_BASE = 0;
export const TOKEN_FINISHED = 58;

export interface Token {
  id: number;
  position: number;
}

export interface Player {
  userId: string;
  name: string;
  color: PlayerColor;
  isAI: boolean;
  connected: boolean;
  tokens: Token[];
}

export interface GameState {
  roomId: string;
  players: Player[];
  currentPlayerIndex: number;
  diceValue: number | null;
  consecutiveSixes: number;
  hasRolledThisTurn: boolean;
  status: GameStatus;
  winnerId: string | null;
  turnStartedAtMillis: number;
}

export interface ValidMove {
  tokenId: number;
  fromPosition: number;
  toPosition: number;
  capturesOpponent: boolean;
}

export interface CapturedToken {
  playerId: string;
  tokenId: number;
}

export interface MoveResult {
  playerId: string;
  tokenId: number;
  fromPosition: number;
  toPosition: number;
  capturedTokens: CapturedToken[];
  grantsExtraTurn: boolean;
  winnerId: string | null;
}
