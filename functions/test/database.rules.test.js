// Added by Athar (Phase 10)
//
// Real tests against database.rules.json, using @firebase/rules-unit-testing
// against the Firebase Local Emulator Suite — NOT mocked, and NOT the same
// thing as the ludoEngine/dailyReward smoke tests (those exercise pure logic
// in-process; these exercise the actual rules file through a real emulator
// evaluating real JSON rules).
//
// Run with: npm run test:rules
//
// STATUS IN THIS REPO: written and reviewed, NOT executed here. Running it
// requires the Firebase Database Emulator jar, downloaded from
// storage.googleapis.com — confirmed unreachable from this sandbox's network
// allowlist (`firebase emulators:start` gets exactly to "downloading
// firebase-database-emulator-v4.11.2.jar..." and then fails with
// "Host not in allowlist: storage.googleapis.com"). This is a sandbox
// limitation, not a statement about whether these tests pass — run
// `npm run test:rules` yourself once you have normal internet access.
const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-ludo3d",
    database: {
      rules: fs.readFileSync(path.resolve(__dirname, "../../firebase/database.rules.json"), "utf8"),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

function asUser(uid) {
  return testEnv.authenticatedContext(uid).database();
}
function unauthenticated() {
  return testEnv.unauthenticatedContext().database();
}

test("an unauthenticated client cannot read or write a room", async () => {
  const db = unauthenticated();
  await assertFails(db.ref("rooms/ATHAR1234/status").set("WAITING"));
  await assertFails(db.ref("rooms/ATHAR1234").get());
});

test("an authenticated user can create their own player node, not someone else's", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("rooms/R1").set({ hostId: "alice", status: "WAITING", maxPlayers: 4 });
  });
  const alice = asUser("alice");
  const bob = asUser("bob");

  await assertSucceeds(
    alice.ref("rooms/R1/players/alice").set({ name: "Alice", color: "RED", ready: false, connected: true })
  );
  await assertFails(
    bob.ref("rooms/R1/players/alice").set({ name: "Hacked", color: "BLUE", ready: true, connected: true })
  );
});

test("a room-full write is rejected even bypassing the client's own transaction logic", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("rooms/R2").set({
      hostId: "p1",
      status: "WAITING",
      maxPlayers: 2,
      players: {
        p1: { name: "P1", color: "RED" },
        p2: { name: "P2", color: "GREEN" },
      },
    });
  });
  // Room already has 2/2 players; a 3rd player writing directly (skipping
  // FirebaseRoomRepository's transaction) must still be rejected by the
  // ".write" rule's own numChildren() < maxPlayers check.
  const gatecrasher = asUser("p3");
  await assertFails(
    gatecrasher.ref("rooms/R2/players/p3").set({ name: "P3", color: "YELLOW", ready: false, connected: true })
  );
});

test("gameState cannot be written by ANY client, even a room member", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("rooms/R3").set({
      hostId: "p1",
      status: "IN_PROGRESS",
      maxPlayers: 2,
      players: { p1: { name: "P1", color: "RED" }, p2: { name: "P2", color: "GREEN" } },
    });
  });
  const p1 = asUser("p1"); // the actual current player, per the game state
  await assertFails(
    p1.ref("rooms/R3/gameState").set({
      status: "FINISHED",
      players: [],
      currentPlayerIndex: 0,
      winnerId: "p1", // exactly the kind of forged-winner write Phase 6 exists to block
    })
  );
});

test("chat messages are immutable, length-capped, and cannot use a spoofed timestamp", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("rooms/R4").set({
      hostId: "p1",
      status: "IN_PROGRESS",
      players: { p1: { name: "P1", color: "RED" } },
    });
  });
  const p1 = asUser("p1");

  await assertFails(
    p1.ref("rooms/R4/chat/m1").set({ senderId: "p1", text: "hi", sentAt: 123 }) // real now would differ; rule requires sentAt === now
  );
  await assertSucceeds(
    p1.ref("rooms/R4/chat/m1").set({ senderId: "p1", text: "hi", sentAt: { ".sv": "timestamp" } })
  );
  // Immutable: overwriting an existing message must fail even from the original sender.
  await assertFails(
    p1.ref("rooms/R4/chat/m1").set({ senderId: "p1", text: "edited", sentAt: { ".sv": "timestamp" } })
  );
  // Length cap.
  await assertFails(
    p1.ref("rooms/R4/chat/m2").set({ senderId: "p1", text: "x".repeat(201), sentAt: { ".sv": "timestamp" } })
  );
});

test("a user can update their cosmetic fields but never coins/wins/xp/level directly", async () => {
  const alice = asUser("alice");
  await assertSucceeds(alice.ref("users/alice/name").set("Alice"));
  await assertSucceeds(alice.ref("users/alice/avatarId").set("astronaut"));
  await assertFails(alice.ref("users/alice/coins").set(999999));
  await assertFails(alice.ref("users/alice/wins").set(999));
  await assertFails(alice.ref("users/alice/level").set(99));
  // Also blocked at the object level: writing the whole node (which would
  // include coins) must fail too, even though `name` alone is writable —
  // this is the actual cascade-permissiveness bug fixed in Phase 8.
  await assertFails(alice.ref("users/alice").set({ name: "Alice", coins: 999999 }));
});

test("any authenticated user can read the leaderboard-relevant fields of any profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.database().ref("users/bob").set({ name: "Bob", wins: 5, coins: 100 });
  });
  await assertSucceeds(asUser("alice").ref("users/bob/wins").get());
});
