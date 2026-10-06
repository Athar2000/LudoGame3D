// Added by Athar
// Field-for-field port of app/src/main/java/com/athar/ludo3d/domain/engine/DailyRewardCalculator.kt.
// See that file's comment for why this must run server-side: granting coins
// is exactly the kind of write a client cannot be trusted to compute honestly.

export interface DailyReward {
  day: number;
  coins: number;
  isPremium: boolean;
}

export const DAILY_REWARDS: DailyReward[] = [
  { day: 1, coins: 100, isPremium: false },
  { day: 2, coins: 150, isPremium: false },
  { day: 3, coins: 200, isPremium: false },
  { day: 4, coins: 250, isPremium: false },
  { day: 5, coins: 300, isPremium: false },
  { day: 6, coins: 400, isPremium: false },
  { day: 7, coins: 750, isPremium: true },
];

export const ONE_DAY_MS = 24 * 60 * 60 * 1000;
export const STREAK_GRACE_MS = 2 * ONE_DAY_MS;

export type DailyRewardOutcome =
  | { kind: "claimed"; reward: DailyReward; newStreakDay: number; claimedAtMillis: number }
  | { kind: "already_claimed_today" };

export function evaluateDailyReward(
  lastClaimedDay: number,
  lastClaimedAtMillis: number,
  now: number
): DailyRewardOutcome {
  const elapsed = lastClaimedAtMillis <= 0 ? Number.MAX_SAFE_INTEGER : now - lastClaimedAtMillis;

  if (lastClaimedAtMillis > 0 && elapsed < ONE_DAY_MS) {
    return { kind: "already_claimed_today" };
  }

  const nextDay = elapsed >= STREAK_GRACE_MS ? 1 : (lastClaimedDay % 7) + 1;
  const reward = DAILY_REWARDS[nextDay - 1];
  return { kind: "claimed", reward, newStreakDay: nextDay, claimedAtMillis: now };
}
