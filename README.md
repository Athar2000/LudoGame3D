# Ludo 3D — Android, Kotlin, Jetpack Compose

Real, working source code, built incrementally in phases. This README is an
honest status report: what's done and verified, what's stubbed out pending
your own Firebase project, and what's not started yet.

## Status: Phase 1 + Phase 2 + (partial) Phase 3 + (partial) Phase 7

| # | Phase | Status |
|---|-------|--------|
| 1 | Project + navigation | ✅ Done |
| 2 | Ludo game engine + local single-player | ✅ Done |
| 3 | Firebase Authentication | ⚠️ Code complete (email/guest/Google), needs YOUR `google-services.json` |
| 4 | Private room + 4-player multiplayer | ✅ Done — real Firebase RTDB room create/join/lobby/presence |
| 5 | Real-time synchronization | ✅ Done — dice/turns/moves/captures/winner/timer/disconnect-skip/rematch, all via Firebase |
| 6 | Server-side validation/security | ✅ Done — Cloud Functions (Admin SDK) are now the ONLY writer of gameState; client can no longer forge it |
| 7 | AI opponent | ✅ Done (local `LudoAIEngine`, Vs Computer screen) |
| 8 | Chat, leaderboard, rewards, shop, profile | ✅ Done (friends leaderboard needs a friends system that doesn't exist yet) |
| 9 | Polished 3D visuals/sound | ✅ Sound effects, capture particles, win confetti (see below); pawn-move animation still jumps cell-to-cell |
| 10 | Testing / production optimization | ⚠️ Engine + AI have real unit tests; UI/integration tests not started |

## What you can actually run today

- Splash → 3-page onboarding → Login (email/password, guest, Google) → Home
- **Local Multiplayer**: 2–4 players, same device, pass-and-play, full standard
  Ludo rules, real 3D-style Canvas board
- **Vs Computer**: 1–3 AI opponents at Easy/Medium/Hard, AI moves automatically
- **Play With Friends (Phase 4)**: create a private room (host picks 2/3/4
  players), get a shareable code like `ATHAR4587`, others join by entering
  that code, everyone sees the live player list and ready states update in
  real time across every connected phone, host-only "Start Game" — all of
  this is real `FirebaseRealtimeDatabase` reads/writes (`FirebaseRoomRepository`),
  including transactional joins (so two phones can't both grab the last open
  slot) and `onDisconnect()` presence (so a phone that loses signal or gets
  killed shows as disconnected on the others automatically, without any
  client having to detect that itself). This needs your Firebase project
  (see Setup below) to actually run — the code is real, not a mock, but it
  has nothing to talk to until `google-services.json` is in place.
  **What it does NOT yet do**: once the host taps Start, the room flips to
  `IN_PROGRESS` and every phone sees the confirmed roster — but synchronized
  dice/turns/token movement isn't wired in yet. That's Phase 5, and building
  it as a real thing (not a demo) needs a Firebase project to test the
  4-phones-at-once behavior against, which is why it's the very next phase
  rather than something faked here to look further along.
- **Online gameplay (Phase 5)**: once the host starts the game, every phone
  in the room runs `OnlineGameScreen` off the exact same `GameScreenContent`
  UI as local/AI play, driven by a live Firebase listener
  (`GameSyncRepository.observeGameState`). Rolling, moving, captures, safe
  squares, exact-finish, winner detection — all the same `LudoGameEngine`
  rules from Phase 2, now re-run against the latest Firebase state on every
  action instead of a local in-memory instance. Also implemented: a 20-second
  turn timer (any connected player, not just the current one, can unstick a
  timed-out turn — important if the current player's phone just died),
  automatic turn-skipping for disconnected players (bounded so an all-
  disconnected room doesn't spin forever), a "leave game" that hands the turn
  off cleanly, and host-triggered rematch. See
  `docs/VERIFICATION_LOG.md` for what was actually tested and how.
  **What it does NOT yet do**: the trust model is still client-side —
  `FirebaseGameSyncRepository` re-validates turn ownership and move legality
  before writing, but a modified client APK could in principle skip that and
  write directly to Realtime Database, since the security rules (necessarily,
  without Phase 6) only check room membership, not which specific action is
  legitimate. `firebase/DATABASE_DESIGN.md` has the full design for closing
  that gap with a Cloud Function.
- **Server-authoritative validation (Phase 6)**: `functions/` is a real
  Node/TypeScript Cloud Functions project — `functions/src/ludoEngine.ts` is
  a field-for-field port of the Kotlin engine, compiled with `tsc` in strict
  mode and verified against a 20-scenario smoke test mirroring the Kotlin
  engine's own JUnit tests (see `functions/README.md`). Six callable
  functions (`startGame`, `rollDice`, `submitMove`, `forceSkipExpiredTurn`,
  `leaveGame`, `requestRematch`) are now the ONLY code allowed to write
  `rooms/{roomId}/gameState` — `database.rules.json` sets that path's
  `.write` to `false` for every client, and the Android
  `FirebaseGameSyncRepository` was rewired to call these functions
  (`httpsCallable`) instead of writing directly. A modified client APK can no
  longer forge dice results, moves, turns, or a winner — Firebase itself
  rejects the write before any application code runs.
  **What it does NOT yet do**: the Cloud Function *handlers* themselves
  (`functions/src/index.ts`) aren't exercised end-to-end here — that needs
  either a deployed Firebase project or the local emulator suite, neither
  reachable from this sandbox. They're reviewed and thin (read state, hand
  it to the already-verified engine, write the result) but "thin" isn't
  "tested"; run `firebase emulators:start` and try it before trusting it in
  production.
- Every rule in section 8 of the spec (dice, six-to-release, captures, safe
  squares, home column, exact-finish, extra turns, triple-six forfeit, winner
  detection) is implemented in `LudoGameEngine.kt` and was verified by
  compiling it with a real Kotlin compiler and running 16 behavioral checks
  against the compiled bytecode — not just described. Same rigor was applied
  to `LudoAIEngine.kt` (9 checks) and the board coordinate math (`BoardLayout.kt`).
  See `docs/VERIFICATION_LOG.md` for the exact commands and output.

## What's NOT in this build yet (and why)

- **Online multiplayer (sections 4–6)**: this requires a real Firebase Realtime
  Database project that only you can create (Firebase Console access, billing,
  your own `google-services.json`). The schema and security rules are fully
  designed in `firebase/`, and `LudoGameEngine` is already structured so a
  Cloud Function can run the identical validation server-side — but the
  Cloud Function itself, the room/matchmaking repository code, and the
  Firebase-backed `GameViewModel` variant are not built yet.
- **Google Sign-In** will throw a clear error until you paste your Firebase
  project's Web client ID into `app/src/main/res/values/strings.xml`
  (`default_web_client_id`). Email/password and guest sign-in work with just
  `google-services.json` in place.
- **Chat, leaderboard, shop, daily rewards, FCM notifications (sections
  13, 16–20)**: not started.
- **Sound effects, particle effects on capture, win celebration animation**:
  not started — the board currently has shadows/gradients/highlight glow but
  no sound or particles yet.

## Setup instructions

1. Open this project folder in Android Studio (Koala or newer recommended).
2. Create a Firebase project at https://console.firebase.google.com.
3. Add an Android app to it with package name `com.athar.ludo3d`.
4. Download the generated `google-services.json` and place it at
   `app/google-services.json` (this file is intentionally NOT included here —
   it contains your project's credentials).
5. In Firebase Console → Authentication → Sign-in method, enable:
   Email/Password, Google, and Anonymous.
6. Copy the "Web client ID" shown under the Google provider and paste it into
   `app/src/main/res/values/strings.xml` → `default_web_client_id`.
7. Build & run. Local Multiplayer and Vs Computer work immediately, offline,
   with no Firebase configuration at all — only Login needs the steps above.
8. For online play (Phase 4/5/6), also deploy the Cloud Functions:
   `cd functions && npm install && npm run build`, then
   `firebase deploy --only functions` (needs the Firebase CLI, `firebase login`,
   and your project set via `firebase use --add` or by editing `.firebaserc`
   directly). Also deploy the security rules:
   `firebase deploy --only database`. Without this step, room creation/lobby
   still works (that's plain Realtime Database, no functions involved), but
   starting a game will fail — `gameState` writes are Cloud-Function-only.

## Architecture

```
presentation/   Compose screens, ViewModels, navigation
domain/         model/ (Player, Token, GameState...), engine/ (LudoGameEngine,
                LudoAIEngine — zero Android/Firebase dependency, pure Kotlin),
                repository/ (interfaces only)
data/           firebase/ (real FirebaseAuth-backed implementations)
di/             Hilt modules
```

`LudoGameEngine` and `LudoAIEngine` have no Android or Firebase imports at
all — they're plain Kotlin classes operating on immutable data classes. This
is what makes them runnable in a JVM unit test today, and portable to a
Cloud Function (Node/TypeScript port, same rules) for Phase 6's
server-authoritative validation without the two implementations drifting
apart conceptually.

## Running the unit tests

`app/src/test/java/com/athar/ludo3d/domain/engine/` contains
`LudoGameEngineTest.kt` (16 tests) and `LudoAIEngineTest.kt` (5 tests). In
Android Studio: right-click either file → Run. From the command line once
Android SDK is set up: `./gradlew testDebugUnitTest`.

- **Phase 8 features**: in-game chat (quick messages, emoji, 1.5s client throttle, immutable/length-capped/server-timestamped messages enforced by rules), global leaderboard (indexed `orderByChild("wins")`), profile with derived achievements, 7-day daily rewards, and a cosmetic coin shop. Coins/XP/wins/level are Cloud-Function-only (`initializeUserProfile`, `claimDailyReward`, `purchaseItem`, and the stats update in `submitMove`); `database.rules.json` no longer has a blanket write on `users/{uid}` (RTDB rules cascade permissively, so that would have let a client write its own coins). Verified: `DailyRewardCalculator` 9/9 (Kotlin) and 8/8 (TypeScript port); Cloud Functions compile in strict mode. **Not verified**: the Firebase-backed repositories, Compose screens, and callable handlers (need Android Studio + a Firebase project/emulator). **Known gaps**: friends leaderboard shows an empty state, the shop only records ownership (equipping cosmetics in-game isn't wired), and a client-side chat throttle can be bypassed by a modified APK (a server-side limit is a Phase 9+ hardening step).

- **Phase 9 (sound + effects)**: 7 sound effects (dice rattle, token move, capture, token home, your turn, win, button tap) generated as WAVs in `res/raw` and played through a `SoundPool` (`SoundManager`), with the on/off toggle now persisted across restarts. Which sound or particle fires is decided by `GameEffectDetector`, which diffs the previous and new `GameState`, so local, AI and online games share one code path and reconnecting mid-game doesn't replay old sounds. Capture particle bursts and win confetti come from the seedable `ParticleSystem` and are drawn by `ParticleOverlay`. Verified: detector + particles 18/18 against compiled classes; WAVs checked for duration/peak. **Not verified**: `SoundManager`, `ParticleOverlay` and the Compose wiring (need a device). **Known gaps**: the sounds are simple synthesized tones, not produced audio; a roll with no legal move online arrives as one combined state update, so it plays no dice sound; smooth token-sliding animation isn't done.

## Phase 10: testing and production hardening

**A real, previously-shipped bug found and fixed here**: `themes.xml` set the
app theme's parent to `Theme.Material3.DayNight.NoActionBar`, a style that
only exists in the classic `com.google.android.material:material` library —
which this project never depended on (it uses `androidx.compose.material3`,
a *different*, Compose-only artifact that does not provide that XML style).
This would have failed the build the first time anyone actually ran it in
Android Studio, since Phase 1. Fixed by pointing at the built-in platform
theme `android:Theme.Material.NoActionBar` instead, which needs no extra
dependency. Caught by auditing every resource reference against what
actually exists in `res/`, the same discipline applied below to R.* usages,
Hilt bindings, and icon references — all confirmed clean.

**A real design regression found and fixed here**: Phase 9 had
`GameScreenContent` call `hiltViewModel()` internally for sound, which quietly
broke the "pure, stateless, shared UI" design documented in that file's own
comment, and made it untestable without a full Hilt test harness. Fixed by
hoisting sound into three plain parameters (`soundOn`, `onToggleSound`,
`onPlaySounds`) that `GameScreen`/`OnlineGameScreen` supply from their own
`hiltViewModel()` calls — restoring both the architecture and, not
coincidentally, the ability to write the instrumented tests below at all.

**Firebase Security Rules tests** (`functions/test/database.rules.test.js`,
using `@firebase/rules-unit-testing`): 7 real tests covering unauthenticated
denial, cross-player write rejection, the room-capacity check surviving a
client that skips the transaction, `gameState` being unwritable by any
client (including the room's own current player), chat immutability/length
cap/anti-spoofed-timestamp, and the `users/{uid}` cascade-permissiveness fix
from Phase 8 (writing the whole node with `coins` folded in must fail even
though `name` alone is writable). Each test was cross-checked line-by-line
against the actual `database.rules.json` logic. **Not executed**: running
them needs the Firebase Database Emulator jar, downloaded from
`storage.googleapis.com` — confirmed unreachable (`firebase emulators:start`
gets exactly to "downloading firebase-database-emulator-v4.11.2.jar..." and
fails with `Host not in allowlist`) from this sandbox. Run `npm run
test:rules` yourself.

**Instrumented Compose UI tests** (`app/src/androidTest/.../{GameScreenContentTest,LocalGameSetupScreenTest}.kt`):
9 tests total — roll button enabled/disabled across three distinct reasons
(not your turn, already rolled, both fine), tap-callback wiring, the
finished-game "Back to Home" swap, the turn-timer text, and
`LocalGameSetupScreen`'s player-count-to-color-assignment logic (including
that changing the count actually changes what gets passed to `onStart`).
Deliberately targeted at the two screens with no Hilt dependency, so a plain
`createComposeRule()` suffices. **Not executed**: no Android SDK or emulator
image exists in this sandbox at all (a harder limitation than the Firebase
emulator's blocked download) — run `./gradlew connectedAndroidTest` in
Android Studio.

**ProGuard/R8**: `isMinifyEnabled` was already `true` for release builds
since Phase 1; added `isShrinkResources = true` and a real
`proguard-rules.pro` (previously missing entirely — release builds would
have used only the default rules from dependencies, which happens to work
for this app's dependency set, but the enum `-keep` rules matter here
specifically because `PlayerColor`/`GameStatus`/etc. names are persisted as
strings in Firebase and parsed back with `valueOf()`, which a shrinker could
otherwise rename and silently break).

**Missing launcher icon fixed**: the manifest referenced `@mipmap/ic_launcher`,
which was never created in any phase — another build-blocker, now replaced
with a real (simple) vector drawable.

## Continuing the build

The natural next phases, in order:
1. **Phase 10**: instrumented UI
   tests, ProGuard/R8 tuning, and actually running the Cloud Functions
   against the Firebase emulator or a real project to exercise Phase 6
   end-to-end (see `functions/README.md` for what's verified vs. not).

Each of these needs your Firebase project to exist first (step 2 of Setup
above) before the code can be meaningfully tested end-to-end.
