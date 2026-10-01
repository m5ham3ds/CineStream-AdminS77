package com.example.models

import com.google.firebase.firestore.IgnoreExtraProperties

enum class SubscriptionState {
    FREE,
    ACTIVE_PRO,
    ACTIVE_PRO_LITE,
    EXPIRED_PRO,
    EXPIRED;

    val isAdFree: Boolean
        get() = this == ACTIVE_PRO || this == ACTIVE_PRO_LITE
}

/**
 * Canonical Subscription Tiers (Phase 03A).
 * Sourced strictly in UPPERCASE.
 */
enum class CanonicalSubscriptionTier {
    FREE,
    PRO_LITE,
    PRO;

    companion object {
        fun fromString(value: String?): CanonicalSubscriptionTier {
            val normalized = value?.trim()?.uppercase() ?: return FREE
            return when {
                normalized == "FREE" -> FREE
                normalized == "PRO_LITE" || normalized == "PRO LITE" || normalized == "PRO-LITE" -> PRO_LITE
                normalized == "PRO" || normalized == "PREMIUM" || normalized == "VIP" -> PRO
                else -> FREE
            }
        }
    }
}

/**
 * Canonical Plan SKUs and Duration mapping.
 */
object CanonicalPlanId {
    const val FREE = "free"
    const val PRO_LITE_1D = "pro_lite_1d"
    const val PRO_LITE_7D = "pro_lite_7d"
    const val PRO_LITE_10D = "pro_lite_10d"
    const val PRO_30D = "pro_30d"

    fun from(tier: CanonicalSubscriptionTier, durationDays: Int?): String {
        return when (tier) {
            CanonicalSubscriptionTier.FREE -> FREE
            CanonicalSubscriptionTier.PRO_LITE -> when (durationDays) {
                1 -> PRO_LITE_1D
                7 -> PRO_LITE_7D
                10 -> PRO_LITE_10D
                else -> if (durationDays != null) "pro_lite_${durationDays}d" else PRO_LITE_7D
            }
            CanonicalSubscriptionTier.PRO -> when (durationDays) {
                30 -> PRO_30D
                else -> if (durationDays != null) "pro_${durationDays}d" else PRO_30D
            }
        }
    }
}

enum class CanonicalSubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELED,
    PENDING;

    companion object {
        fun fromString(value: String?): CanonicalSubscriptionStatus {
            return when (value?.trim()?.uppercase()) {
                "ACTIVE" -> ACTIVE
                "EXPIRED" -> EXPIRED
                "CANCELED" -> CANCELED
                "PENDING" -> PENDING
                else -> ACTIVE
            }
        }
    }
}

enum class CanonicalSubscriptionSource {
    MONEY,
    POINTS,
    ADMIN_GRANT,
    LEGACY;

    companion object {
        fun fromString(value: String?): CanonicalSubscriptionSource {
            return when (value?.trim()?.uppercase()) {
                "MONEY" -> MONEY
                "POINTS" -> POINTS
                "ADMIN_GRANT" -> ADMIN_GRANT
                "LEGACY" -> LEGACY
                else -> LEGACY
            }
        }
    }
}

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val id: String = uid,
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastLoginAt: Long = 0L,
    val lastLoginTimestamp: Long = lastLoginAt,
    val lastActiveAt: Long = updatedAt,
    val isActive: Boolean = true,
    // Canonical Subscription Fields (Authoritative Contract Phase 03A)
    val subscriptionTier: String = "FREE",
    val planId: String = "free",
    val durationDays: Int? = null,
    val subscriptionStatus: String = "ACTIVE",
    val subscriptionSource: String = "LEGACY",
    val subscriptionReferenceId: String? = null,
    val subscriptionStartedAt: Long? = null,
    val subscriptionExpiresAt: Long? = null,
    // Compatibility Subscription Fields (Consumers: Legacy Users App)
    val isPremium: Boolean = false,
    val isPro: Boolean = isPremium,
    val plan: String? = subscriptionTier,
    val proPlan: String? = subscriptionTier,
    val proExpiresAt: Long? = subscriptionExpiresAt,
    // Canonical Points Economy (Cached Summary Fields)
    val pointsBalance: Long = 0L,
    val totalPointsEarned: Long = 0L,
    val totalPointsSpent: Long = 0L,
    // Technical Account & Feature Permissions (STRICTLY INDEPENDENT from Subscription!)
    val role: String = "user",
    // Account-level ban flags (canonical security fields in firestore.rules)
    val isBanned: Boolean = false,
    val banReason: String? = null,
    val banExpiresAt: Long? = null,
    // Positive feature permissions
    val canWatch: Boolean = true,
    val canDownload: Boolean = true,
    val canChat: Boolean = true,
    val canStory: Boolean = true,
    val canP2P: Boolean = true,
    val canComment: Boolean = true,
    val canUpload: Boolean = false,
    val canRequest: Boolean = true,
    // Ban flags maintained for compatibility
    val watchBan: Boolean = false,
    val downloadBan: Boolean = false,
    val chatBan: Boolean = false,
    val storyBan: Boolean = false,
    val p2pBan: Boolean = false,
    val deviceLimit: Int = 2,
    val maxDevices: Int = deviceLimit,
    // Independent Technical Permissions (NEVER granted or restricted by Subscription tier!)
    val allowedQuality: String? = null,
    val downloadLimit: Int? = null,
    val offlineDaysOverride: Int? = null,
    val forcedAdsOverride: Int? = null,
    val appVersion: String = "",
    val admin: Boolean = false,
    val isAdmin: Boolean = false,
    val hasPendingSync: Boolean = false
) {
    // Feature permission evaluations
    val isChatAllowed: Boolean get() = canChat && !chatBan
    val isStoryAllowed: Boolean get() = canStory && !storyBan
    val isDownloadAllowed: Boolean get() = canDownload && !downloadBan
    val isP2PAllowed: Boolean get() = canP2P && !p2pBan
    val isWatchAllowed: Boolean get() = canWatch && !watchBan
    val isCommentAllowed: Boolean get() = canComment
    val isUploadAllowed: Boolean get() = canUpload
    val isRequestAllowed: Boolean get() = canRequest
    val hasFeatureRestrictions: Boolean
        get() = !isWatchAllowed || !isDownloadAllowed || !isChatAllowed || !isStoryAllowed || !isP2PAllowed || !isCommentAllowed || !isRequestAllowed

    // Account-level Ban state machine & automatic expiration interpretation
    val isAccountBanned: Boolean
        get() {
            if (!isBanned) return false
            val expires = banExpiresAt ?: return true // Permanent ban if null
            return expires > System.currentTimeMillis()
        }

    val isBanExpired: Boolean
        get() {
            if (!isBanned) return false
            val expires = banExpiresAt ?: return false
            return expires <= System.currentTimeMillis()
        }

    val isPermanentBan: Boolean
        get() = isBanned && banExpiresAt == null

    // Canonical Subscription Tier Normalization & Precedence
    val canonicalTier: CanonicalSubscriptionTier
        get() {
            val raw = subscriptionTier.trim()
            if (raw.isNotBlank() && !raw.equals("free", ignoreCase = true)) {
                val upper = raw.uppercase()
                if (upper == "PRO_LITE" || upper == "PRO LITE" || upper == "PRO-LITE") return CanonicalSubscriptionTier.PRO_LITE
                if (upper == "PRO" || upper == "PREMIUM" || upper == "VIP") return CanonicalSubscriptionTier.PRO
            }
            val legacyPlan = (plan ?: proPlan)?.trim()?.uppercase()
            if (!legacyPlan.isNullOrBlank() && legacyPlan != "FREE") {
                if (legacyPlan == "PRO_LITE" || legacyPlan == "PRO LITE" || legacyPlan == "PRO-LITE") return CanonicalSubscriptionTier.PRO_LITE
                if (legacyPlan == "PRO" || legacyPlan == "PREMIUM" || legacyPlan == "VIP") return CanonicalSubscriptionTier.PRO
            }
            // Compatibility fallback if canonical tier was blank or "free" but legacy flags are true
            if (isPremium || isPro) {
                return CanonicalSubscriptionTier.PRO
            }
            return CanonicalSubscriptionTier.FREE
        }

    // Subscription state machine & deterministic expiration interpretation
    val subscriptionState: SubscriptionState
        get() {
            val tier = canonicalTier
            if (tier == CanonicalSubscriptionTier.FREE) {
                return SubscriptionState.FREE
            }
            val now = System.currentTimeMillis()
            val effectiveExpiresAt = subscriptionExpiresAt ?: proExpiresAt

            if (effectiveExpiresAt != null && effectiveExpiresAt <= now) {
                return if (tier == CanonicalSubscriptionTier.PRO_LITE) SubscriptionState.EXPIRED else SubscriptionState.EXPIRED_PRO
            }
            return when (tier) {
                CanonicalSubscriptionTier.PRO_LITE -> SubscriptionState.ACTIVE_PRO_LITE
                CanonicalSubscriptionTier.PRO -> SubscriptionState.ACTIVE_PRO
                CanonicalSubscriptionTier.FREE -> SubscriptionState.FREE
            }
        }

    /**
     * Absolute Rule: Subscription benefit is REMOVE_ADS only.
     * All video qualities remain accessible to all users.
     */
    val isAdFree: Boolean
        get() = subscriptionState == SubscriptionState.ACTIVE_PRO || subscriptionState == SubscriptionState.ACTIVE_PRO_LITE

    val isSubscriptionActive: Boolean
        get() = isAdFree
}

@IgnoreExtraProperties
data class AdminUser(
    val uid: String = "",
    val email: String = "",
    val role: String = "admin",
    val enabled: Boolean = true,
    val createdAt: Long = 0L
)

@IgnoreExtraProperties
data class AppConfig(
    // Maintenance Mode
    val maintenanceEnabled: Boolean = false,
    val maintenanceTitle: String = "Under Scheduled Maintenance",
    val maintenanceMessage: String = "CineStream is temporarily offline for upgrades. Please check back shortly.",
    val minimumVersionCode: Int = 1,
    // App OTA Updates
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val apkUrl: String = "",
    val apkSha256: String = "",
    val mandatoryUpdate: Boolean = false,
    val releaseNotes: String = "",
    // Media Providers and Scrapers Dynamic Catalog
    val providersJson: String = "{\n  \"providers\": [],\n  \"scrapers\": [],\n  \"endpoints\": []\n}",
    // DRM & Playback Defaults
    val defaultOfflineDays: Int = 2,
    val defaultForcedAds: Int = 5,
    // Cloudinary Media Configuration (Safe client settings - NO API Secret)
    val cloudinaryCloudName: String = "cinestream",
    val cloudinaryUploadPreset: String = "cinestream_unsigned",
    val updatedAt: Long = 0L
)

typealias GlobalConfig = AppConfig

@IgnoreExtraProperties
data class ExtensionItem(
    val id: String = "",
    val name: String = "",
    val packageName: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val apkUrl: String = "",
    val apkSha256: String = "",
    val sha256: String = apkSha256,
    val minAppVersionCode: Int = 1,
    val enabled: Boolean = true,
    val mandatory: Boolean = false,
    val releaseNotes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Authoritative Canonical App Update Entity (Phase C7 Alignment).
 * Stored at: /app_updates/{updateId}
 */
@IgnoreExtraProperties
data class AppUpdate(
    val id: String = "",
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val minVersionCode: Int = 1,
    val apkUrl: String = "",
    val apkSha256: String = "",
    val mandatoryUpdate: Boolean = false,
    val releaseNotes: String = "",
    val status: String = "PUBLISHED", // "PUBLISHED", "DRAFT", "ARCHIVED"
    val publishedBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class NotificationRequest(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val message: String = body,
    val type: String = "SYSTEM",
    val target: String = "ALL",
    val targetType: TargetType = TargetType.ALL,
    val targetUid: String = "",
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val isActive: Boolean = true,
    val status: String = "PENDING"
) {
    enum class TargetType {
        ALL, UID, PRO
    }
}

@IgnoreExtraProperties
data class AuditLog(
    val id: String = "",
    val actorUid: String = "",
    val adminUid: String = actorUid,
    val adminEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class UserReport(
    val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val type: String = "ISSUE",
    val targetType: String = "",
    val targetId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "PENDING",
    val resolutionNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolvedBy: String = ""
)

@IgnoreExtraProperties
data class CloudinaryMedia(
    val url: String = "",
    val publicId: String = "",
    val resourceType: String = "image",
    val format: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val duration: Double? = null,
    val bytes: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

