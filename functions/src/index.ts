import * as admin from "firebase-admin";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { LudoGameEngine, InvalidGameActionError } from "./ludoEngine";
import { GameState, Player, PlayerColor } from "./types";
export { claimDailyReward, purchaseItem } from "./rewardsAndShop";
export { initializeUserProfile } from "./onUserCreate";

// Added by Athar
//
// This is what turns "the client re-validates itself" (Phase 5) into
// genuine server-authoritative anti-cheat (section 6). These six functions
// are the ONLY code path allowed to write rooms/{roomId}/gameState — see the
// updated database.rules.json, which now sets gameState's ".write" to
// `false` for every client. The Admin SDK used here bypasses security rules
// entirely, which is exactly why only code running here (not the app) can
// touch this data.
//
// Every function follows the same shape:
//   1. require the caller to be authenticated (context.auth)
//   2. read the CURRENT gameState fresh from Realtime Database — never trust
//      anything the client sent except which room/token it's acting on
//   3. reconstruct a LudoGameEngine and let IT decide whether the action is
//      legal (turn ownership, move legality, timer expiry)
//   4. write the engine's resulting state back
//   5. throw a structured HttpsError (see ERROR_CODES) on anything illegal
//
// Error codes are shared with the Android client's GameSyncError sealed
// class (domain/repository/GameSyncRepository.kt) by matching string, so
// FirebaseGameSyncRepository can turn a thrown HttpsError back into the
// right typed error for the UI to show.

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const TURN_DURATION_MS = 20_000;
const COLOR_ORDER: PlayerColor[] = ["RED", "GREEN", "YELLOW", "BLUE"];

export const ERROR_CODES = {
  NOT_YOUR_TURN: "NOT_YOUR_TURN",
  GAME_NOT_FOUND: "GAME_NOT_FOUND",
  GAME_NOT_IN_PROGRESS: "GAME_NOT_IN_PROGRESS",
  ALREADY_ROLLED: "ALREADY_ROLLED",
  NO_DICE_ROLLED: "NO_DICE_ROLLED",
  INVALID_MOVE: "INVALID_MOVE",
  TURN_NOT_EXPIRED_YET: "TURN_NOT_EXPIRED_YET",
  NOT_HOST: "NOT_HOST",
  ROOM_NOT_FOUND: "ROOM_NOT_FOUND",
} as const;

function requireAuth(auth: { uid: string } | undefined): string {
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  return auth.uid;
}

async function readGameState(roomId: string): Promise<GameState> {
  const snapshot = await admin.database().ref(`rooms/${roomId}/gameState`).get();
  if (!snapshot.exists()) throw new HttpsError("failed-precondition", ERROR_CODES.GAME_NOT_FOUND);
  const raw = snapshot.val();
  return {
    roomId,
    players: raw.players ?? [],
    currentPlayerIndex: raw.currentPlayerIndex ?? 0,
    diceValue: raw.diceValue ?? null,
    consecutiveSixes: raw.consecutiveSixes ?? 0,
    hasRolledThisTurn: raw.hasRolledThisTurn ?? false,
    status: raw.status ?? "WAITING",
    winnerId: raw.winnerId ?? null,
    turnStartedAtMillis: raw.turnStartedAt ?? 0,
  };
}

async function fetchConnectedMap(roomId: string): Promise<Record<string, boolean>> {
  const snapshot = await admin.database().ref(`rooms/${roomId}/players`).get();
  const result: Record<string, boolean> = {};
  snapshot.forEach((child) => {
    result[child.key as string] = child.child("connected").val() ?? true;
    return false;
  });
  return result;
}

/** Mirrors FirebaseGameSyncRepository's skipDisconnectedPlayers exactly. */
async function skipDisconnectedPlayers(engine: LudoGameEngine, roomId: string, maxIterations = 4): Promise<boolean> {
  const connected = await fetchConnectedMap(roomId);
  let changed = false;
  let iterations = 0;
  while (
    engine.state.status === "IN_PROGRESS" &&
    connected[engine.state.players[engine.state.currentPlayerIndex].userId] === false &&
    iterations < maxIterations
  ) {
    engine.nextTurn();
    changed = true;
    iterations++;
  }
  return changed;
}

async function writeGameState(roomId: string, state: GameState, stampNewTurn: boolean): Promise<void> {
  await admin.database().ref(`rooms/${roomId}/gameState`).set({
    status: state.status,
    currentPlayerIndex: state.currentPlayerIndex,
    diceValue: state.diceValue,
    hasRolledThisTurn: state.hasRolledThisTurn,
    consecutiveSixes: state.consecutiveSixes,
    winnerId: state.winnerId,
    turnStartedAt: stampNewTurn ? admin.database.ServerValue.TIMESTAMP : state.turnStartedAtMillis,
    players: state.players.map((p) => ({
      userId: p.userId,
      name: p.name,
      color: p.color,
      isAI: p.isAI,
      tokens: p.tokens.map((t) => ({ id: t.id, position: t.position })),
    })),
  });
}

function wrapEngineError(e: unknown): never {
  if (e instanceof InvalidGameActionError) {
    throw new HttpsError("failed-precondition", ERROR_CODES.INVALID_MOVE, e.message);
  }
  throw e;
}

// ---------------------------------------------------------------------------

export const startGame = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const roomId: string = request.data?.roomId;
  if (!roomId) throw new HttpsError("invalid-argument", "roomId is required");

  const roomSnapshot = await admin.database().ref(`rooms/${roomId}`).get();
  if (!roomSnapshot.exists()) throw new HttpsError("failed-precondition", ERROR_CODES.ROOM_NOT_FOUND);
  if (roomSnapshot.child("hostId").val() !== uid) {
    throw new HttpsError("permission-denied", ERROR_CODES.NOT_HOST);
  }

  const players: Player[] = [];
  roomSnapshot.child("players").forEach((p) => {
    const color = p.child("color").val();
    if (!color) return false;
    players.push({
      userId: p.key as string,
      name: p.child("name").val(),
      color,
      isAI: p.child("isAI").val() ?? false,
      connected: p.child("connected").val() ?? true,
      tokens: [0, 1, 2, 3].map((id) => ({ id, position: 0 })),
    });
    return false;
  });

  const initialState: GameState = {
    roomId,
    players,
    currentPlayerIndex: 0,
    diceValue: null,
    consecutiveSixes: 0,
    hasRolledThisTurn: false,
    status: "IN_PROGRESS",
    winnerId: null,
    turnStartedAtMillis: 0,
  };
  await writeGameState(roomId, initialState, true);
  return { ok: true };
});

export const rollDice = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const roomId: string = request.data?.roomId;
  if (!roomId) throw new HttpsError("invalid-argument", "roomId is required");

  const current = await readGameState(roomId);
  if (current.status !== "IN_PROGRESS") throw new HttpsError("failed-precondition", ERROR_CODES.GAME_NOT_IN_PROGRESS);
  if (current.players[current.currentPlayerIndex].userId !== uid) {
    throw new HttpsError("permission-denied", ERROR_CODES.NOT_YOUR_TURN);
  }
  if (current.hasRolledThisTurn) throw new HttpsError("failed-precondition", ERROR_CODES.ALREADY_ROLLED);

  const engine = new LudoGameEngine(current);
  const diceValue = engine.rollDice();

  let stampNewTurn = false;
  if (engine.getValidMoves().length === 0) {
    engine.skipTurnIfNoValidMoves();
    stampNewTurn = true;
  }
  stampNewTurn = (await skipDisconnectedPlayers(engine, roomId)) || stampNewTurn;

  await writeGameState(roomId, engine.state, stampNewTurn);
  return { diceValue };
});

export const submitMove = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const roomId: string = request.data?.roomId;
  const tokenId: number = request.data?.tokenId;
  if (!roomId || tokenId === undefined) throw new HttpsError("invalid-argument", "roomId and tokenId are required");

  const current = await readGameState(roomId);
  if (current.status !== "IN_PROGRESS") throw new HttpsError("failed-precondition", ERROR_CODES.GAME_NOT_IN_PROGRESS);
  if (current.players[current.currentPlayerIndex].userId !== uid) {
    throw new HttpsError("permission-denied", ERROR_CODES.NOT_YOUR_TURN);
  }
  if (current.diceValue === null) throw new HttpsError("failed-precondition", ERROR_CODES.NO_DICE_ROLLED);

  const engine = new LudoGameEngine(current);
  if (!engine.getValidMoves().some((m) => m.tokenId === tokenId)) {
    throw new HttpsError("failed-precondition", ERROR_CODES.INVALID_MOVE);
  }

  const turnIndexBefore = current.currentPlayerIndex;
  let result;
  try {
    result = engine.moveToken(uid, tokenId);
  } catch (e) {
    wrapEngineError(e);
  }

  let stampNewTurn = engine.state.currentPlayerIndex !== turnIndexBefore || engine.state.status === "FINISHED";
  if (engine.state.status === "IN_PROGRESS") {
    stampNewTurn = (await skipDisconnectedPlayers(engine, roomId)) || stampNewTurn;
  }

  await writeGameState(roomId, engine.state, stampNewTurn);

  if (engine.state.status === "FINISHED" && engine.state.winnerId) {
    await applyGameFinishedStats(engine.state.winnerId, engine.state.players.map((p) => p.userId));
  }

  return result;
});

/**
 * Section 16/18: called once, server-side, the moment a game actually
 * finishes — never from a client claiming "I won." Every player in the room
 * gets gamesPlayed +1; the winner also gets wins +1 and a coin bonus. This
 * lives in the Cloud Function rather than being writable by the client for
 * the same reason coin balances generally aren't (see rewardsAndShop.ts):
 * a client could otherwise just increment its own win count directly.
 */
async function applyGameFinishedStats(winnerId: string, allPlayerIds: string[]): Promise<void> {
  const WIN_COIN_BONUS = 50;
  const PARTICIPATION_XP = 10;
  const WIN_XP_BONUS = 25;

  const updates: Record<string, unknown> = {};
  for (const uid of allPlayerIds) {
    const base = `users/${uid}`;
    const snapshot = await admin.database().ref(base).get();
    if (!snapshot.exists()) continue; // AI players or stale entries have no user profile to update

    const gamesPlayed = (snapshot.child("gamesPlayed").val() ?? 0) + 1;
    const isWinner = uid === winnerId;
    const wins = (snapshot.child("wins").val() ?? 0) + (isWinner ? 1 : 0);
    const xp = (snapshot.child("xp").val() ?? 0) + PARTICIPATION_XP + (isWinner ? WIN_XP_BONUS : 0);
    const coins = (snapshot.child("coins").val() ?? 0) + (isWinner ? WIN_COIN_BONUS : 0);
    const level = 1 + Math.floor(xp / 200); // 200 XP per level, simple and predictable

    updates[`${base}/gamesPlayed`] = gamesPlayed;
    updates[`${base}/wins`] = wins;
    updates[`${base}/xp`] = xp;
    updates[`${base}/coins`] = coins;
    updates[`${base}/level`] = level;
  }
  if (Object.keys(updates).length > 0) {
    await admin.database().ref().update(updates);
  }
}

export const forceSkipExpiredTurn = onCall(async (request) => {
  requireAuth(request.auth); // any authenticated room member may call this
  const roomId: string = request.data?.roomId;
  if (!roomId) throw new HttpsError("invalid-argument", "roomId is required");

  const current = await readGameState(roomId);
  if (current.status !== "IN_PROGRESS") throw new HttpsError("failed-precondition", ERROR_CODES.GAME_NOT_IN_PROGRESS);

  const elapsed = Date.now() - current.turnStartedAtMillis;
  if (elapsed < TURN_DURATION_MS) throw new HttpsError("failed-precondition", ERROR_CODES.TURN_NOT_EXPIRED_YET);

  const engine = new LudoGameEngine(current);
  engine.nextTurn();
  await skipDisconnectedPlayers(engine, roomId);
  await writeGameState(roomId, engine.state, true);
  return { ok: true };
});

export const leaveGame = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const roomId: string = request.data?.roomId;
  if (!roomId) throw new HttpsError("invalid-argument", "roomId is required");

  await admin.database().ref(`rooms/${roomId}/players/${uid}/connected`).set(false);

  const current = await readGameState(roomId).catch(() => null);
  if (!current || current.status !== "IN_PROGRESS") return { ok: true };
  if (current.players[current.currentPlayerIndex].userId !== uid) return { ok: true };

  const engine = new LudoGameEngine(current);
  engine.nextTurn();
  await skipDisconnectedPlayers(engine, roomId);
  await writeGameState(roomId, engine.state, true);
  return { ok: true };
});

export const requestRematch = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const roomId: string = request.data?.roomId;
  if (!roomId) throw new HttpsError("invalid-argument", "roomId is required");

  const roomSnapshot = await admin.database().ref(`rooms/${roomId}`).get();
  if (!roomSnapshot.exists()) throw new HttpsError("failed-precondition", ERROR_CODES.ROOM_NOT_FOUND);
  if (roomSnapshot.child("hostId").val() !== uid) throw new HttpsError("permission-denied", ERROR_CODES.NOT_HOST);

  const players: Player[] = [];
  roomSnapshot.child("players").forEach((p) => {
    const color = p.child("color").val();
    if (!color) return false;
    players.push({
      userId: p.key as string,
      name: p.child("name").val(),
      color,
      isAI: p.child("isAI").val() ?? false,
      connected: p.child("connected").val() ?? true,
      tokens: [0, 1, 2, 3].map((id) => ({ id, position: 0 })),
    });
    return false;
  });

  const freshState: GameState = {
    roomId,
    players,
    currentPlayerIndex: 0,
    diceValue: null,
    consecutiveSixes: 0,
    hasRolledThisTurn: false,
    status: "IN_PROGRESS",
    winnerId: null,
    turnStartedAtMillis: 0,
  };
  await writeGameState(roomId, freshState, true);
  return { ok: true };
});

// Referenced only for the COLOR_ORDER constant used during room->player
// color assignment fallback; kept here rather than in ludoEngine.ts since
// it's a room-setup concern, not a rules-engine concern.
export function nextAvailableColor(usedColors: PlayerColor[]): PlayerColor | null {
  return COLOR_ORDER.find((c) => !usedColors.includes(c)) ?? null;
}
