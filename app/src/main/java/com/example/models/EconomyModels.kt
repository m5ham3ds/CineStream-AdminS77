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
) {
    fun validate(existingTaskIds: Set<String> = emptySet(), isNew: Boolean = false): List<String> {
        val errors = mutableListOf<String>()
        val cleanId = taskId.trim()
        if (cleanId.isBlank()) {
            errors.add("Task ID is required")
        } else if (!cleanId.matches(Regex("^[a-zA-Z0-9_-]{2,64}$"))) {
            errors.add("Task ID must be 2-64 alphanumeric characters, underscores, or hyphens")
        } else if (isNew && existingTaskIds.contains(cleanId)) {
            errors.add("Task ID '$cleanId' already exists. Must be unique.")
        }

        if (title.trim().isBlank()) {
            errors.add("Task title is required")
        }
        if (rewardPoints < 0L) {
            errors.add("Reward points cannot be negative ($rewardPoints)")
        }
        if (!TaskTypes.ALL.contains(taskType.trim().uppercase())) {
            errors.add("Unknown task type '$taskType'. Allowed: ${TaskTypes.ALL}")
        }
        return errors
    }
}

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
) {
    companion object {
        val CANONICAL_SKUS = setOf("pro_lite_1d", "pro_lite_7d", "pro_lite_10d", "pro_30d")

        fun validateRedemptionCosts(costs: Map<String, Long>): List<String> {
            val errors = mutableListOf<String>()
            for ((sku, cost) in costs) {
                if (!CANONICAL_SKUS.contains(sku)) {
                    errors.add("Unknown or non-canonical SKU: '$sku'. Only canonical SKUs are permitted ($CANONICAL_SKUS)")
                }
                if (cost <= 0L) {
                    errors.add("Points cost for '$sku' must be strictly positive (got $cost)")
                }
            }
            CANONICAL_SKUS.forEach { requiredSku ->
                if (!costs.containsKey(requiredSku)) {
                    errors.add("Missing price for canonical SKU: '$requiredSku'")
                }
            }
            return errors
        }

        fun validateDailyLoginRewards(rewards: List<Long>): List<String> {
            val errors = mutableListOf<String>()
            if (rewards.size != 7) {
                errors.add("Daily login ladder must contain exactly 7 days (got ${rewards.size})")
            }
            rewards.forEachIndexed { index, reward ->
                if (reward < 0L) {
                    errors.add("Reward for Day ${index + 1} cannot be negative (got $reward)")
                }
            }
            return errors
        }

        fun validateRewardedAds(points: Long, dailyCap: Int, cooldownSeconds: Int): List<String> {
            val errors = mutableListOf<String>()
            if (points < 0L) errors.add("Rewarded ad points cannot be negative ($points)")
            if (dailyCap < 0) errors.add("Daily ad cap cannot be negative ($dailyCap)")
            if (cooldownSeconds < 0) errors.add("Ad cooldown seconds cannot be negative ($cooldownSeconds)")
            return errors
        }
    }
}

/**
 * Canonical Task Types.
 */
object TaskTypes {
    const val CUSTOM = "CUSTOM"
    const val WATCH_VIDEO = "WATCH_VIDEO"
    const val FOLLOW_SOCIAL = "FOLLOW_SOCIAL"
    const val SURVEY = "SURVEY"
    const val SHARE_APP = "SHARE_APP"

    val ALL = listOf(CUSTOM, WATCH_VIDEO, FOLLOW_SOCIAL, SURVEY, SHARE_APP)
}

/**
 * Leaderboard Entry for Current Weekly Leaderboard.
 */
@IgnoreExtraProperties
data class LeaderboardEntry(
    val rank: Int = 0,
    val userId: String = "",
    val displayName: String = "",
    val points: Long = 0L,
    val avatarUrl: String = ""
)

/**
 * Weekly Leaderboard Snapshot (/leaderboard/weekly_current).
 * Strictly Read-Only in Admin App.
 */
@IgnoreExtraProperties
data class WeeklyLeaderboard(
    val cycleId: String = "WEEKLY_CURRENT",
    val updatedAt: Long = 0L,
    val rankings: List<LeaderboardEntry> = emptyList()
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
