import * as admin from "firebase-admin";
// Added by Athar
// v1 auth triggers are used here deliberately, not v2 identity triggers:
// v2's equivalent (beforeUserCreated/afterUserCreated under
// firebase-functions/v2/identity) requires Identity Platform + Blaze,
// which is a heavier prerequisite than a brand-new project necessarily has.
// The v1 "onCreate" trigger works on the default Auth provider with no
// extra setup, which fits the "run this the moment you have a Firebase
// project" spirit of this whole file set.
import * as functionsV1 from "firebase-functions/v1";

const STARTER_COINS = 100;

// Added by Athar
// Section 3: "Create a unique player ID for every user" — Firebase Auth
// already gives every user a unique uid; this is the other half, giving
// that uid a starter profile the moment it's created. Runs automatically on
// EVERY sign-up path (email/password, Google, and anonymous/guest), so
// there's no separate "did the client remember to call this" step that
// could be skipped or spoofed.
export const initializeUserProfile = functionsV1.auth.user().onCreate(async (user) => {
  // Admin SDK's UserRecord has no isAnonymous flag (that's a client-only
  // FirebaseUser concept) — an anonymous/guest sign-in has no linked
  // provider, so an empty providerData array is the server-side signal.
  const isAnonymous = user.providerData.length === 0;
  const displayName = user.displayName?.trim() || (isAnonymous ? `Guest-${user.uid.slice(0, 6)}` : "Player");

  await admin.database().ref(`users/${user.uid}`).set({
    name: displayName,
    email: user.email ?? null,
    avatarId: "default",
    coins: STARTER_COINS,
    level: 1,
    xp: 0,
    wins: 0,
    gamesPlayed: 0,
    lastDailyRewardClaimedDay: 0,
    lastDailyRewardClaimedAt: 0,
    ownedItems: { default: true },
  });
});
