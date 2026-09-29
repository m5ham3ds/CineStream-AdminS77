package com.example.models

import com.google.firebase.firestore.IgnoreExtraProperties

enum class SubscriptionState {
    FREE,
    ACTIVE_PRO,
    EXPIRED_PRO
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
    // Canonical Subscription Fields (Authoritative Contract v1)
    val isPremium: Boolean = false,
    val subscriptionTier: String = "free",
    val subscriptionStatus: String = "free",
    val subscriptionExpiresAt: Long? = null,
    // Compatibility Subscription Fields (Consumers: Legacy Users App)
    val isPro: Boolean = isPremium,
    val plan: String? = subscriptionTier,
    val proPlan: String? = subscriptionTier,
    val proExpiresAt: Long? = subscriptionExpiresAt,
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

    // Subscription state machine & deterministic expiration interpretation
    val subscriptionState: SubscriptionState
        get() {
            val now = System.currentTimeMillis()
            val effectiveExpiresAt = subscriptionExpiresAt ?: proExpiresAt
            val hasProPlan = (subscriptionTier != "free" && subscriptionTier.isNotBlank()) ||
                    (plan != null && plan != "free" && plan.isNotBlank()) ||
                    (proPlan != null && proPlan != "free" && proPlan.isNotBlank())
            val hasProFlag = isPremium || isPro

            if (effectiveExpiresAt != null && effectiveExpiresAt <= now) {
                return if (hasProFlag || hasProPlan) {
                    SubscriptionState.EXPIRED_PRO
                } else {
                    SubscriptionState.FREE
                }
            }
            if (hasProFlag || hasProPlan) {
                return SubscriptionState.ACTIVE_PRO
            }
            return SubscriptionState.FREE
        }

    val isSubscriptionActive: Boolean
        get() = subscriptionState == SubscriptionState.ACTIVE_PRO
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

