import { LudoGameEngine } from "./ludoEngine";
import { GameState, Player, Token } from "./types";

// Added by Athar
// Mirrors app/src/test/.../LudoGameEngineTest.kt scenario-for-scenario, so a
// pass here plus a pass there is real evidence the TS port and the Kotlin
// original agree on every rule, not just that each compiles.

let pass = 0;
let fail = 0;
function check(name: string, cond: boolean) {
  if (cond) {
    pass++;
    console.log(`PASS: ${name}`);
  } else {
    fail++;
    console.log(`FAIL: ${name}`);
  }
}

function makeToken(id: number, position = 0): Token {
  return { id, position };
}

function fourTokens(): Token[] {
  return [makeToken(0), makeToken(1), makeToken(2), makeToken(3)];
}

function baseState(): GameState {
  const p1: Player = { userId: "p1", name: "Athar", color: "RED", isAI: false, connected: true, tokens: fourTokens() };
  const p2: Player = { userId: "p2", name: "Ali", color: "GREEN", isAI: false, connected: true, tokens: fourTokens() };
  return {
    roomId: "test-room",
    players: [p1, p2],
    currentPlayerIndex: 0,
    diceValue: null,
    consecutiveSixes: 0,
    hasRolledThisTurn: false,
    status: "IN_PROGRESS",
    winnerId: null,
    turnStartedAtMillis: 0,
  };
}

function fixedRng(value: number): () => number {
  // rollDice() computes 1 + floor(rng() * 6); to force a specific 1..6
  // result, rng() must return (value - 1) / 6 exactly (avoiding the 6.0 edge).
  return () => (value - 1) / 6 + 0.0001;
}

function withPlayers(state: GameState, players: Player[]): GameState {
  return { ...state, players };
}

// 1. Dice always in range.
{
  let ok = true;
  for (let seed = 0; seed < 50; seed++) {
    const engine = new LudoGameEngine(baseState(), Math.random);
    const d = engine.rollDice();
    if (d < 1 || d > 6) ok = false;
  }
  check("dice always 1..6", ok);
}

// 2. Cannot leave base without a six.
{
  const engine = new LudoGameEngine(baseState(), fixedRng(4));
  engine.rollDice();
  check("no moves with dice=4, all tokens in base", engine.getValidMoves().length === 0);
}

// 3. Six releases all 4 tokens.
{
  const engine = new LudoGameEngine(baseState(), fixedRng(6));
  engine.rollDice();
  const moves = engine.getValidMoves();
  check("six releases 4 tokens", moves.length === 4);
  check("release destination is local position 1", moves[0].toPosition === 1);
}

// 4. Moving updates position; six grants an extra turn.
{
  const engine = new LudoGameEngine(baseState(), fixedRng(6));
  engine.rollDice();
  const result = engine.moveToken("p1", 0);
  check("token moved to position 1", result.toPosition === 1);
  check("extra turn on six keeps same player", engine.state.currentPlayerIndex === 0 && engine.state.diceValue === null);
}

// 5. Non-six, non-capturing move passes the turn.
{
  const state = baseState();
  const p1 = { ...state.players[0], tokens: [makeToken(0, 5), makeToken(1), makeToken(2), makeToken(3)] };
  const engine = new LudoGameEngine(withPlayers(state, [p1, state.players[1]]), fixedRng(3));
  engine.rollDice();
  engine.moveToken("p1", 0);
  check("non-six move passes turn to p2", engine.state.currentPlayerIndex === 1);
}

// 6. Capture sends the opponent token back to base.
{
  const state = baseState();
  const p1 = { ...state.players[0], tokens: [makeToken(0, 9), makeToken(1), makeToken(2), makeToken(3)] };
  // p2 (GREEN, offset 13) local pos 49 -> global (13+49-1)%52 = 9
  const p2 = { ...state.players[1], tokens: [makeToken(0, 49), makeToken(1), makeToken(2), makeToken(3)] };
  const engine = new LudoGameEngine(withPlayers(state, [p1, p2]), fixedRng(1)); // p1: 9 -> 10, global 9 (not safe)
  engine.rollDice();
  const result = engine.moveToken("p1", 0);
  check("capture detected", result.capturedTokens.length === 1 && result.capturedTokens[0].playerId === "p2");
  check("captured token sent to base", engine.state.players[1].tokens[0].position === 0);
}

// 7. Safe square blocks capture.
{
  const state = baseState();
  const p1 = { ...state.players[0], tokens: [makeToken(0, 8), makeToken(1), makeToken(2), makeToken(3)] };
  // p2 GREEN offset 13, local 48 -> global (13+48-1)%52 = 8 (safe square)
  const p2 = { ...state.players[1], tokens: [makeToken(0, 48), makeToken(1), makeToken(2), makeToken(3)] };
  const engine = new LudoGameEngine(withPlayers(state, [p1, p2]), fixedRng(1));
  engine.rollDice();
  const result = engine.moveToken("p1", 0);
  check("safe square blocks capture", result.capturedTokens.length === 0);
}

// 8. Cannot overshoot final home.
{
  const state = baseState();
  const p1 = { ...state.players[0], tokens: [makeToken(0, 56), makeToken(1), makeToken(2), makeToken(3)] };
  const engine = new LudoGameEngine(withPlayers(state, [p1, state.players[1]]), fixedRng(5)); // 56+5=61 > 58
  engine.rollDice();
  check("overshoot rejected", engine.getValidMoves().every((m) => m.tokenId !== 0));
}

// 9. Exact finish.
{
  const state = baseState();
  const p1 = { ...state.players[0], tokens: [makeToken(0, 52), makeToken(1), makeToken(2), makeToken(3)] };
  const engine = new LudoGameEngine(withPlayers(state, [p1, state.players[1]]), fixedRng(6));
  engine.rollDice();
  const result = engine.moveToken("p1", 0);
  check("exact finish reaches 58", result.toPosition === 58);
}

// 10. Winner detection.
{
  const state = baseState();
  const p1 = {
    ...state.players[0],
    tokens: [makeToken(0, 52), makeToken(1, 58), makeToken(2, 58), makeToken(3, 58)],
  };
  const engine = new LudoGameEngine(withPlayers(state, [p1, state.players[1]]), fixedRng(6));
  engine.rollDice();
  const result = engine.moveToken("p1", 0);
  check("winner detected", result.winnerId === "p1" && engine.state.status === "FINISHED");
}

// 11. Out-of-turn move rejected.
{
  const engine = new LudoGameEngine(baseState(), fixedRng(6));
  engine.rollDice();
  let threw = false;
  try {
    engine.moveToken("p2", 0);
  } catch (e) {
    threw = true;
  }
  check("out-of-turn move rejected", threw);
}

// 12. Triple six forfeits the turn.
{
  let state = baseState();
  function rollSix(current: GameState): GameState {
    const engine = new LudoGameEngine(current, fixedRng(6));
    engine.rollDice();
    const move = engine.getValidMoves()[0];
    engine.moveToken("p1", move.tokenId);
    return engine.state;
  }
  state = rollSix(state);
  check("1st six stays p1", state.currentPlayerIndex === 0);
  state = rollSix(state);
  check("2nd six stays p1", state.currentPlayerIndex === 0);
  state = rollSix(state);
  check("3rd six forfeits to p2", state.currentPlayerIndex === 1);
}

// 13. isSafeCell reports classic star/start squares correctly.
{
  const engine = new LudoGameEngine(baseState(), fixedRng(1));
  check("global square 0 is safe", engine.isSafeCell(0));
  check("global square 8 is safe", engine.isSafeCell(8));
  check("global square 5 is NOT safe", !engine.isSafeCell(5));
}

console.log(`\n===== ${pass} passed, ${fail} failed =====`);
if (fail > 0) process.exit(1);
