# Verification log

This is not marketing copy — these are the actual commands run against the
actual files in this repo during development, and their actual output.
Android Studio wasn't available in the sandbox this was built in, so a real
Kotlin compiler (kotlinc 1.9.24, the same version pinned in `build.gradle.kts`)
was downloaded and used directly to compile and execute the Android-independent
layers (domain/model, domain/engine, and the board coordinate math), instead
of only describing what the code "should" do.

## Commands

```bash
kotlinc app/src/main/java/com/athar/ludo3d/domain/model/*.kt \
        app/src/main/java/com/athar/ludo3d/domain/engine/*.kt \
        app/src/main/java/com/athar/ludo3d/presentation/screens/game/board/BoardLayout.kt \
        -d out
# -> compiles clean, zero errors
```

Standalone runners (not JUnit — JUnit's jar wasn't reachable from the sandbox's
network allowlist) exercised the compiled classes directly:

- Engine: 16/16 checks passed — dice range, base-release-on-six, movement,
  extra turn on six, turn rotation, capture, safe-square protection, overshoot
  rejection, exact finish, winner detection, out-of-turn rejection, triple-six
  forfeiture.
- AI: 9/9 checks passed — capture preference, EASY randomness, HARD risk
  avoidance, "never returns an illegal move" across 30 random boards.
- Online sync orchestration: 12/12 checks passed — turn-stamping decisions
  (does a roll/move actually change whose turn it is), the bounded
  disconnected-player skip loop, expired-turn handling, and winner detection
  mid-sync, all run against the real compiled `LudoGameEngine`. This
  validates the ORCHESTRATION logic in `FirebaseGameSyncRepository`; the
  actual Firebase Android SDK jars aren't reachable from this sandbox's
  network allowlist, so the class that does real Firebase I/O couldn't be
  compiled here — that needs Android Studio + a real Firebase project, same
  as everything else marked "needs your Firebase project" in the README.
- Board geometry: 52 distinct path cells, all four color starts land on the
  exact global square the engine expects, home-column cells don't overlap the
  path.

## Phase 6: the TypeScript Cloud Functions engine port

`functions/src/ludoEngine.ts` is a field-for-field port of the Kotlin
`LudoGameEngine`, meant to run server-side with the Admin SDK. Unlike the
Kotlin/Compose layers, Node and npm ARE reachable from this sandbox, so this
one was verified the same rigorous way as the Kotlin engine, not just
reviewed:

```bash
cd functions
npm install                      # real dependency install, 240 packages
./node_modules/.bin/tsc          # strict mode, noUnusedLocals, noUnusedParameters — zero errors
node lib/ludoEngine.smoke.js     # 20/20 scenarios passed
```

The 20 scenarios mirror the Kotlin engine's JUnit tests exactly (dice range,
six-to-release, extra turn on six, capture, safe-square protection, overshoot
rejection, exact finish, winner detection, out-of-turn rejection, triple-six
forfeiture, isSafeCell) — a pass on both sides is real evidence the two
implementations agree, not just that each independently claims to be correct.

What's NOT verified: `functions/src/index.ts`, the actual callable Cloud
Function handlers. They compile (same `tsc` run above covers the whole
`src/` directory) but weren't executed, because that requires either a
deployed Firebase project or the Firebase emulator suite, and neither is
reachable from this sandbox (no Firebase CLI login, no emulator binaries on
the network allowlist). Run `firebase emulators:start --only functions` and
call each function before trusting them in production — see
`functions/README.md`.

The equivalent JUnit test files (`LudoGameEngineTest.kt`, `LudoAIEngineTest.kt`)
mirror these same scenarios and will run in Android Studio /
`./gradlew testDebugUnitTest` — they weren't the tool used to verify during
this build only because the sandbox couldn't reach Maven Central to fetch the
JUnit jar.

## What this does NOT verify

Compose UI code (screens, navigation, the Canvas board renderer) requires the
Android SDK and Compose runtime, which this sandbox does not have — those
files were written carefully and reviewed manually but not compiled or run
here. Build them in Android Studio and fix any reported errors before
treating them as final.

## Phase 8

- `DailyRewardCalculator` (Kotlin): 9/9 checks against the compiled class. Its TypeScript port `functions/src/dailyReward.ts`: 8/8 via `node lib/dailyReward.smoke.js`.
- `tsc` strict mode caught a real bug (`UserRecord.isAnonymous` doesn't exist in the Admin SDK); fixed by treating empty `providerData` as anonymous.
- All Cloud Functions (`index.ts`, `rewardsAndShop.ts`, `onUserCreate.ts`) compile. They are NOT executed here (no emulator/project).
- Not verified: Firebase repositories, chat/leaderboard/profile/shop/rewards screens, HomeViewModel wiring.
- Regression at end of Phase 8: Kotlin engine 16, AI 9, sync logic 12, rewards 9; TypeScript engine 20, rewards 8.

## Phase 9

- `GameEffectDetector` + `ParticleSystem` compiled with kotlinc and run against 18 checks (first-emission silence, dice, move, capture-over-move priority with victim position, token home, YOUR_TURN gating incl. pass-and-play, WIN fires once, particle determinism/expiry): 18/18.
- The 7 WAVs were synthesized with Python's `wave` module and read back: all 0.05-1.4 s, peaks 0.3-0.7 (no clipping). Whether they *sound* good needs a human ear.
- Not verified: SoundManager (SoundPool), SoundViewModel, ParticleOverlay, GameScreenContent wiring.
- Also fixed: local GameViewModel never published the rolled dice when no move was legal, so the number was never shown; it now publishes before the pause.

## Phase 10

- Full resource audit: every `R.string`/`R.raw` reference matched against
  `res/`; every Hilt ViewModel's constructor params matched against
  `@Provides`/`@Binds`/`@Inject constructor` — all clean.
- Found & fixed a real, previously-shipped build-blocker: `themes.xml`
  referenced a style (`Theme.Material3.DayNight.NoActionBar`) from a library
  this project never depends on. Also missing: a launcher icon (manifest
  pointed at a mipmap that was never created).
- Found & fixed a real architecture regression from Phase 9: `GameScreenContent`
  had started calling `hiltViewModel()` internally, breaking its documented
  "pure, stateless" design and making it untestable without a Hilt harness.
  Refactored to accept sound as plain parameters.
- Regression after all of the above: Kotlin engine 16, AI 9, sync 12,
  rewards 9, effects/particles 18 (= 64); TypeScript engine 20, rewards 8
  (= 28). 92 total, all still passing.
- Wrote 7 Firebase Security Rules tests (`@firebase/rules-unit-testing`) and
  9 instrumented Compose UI tests. Neither could run in this sandbox —
  confirmed exactly where each stops (emulator JAR download blocked by the
  network allowlist; no Android SDK/emulator image present at all,
  respectively) rather than assumed. Both are real code, cross-checked
  against the actual rules file / composable signatures, ready to run in a
  normal environment.

## Post-Phase-10 audit (in response to "complete it")

No sandbox here can compile the Android app or run it on a device — that
boundary doesn't move no matter how many more passes are done. What DOES
move with more effort is how many bugs are caught before your first real
build. This pass re-read every file touched by the Phase 9/10 sound-hoisting
refactor end-to-end (not just diffed), re-verified `NavGraph.kt` and
`Screen.kt` route-string-to-nav-arg consistency in full, re-ran the
hiltViewModel()-import cross-check (2 hits, both false positives — leftover
comment text, not actual calls), and checked `build.gradle.kts` for
duplicate/mismatched dependency declarations. No new bugs found this round —
which is meaningfully different from "no bugs exist"; it just means this
particular reviewer, reading this carefully, on this pass, found none. A
real compiler will still be the first genuine test.

## Real build error from the user's machine, fixed

First actual Android Studio sync attempt failed with:
`NoSuchMethodError: Configuration.fileCollection(Spec)` inside Kotlin's kapt
plugin. Root cause: `gradle/wrapper/gradle-wrapper.properties` was never
included in this project at all, across every phase — so Android Studio
fell back to its own (much newer) bundled Gradle, which removed an API that
Kotlin 1.9.24's kapt plugin still calls. Fixed by adding
`gradle-wrapper.properties` pinning Gradle 8.7, the documented minimum for
AGP 8.5.2 (the version already set in app/build.gradle.kts since Phase 1).
This is exactly the kind of bug that only a real build attempt could surface
— no amount of additional manual review here would have caught a missing
file whose absence only matters at Gradle-sync time.
