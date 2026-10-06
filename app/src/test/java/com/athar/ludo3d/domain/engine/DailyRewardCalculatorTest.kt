package com.athar.ludo3d.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Added by Athar
// Mirrors the standalone runner verified against the compiled class
// (9/9) and functions/src/dailyReward.smoke.ts (8/8).
class DailyRewardCalculatorTest {
    private val day = DailyRewardCalculator.ONE_DAY_MS

    private fun claimed(o: DailyRewardOutcome) = o as DailyRewardOutcome.Claimed

    @Test fun `first claim ever is day 1 for 100 coins`() {
        val o = claimed(DailyRewardCalculator { 1_000_000L }.evaluate(0, 0L))
        assertEquals(1, o.newStreakDay); assertEquals(100, o.reward.coins)
    }

    @Test fun `claiming within 24h is rejected`() {
        val o = DailyRewardCalculator { 1_000_000L + day / 2 }.evaluate(1, 1_000_000L)
        assertTrue(o is DailyRewardOutcome.AlreadyClaimedToday)
    }

    @Test fun `claim after 24h advances streak`() {
        assertEquals(2, claimed(DailyRewardCalculator { 1_000_000L + day + 1000 }.evaluate(1, 1_000_000L)).newStreakDay)
    }

    @Test fun `missing more than grace window resets to day 1`() {
        assertEquals(1, claimed(DailyRewardCalculator { 1_000_000L + 3 * day }.evaluate(4, 1_000_000L)).newStreakDay)
    }

    @Test fun `day 7 wraps to day 1`() {
        assertEquals(1, claimed(DailyRewardCalculator { 1_000_000L + day + 1000 }.evaluate(7, 1_000_000L)).newStreakDay)
    }

    @Test fun `day 7 is premium and highest value`() {
        assertTrue(DAILY_REWARDS[6].isPremium)
        assertEquals(DAILY_REWARDS.maxOf { it.coins }, DAILY_REWARDS[6].coins)
    }
}
