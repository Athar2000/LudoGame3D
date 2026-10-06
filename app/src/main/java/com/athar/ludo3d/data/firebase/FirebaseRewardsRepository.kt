package com.athar.ludo3d.data.firebase

import com.athar.ludo3d.domain.repository.DailyClaimResult
import com.athar.ludo3d.domain.repository.RewardsError
import com.athar.ludo3d.domain.repository.RewardsRepository
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Added by Athar
// Same pattern as FirebaseGameSyncRepository (Phase 6): both mutating calls
// here are httpsCallable to Cloud Functions (functions/src/rewardsAndShop.ts),
// never a direct write to users/{uid}/coins — that path isn't client-writable
// at all per database.rules.json.
@Singleton
class FirebaseRewardsRepository @Inject constructor(
    private val functions: FirebaseFunctions
) : RewardsRepository {

    override suspend fun claimDailyReward(): Result<DailyClaimResult> = runCatching {
        val result = functions.getHttpsCallable("claimDailyReward").call().await()
        @Suppress("UNCHECKED_CAST")
        val data = result.data as Map<String, Any?>
        DailyClaimResult(
            day = (data["day"] as? Long)?.toInt() ?: 0,
            coinsAwarded = (data["coinsAwarded"] as? Long)?.toInt() ?: 0,
            isPremium = data["isPremium"] as? Boolean ?: false
        )
    }.recoverCatching { e -> throw e.toRewardsError() }

    override suspend fun purchaseItem(itemId: String): Result<Unit> = runCatching {
        functions.getHttpsCallable("purchaseItem").call(mapOf("itemId" to itemId)).await()
        Unit
    }.recoverCatching { e -> throw e.toRewardsError() }

    private fun Throwable.toRewardsError(): Throwable {
        val ffe = this as? FirebaseFunctionsException ?: return this
        return when (ffe.message) {
            "ALREADY_CLAIMED_TODAY" -> RewardsError.AlreadyClaimedToday
            "ITEM_NOT_FOUND" -> RewardsError.ItemNotFound
            "ALREADY_OWNED" -> RewardsError.AlreadyOwned
            "INSUFFICIENT_COINS" -> RewardsError.InsufficientCoins
            else -> this
        }
    }
}
