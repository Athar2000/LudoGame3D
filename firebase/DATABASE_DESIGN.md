# Realtime Database structure (Phase 4/5/6)

```
users/
  {uid}/
    name: string
    email: string | null
    avatarId: string
    coins: number
    level: number
    xp: number
    wins: number
    gamesPlayed: number
    lastDailyRewardClaimedDay: number   # 0..6, prevents re-claiming (section 19)
    lastDailyRewardClaimedAt: number    # server timestamp

rooms/
  {roomId}/                              # roomId = 9-char code, e.g. ATHAR4587
    hostId: string
    status: "WAITING" | "IN_PROGRESS" | "FINISHED"
    maxPlayers: 2 | 3 | 4
    isPrivate: boolean
    createdAt: number                    # ServerValue.TIMESTAMP

    players/
      {uid}/
        name: string
        color: "RED" | "GREEN" | "YELLOW" | "BLUE"
        ready: boolean
        connected: boolean               # driven by onDisconnect(), see below
        isAI: boolean
        joinedAt: number

    gameState/
      status: "WAITING" | "IN_PROGRESS" | "PAUSED" | "FINISHED"
      currentPlayerIndex: number
      diceValue: number | null
      hasRolledThisTurn: boolean
      consecutiveSixes: number
      turnStartedAt: number              # ServerValue.TIMESTAMP, restamped only when the turn changes — powers the turn timer (section 8)
      winnerId: string | null
      players/                            # embedded array, ordered — see note below
        [n]/
          userId: string
          name: string
          color: "RED" | "GREEN" | "YELLOW" | "BLUE"
          isAI: boolean
          tokens/
            [0..3]/ { id: number, position: number }  # 0=base, 1-51=shared path, 52-57=home column, 58=finished

    chat/
      {messageId}/
        senderId: string
        senderName: string
        text: string
        type: "text" | "quick" | "emoji"
        sentAt: number

matchmakingQueue/
  {uid}/
    playerCount: 2 | 3 | 4
    joinedAt: number
```

## Implementation note: gameState is written as one node, not flattened

An earlier draft of this document (Phase 4) planned flattened keys like
`gameState/tokens/{uid}_{tokenId}` so a single move would only touch one
small key. Phase 5's actual implementation (`FirebaseGameSyncRepository`)
instead reads the ENTIRE `gameState` node, runs `LudoGameEngine` against it,
and writes the entire node back with one `setValue()` call per roll/move.

This is a deliberate simplification, not an oversight: `LudoGameEngine`
operates on a single immutable `GameState` object containing all players and
their tokens — reconstructing that object from N separate flattened
Firebase keys, then re-flattening it after every engine call, adds real
complexity for a payload that's still tiny (4 players × 4 tokens × a few
bytes each, well under 1 KB). The cost is that every roll/move now
transfers a few hundred bytes instead of a handful, which is not a problem
at Ludo's scale (a handful of writes per minute per room) but would be
worth revisiting if this pattern were reused for something with much larger
per-entity state.

## Why tokens are flattened under `gameState/tokens`

(Historical rationale, superseded by the note above for THIS project's
actual implementation — kept here because the underlying principle is still
correct advice for state trees that don't fit in one small object.)

Storing token positions as `gameState/tokens/{uid}_{tokenId}` instead of nested
under `players/{uid}/token1..4` means a single dice roll + move only ever
writes ONE small key, not the entire player subtree. Realtime Database
listeners fire per-path, so this keeps every client's `onDataChange` payload
minimal — important for section 25 (avoid unnecessary reads/writes, work well
on mid-range phones).

## Server-authoritative validation pipeline (section 6) — IMPLEMENTED

Realtime Database security rules (see `database.rules.json`) can enforce
*shape* and *ownership* (who is allowed to write which path), but they cannot
run the full Ludo rules engine (turn order, valid-move computation, capture
resolution) — RTDB rules have no access to sibling game history beyond what's
readable at validation time in a reasonably efficient way. This is why the
pipeline below exists as real Cloud Functions (`../functions/src/index.ts`),
not just security rules.

```
Client (Phone A)                 Cloud Function                 Realtime DB
      |  calls httpsCallable       |                                |
      |  "submitMove(roomId,       |                                |
      |   tokenId)"                |                                |
      |---------------------------->|  1. reads rooms/{id}/gameState |
      |                             |     with the Admin SDK          |
      |                             |  2. reconstructs a              |
      |                             |     LudoGameEngine (the exact   |
      |                             |     Kotlin logic, ported to     |
      |                             |     TypeScript in               |
      |                             |     functions/src/ludoEngine.ts)|
      |                             |  3. validates: is it this       |
      |                             |     player's turn? is the       |
      |                             |     token move in               |
      |                             |     getValidMoves()?            |
      |                             |  4. if valid: writes the new    |
      |                             |     gameState via Admin SDK     |
      |                             |     (bypasses client rules      |
      |                             |     entirely)                   |
      |                             |  5. if invalid: throws          |
      |                             |     HttpsError('failed-         |
      |                             |     precondition', ERROR_CODE)  |
      |<----------------------------|                                |
      |                             |  Admin SDK write --------------->|
      |                                                                |
Client (Phones B, C, D) <---- realtime `onValue` listener on           |
                               rooms/{id}/gameState  <-------------------
```

Clients now NEVER write `gameState` at all — `database.rules.json` sets its
`.write` to `false` unconditionally. The six callable functions
(`startGame`, `rollDice`, `submitMove`, `forceSkipExpiredTurn`, `leaveGame`,
`requestRematch`) are the only code path that can touch that node, because
the Admin SDK they run with bypasses security rules by design. This mirrors
the Kotlin `LudoGameEngine` logic 1:1 (see `functions/README.md` for how that
was verified — compiled with `tsc` in strict mode and run against a smoke
test mirroring the Kotlin engine's own test scenarios, 20/20 passing) so "the
server logic" and "the client logic" are never two different implementations
that can drift apart.

## Anti-cheat checklist this pipeline blocks (section 6)

| Attack                              | Blocked by                                                        |
|--------------------------------------|---------------------------------------------------------------------|
| Changing the dice result             | Dice is rolled server-side inside the Cloud Function, never client  |
| Moving without it being your turn    | Function checks `gameState.currentPlayerIndex` resolves to `auth.uid`, throws `NOT_YOUR_TURN` otherwise |
| Moving a token extra spaces          | Function recomputes `getValidMoves()`; destination must be in it, throws `INVALID_MOVE` otherwise |
| Playing after the turn timer expires | `forceSkipExpiredTurn` checks `Date.now() - turnStartedAt >= 20000` before allowing any player to skip a stuck turn |
| Sending a fake winner status         | `winnerId` is only ever set by the Function after `LudoGameEngine`'s own win check, never accepted from the client |
| Writing gameState directly, bypassing all of the above | `database.rules.json`: `"gameState": { ".write": false }` — rejected by Firebase itself, before any application code runs |
