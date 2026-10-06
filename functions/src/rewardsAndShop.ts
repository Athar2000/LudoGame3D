import * as admin from "firebase-admin";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { evaluateDailyReward } from "./dailyReward";
import { SHOP_CATALOG } from "./shopCatalog";

// Added by Athar
//
// Section 17/19: coins are a value the app grants, so — same principle as
// gameState in index.ts — they must not be a path any client can write
// directly. database.rules.json locks `users/{uid}/coins`,
// `users/{uid}/wins`, `users/{uid}/xp`, and `users/{uid}/level` to
// Cloud-Function-only for exactly this reason; a client CAN still write its
// own `name`/`avatarId`/`favoriteColor` directly (those are cosmetic, not
// value).
//
// claimDailyReward() re-derives everything from the user's OWN stored
// lastClaimedDay/lastClaimedAt (never from anything the client sends) via
// evaluateDailyReward(), the same pure function verified in
// dailyReward.smoke.ts. purchaseItem() checks the item exists in the fixed
// server-side SHOP_CATALOG (never trusts a client-supplied price) and that
// the user can actually afford it before touching anything.

export const ERROR_CODES_REWARDS = {
  ALREADY_CLAIMED_TODAY: "ALREADY_CLAIMED_TODAY",
  ITEM_NOT_FOUND: "ITEM_NOT_FOUND",
  ALREADY_OWNED: "ALREADY_OWNED",
  INSUFFICIENT_COINS: "INSUFFICIENT_COINS",
} as const;

function requireAuth(auth: { uid: string } | undefined): string {
  if (!auth) throw new HttpsError("unauthenticated", "Sign in required.");
  return auth.uid;
}

export const claimDailyReward = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const userRef = admin.database().ref(`users/${uid}`);
  const snapshot = await userRef.get();

  const lastClaimedDay: number = snapshot.child("lastDailyRewardClaimedDay").val() ?? 0;
  const lastClaimedAt: number = snapshot.child("lastDailyRewardClaimedAt").val() ?? 0;

  const outcome = evaluateDailyReward(lastClaimedDay, lastClaimedAt, Date.now());
  if (outcome.kind === "already_claimed_today") {
    throw new HttpsError("failed-precondition", ERROR_CODES_REWARDS.ALREADY_CLAIMED_TODAY);
  }

  const currentCoins: number = snapshot.child("coins").val() ?? 0;
  await userRef.update({
    coins: currentCoins + outcome.reward.coins,
    lastDailyRewardClaimedDay: outcome.newStreakDay,
    lastDailyRewardClaimedAt: admin.database.ServerValue.TIMESTAMP,
  });

  return {
    day: outcome.newStreakDay,
    coinsAwarded: outcome.reward.coins,
    isPremium: outcome.reward.isPremium,
  };
});

export const purchaseItem = onCall(async (request) => {
  const uid = requireAuth(request.auth);
  const itemId: string = request.data?.itemId;
  if (!itemId) throw new HttpsError("invalid-argument", "itemId is required");

  const item = SHOP_CATALOG.find((i) => i.id === itemId);
  if (!item) throw new HttpsError("not-found", ERROR_CODES_REWARDS.ITEM_NOT_FOUND);

  const userRef = admin.database().ref(`users/${uid}`);
  const snapshot = await userRef.get();
  const currentCoins: number = snapshot.child("coins").val() ?? 0;
  const alreadyOwned: boolean = snapshot.child(`ownedItems/${itemId}`).val() === true;

  if (alreadyOwned) throw new HttpsError("failed-precondition", ERROR_CODES_REWARDS.ALREADY_OWNED);
  if (currentCoins < item.priceCoins) {
    throw new HttpsError("failed-precondition", ERROR_CODES_REWARDS.INSUFFICIENT_COINS);
  }

  await userRef.update({
    coins: currentCoins - item.priceCoins,
    [`ownedItems/${itemId}`]: true,
  });

  return { ok: true, remainingCoins: currentCoins - item.priceCoins };
});
