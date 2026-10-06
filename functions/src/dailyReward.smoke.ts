import { evaluateDailyReward, DAILY_REWARDS, ONE_DAY_MS } from "./dailyReward";

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

// 1. First-ever claim grants day 1.
{
  const outcome = evaluateDailyReward(0, 0, 1_000_000);
  check("first claim ever is day 1", outcome.kind === "claimed" && outcome.newStreakDay === 1);
  check("day 1 grants 100 coins", outcome.kind === "claimed" && outcome.reward.coins === 100);
}

// 2. Claiming again within 24h is rejected.
{
  const start = 1_000_000;
  const outcome = evaluateDailyReward(1, start, start + ONE_DAY_MS / 2);
  check("claiming again within 24h is rejected", outcome.kind === "already_claimed_today");
}

// 3. Claiming after 24h+ advances the streak.
{
  const start = 1_000_000;
  const outcome = evaluateDailyReward(1, start, start + ONE_DAY_MS + 1000);
  check("claim after 24h+ advances to day 2", outcome.kind === "claimed" && outcome.newStreakDay === 2);
}

// 4. Missing more than the grace window resets to day 1.
{
  const start = 1_000_000;
  const outcome = evaluateDailyReward(4, start, start + 3 * ONE_DAY_MS);
  check("missing 3 days resets streak to day 1", outcome.kind === "claimed" && outcome.newStreakDay === 1);
}

// 5. Day 7 wraps to day 1.
{
  const start = 1_000_000;
  const outcome = evaluateDailyReward(7, start, start + ONE_DAY_MS + 1000);
  check("day 7 wraps to day 1", outcome.kind === "claimed" && outcome.newStreakDay === 1);
}

// 6. Day 7 is premium and highest value.
{
  check("day 7 is marked premium", DAILY_REWARDS[6].isPremium);
  check(
    "day 7 has the highest coin value",
    DAILY_REWARDS[6].coins === Math.max(...DAILY_REWARDS.map((r) => r.coins))
  );
}

console.log(`\n===== ${pass} passed, ${fail} failed =====`);
if (fail > 0) process.exit(1);
