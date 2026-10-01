package com.example.models

/**
 * Pure derived analytics models for Phase C6 — Admin Analytics & Operational Dashboard.
 *
 * Invariants:
 * - Read-only derived domain models.
 * - Sourced strictly from canonical Firestore collections (/users, /managed_extensions,
 *   /support_conversations, /pro_requests, /auditLogs).
 * - Zero external analytics backends or SDKs.
 * - Zero local analytics database storage.
 * - Defensive handling against empty datasets and malformed documents.
 */

data class UserMetrics(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val inactiveUsers: Int = 0,
    val bannedUsers: Int = 0, // Canonical global account bans (isAccountBanned)
    val featureRestrictedUsers: Int = 0, // Users with active feature restrictions but not globally banned
    val totalRestrictedUsers: Int = 0, // Users with global ban OR feature restrictions
    val premiumUsers: Int = 0, // Total users with PRO/VIP access
    val freeUsers: Int = 0, // Users on Free tier
    val activePremium: Int = 0, // Users with active, non-expired PRO
    val expiredPremium: Int = 0, // Users with expired PRO
    val tierBreakdown: Map<String, Int> = emptyMap()
)

data class FeatureRestrictionMetrics(
    val watchRestricted: Int = 0,
    val downloadRestricted: Int = 0,
    val chatRestricted: Int = 0,
    val storyRestricted: Int = 0,
    val p2pRestricted: Int = 0
)

data class SupportMetrics(
    val totalConversations: Int = 0,
    val openConversations: Int = 0,
    val unreadConversations: Int = 0
)

data class ProRequestMetrics(
    val totalRequests: Int = 0,
    val pendingRequests: Int = 0,
    val approvedRequests: Int = 0,
    val rejectedRequests: Int = 0
)

data class ManagedExtensionMetrics(
    val totalExtensions: Int = 0,
    val activeExtensions: Int = 0,
    val maintenanceExtensions: Int = 0,
    val disabledExtensions: Int = 0,
    val deprecatedExtensions: Int = 0
)

sealed class DashboardSectionState<out T> {
    object Loading : DashboardSectionState<Nothing>()
    data class Success<T>(val data: T) : DashboardSectionState<T>()
    data class Error(val message: String) : DashboardSectionState<Nothing>()
}

enum class DashboardTab {
    OVERVIEW,
    USERS
}

/**
 * Pure, isolated calculator for Phase C6 metrics.
 * Deterministic, easily unit-testable, handles empty/malformed inputs safely.
 */
object DashboardAnalyticsCalculator {

    /**
     * Calculates User & Subscription metrics using canonical C1/C5 rules.
     * Active/Inactive threshold preserves C5 semantics (30-day window based on lastLoginTimestamp).
     */
    fun calculateUserMetrics(users: List<User>, currentTimeMillis: Long = System.currentTimeMillis()): UserMetrics {
        if (users.isEmpty()) {
            return UserMetrics()
        }

        val total = users.size
        val threshold = currentTimeMillis - 30L * 24 * 60 * 60 * 1000
        val active = users.count { it.lastLoginTimestamp >= threshold }
        val inactive = (total - active).coerceAtLeast(0)

        val globalBans = users.count { it.isAccountBanned }
        val featureRestrictedOnly = users.count { !it.isAccountBanned && it.hasFeatureRestrictions }
        val totalRestricted = users.count { it.isAccountBanned || it.hasFeatureRestrictions }

        val activePrem = users.count { it.subscriptionState.isAdFree }
        val expiredPrem = users.count { it.subscriptionState == SubscriptionState.EXPIRED_PRO || it.subscriptionState == SubscriptionState.EXPIRED }
        val isPremTotal = users.count {
            it.isPremium || it.isPro || it.subscriptionState.isAdFree
        }
        val freeTotal = users.count {
            !it.isPremium && !it.isPro && it.subscriptionTier.lowercase().trim().let { tier ->
                tier.isEmpty() || tier == "free"
            }
        }

        val tiers = mutableMapOf<String, Int>()
        for (u in users) {
            val rawTier = when {
                u.subscriptionTier.isNotBlank() && !u.subscriptionTier.equals("free", ignoreCase = true) -> u.subscriptionTier
                !u.plan.isNullOrBlank() && !u.plan.equals("free", ignoreCase = true) -> u.plan!!
                !u.proPlan.isNullOrBlank() && !u.proPlan.equals("free", ignoreCase = true) -> u.proPlan!!
                u.subscriptionTier.isNotBlank() -> u.subscriptionTier
                !u.plan.isNullOrBlank() -> u.plan!!
                !u.proPlan.isNullOrBlank() -> u.proPlan!!
                else -> "free"
            }.lowercase().trim()
            val safeTier = if (rawTier.isEmpty()) "free" else rawTier
            tiers[safeTier] = (tiers[safeTier] ?: 0) + 1
        }

        return UserMetrics(
            totalUsers = total,
            activeUsers = active,
            inactiveUsers = inactive,
            bannedUsers = globalBans,
            featureRestrictedUsers = featureRestrictedOnly,
            totalRestrictedUsers = totalRestricted,
            premiumUsers = isPremTotal,
            freeUsers = freeTotal,
            activePremium = activePrem,
            expiredPremium = expiredPrem,
            tierBreakdown = tiers
        )
    }

    /**
     * Calculates granular Feature Restriction metrics without merging them into global bans.
     */
    fun calculateFeatureRestrictions(users: List<User>): FeatureRestrictionMetrics {
        if (users.isEmpty()) return FeatureRestrictionMetrics()
        return FeatureRestrictionMetrics(
            watchRestricted = users.count { !it.isWatchAllowed },
            downloadRestricted = users.count { !it.isDownloadAllowed },
            chatRestricted = users.count { !it.isChatAllowed },
            storyRestricted = users.count { !it.isStoryAllowed },
            p2pRestricted = users.count { !it.isP2PAllowed }
        )
    }

    /**
     * Calculates Support Desk metrics using C3 canonical models.
     */
    fun calculateSupportMetrics(conversations: List<SupportConversation>): SupportMetrics {
        if (conversations.isEmpty()) return SupportMetrics()
        val total = conversations.size
        val open = conversations.count {
            it.statusEnum == SupportConversationStatus.OPEN ||
                it.statusEnum == SupportConversationStatus.PENDING
        }
        val unread = conversations.count { it.unreadByAdmin }
        return SupportMetrics(
            totalConversations = total,
            openConversations = open,
            unreadConversations = unread
        )
    }

    /**
     * Calculates Pro Request verification metrics using C4 canonical lifecycle states.
     */
    fun calculateProRequestMetrics(requests: List<ProRequest>): ProRequestMetrics {
        if (requests.isEmpty()) return ProRequestMetrics()
        val total = requests.size
        val pending = requests.count { it.status.equals(ProRequestStatus.PENDING.name, ignoreCase = true) }
        val approved = requests.count { it.status.equals(ProRequestStatus.APPROVED.name, ignoreCase = true) }
        val rejected = requests.count { it.status.equals(ProRequestStatus.REJECTED.name, ignoreCase = true) }
        return ProRequestMetrics(
            totalRequests = total,
            pendingRequests = pending,
            approvedRequests = approved,
            rejectedRequests = rejected
        )
    }

    /**
     * Calculates Managed Extensions operational metrics using C2 lifecycle.
     */
    fun calculateManagedExtensionMetrics(extensions: List<ManagedExtension>): ManagedExtensionMetrics {
        if (extensions.isEmpty()) return ManagedExtensionMetrics()
        val total = extensions.size
        val active = extensions.count {
            it.status.equals(ManagedExtensionStatus.ACTIVE.name, ignoreCase = true) && it.enabled
        }
        val maintenance = extensions.count {
            it.status.equals(ManagedExtensionStatus.MAINTENANCE.name, ignoreCase = true)
        }
        val disabled = extensions.count {
            it.status.equals(ManagedExtensionStatus.DISABLED.name, ignoreCase = true) ||
                (!it.enabled && !it.status.equals(ManagedExtensionStatus.DEPRECATED.name, ignoreCase = true) &&
                    !it.status.equals(ManagedExtensionStatus.MAINTENANCE.name, ignoreCase = true))
        }
        val deprecated = extensions.count {
            it.status.equals(ManagedExtensionStatus.DEPRECATED.name, ignoreCase = true)
        }
        return ManagedExtensionMetrics(
            totalExtensions = total,
            activeExtensions = active,
            maintenanceExtensions = maintenance,
            disabledExtensions = disabled,
            deprecatedExtensions = deprecated
        )
    }
}
