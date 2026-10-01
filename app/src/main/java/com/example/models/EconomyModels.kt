package com.example.models

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Canonical Transaction Types for CineStream Points Economy (Phase 03A).
 * Strict closed-loop internal currency ledger.
 */
enum class PointTransactionType {
    DAILY_LOGIN,
    REWARDED_AD,
    TASK_REWARD,
    GAME_REWARD,
    LEADERBOARD_REWARD,
    SUBSCRIPTION_REDEMPTION,
    ADMIN_GRANT,
    ADMIN_ADJUSTMENT,
    REVERSAL;

    companion object {
        fun fromString(value: String?): PointTransactionType {
            return when (value?.trim()?.uppercase()) {
                "DAILY_LOGIN" -> DAILY_LOGIN
                "REWARDED_AD" -> REWARDED_AD
                "TASK_REWARD" -> TASK_REWARD
                "GAME_REWARD" -> GAME_REWARD
                "LEADERBOARD_REWARD" -> LEADERBOARD_REWARD
                "SUBSCRIPTION_REDEMPTION" -> SUBSCRIPTION_REDEMPTION
                "ADMIN_GRANT" -> ADMIN_GRANT
                "ADMIN_ADJUSTMENT" -> ADMIN_ADJUSTMENT
                "REVERSAL" -> REVERSAL
                else -> ADMIN_ADJUSTMENT
            }
        }
    }
}

/**
 * Immutable Point Ledger Record.
 * Stored at: /users/{uid}/point_transactions/{txId}
 */
@IgnoreExtraProperties
data class PointTransaction(
    val txId: String = "",
    val userId: String = "",
    val type: String = PointTransactionType.ADMIN_ADJUSTMENT.name,
    val amount: Long = 0L,
    val balanceBefore: Long = 0L,
    val balanceAfter: Long = 0L,
    val referenceId: String? = null,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val actorUid: String = ""
) {
    val transactionType: PointTransactionType
        get() = PointTransactionType.fromString(type)
}

/**
 * Canonical Reward Task Item.
 * Stored at: /reward_tasks/{taskId}
 */
@IgnoreExtraProperties
data class RewardTask(
    val taskId: String = "",
    val title: String = "",
    val description: String = "",
    val rewardPoints: Long = 50L,
    val taskType: String = "CUSTOM",
    val actionUrl: String? = null,
    val isActive: Boolean = true,
    val expiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Supported Feature Control States.
 */
enum class FeatureState {
    ACTIVE,
    COMING_SOON,
    DISABLED;

    companion object {
        fun fromString(value: String?): FeatureState {
            return when (value?.trim()?.uppercase()) {
                "ACTIVE" -> ACTIVE
                "COMING_SOON", "COMINGSOON", "SOON" -> COMING_SOON
                "DISABLED" -> DISABLED
                else -> ACTIVE
            }
        }
    }
}

/**
 * Feature Control Item Configuration.
 */
@IgnoreExtraProperties
data class FeatureItemConfig(
    val status: String = FeatureState.ACTIVE.name,
    val disabledMessage: String = "هذه الميزة غير متاحة حالياً",
    val comingSoonMessage: String = "هذه الميزة ستضاف قريبًا"
) {
    val state: FeatureState
        get() = FeatureState.fromString(status)
}

/**
 * Canonical System Feature Control Configuration.
 * Stored at: /config/features
 */
@IgnoreExtraProperties
data class FeatureControlConfig(
    val subscriptions: FeatureItemConfig = FeatureItemConfig(),
    val points: FeatureItemConfig = FeatureItemConfig(),
    val dailyLogin: FeatureItemConfig = FeatureItemConfig(),
    val rewardedAds: FeatureItemConfig = FeatureItemConfig(),
    val tasks: FeatureItemConfig = FeatureItemConfig(),
    val leaderboard: FeatureItemConfig = FeatureItemConfig(),
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
)

/**
 * Canonical System Economy Configuration.
 * Stored at: /config/economy
 */
@IgnoreExtraProperties
data class EconomyConfig(
    val redemptionCosts: Map<String, Long> = mapOf(
        "pro_lite_1d" to 50L,
        "pro_lite_7d" to 250L,
        "pro_lite_10d" to 350L,
        "pro_30d" to 1000L
    ),
    val dailyLoginRewards: List<Long> = listOf(10L, 15L, 20L, 25L, 30L, 40L, 50L),
    val rewardedAdPoints: Long = 15L,
    val rewardedAdDailyCap: Int = 5,
    val rewardedAdCooldownSeconds: Int = 300,
    val updatedAt: Long = 0L
)

/**
 * Leaderboard Reward Definition.
 */
@IgnoreExtraProperties
data class LeaderboardReward(
    val points: Long = 0L,
    val planId: String? = null
)

/**
 * Canonical Leaderboard Configuration.
 */
@IgnoreExtraProperties
data class LeaderboardConfig(
    val cycle: String = "WEEKLY", // Monday 00:00 UTC through Sunday 23:59 UTC
    val metric: String = "weeklyEarnedPoints",
    val firstPlaceReward: LeaderboardReward = LeaderboardReward(points = 500L, planId = "pro_lite_7d"),
    val secondPlaceReward: LeaderboardReward = LeaderboardReward(points = 300L, planId = "pro_lite_1d"),
    val thirdPlaceReward: LeaderboardReward = LeaderboardReward(points = 150L, planId = null)
)
