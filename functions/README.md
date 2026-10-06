# Ludo 3D — Cloud Functions (Phase 6)

Server-authoritative move validation. Six callable functions
(`startGame`, `rollDice`, `submitMove`, `forceSkipExpiredTurn`, `leaveGame`,
`requestRematch`) are the ONLY code allowed to write `rooms/{roomId}/gameState`
— see `../firebase/database.rules.json`, which sets that path's `.write` to
`false` for every client.

## What's actually verified here

`src/ludoEngine.ts` is a field-for-field TypeScript port of the Kotlin
`LudoGameEngine` (`app/src/main/java/com/athar/ludo3d/domain/engine/LudoGameEngine.kt`).
This was:

1. **Compiled for real** with `tsc` in strict mode (`noUnusedLocals`,
   `noUnusedParameters` included) — zero errors.
2. **Run for real** against `src/ludoEngine.smoke.ts`, which mirrors every
   scenario in the Kotlin engine's JUnit tests (dice range, six-to-release,
   captures, safe squares, overshoot rejection, exact finish, winner
   detection, out-of-turn rejection, triple-six forfeiture) — 20/20 passed.

What's NOT verified here: `src/index.ts`, the actual Cloud Function handlers,
because that requires either deploying to a real Firebase project or running
the Firebase emulator suite, neither of which is possible from this sandbox
(no Firebase CLI login, no emulator binaries reachable from the network
allowlist this was built in). The handlers themselves are thin — read state,
hand it to the already-verified engine, write the result — but "thin" is not
"tested," so treat `index.ts` as reviewed-but-unexercised until you run it
against the emulator or a real project.

## Setup

```bash
cd functions
npm install
npm run build   # compiles TypeScript, also what `npm test` and deploy do first
npm test        # runs the engine smoke test above
```

To actually deploy, you need the Firebase CLI and a real project:

```bash
npm install -g firebase-tools
firebase login
firebase use --add        # pick your project, alias it "default"
                           # (or edit ../.firebaserc directly)
firebase deploy --only functions
```

To test locally against the emulator instead of deploying:

```bash
firebase emulators:start --only functions,database,auth
```

and point the Android app's `FirebaseFunctions` / `FirebaseDatabase`
instances at the emulator (`useEmulator("10.0.2.2", 5001)` /
`useEmulator("10.0.2.2", 9000)` for an Android emulator, or your machine's
LAN IP for a real phone on the same network) before running it.

## Error codes

Each function throws a `functions.https.HttpsError` whose `message` is one
of the `ERROR_CODES` constants in `src/index.ts` (e.g. `"NOT_YOUR_TURN"`).
The Android client (`FirebaseGameSyncRepository.toGameSyncError()`) matches
on that exact string to reconstruct the typed `GameSyncError` the UI expects
— if you add a new error code here, add the matching case there too.
