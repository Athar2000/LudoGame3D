package com.athar.ludo3d.domain.engine

// Added by Athar
// Section 19: 7-day escalating reward streak. Pure Kotlin, no Firebase — the
// actual coin grant happens server-side (Cloud Function claimDailyReward,
// functions/src/dailyReward.ts, a port of THIS file) so a client can't just
// write its own coin balance. This class is what decides WHICH day's reward
// applies and whether the streak continues or resets; the Cloud Function is
// what makes that decision trustworthy by running it against the server's
// own clock and the server's own stored lastClaimedAt, not whatever the
// client claims.
data class DailyReward(val day: Int, val coins: Int, val isPremium: Boolean = false)

val DAILY_REWARDS: List<DailyReward> = listOf(
    DailyReward(1, 100),
    DailyReward(2, 150),
    DailyReward(3, 200),
    DailyReward(4, 250),
    DailyReward(5, 300),
    DailyReward(6, 400),
    DailyReward(7, 750, isPremium = true)
)

sealed class DailyRewardOutcome {
    data class Claimed(val reward: DailyReward, val newStreakDay: Int, val claimedAtMillis: Long) : DailyRewardOutcome()
    data object AlreadyClaimedToday : DailyRewardOutcome()
}

class DailyRewardCalculator(private val nowProvider: () -> Long = System::currentTimeMillis) {

    companion object {
        const val ONE_DAY_MS = 24L * 60 * 60 * 1000
        const val STREAK_GRACE_MS = 2 * ONE_DAY_MS // miss more than this since last claim -> streak resets to day 1
    }

    /**
     * evaluate() — the whole rule in one place: prevents claiming twice
     * within 24h (section 19's "prevent claiming the same reward repeatedly"),
     * advances the streak by one day if claimed within the grace window,
     * otherwise resets to day 1. Day 7 wraps back to day 1 on the next claim.
     */
    fun evaluate(lastClaimedDay: Int, lastClaimedAtMillis: Long): DailyRewardOutcome {
        val now = nowProvider()
        val elapsed = if (lastClaimedAtMillis <= 0) Long.MAX_VALUE else now - lastClaimedAtMillis

        if (lastClaimedAtMillis > 0 && elapsed < ONE_DAY_MS) {
            return DailyRewardOutcome.AlreadyClaimedToday
        }

        val nextDay = if (elapsed >= STREAK_GRACE_MS) 1 else (lastClaimedDay % 7) + 1
        val reward = DAILY_REWARDS[nextDay - 1]
        return DailyRewardOutcome.Claimed(reward, nextDay, now)
    }
}
