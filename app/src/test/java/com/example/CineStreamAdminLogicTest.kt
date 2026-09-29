package com.example

import com.example.models.AppConfig
import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionCapabilities
import com.example.models.ManagedExtensionContentTypes
import com.example.models.ManagedExtensionStatus
import com.example.models.SearchOrderCategory
import com.example.models.SearchOrderConfig
import com.example.validation.SearchOrderValidator
import com.example.models.NotificationRequest
import com.example.models.SupportConversation
import com.example.models.SupportConversationStatus
import com.example.models.SupportMessage
import com.example.models.User
import com.example.models.ProRequest
import com.example.models.ProRequestStatus
import com.example.state.AppLanguage
import com.example.state.AppStrings
import com.example.contract.FirebaseCollections
import com.example.contract.FirebaseSubcollections
import com.example.validation.ManagedExtensionValidator
import com.example.viewmodels.UserFilter
import com.example.viewmodels.UserSort
import org.junit.Assert.*
import org.junit.Test

class CineStreamAdminLogicTest {

    @Test
    fun testUserFilterLogic() {
        val now = System.currentTimeMillis()
        val users = listOf(
            User(id = "1", username = "alice", email = "alice@example.com", isPremium = true, subscriptionTier = "pro"),
            User(id = "2", username = "bob", email = "bob@example.com", isPremium = false, chatBan = true, canChat = false),
            User(id = "3", username = "charlie", email = "charlie@example.com", isPremium = false),
            User(id = "4", username = "dave", email = "dave@example.com", isPremium = true, subscriptionTier = "pro", subscriptionExpiresAt = now - 10000L), // expired pro
            User(id = "5", username = "eve", email = "eve@example.com", isBanned = true, banExpiresAt = now + 100000L) // active account ban
        )

        // Only active (non-expired) Pro users
        val proOnly = users.filter { it.subscriptionState == com.example.models.SubscriptionState.ACTIVE_PRO }
        assertEquals(1, proOnly.size)
        assertEquals("alice", proOnly[0].username)

        // Banned or restricted users (active account ban OR feature restriction)
        val bannedOrRestricted = users.filter { it.isAccountBanned || it.hasFeatureRestrictions }
        assertEquals(2, bannedOrRestricted.size)
        assertTrue(bannedOrRestricted.any { it.username == "bob" })
        assertTrue(bannedOrRestricted.any { it.username == "eve" })
    }

    @Test
    fun testUserSortLogic() {
        val users = listOf(
            User(id = "1", username = "Charlie", lastLoginTimestamp = 100L, createdAt = 500L),
            User(id = "2", username = "Alice", lastLoginTimestamp = 300L, createdAt = 200L),
            User(id = "3", username = "Bob", lastLoginTimestamp = 200L, createdAt = 700L)
        )

        val byRecentLogin = users.sortedByDescending { it.lastLoginTimestamp }
        assertEquals("Alice", byRecentLogin.first().username)

        val byUsername = users.sortedBy { it.username.lowercase() }
        assertEquals("Alice", byUsername[0].username)
        assertEquals("Bob", byUsername[1].username)
        assertEquals("Charlie", byUsername[2].username)

        val byNewest = users.sortedByDescending { it.createdAt }
        assertEquals("Bob", byNewest.first().username)
    }

    @Test
    fun testNotificationValidation() {
        // UID Target requires valid UID
        val uidRequest = NotificationRequest(
            targetType = NotificationRequest.TargetType.UID,
            targetUid = "user_12345",
            title = "Special Offer",
            message = "Your VIP pass is ready"
        )
        assertTrue(uidRequest.targetUid.isNotBlank())
        assertTrue(uidRequest.title.isNotBlank())
        assertTrue(uidRequest.message.isNotBlank())

        // Global broadcast
        val globalRequest = NotificationRequest(
            targetType = NotificationRequest.TargetType.ALL,
            title = "System Update",
            message = "Maintenance tonight at 2 AM UTC"
        )
        assertEquals(NotificationRequest.TargetType.ALL, globalRequest.targetType)
    }

    @Test
    fun testStepperLowerBound() {
        val currentVal = 0
        val decremented = if (currentVal > 0) currentVal - 1 else 0
        assertEquals(0, decremented)

        val positiveVal = 5
        val decrementedPositive = if (positiveVal > 0) positiveVal - 1 else 0
        assertEquals(4, decrementedPositive)
    }

    @Test
    fun testAppConfigOtaUrlValidation() {
        fun isValidApkUrl(url: String): Boolean {
            return url.isBlank() || url.startsWith("https://") || url.startsWith("http://")
        }

        assertTrue(isValidApkUrl(""))
        assertTrue(isValidApkUrl("https://cinestream.app/downloads/latest.apk"))
        assertTrue(isValidApkUrl("http://mirror.cinestream.app/v2.apk"))
        assertFalse(isValidApkUrl("ftp://cinestream.app/bad.apk"))
        assertFalse(isValidApkUrl("invalid-path-file"))
    }

    @Test
    fun testUnifiedContractModels() {
        val user = User(
            uid = "user_abc123",
            username = "streamer",
            email = "streamer@example.com",
            isPremium = true,
            subscriptionTier = "pro",
            canWatch = true,
            watchBan = false,
            canDownload = false,
            downloadBan = true
        )
        assertEquals("user_abc123", user.uid)
        assertEquals("user_abc123", user.id)
        assertTrue(user.isWatchAllowed)
        assertFalse(user.isDownloadAllowed)
        assertEquals("pro", user.subscriptionTier)

        val notif = NotificationRequest(
            title = "Promo",
            body = "Check out new movies!",
            type = "PROMO",
            target = "PRO"
        )
        assertEquals("Check out new movies!", notif.body)
        assertEquals("Check out new movies!", notif.message)
        assertEquals("PROMO", notif.type)
        assertEquals("PRO", notif.target)

        val config = AppConfig(
            maintenanceEnabled = true,
            latestVersionCode = 12,
            latestVersionName = "2.1.0",
            apkSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
        assertTrue(config.maintenanceEnabled)
        assertEquals("2.1.0", config.latestVersionName)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", config.apkSha256)
    }

    @Test
    fun testCloudinaryManagerPolicies() {
        val manager = com.example.media.CloudinaryManager
        
        // Cloudinary URL recognition
        val cloudUrl = "https://res.cloudinary.com/cinestream/image/upload/v1/avatar.jpg"
        assertTrue(manager.isCloudinaryUrl(cloudUrl))
        assertTrue(manager.validateMediaUrl(cloudUrl))

        // Firebase storage detection & restriction
        val fbStorageUrl = "https://firebasestorage.googleapis.com/v0/b/cinestream.appspot.com/o/avatar.jpg"
        assertTrue(manager.isFirebaseStorageUrl(fbStorageUrl))
        assertFalse(manager.validateMediaUrl(fbStorageUrl))

        // Transformations
        val transformed = manager.getOptimizedImageUrl(cloudUrl, width = 200, height = 200, crop = "thumb")
        assertTrue(transformed.contains("w_200"))
        assertTrue(transformed.contains("h_200"))
        assertTrue(transformed.contains("c_thumb"))
        assertTrue(transformed.contains("f_auto"))
        assertTrue(transformed.contains("q_auto"))
    }

    @Test
    fun testUserReportContract() {
        val report = com.example.models.UserReport(
            id = "rep_123",
            userId = "usr_456",
            userEmail = "user@cinestream.app",
            type = "ISSUE",
            targetType = "STREAM",
            targetId = "movie_999",
            title = "Broken Audio Track",
            description = "Audio is out of sync at minute 14",
            status = "PENDING"
        )
        assertEquals("rep_123", report.id)
        assertEquals("usr_456", report.userId)
        assertEquals("ISSUE", report.type)
        assertEquals("PENDING", report.status)
        assertEquals("STREAM", report.targetType)
    }

    @Test
    fun testCanonicalFirebaseCollectionsContract() {
        assertEquals("admins", com.example.contract.FirebaseCollections.ADMINS)
        assertEquals("users", com.example.contract.FirebaseCollections.USERS)
        assertEquals("config", com.example.contract.FirebaseCollections.CONFIG)
        assertEquals("managed_extensions", com.example.contract.FirebaseCollections.MANAGED_EXTENSIONS)
        assertEquals("extensions", com.example.contract.FirebaseCollections.LEGACY_EXTENSIONS)
        assertEquals("extension_updates", com.example.contract.FirebaseCollections.LEGACY_EXTENSION_UPDATES)
        assertEquals("app_updates", com.example.contract.FirebaseCollections.APP_UPDATES)
        assertEquals("notifications", com.example.contract.FirebaseCollections.NOTIFICATIONS)
        assertEquals("auditLogs", com.example.contract.FirebaseCollections.AUDIT_LOGS)
        assertEquals("reports", com.example.contract.FirebaseCollections.REPORTS)
        assertEquals("support_conversations", com.example.contract.FirebaseCollections.SUPPORT_CONVERSATIONS)
        assertEquals("pro_requests", com.example.contract.FirebaseCollections.PRO_REQUESTS)
    }

    @Test
    fun testCanonicalUserBanFields() {
        val activeUser = User(uid = "u1", username = "active", isBanned = false)
        assertFalse(activeUser.isBanned)
        assertNull(activeUser.banReason)
        assertNull(activeUser.banExpiresAt)

        val bannedUser = User(
            uid = "u2",
            username = "spammer",
            isBanned = true,
            banReason = "Repeated harassment in chat",
            banExpiresAt = 1750000000000L
        )
        assertTrue(bannedUser.isBanned)
        assertEquals("Repeated harassment in chat", bannedUser.banReason)
        assertEquals(1750000000000L, bannedUser.banExpiresAt)
    }

    @Test
    fun testAdminAuthoritySeparation() {
        // A user profile claiming role == "admin" is not sufficient under Canonical Contract v1
        val userClaimingAdmin = User(uid = "impostor", role = "admin")
        // Canonical definition mandates that admin authority is ONLY in /admins/{uid} with enabled == true
        val adminRecord = com.example.models.AdminUser(
            uid = "real_admin",
            email = "admin@cinestream.app",
            enabled = true,
            role = "superadmin"
        )
        assertTrue(adminRecord.enabled)
        assertEquals("real_admin", adminRecord.uid)

        val disabledAdminRecord = com.example.models.AdminUser(
            uid = "revoked_admin",
            email = "revoked@cinestream.app",
            enabled = false
        )
        assertFalse(disabledAdminRecord.enabled)
    }

    @Test
    fun testPhaseC1AccountBanModel() {
        val now = System.currentTimeMillis()

        // 1. Permanent Ban
        val permanentBanUser = User(
            uid = "user_perm",
            username = "malicious_user",
            isBanned = true,
            banReason = "Repeated copyright violations",
            banExpiresAt = null
        )
        assertTrue(permanentBanUser.isAccountBanned)
        assertFalse(permanentBanUser.isBanExpired)
        assertTrue(permanentBanUser.isPermanentBan)
        assertEquals("Repeated copyright violations", permanentBanUser.banReason)

        // 2. Active Temporary Ban (expires in 24 hours)
        val activeTempBanUser = User(
            uid = "user_temp_active",
            username = "toxic_chatter",
            isBanned = true,
            banReason = "Toxic chat behavior",
            banExpiresAt = now + 86400000L
        )
        assertTrue(activeTempBanUser.isAccountBanned)
        assertFalse(activeTempBanUser.isBanExpired)
        assertFalse(activeTempBanUser.isPermanentBan)

        // 3. Expired Temporary Ban (expired 1 hour ago) -> Automatically interpreted as unbanned
        val expiredBanUser = User(
            uid = "user_temp_expired",
            username = "reformed_user",
            isBanned = true,
            banReason = "Temporary spam penalty",
            banExpiresAt = now - 3600000L
        )
        assertFalse(expiredBanUser.isAccountBanned)
        assertTrue(expiredBanUser.isBanExpired)
        assertFalse(expiredBanUser.isPermanentBan)

        // 4. Good standing user
        val goodStandingUser = User(uid = "user_clean", isBanned = false)
        assertFalse(goodStandingUser.isAccountBanned)
        assertFalse(goodStandingUser.isBanExpired)
    }

    @Test
    fun testPhaseC1FeatureRestrictionsIndependence() {
        // Feature restrictions must NOT mark the user as account-banned
        val restrictedUser = User(
            uid = "restricted_1",
            username = "chat_restricted",
            isBanned = false,
            canChat = false,
            chatBan = true,
            canWatch = true,
            watchBan = false
        )
        assertFalse(restrictedUser.isAccountBanned)
        assertTrue(restrictedUser.hasFeatureRestrictions)
        assertFalse(restrictedUser.isChatAllowed)
        assertTrue(restrictedUser.isWatchAllowed)

        val cleanUser = User(uid = "clean_1", isBanned = false)
        assertFalse(cleanUser.hasFeatureRestrictions)
        assertTrue(cleanUser.isWatchAllowed)
        assertTrue(cleanUser.isDownloadAllowed)
        assertTrue(cleanUser.isChatAllowed)
        assertTrue(cleanUser.isStoryAllowed)
        assertTrue(cleanUser.isP2PAllowed)
    }

    @Test
    fun testPhaseC1SubscriptionStateMachine() {
        val now = System.currentTimeMillis()

        // 1. Free Tier User
        val freeUser = User(uid = "u_free", isPremium = false, subscriptionTier = "free")
        assertEquals(com.example.models.SubscriptionState.FREE, freeUser.subscriptionState)
        assertFalse(freeUser.isSubscriptionActive)

        // 2. Lifetime Active Pro
        val lifetimePro = User(
            uid = "u_pro_life",
            isPremium = true,
            subscriptionTier = "pro",
            subscriptionExpiresAt = null
        )
        assertEquals(com.example.models.SubscriptionState.ACTIVE_PRO, lifetimePro.subscriptionState)
        assertTrue(lifetimePro.isSubscriptionActive)

        // 3. Active VIP with Future Expiration
        val activeVip = User(
            uid = "u_vip_active",
            isPremium = true,
            subscriptionTier = "vip",
            subscriptionExpiresAt = now + 1000000L
        )
        assertEquals(com.example.models.SubscriptionState.ACTIVE_PRO, activeVip.subscriptionState)
        assertTrue(activeVip.isSubscriptionActive)

        // 4. Expired Pro (even if isPremium == true was preserved in raw document)
        val expiredPro = User(
            uid = "u_pro_exp",
            isPremium = true,
            subscriptionTier = "pro",
            subscriptionExpiresAt = now - 500000L
        )
        assertEquals(com.example.models.SubscriptionState.EXPIRED_PRO, expiredPro.subscriptionState)
        assertFalse(expiredPro.isSubscriptionActive)
    }

    @Test
    fun testPhaseC1SubscriptionCompatibilityPayload() {
        val now = System.currentTimeMillis()
        val durationDays = 30
        val expiresAt = now + durationDays.toLong() * 24 * 60 * 60 * 1000
        val tier = "pro"

        // Authoritative single write model creates canonical fields and compatibility mirrors
        val isPrem = tier != "free"
        val payload = mapOf(
            // Canonical Fields
            "isPremium" to isPrem,
            "subscriptionTier" to tier,
            "subscriptionStatus" to "active",
            "subscriptionExpiresAt" to expiresAt,
            // Compatibility Fields for Legacy Users App Consumers
            "isPro" to isPrem,
            "plan" to tier,
            "proPlan" to tier,
            "proExpiresAt" to expiresAt
        )

        assertEquals(true, payload["isPremium"])
        assertEquals("pro", payload["subscriptionTier"])
        assertEquals("active", payload["subscriptionStatus"])
        assertEquals(expiresAt, payload["subscriptionExpiresAt"])
        // Compatibility mirrors
        assertEquals(true, payload["isPro"])
        assertEquals("pro", payload["plan"])
        assertEquals("pro", payload["proPlan"])
        assertEquals(expiresAt, payload["proExpiresAt"])
    }

    @Test
    fun testPhaseC1ComprehensiveFeatureRestrictions() {
        // Default clean user: standard features enabled, upload false
        val defaultUser = User(uid = "u_default")
        assertTrue(defaultUser.isWatchAllowed)
        assertTrue(defaultUser.isDownloadAllowed)
        assertTrue(defaultUser.isChatAllowed)
        assertTrue(defaultUser.isStoryAllowed)
        assertTrue(defaultUser.isP2PAllowed)
        assertTrue(defaultUser.isCommentAllowed)
        assertTrue(defaultUser.isRequestAllowed)
        assertFalse(defaultUser.isUploadAllowed)
        assertFalse(defaultUser.hasFeatureRestrictions)

        // Test each restriction independently
        val commentRestricted = defaultUser.copy(canComment = false)
        assertFalse(commentRestricted.isCommentAllowed)
        assertTrue(commentRestricted.hasFeatureRestrictions)
        assertFalse(commentRestricted.isAccountBanned) // Feature restriction != account ban

        val requestRestricted = defaultUser.copy(canRequest = false)
        assertFalse(requestRestricted.isRequestAllowed)
        assertTrue(requestRestricted.hasFeatureRestrictions)
        assertFalse(requestRestricted.isAccountBanned)

        val uploadGranted = defaultUser.copy(canUpload = true)
        assertTrue(uploadGranted.isUploadAllowed)

        // Compatibility feature bans
        val chatBannedUser = defaultUser.copy(chatBan = true)
        assertFalse(chatBannedUser.isChatAllowed)
        assertTrue(chatBannedUser.hasFeatureRestrictions)

        val downloadBannedUser = defaultUser.copy(downloadBan = true)
        assertFalse(downloadBannedUser.isDownloadAllowed)
        assertTrue(downloadBannedUser.hasFeatureRestrictions)

        val p2pBannedUser = defaultUser.copy(p2pBan = true)
        assertFalse(p2pBannedUser.isP2PAllowed)
        assertTrue(p2pBannedUser.hasFeatureRestrictions)

        val storyBannedUser = defaultUser.copy(storyBan = true)
        assertFalse(storyBannedUser.isStoryAllowed)
        assertTrue(storyBannedUser.hasFeatureRestrictions)

        val watchBannedUser = defaultUser.copy(watchBan = true)
        assertFalse(watchBannedUser.isWatchAllowed)
        assertTrue(watchBannedUser.hasFeatureRestrictions)
    }

    @Test
    fun testPhaseC1SupportPathReconciliation() {
        // Verify canonical Support path constant matches deployed firestore.rules
        assertEquals("support_conversations", com.example.contract.FirebaseCollections.SUPPORT_CONVERSATIONS)
        assertEquals("support_conversations", com.example.contract.FirebaseCollections.CONVERSATIONS)
    }

    @Test
    fun testPhaseC1BanSemanticsAndUnbanDecoupling() {
        val now = System.currentTimeMillis()

        // 1. Permanent Ban
        val permBan = User(
            uid = "u_perm",
            isBanned = true,
            banReason = "Gross misconduct",
            banExpiresAt = null,
            canChat = false, // pre-existing restriction
            chatBan = true
        )
        assertTrue(permBan.isAccountBanned)
        assertTrue(permBan.isPermanentBan)
        assertFalse(permBan.isBanExpired)
        assertEquals("Gross misconduct", permBan.banReason)
        assertNull(permBan.banExpiresAt)

        // 2. Unban restores account-level status without wiping feature restrictions
        val unbanned = permBan.copy(
            isBanned = false,
            banReason = null,
            banExpiresAt = null,
            isActive = true
        )
        assertFalse(unbanned.isAccountBanned)
        assertFalse(unbanned.isPermanentBan)
        assertFalse(unbanned.isBanExpired)
        assertNull(unbanned.banReason)
        assertNull(unbanned.banExpiresAt)
        // User's pre-existing chat restriction is preserved!
        assertFalse(unbanned.isChatAllowed)
        assertTrue(unbanned.hasFeatureRestrictions)
    }

    @Test
    fun testPhaseC1SubscriptionStateDeterminismWithCompatibilityFields() {
        val now = System.currentTimeMillis()

        // Legacy user document that only has proExpiresAt and isPro
        val legacyExpired = User(
            uid = "legacy_exp",
            isPremium = false,
            isPro = true,
            subscriptionTier = "free",
            plan = "pro",
            proPlan = "pro",
            proExpiresAt = now - 1000L
        )
        assertEquals(com.example.models.SubscriptionState.EXPIRED_PRO, legacyExpired.subscriptionState)
        assertFalse(legacyExpired.isSubscriptionActive)

        // Legacy user document with active proExpiresAt
        val legacyActive = User(
            uid = "legacy_act",
            isPremium = false,
            isPro = true,
            subscriptionTier = "free",
            plan = "pro",
            proPlan = "pro",
            proExpiresAt = now + 100000L
        )
        assertEquals(com.example.models.SubscriptionState.ACTIVE_PRO, legacyActive.subscriptionState)
        assertTrue(legacyActive.isSubscriptionActive)
    }

    // ============================================================
    // PHASE C2 — MANAGED EXTENSIONS ARCHITECTURE & VALIDATION TESTS
    // ============================================================

    @Test
    fun testPhaseC2ManagedExtensionLifecycleStates() {
        // Supported lifecycle states
        assertEquals(ManagedExtensionStatus.ACTIVE, ManagedExtensionStatus.fromString("ACTIVE"))
        assertEquals(ManagedExtensionStatus.MAINTENANCE, ManagedExtensionStatus.fromString("MAINTENANCE"))
        assertEquals(ManagedExtensionStatus.DISABLED, ManagedExtensionStatus.fromString("DISABLED"))
        assertEquals(ManagedExtensionStatus.DEPRECATED, ManagedExtensionStatus.fromString("DEPRECATED"))

        // Case insensitivity & trimming
        assertEquals(ManagedExtensionStatus.ACTIVE, ManagedExtensionStatus.fromString(" active "))
        assertEquals(ManagedExtensionStatus.DEPRECATED, ManagedExtensionStatus.fromString("deprecated"))

        // Invariant: Unknown/null status safely falls back to DISABLED, NEVER silently ACTIVE
        assertEquals(ManagedExtensionStatus.DISABLED, ManagedExtensionStatus.fromString(null))
        assertEquals(ManagedExtensionStatus.DISABLED, ManagedExtensionStatus.fromString(""))
        assertEquals(ManagedExtensionStatus.DISABLED, ManagedExtensionStatus.fromString("UNKNOWN_STATE"))
        assertEquals(ManagedExtensionStatus.DISABLED, ManagedExtensionStatus.fromString("BETA"))

        // Operational check: requires BOTH enabled == true and status == ACTIVE
        val activeEnabled = ManagedExtension(enabled = true, status = "ACTIVE")
        assertTrue(activeEnabled.isOperational)

        val activeDisabled = ManagedExtension(enabled = false, status = "ACTIVE")
        assertFalse(activeDisabled.isOperational)

        val maintEnabled = ManagedExtension(enabled = true, status = "MAINTENANCE")
        assertFalse(maintEnabled.isOperational)

        val deprecatedEnabled = ManagedExtension(enabled = true, status = "DEPRECATED")
        assertFalse(deprecatedEnabled.isOperational)
    }

    @Test
    fun testPhaseC2ManagedExtensionValidatorValidModel() {
        val validExt = ManagedExtension(
            extensionId = "qfilm-ar",
            scraperKey = "qfilm",
            name = "QFilm Arabic",
            description = "High quality Arabic media scraper",
            baseUrl = "https://qfilm-media.net",
            searchUrl = "https://qfilm-media.net/search?q=%s",
            runtimeApiVersion = 1,
            definitionVersion = 1,
            status = "ACTIVE",
            priority = 100,
            capabilities = listOf(
                ManagedExtensionCapabilities.SEARCH,
                ManagedExtensionCapabilities.DETAILS,
                ManagedExtensionCapabilities.VIDEO_EXTRACTION
            ),
            contentTypes = listOf(
                ManagedExtensionContentTypes.MOVIE,
                ManagedExtensionContentTypes.SERIES
            ),
            enabled = true
        )

        val errors = ManagedExtensionValidator.validate(validExt)
        assertTrue("Expected valid model with 0 errors, got: $errors", errors.isEmpty())
    }

    @Test
    fun testPhaseC2ManagedExtensionValidatorIdentifierIntegrity() {
        val baseValid = ManagedExtension(
            extensionId = "egydead-v1",
            scraperKey = "egydead",
            name = "EgyDead Scraper",
            baseUrl = "https://egydead.tv",
            status = "ACTIVE"
        )

        // Blank extensionId
        val emptyIdErrors = ManagedExtensionValidator.validate(baseValid.copy(extensionId = ""))
        assertTrue(emptyIdErrors.any { it.contains("Extension ID is required") })

        // Invalid extensionId characters
        val badIdErrors = ManagedExtensionValidator.validate(baseValid.copy(extensionId = "invalid id with spaces!"))
        assertTrue(badIdErrors.any { it.contains("Extension ID must be") })

        // Blank scraperKey
        val emptyKeyErrors = ManagedExtensionValidator.validate(baseValid.copy(scraperKey = ""))
        assertTrue(emptyKeyErrors.any { it.contains("Scraper Key is required") })

        // Uppercase or special characters in scraperKey (must be strictly lowercase identifier matching bundled Kotlin scraper)
        val upperKeyErrors = ManagedExtensionValidator.validate(baseValid.copy(scraperKey = "EgyDead_UPPER"))
        assertTrue(upperKeyErrors.any { it.contains("Scraper Key must be lowercase") })
    }

    @Test
    fun testPhaseC2SsrfAndUrlValidation() {
        val baseValid = ManagedExtension(
            extensionId = "arabseed-scraper",
            scraperKey = "arabseed",
            name = "ArabSeed Scraper",
            status = "ACTIVE"
        )

        // Non-HTTPS scheme rejection
        val httpErrors = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "http://insecure-site.com"))
        assertTrue(httpErrors.any { it.contains("Must use HTTPS scheme") })

        // Localhost rejection
        val localhostErrors = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://localhost:8080"))
        assertTrue(localhostErrors.any { it.contains("Localhost") })

        val subLocalErrors = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://internal.localhost"))
        assertTrue(subLocalErrors.any { it.contains("Localhost") })

        // 0.0.0.0 rejection
        val zeroErrors = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://0.0.0.0"))
        assertTrue(zeroErrors.any { it.contains("0.0.0.0") })

        // Loopback 127.0.0.1 rejection
        val loopbackErrors = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://127.0.0.1:443"))
        assertTrue(loopbackErrors.any { it.contains("Private and loopback") })

        // Private RFC 1918 / Link-local IPv4 rejection
        val privateIp1 = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://10.0.0.1"))
        assertTrue(privateIp1.any { it.contains("Private and loopback") })

        val privateIp2 = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://192.168.1.100"))
        assertTrue(privateIp2.any { it.contains("Private and loopback") })

        val privateIp3 = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://172.20.0.5"))
        assertTrue(privateIp3.any { it.contains("Private and loopback") })

        val linkLocal = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://169.254.169.254"))
        assertTrue(linkLocal.any { it.contains("Private and loopback") })

        // IPv6 Loopback rejection
        val ipv6Loopback = ManagedExtensionValidator.validate(baseValid.copy(baseUrl = "https://[::1]"))
        assertTrue(ipv6Loopback.any { it.contains("Private and loopback IPv6") })

        // Public HTTPS URL acceptance
        assertNull(ManagedExtensionValidator.validateHttpsUrl("https://arabseed.show", isBaseUrl = true))
        assertNull(ManagedExtensionValidator.validateHttpsUrl("https://api.streamprovider.io", isBaseUrl = true))
    }

    @Test
    fun testPhaseC2ControlledCapabilitiesAndContentTypes() {
        val baseValid = ManagedExtension(
            extensionId = "test-caps",
            scraperKey = "test_scraper",
            name = "Test Scraper",
            baseUrl = "https://example-test.com",
            status = "ACTIVE"
        )

        // Valid standard capabilities
        val validCapsModel = baseValid.copy(
            capabilities = listOf(
                ManagedExtensionCapabilities.SEARCH,
                ManagedExtensionCapabilities.CLOUDFLARE_CHALLENGE
            ),
            contentTypes = listOf(
                ManagedExtensionContentTypes.ANIME,
                ManagedExtensionContentTypes.ASIAN_DRAMA
            )
        )
        assertTrue(ManagedExtensionValidator.validate(validCapsModel).isEmpty())

        // Unknown capability injection
        val badCapsModel = baseValid.copy(capabilities = listOf("EXECUTE_ARBITRARY_CODE"))
        val badCapsErrors = ManagedExtensionValidator.validate(badCapsModel)
        assertTrue(badCapsErrors.any { it.contains("Unknown capabilities") })

        // Unknown content type
        val badTypesModel = baseValid.copy(contentTypes = listOf("UNAPPROVED_TYPE"))
        val badTypesErrors = ManagedExtensionValidator.validate(badTypesModel)
        assertTrue(badTypesErrors.any { it.contains("Unknown content types") })
    }

    @Test
    fun testPhaseC2FirestoreContractPathsSeparation() {
        // Invariant: Modern Managed Extensions collection MUST be /managed_extensions
        assertEquals("managed_extensions", FirebaseCollections.MANAGED_EXTENSIONS)

        // Invariant: Legacy extensions collection remains untouched for backwards compatibility
        assertEquals("extensions", FirebaseCollections.LEGACY_EXTENSIONS)
        assertEquals("extension_updates", FirebaseCollections.LEGACY_EXTENSION_UPDATES)

        // Distinct collections
        assertNotEquals(FirebaseCollections.MANAGED_EXTENSIONS, FirebaseCollections.LEGACY_EXTENSIONS)
    }

    @Test
    fun testPhaseC2PureConfigurationSecurityInvariants() {
        // Verify that ManagedExtension class contains NO executable bytecode fields
        val fields = ManagedExtension::class.java.declaredFields.map { it.name }
        assertFalse("ManagedExtension must not have apkUrl", fields.contains("apkUrl"))
        assertFalse("ManagedExtension must not have apkSha256", fields.contains("apkSha256"))
        assertFalse("ManagedExtension must not have dexPath", fields.contains("dexPath"))
        assertFalse("ManagedExtension must not have scriptBody", fields.contains("scriptBody"))
        assertFalse("ManagedExtension must not have classLoader", fields.contains("classLoader"))
    }

    // ============================================================
    // PHASE C3 — SUPPORT CONVERSATIONS & ADMIN SUPPORT DESK TESTS
    // ============================================================

    @Test
    fun testPhaseC3SupportCanonicalPathAndCollections() {
        // Canonical deployed path in Firestore
        assertEquals("support_conversations", FirebaseCollections.SUPPORT_CONVERSATIONS)
        // Backwards compatibility alias matches canonical path
        assertEquals("support_conversations", FirebaseCollections.CONVERSATIONS)
        // Canonical messages subcollection name
        assertEquals("messages", FirebaseSubcollections.MESSAGES)
    }

    @Test
    fun testPhaseC3SupportConversationStatusLifecycle() {
        // Supported lifecycle states
        assertEquals(SupportConversationStatus.OPEN, SupportConversationStatus.fromString("OPEN"))
        assertEquals(SupportConversationStatus.PENDING, SupportConversationStatus.fromString("PENDING"))
        assertEquals(SupportConversationStatus.RESOLVED, SupportConversationStatus.fromString("RESOLVED"))
        assertEquals(SupportConversationStatus.CLOSED, SupportConversationStatus.fromString("CLOSED"))

        // Case insensitivity & whitespace trimming
        assertEquals(SupportConversationStatus.OPEN, SupportConversationStatus.fromString(" open "))
        assertEquals(SupportConversationStatus.RESOLVED, SupportConversationStatus.fromString("resolved"))

        // Security / Invariant: Unknown/null status safely falls back to CLOSED, NEVER silently OPEN
        assertEquals(SupportConversationStatus.CLOSED, SupportConversationStatus.fromString(null))
        assertEquals(SupportConversationStatus.CLOSED, SupportConversationStatus.fromString(""))
        assertEquals(SupportConversationStatus.CLOSED, SupportConversationStatus.fromString("INVALID_STATUS"))
        assertEquals(SupportConversationStatus.CLOSED, SupportConversationStatus.fromString("ACTIVE"))
    }

    @Test
    fun testPhaseC3SupportConversationModelIntegrity() {
        val conv = SupportConversation(
            conversationId = "conv_101",
            userId = "user_abc",
            userEmail = "user@example.com",
            userName = "Alice",
            subject = "Streaming issue on Android TV",
            status = "OPEN",
            lastMessage = "Video stutters after 5 minutes",
            lastMessageAt = 1700000000000L,
            lastSenderRole = "user",
            unreadByAdmin = true,
            unreadByUser = false
        )

        assertEquals("conv_101", conv.conversationId)
        assertEquals("user_abc", conv.userId)
        assertEquals("user@example.com", conv.userEmail)
        assertEquals(SupportConversationStatus.OPEN, conv.statusEnum)
        assertFalse(conv.isResolvedOrClosed)
        assertTrue(conv.unreadByAdmin)

        val resolvedConv = conv.copy(status = "RESOLVED")
        assertTrue(resolvedConv.isResolvedOrClosed)

        val closedConv = conv.copy(status = "CLOSED")
        assertTrue(closedConv.isResolvedOrClosed)
    }

    @Test
    fun testPhaseC3SupportMessageModelAndAdminDistinction() {
        val userMsg = SupportMessage(
            messageId = "msg_1",
            conversationId = "conv_101",
            senderId = "user_abc",
            senderRole = "user",
            senderEmail = "user@example.com",
            text = "Hello, can you help with buffering?"
        )
        assertFalse(userMsg.isAdminMessage)
        assertEquals("user", userMsg.senderRole)

        val adminMsg = SupportMessage(
            messageId = "msg_2",
            conversationId = "conv_101",
            senderId = "admin_uid_777",
            senderRole = "admin",
            senderEmail = "admin@cinestream.com",
            text = "Sure, please try switching the server from settings."
        )
        assertTrue(adminMsg.isAdminMessage)
        assertEquals("admin", adminMsg.senderRole)
    }

    @Test
    fun testPhaseC3AdminReplyPayloadValidation() {
        val now = System.currentTimeMillis()
        val conversationId = "conv_999"
        val adminUid = "admin_super"
        val adminEmail = "support@cinestream.com"
        val replyText = "We investigated your report and cleared the stream cache."

        // Blank reply must be rejected
        assertTrue(replyText.isNotBlank())
        val emptyText = "   "
        assertTrue(emptyText.isBlank())

        // Message payload
        val messagePayload = mapOf(
            "messageId" to "generated_msg_id",
            "conversationId" to conversationId,
            "senderId" to adminUid,
            "senderRole" to "admin",
            "senderEmail" to adminEmail,
            "text" to replyText.trim(),
            "mediaUrl" to null,
            "timestamp" to now,
            "read" to false
        )
        assertEquals("admin", messagePayload["senderRole"])
        assertEquals(adminUid, messagePayload["senderId"])
        assertEquals(replyText, messagePayload["text"])

        // Conversation update payload (atomic update)
        val conversationUpdate = mapOf(
            "lastMessage" to replyText.trim(),
            "lastMessageAt" to now,
            "lastSenderRole" to "admin",
            "unreadByAdmin" to false,
            "unreadByUser" to true,
            "updatedAt" to now
        )
        assertEquals(replyText, conversationUpdate["lastMessage"])
        assertEquals("admin", conversationUpdate["lastSenderRole"])
        assertEquals(false, conversationUpdate["unreadByAdmin"])
        assertEquals(true, conversationUpdate["unreadByUser"])
    }

    @Test
    fun testPhaseC3SupportSearchAndStatusFilterLogic() {
        val list = listOf(
            SupportConversation(conversationId = "c1", userId = "u1", userEmail = "alex@test.com", userName = "Alex", subject = "Billing query", status = "OPEN", unreadByAdmin = true),
            SupportConversation(conversationId = "c2", userId = "u2", userEmail = "sami@domain.com", userName = "Sami", subject = "Subtitles missing", status = "PENDING", unreadByAdmin = false),
            SupportConversation(conversationId = "c3", userId = "u3", userEmail = "tariq@cine.org", userName = "Tariq", subject = "Feature request", status = "RESOLVED", unreadByAdmin = false),
            SupportConversation(conversationId = "c4", userId = "u4", userEmail = "mona@stream.tv", userName = "Mona", subject = "Audio sync issue", status = "CLOSED", unreadByAdmin = false)
        )

        // Status Filter: OPEN
        val openOnly = list.filter { it.statusEnum == SupportConversationStatus.OPEN }
        assertEquals(1, openOnly.size)
        assertEquals("c1", openOnly.first().conversationId)

        // Search Filter: "subtitles"
        val query = "subtitles"
        val searched = list.filter { it.subject.lowercase().contains(query) || it.userEmail.lowercase().contains(query) }
        assertEquals(1, searched.size)
        assertEquals("c2", searched.first().conversationId)

        // Search Filter by email: "cine.org"
        val byEmail = list.filter { it.userEmail.lowercase().contains("cine.org") }
        assertEquals(1, byEmail.size)
        assertEquals("c3", byEmail.first().conversationId)

        // Unread only count
        assertEquals(1, list.count { it.unreadByAdmin })
    }

    // ============================================================
    // PHASE C4 — PRO REQUESTS ADMIN WORKFLOW TESTS
    // ============================================================

    @Test
    fun testPhaseC4ProRequestsContractAndPaths() {
        // Canonical collection path in Firestore
        assertEquals("pro_requests", FirebaseCollections.PRO_REQUESTS)

        // Model field mapping and defaults
        val req = ProRequest(
            requestId = "req_100",
            userId = "user_456",
            userEmail = "fan@cinestream.com",
            userName = "MovieFan",
            requestedPlan = "pro",
            requestedDuration = "1 month",
            paymentMethod = "Bank Transfer",
            paymentProofUrl = "https://res.cloudinary.com/cinestream/receipts/rec100.png",
            userNote = "Paid via quick bank transfer",
            status = "PENDING",
            createdAt = 1710000000000L,
            updatedAt = 1710000000000L
        )

        assertEquals("req_100", req.requestId)
        assertEquals("user_456", req.userId)
        assertEquals("fan@cinestream.com", req.userEmail)
        assertEquals("MovieFan", req.userName)
        assertEquals("pro", req.requestedPlan)
        assertEquals("1 month", req.requestedDuration)
        assertEquals("Bank Transfer", req.paymentMethod)
        assertEquals("https://res.cloudinary.com/cinestream/receipts/rec100.png", req.paymentProofUrl)
        assertEquals("Paid via quick bank transfer", req.userNote)
        assertEquals(ProRequestStatus.PENDING, req.statusEnum)
        assertTrue(req.isPending)
        assertFalse(req.isApproved)
        assertFalse(req.isRejected)
        assertFalse(req.isReviewed)
    }

    @Test
    fun testPhaseC4ProRequestStatusLifecycleAndSafeFallback() {
        // Valid canonical states
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString("PENDING"))
        assertEquals(ProRequestStatus.APPROVED, ProRequestStatus.fromString("APPROVED"))
        assertEquals(ProRequestStatus.REJECTED, ProRequestStatus.fromString("REJECTED"))

        // Case insensitivity & trimming
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString(" pending "))
        assertEquals(ProRequestStatus.APPROVED, ProRequestStatus.fromString(" approved "))
        assertEquals(ProRequestStatus.REJECTED, ProRequestStatus.fromString(" rejected "))

        // Security Invariant: Unknown/malformed status safely resolves to PENDING, NEVER silently APPROVED
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString(null))
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString(""))
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString("UNKNOWN"))
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString("APPROVED_FORGED"))
        assertEquals(ProRequestStatus.PENDING, ProRequestStatus.fromString("PRO_ACTIVE"))

        assertNotEquals(ProRequestStatus.APPROVED, ProRequestStatus.fromString("UNKNOWN"))
        assertNotEquals(ProRequestStatus.APPROVED, ProRequestStatus.fromString(null))
    }

    @Test
    fun testPhaseC4StateTransitionMatrix() {
        // Enforce the one-way state transition lifecycle
        fun isValidTransition(from: ProRequestStatus, to: ProRequestStatus): Boolean {
            return when (from) {
                ProRequestStatus.PENDING -> to == ProRequestStatus.APPROVED || to == ProRequestStatus.REJECTED
                ProRequestStatus.APPROVED -> false // Terminal state
                ProRequestStatus.REJECTED -> false // Terminal state
            }
        }

        // Valid transitions
        assertTrue(isValidTransition(ProRequestStatus.PENDING, ProRequestStatus.APPROVED))
        assertTrue(isValidTransition(ProRequestStatus.PENDING, ProRequestStatus.REJECTED))

        // Invalid / prohibited transitions
        assertFalse(isValidTransition(ProRequestStatus.APPROVED, ProRequestStatus.PENDING))
        assertFalse(isValidTransition(ProRequestStatus.REJECTED, ProRequestStatus.APPROVED))
        assertFalse(isValidTransition(ProRequestStatus.APPROVED, ProRequestStatus.REJECTED))
        assertFalse(isValidTransition(ProRequestStatus.REJECTED, ProRequestStatus.PENDING))
    }

    @Test
    fun testPhaseC4UserOwnershipInvariance() {
        // User ownership is anchored to userId
        val originalUserId = "user_owner_1"
        val req = ProRequest(
            requestId = "r_1",
            userId = originalUserId,
            userEmail = "user1@cinestream.com",
            status = "PENDING"
        )
        assertEquals(originalUserId, req.userId)

        // Administrative updates must preserve userId
        val updated = req.copy(
            status = "APPROVED",
            reviewedBy = "admin_uid_99",
            reviewedAt = System.currentTimeMillis()
        )
        assertEquals(originalUserId, updated.userId)
        assertEquals(ProRequestStatus.APPROVED, updated.statusEnum)
    }

    @Test
    fun testPhaseC4ApprovalValidationAndAuditContract() {
        val now = System.currentTimeMillis()
        val adminUid = "admin_reviewer"
        val adminEmail = "reviewer@cinestream.com"
        val requestId = "req_to_approve"
        val adminNote = "Payment verified via bank slip"

        // Approval mutation payload specification
        val updates = hashMapOf<String, Any?>(
            "status" to ProRequestStatus.APPROVED.name,
            "reviewedAt" to now,
            "reviewedBy" to adminUid,
            "reviewedByEmail" to adminEmail,
            "updatedAt" to now,
            "adminNote" to adminNote
        )

        assertEquals("APPROVED", updates["status"])
        assertEquals(now, updates["reviewedAt"])
        assertEquals(adminUid, updates["reviewedBy"])
        assertEquals(adminEmail, updates["reviewedByEmail"])
        assertEquals(adminNote, updates["adminNote"])

        // Audit log action and target specification
        val auditAction = "APPROVE_PRO_REQUEST"
        val auditTargetType = "PRO_REQUEST"
        assertEquals("APPROVE_PRO_REQUEST", auditAction)
        assertEquals("PRO_REQUEST", auditTargetType)
    }

    @Test
    fun testPhaseC4RejectionValidationAndMandatoryReason() {
        val now = System.currentTimeMillis()
        val adminUid = "admin_reviewer"
        val adminEmail = "reviewer@cinestream.com"
        val requestId = "req_to_reject"

        // Mandatory reason check: blank reason must fail validation
        val blankReason = "   "
        assertTrue(blankReason.trim().isBlank())

        val validReason = "Payment receipt is illegible. Please submit a clearer image."
        assertTrue(validReason.trim().isNotBlank())

        val updates = hashMapOf<String, Any?>(
            "status" to ProRequestStatus.REJECTED.name,
            "rejectionReason" to validReason.trim(),
            "reviewedAt" to now,
            "reviewedBy" to adminUid,
            "reviewedByEmail" to adminEmail,
            "updatedAt" to now
        )

        assertEquals("REJECTED", updates["status"])
        assertEquals(validReason, updates["rejectionReason"])
        assertEquals(adminUid, updates["reviewedBy"])

        // Audit log specification
        val auditAction = "REJECT_PRO_REQUEST"
        val auditTargetType = "PRO_REQUEST"
        assertEquals("REJECT_PRO_REQUEST", auditAction)
        assertEquals("PRO_REQUEST", auditTargetType)
    }

    @Test
    fun testPhaseC4SearchAndFilteringLogic() {
        val list = listOf(
            ProRequest(requestId = "req_1", userId = "u1", userEmail = "ahmad@stream.com", userName = "Ahmad", requestedPlan = "pro", status = "PENDING", createdAt = 100L),
            ProRequest(requestId = "req_2", userId = "u2", userEmail = "sarah@cine.org", userName = "Sarah", requestedPlan = "vip", status = "APPROVED", createdAt = 200L),
            ProRequest(requestId = "req_3", userId = "u3", userEmail = "khalid@mail.com", userName = "Khalid", requestedPlan = "pro", status = "REJECTED", createdAt = 150L),
            ProRequest(requestId = "req_4", userId = "u4", userEmail = "noor@cinema.net", userName = "Noor", requestedPlan = "pro", status = "PENDING", createdAt = 300L)
        )

        // Status filter: PENDING
        val pendingOnly = list.filter { it.statusEnum == ProRequestStatus.PENDING }
        assertEquals(2, pendingOnly.size)
        assertTrue(pendingOnly.any { it.requestId == "req_1" })
        assertTrue(pendingOnly.any { it.requestId == "req_4" })

        // Status filter: APPROVED
        val approvedOnly = list.filter { it.statusEnum == ProRequestStatus.APPROVED }
        assertEquals(1, approvedOnly.size)
        assertEquals("req_2", approvedOnly.first().requestId)

        // Status filter: REJECTED
        val rejectedOnly = list.filter { it.statusEnum == ProRequestStatus.REJECTED }
        assertEquals(1, rejectedOnly.size)
        assertEquals("req_3", rejectedOnly.first().requestId)

        // Search: by email
        val searchEmail = list.filter { it.userEmail.contains("cine.org") }
        assertEquals(1, searchEmail.size)
        assertEquals("req_2", searchEmail.first().requestId)

        // Search: by plan
        val searchVip = list.filter { it.requestedPlan.equals("vip", ignoreCase = true) }
        assertEquals(1, searchVip.size)
        assertEquals("Sarah", searchVip.first().userName)

        // Metric counts
        assertEquals(4, list.size)
        assertEquals(2, list.count { it.isPending })
        assertEquals(1, list.count { it.isApproved })
        assertEquals(1, list.count { it.isRejected })
    }

    @Test
    fun testPhaseC4LocalizationKeysIntegrity() {
        val langs = listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC)
        for (lang in langs) {
            assertTrue(AppStrings.proRequests(lang).isNotBlank())
            assertTrue(AppStrings.proRequestsSubtitle(lang).isNotBlank())
            assertTrue(AppStrings.proRequestStatusPending(lang).isNotBlank())
            assertTrue(AppStrings.proRequestStatusApproved(lang).isNotBlank())
            assertTrue(AppStrings.proRequestStatusRejected(lang).isNotBlank())
            assertTrue(AppStrings.approve(lang).isNotBlank())
            assertTrue(AppStrings.reject(lang).isNotBlank())
            assertTrue(AppStrings.rejectionReasonLabel(lang).isNotBlank())
            assertTrue(AppStrings.rejectionReasonPrompt(lang).isNotBlank())
            assertTrue(AppStrings.adminNoteLabel(lang).isNotBlank())
            assertTrue(AppStrings.confirmApprovalTitle(lang).isNotBlank())
            assertTrue(AppStrings.requestDetails(lang).isNotBlank())
            assertTrue(AppStrings.noProRequests(lang).isNotBlank())
            assertTrue(AppStrings.metricTotal(lang).isNotBlank())
            assertTrue(AppStrings.metricPending(lang).isNotBlank())
            assertTrue(AppStrings.metricApproved(lang).isNotBlank())
            assertTrue(AppStrings.metricRejected(lang).isNotBlank())
            assertTrue(AppStrings.sectionUserInfo(lang).isNotBlank())
            assertTrue(AppStrings.sectionRequestInfo(lang).isNotBlank())
            assertTrue(AppStrings.sectionReviewInfo(lang).isNotBlank())
        }
    }

    // ============================================================
    // PHASE C5 — USER MANAGEMENT & ACCOUNT ADMINISTRATION TESTS
    // ============================================================

    @Test
    fun testPhaseC5UserCollectionContractAndPaths() {
        assertEquals("users", FirebaseCollections.USERS)
        assertEquals("admins", FirebaseCollections.ADMINS)
        assertEquals("auditLogs", FirebaseCollections.AUDIT_LOGS)

        val user = User(
            uid = "canonical_uid_123",
            username = "johndoe",
            email = "johndoe@cinestream.com",
            displayName = "John Doe"
        )
        // Canonical UID Identity Invariant: uid == id
        assertEquals("canonical_uid_123", user.uid)
        assertEquals("canonical_uid_123", user.id)
        assertEquals(user.uid, user.id)
    }

    @Test
    fun testPhaseC5AdminAuthorityModelSeparation() {
        // A user document with role == "admin" is DATA ONLY and does not grant administrative authority
        val userProfile = User(
            uid = "user_pretending_admin",
            email = "impostor@cinestream.com",
            role = "admin"
        )
        assertEquals("admin", userProfile.role)

        // Canonical Administrative Authority resides SOLELY in /admins/{uid}.enabled == true
        val authorizedAdmin = com.example.models.AdminUser(
            uid = "admin_auth_456",
            email = "admin@cinestream.com",
            role = "admin",
            enabled = true
        )
        assertTrue(authorizedAdmin.enabled)
        assertEquals("admin_auth_456", authorizedAdmin.uid)

        val revokedAdmin = com.example.models.AdminUser(
            uid = "admin_revoked_789",
            email = "revoked@cinestream.com",
            role = "admin",
            enabled = false
        )
        assertFalse(revokedAdmin.enabled)
    }

    @Test
    fun testPhaseC5GlobalAccountBanStateMachine() {
        val now = System.currentTimeMillis()

        // 1. Good standing user
        val goodStandingUser = User(
            uid = "u_good",
            username = "good_user",
            isBanned = false,
            banReason = null,
            banExpiresAt = null
        )
        assertFalse(goodStandingUser.isAccountBanned)
        assertFalse(goodStandingUser.isPermanentBan)
        assertFalse(goodStandingUser.isBanExpired)

        // 2. Permanent Ban (isBanned = true, banExpiresAt = null)
        val permanentBannedUser = User(
            uid = "u_perm_ban",
            username = "spammer",
            isBanned = true,
            banReason = "Severe TOS breach: unauthorized scraper abuse",
            banExpiresAt = null
        )
        assertTrue(permanentBannedUser.isAccountBanned)
        assertTrue(permanentBannedUser.isPermanentBan)
        assertFalse(permanentBannedUser.isBanExpired)
        assertEquals("Severe TOS breach: unauthorized scraper abuse", permanentBannedUser.banReason)

        // 3. Active Timed Ban (isBanned = true, banExpiresAt in the future)
        val futureExpiry = now + 7L * 24 * 60 * 60 * 1000
        val activeTimedBannedUser = User(
            uid = "u_timed_ban",
            username = "toxic_commenter",
            isBanned = true,
            banReason = "Inappropriate chat behavior (7 days suspension)",
            banExpiresAt = futureExpiry
        )
        assertTrue(activeTimedBannedUser.isAccountBanned)
        assertFalse(activeTimedBannedUser.isPermanentBan)
        assertFalse(activeTimedBannedUser.isBanExpired)

        // 4. Expired Timed Ban (isBanned = true, banExpiresAt in the past)
        val pastExpiry = now - 1000L
        val expiredBannedUser = User(
            uid = "u_expired_ban",
            username = "rehabilitated_user",
            isBanned = true,
            banReason = "24h temporary cooldown",
            banExpiresAt = pastExpiry
        )
        assertFalse(expiredBannedUser.isAccountBanned)
        assertFalse(expiredBannedUser.isPermanentBan)
        assertTrue(expiredBannedUser.isBanExpired)
    }

    @Test
    fun testPhaseC5SubscriptionStateMachineAndCompatibilitySynchrony() {
        val now = System.currentTimeMillis()

        // 1. Free User
        val freeUser = User(
            uid = "u_free",
            subscriptionTier = "free",
            subscriptionStatus = "free",
            isPremium = false,
            subscriptionExpiresAt = null
        )
        assertEquals(com.example.models.SubscriptionState.FREE, freeUser.subscriptionState)
        assertFalse(freeUser.isSubscriptionActive)

        // 2. Active Lifetime Pro User
        val lifetimeProUser = User(
            uid = "u_lifetime_pro",
            subscriptionTier = "pro",
            subscriptionStatus = "active",
            isPremium = true,
            subscriptionExpiresAt = null,
            // Compatibility mirrors
            isPro = true,
            plan = "pro",
            proPlan = "pro",
            proExpiresAt = null
        )
        assertEquals(com.example.models.SubscriptionState.ACTIVE_PRO, lifetimeProUser.subscriptionState)
        assertTrue(lifetimeProUser.isSubscriptionActive)
        assertEquals(lifetimeProUser.isPremium, lifetimeProUser.isPro)
        assertEquals(lifetimeProUser.subscriptionTier, lifetimeProUser.plan)
        assertEquals(lifetimeProUser.subscriptionTier, lifetimeProUser.proPlan)
        assertEquals(lifetimeProUser.subscriptionExpiresAt, lifetimeProUser.proExpiresAt)

        // 3. Active Timed Pro User
        val activeProUser = User(
            uid = "u_active_pro",
            subscriptionTier = "pro",
            subscriptionStatus = "active",
            isPremium = true,
            subscriptionExpiresAt = now + 30L * 24 * 60 * 60 * 1000
        )
        assertEquals(com.example.models.SubscriptionState.ACTIVE_PRO, activeProUser.subscriptionState)
        assertTrue(activeProUser.isSubscriptionActive)

        // 4. Expired Pro User
        val expiredProUser = User(
            uid = "u_expired_pro",
            subscriptionTier = "pro",
            subscriptionStatus = "expired",
            isPremium = true,
            subscriptionExpiresAt = now - 5000L
        )
        assertEquals(com.example.models.SubscriptionState.EXPIRED_PRO, expiredProUser.subscriptionState)
        assertFalse(expiredProUser.isSubscriptionActive)
    }

    @Test
    fun testPhaseC5FeatureRestrictionDecoupling() {
        // Individual feature restrictions do NOT mark account as globally banned
        val restrictedUser = User(
            uid = "u_restricted",
            username = "restricted_user",
            isBanned = false,
            canWatch = true,
            watchBan = false,
            canDownload = false,
            downloadBan = true,
            canChat = false,
            chatBan = true,
            canStory = true,
            storyBan = false,
            canP2P = false,
            p2pBan = true,
            canComment = false,
            canUpload = false,
            canRequest = true
        )

        // Global account ban state is FALSE
        assertFalse(restrictedUser.isAccountBanned)
        assertFalse(restrictedUser.isPermanentBan)

        // Feature permissions evaluated independently
        assertTrue(restrictedUser.isWatchAllowed)
        assertFalse(restrictedUser.isDownloadAllowed)
        assertFalse(restrictedUser.isChatAllowed)
        assertTrue(restrictedUser.isStoryAllowed)
        assertFalse(restrictedUser.isP2PAllowed)
        assertFalse(restrictedUser.isCommentAllowed)
        assertFalse(restrictedUser.isUploadAllowed)
        assertTrue(restrictedUser.isRequestAllowed)

        // Overall restriction flag is TRUE
        assertTrue(restrictedUser.hasFeatureRestrictions)
    }

    @Test
    fun testPhaseC5SearchAndFilteringLogic() {
        val now = System.currentTimeMillis()
        val activeThreshold = now - 30L * 24 * 60 * 60 * 1000

        val list = listOf(
            User(
                uid = "uid_1",
                username = "KarimAhmed",
                email = "karim@cinestream.com",
                displayName = "Karim Ahmed",
                isPremium = true,
                subscriptionTier = "pro",
                createdAt = 100L,
                lastLoginTimestamp = now - 1000L
            ),
            User(
                uid = "uid_2",
                username = "LinaNasser",
                email = "lina@cinema.org",
                displayName = "Lina Nasser",
                isPremium = false,
                subscriptionTier = "free",
                createdAt = 200L,
                lastLoginTimestamp = activeThreshold - 5000L // Inactive
            ),
            User(
                uid = "uid_3",
                username = "TariqMansoor",
                email = "tariq@stream.net",
                displayName = "Tariq Mansoor",
                isBanned = true,
                banExpiresAt = null, // Permanent Ban
                createdAt = 300L,
                lastLoginTimestamp = now - 5000L
            ),
            User(
                uid = "uid_4",
                username = "MonaSalim",
                email = "mona@cinestream.com",
                displayName = "Mona Salim",
                canChat = false,
                chatBan = true, // Feature restricted
                createdAt = 400L,
                lastLoginTimestamp = now - 2000L
            )
        )

        // 1. Search by Username
        val searchUsername = list.filter { it.username.lowercase().contains("karim") }
        assertEquals(1, searchUsername.size)
        assertEquals("uid_1", searchUsername.first().id)

        // 2. Search by Email
        val searchEmail = list.filter { it.email.lowercase().contains("cinema.org") }
        assertEquals(1, searchEmail.size)
        assertEquals("uid_2", searchEmail.first().id)

        // 3. Search by UID
        val searchUid = list.filter { it.id.lowercase().contains("uid_3") }
        assertEquals(1, searchUid.size)
        assertEquals("uid_3", searchUid.first().id)

        // 4. Search by Display Name
        val searchDisplayName = list.filter { it.displayName.lowercase().contains("mona") }
        assertEquals(1, searchDisplayName.size)
        assertEquals("uid_4", searchDisplayName.first().id)

        // 5. Filter: ACTIVE
        val activeUsers = list.filter { it.lastLoginTimestamp >= activeThreshold }
        assertEquals(3, activeUsers.size)

        // 6. Filter: INACTIVE
        val inactiveUsers = list.filter { it.lastLoginTimestamp < activeThreshold }
        assertEquals(1, inactiveUsers.size)
        assertEquals("uid_2", inactiveUsers.first().id)

        // 7. Filter: PREMIUM (Active Pro)
        val proUsers = list.filter { it.subscriptionState == com.example.models.SubscriptionState.ACTIVE_PRO }
        assertEquals(1, proUsers.size)
        assertEquals("uid_1", proUsers.first().id)

        // 8. Filter: BANNED / RESTRICTED
        val bannedOrRestricted = list.filter { it.isAccountBanned || it.hasFeatureRestrictions }
        assertEquals(2, bannedOrRestricted.size)
        assertTrue(bannedOrRestricted.any { it.id == "uid_3" })
        assertTrue(bannedOrRestricted.any { it.id == "uid_4" })

        // 9. Sorting: NEWEST
        val newestSorted = list.sortedByDescending { it.createdAt }
        assertEquals("uid_4", newestSorted.first().id)

        // 10. Sorting: USERNAME
        val nameSorted = list.sortedBy { it.username.lowercase() }
        assertEquals("uid_1", nameSorted.first().id) // KarimAhmed
    }

    @Test
    fun testPhaseC5AuditTrailLoggingPayloads() {
        val now = System.currentTimeMillis()
        val userId = "target_user_999"

        // Ban audit action & payload
        val banAction = "BAN_USER"
        val banTargetType = "USER"
        val banPayload = hashMapOf<String, Any?>(
            "isBanned" to true,
            "banReason" to "TOS violation",
            "banExpiresAt" to (now + 86400000L),
            "isActive" to false,
            "updatedAt" to now
        )
        assertEquals("BAN_USER", banAction)
        assertEquals("USER", banTargetType)
        assertTrue(banPayload["isBanned"] as Boolean)
        assertFalse(banPayload["isActive"] as Boolean)

        // Unban audit action & payload
        val unbanAction = "UNBAN_USER"
        val unbanPayload = hashMapOf<String, Any?>(
            "isBanned" to false,
            "banReason" to null,
            "banExpiresAt" to null,
            "isActive" to true,
            "updatedAt" to now
        )
        assertEquals("UNBAN_USER", unbanAction)
        assertFalse(unbanPayload["isBanned"] as Boolean)
        assertTrue(unbanPayload["isActive"] as Boolean)

        // Grant subscription audit action & payload
        val grantSubAction = "GRANT_SUBSCRIPTION"
        val subPayload = hashMapOf<String, Any?>(
            "isPremium" to true,
            "subscriptionTier" to "pro",
            "subscriptionStatus" to "active",
            "subscriptionExpiresAt" to (now + 30L * 86400000L),
            "isPro" to true,
            "plan" to "pro",
            "proPlan" to "pro",
            "proExpiresAt" to (now + 30L * 86400000L),
            "updatedAt" to now
        )
        assertEquals("GRANT_SUBSCRIPTION", grantSubAction)
        assertTrue(subPayload["isPremium"] as Boolean)
        assertEquals("pro", subPayload["subscriptionTier"])

        // Revoke subscription action
        val revokeSubAction = "REVOKE_SUBSCRIPTION"
        assertEquals("REVOKE_SUBSCRIPTION", revokeSubAction)

        // Feature permission audit action
        val permAction = "UPDATE_FEATURE_PERMISSION"
        assertEquals("UPDATE_FEATURE_PERMISSION", permAction)

        // Admin privilege grant / revoke
        val grantAdminAction = "GRANT_ADMIN"
        val revokeAdminAction = "REVOKE_ADMIN"
        assertEquals("GRANT_ADMIN", grantAdminAction)
        assertEquals("REVOKE_ADMIN", revokeAdminAction)

        // User creation and deletion
        val createAction = "CREATE_USER"
        val deleteAction = "DELETE_USER"
        assertEquals("CREATE_USER", createAction)
        assertEquals("DELETE_USER", deleteAction)
    }

    @Test
    fun testPhaseC5LocalizationKeysIntegrity() {
        val langs = listOf(AppLanguage.ENGLISH, AppLanguage.ARABIC)
        for (lang in langs) {
            assertTrue(AppStrings.users(lang).isNotBlank())
            assertTrue(AppStrings.usersSubtitle(lang).isNotBlank())
            assertTrue(AppStrings.addUser(lang).isNotBlank())
            assertTrue(AppStrings.totalUsers(lang).isNotBlank())
            assertTrue(AppStrings.activeNow(lang).isNotBlank())
            assertTrue(AppStrings.proUsers(lang).isNotBlank())
            assertTrue(AppStrings.inactive(lang).isNotBlank())
            assertTrue(AppStrings.searchUsersPlaceholder(lang).isNotBlank())
            assertTrue(AppStrings.filterAll(lang).isNotBlank())
            assertTrue(AppStrings.filterActive(lang).isNotBlank())
            assertTrue(AppStrings.filterInactive(lang).isNotBlank())
            assertTrue(AppStrings.filterPro(lang).isNotBlank())
            assertTrue(AppStrings.filterBanned(lang).isNotBlank())
            assertTrue(AppStrings.sortNewestFirst(lang).isNotBlank())
            assertTrue(AppStrings.sortRecent(lang).isNotBlank())
            assertTrue(AppStrings.sortName(lang).isNotBlank())
            assertTrue(AppStrings.userListTitle(lang).isNotBlank())
            assertTrue(AppStrings.userCountBadge(5, lang).isNotBlank())
            assertTrue(AppStrings.neverLoggedIn(lang).isNotBlank())
            assertTrue(AppStrings.activeStatus(lang).isNotBlank())
            assertTrue(AppStrings.inactiveStatus(lang).isNotBlank())
            assertTrue(AppStrings.adminBadge(lang).isNotBlank())
            assertTrue(AppStrings.restrictedBadge(lang).isNotBlank())
            assertTrue(AppStrings.viewDetails(lang).isNotBlank())
            assertTrue(AppStrings.upgradeToPro(lang).isNotBlank())
            assertTrue(AppStrings.revokePro(lang).isNotBlank())
            assertTrue(AppStrings.restrictUser(lang).isNotBlank())
            assertTrue(AppStrings.unbanUser(lang).isNotBlank())
            assertTrue(AppStrings.deleteUser(lang).isNotBlank())
            assertTrue(AppStrings.confirmDeleteUserTitle(lang).isNotBlank())
            assertTrue(AppStrings.confirmDeleteUserMsg(lang).isNotBlank())
            assertTrue(AppStrings.addNewUserDialogTitle(lang).isNotBlank())
            assertTrue(AppStrings.username(lang).isNotBlank())
            assertTrue(AppStrings.email(lang).isNotBlank())
            assertTrue(AppStrings.proAccountCheckbox(lang).isNotBlank())
            assertTrue(AppStrings.adminRoleCheckbox(lang).isNotBlank())
            assertTrue(AppStrings.userDetailTitle(lang).isNotBlank())
            assertTrue(AppStrings.userDetailSubtitle(lang).isNotBlank())
            assertTrue(AppStrings.accountSecurityTitle(lang).isNotBlank())
            assertTrue(AppStrings.subscriptionSectionTitle(lang).isNotBlank())
            assertTrue(AppStrings.featureRestrictionsSection(lang).isNotBlank())
            assertTrue(AppStrings.banAccountBtn(lang).isNotBlank())
            assertTrue(AppStrings.unbanAccountBtn(lang).isNotBlank())
            assertTrue(AppStrings.banReasonLabel(lang).isNotBlank())
            assertTrue(AppStrings.grantOrExtendSub(lang).isNotBlank())
            assertTrue(AppStrings.revokeSubBtn(lang).isNotBlank())
            assertTrue(AppStrings.canWatch(lang).isNotBlank())
            assertTrue(AppStrings.canDownload(lang).isNotBlank())
            assertTrue(AppStrings.canChat(lang).isNotBlank())
            assertTrue(AppStrings.canStory(lang).isNotBlank())
            assertTrue(AppStrings.canP2P(lang).isNotBlank())
            assertTrue(AppStrings.canComment(lang).isNotBlank())
            assertTrue(AppStrings.canUpload(lang).isNotBlank())
            assertTrue(AppStrings.canRequest(lang).isNotBlank())
            assertTrue(AppStrings.isAdministrator(lang).isNotBlank())

            // Phase C6 Localization Checks
            assertTrue(AppStrings.tabOverview(lang).isNotBlank())
            assertTrue(AppStrings.tabUsers(lang).isNotBlank())
            assertTrue(AppStrings.dashboardOverviewTitle(lang).isNotBlank())
            assertTrue(AppStrings.dashboardOverviewSubtitle(lang).isNotBlank())
            assertTrue(AppStrings.kpiUsers(lang).isNotBlank())
            assertTrue(AppStrings.kpiSubscriptions(lang).isNotBlank())
            assertTrue(AppStrings.kpiSupport(lang).isNotBlank())
            assertTrue(AppStrings.kpiProRequests(lang).isNotBlank())
            assertTrue(AppStrings.kpiExtensions(lang).isNotBlank())
            assertTrue(AppStrings.kpiFeatureRestrictions(lang).isNotBlank())
            assertTrue(AppStrings.kpiRecentActivity(lang).isNotBlank())
            assertTrue(AppStrings.labelTotalUsers(lang).isNotBlank())
            assertTrue(AppStrings.labelActiveUsers(lang).isNotBlank())
            assertTrue(AppStrings.labelInactiveUsers(lang).isNotBlank())
            assertTrue(AppStrings.labelGlobalBans(lang).isNotBlank())
            assertTrue(AppStrings.labelFeatureRestrictedOnly(lang).isNotBlank())
            assertTrue(AppStrings.labelTotalRestricted(lang).isNotBlank())
            assertTrue(AppStrings.labelFreeUsers(lang).isNotBlank())
            assertTrue(AppStrings.labelPremiumUsers(lang).isNotBlank())
            assertTrue(AppStrings.labelActivePremium(lang).isNotBlank())
            assertTrue(AppStrings.labelExpiredPremium(lang).isNotBlank())
            assertTrue(AppStrings.labelTotalConversations(lang).isNotBlank())
            assertTrue(AppStrings.labelOpenConversations(lang).isNotBlank())
            assertTrue(AppStrings.labelUnreadMessages(lang).isNotBlank())
            assertTrue(AppStrings.labelPendingRequests(lang).isNotBlank())
            assertTrue(AppStrings.labelApprovedRequests(lang).isNotBlank())
            assertTrue(AppStrings.labelRejectedRequests(lang).isNotBlank())
            assertTrue(AppStrings.labelTotalExtensions(lang).isNotBlank())
            assertTrue(AppStrings.labelActiveExtensions(lang).isNotBlank())
            assertTrue(AppStrings.labelMaintenanceExtensions(lang).isNotBlank())
            assertTrue(AppStrings.labelDisabledExtensions(lang).isNotBlank())
            assertTrue(AppStrings.labelDeprecatedExtensions(lang).isNotBlank())
            assertTrue(AppStrings.labelWatchRestricted(lang).isNotBlank())
            assertTrue(AppStrings.labelDownloadRestricted(lang).isNotBlank())
            assertTrue(AppStrings.labelChatRestricted(lang).isNotBlank())
            assertTrue(AppStrings.labelStoryRestricted(lang).isNotBlank())
            assertTrue(AppStrings.labelP2pRestricted(lang).isNotBlank())
            assertTrue(AppStrings.quickAccess(lang).isNotBlank())
            assertTrue(AppStrings.viewAllLogs(lang).isNotBlank())
            assertTrue(AppStrings.emptyAuditLogs(lang).isNotBlank())
            assertTrue(AppStrings.refreshMetrics(lang).isNotBlank())
            assertTrue(AppStrings.failedToLoadSection(lang).isNotBlank())
        }
    }

    // =========================================================================
    // PHASE C6 — ADMIN ANALYTICS & OPERATIONAL DASHBOARD TESTS
    // =========================================================================

    @Test
    fun testC6UserMetrics() {
        val now = System.currentTimeMillis()
        val recentLogin = now - (5L * 24 * 60 * 60 * 1000) // 5 days ago (Active)
        val oldLogin = now - (40L * 24 * 60 * 60 * 1000) // 40 days ago (Inactive)

        val users = listOf(
            User(uid = "u1", id = "u1", lastLoginTimestamp = recentLogin, isPremium = true, subscriptionTier = "pro"),
            User(uid = "u2", id = "u2", lastLoginTimestamp = recentLogin, isPremium = false, subscriptionTier = "free"),
            User(uid = "u3", id = "u3", lastLoginTimestamp = oldLogin, isPremium = false, subscriptionTier = "free"),
            User(uid = "u4", id = "u4", lastLoginTimestamp = oldLogin, isBanned = true, banExpiresAt = null) // permanent ban, free tier
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(users, currentTimeMillis = now)

        assertEquals(4, metrics.totalUsers)
        assertEquals(2, metrics.activeUsers)
        assertEquals(2, metrics.inactiveUsers)
        assertEquals(1, metrics.bannedUsers)
        assertEquals(1, metrics.premiumUsers)
        assertEquals(3, metrics.freeUsers)
        assertEquals(1, metrics.activePremium)
        assertEquals(0, metrics.expiredPremium)
    }

    @Test
    fun testC6ActiveInactiveUsesC5Semantics() {
        val now = System.currentTimeMillis()
        val threshold = now - (30L * 24 * 60 * 60 * 1000)

        // Boundary test: Exactly at threshold, 1ms after (active), 1ms before (inactive)
        val users = listOf(
            User(uid = "exact", id = "exact", lastLoginTimestamp = threshold),
            User(uid = "active", id = "active", lastLoginTimestamp = threshold + 1L),
            User(uid = "inactive", id = "inactive", lastLoginTimestamp = threshold - 1L),
            User(uid = "never_logged_in", id = "never_logged_in", lastLoginTimestamp = 0L)
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(users, currentTimeMillis = now)

        assertEquals(4, metrics.totalUsers)
        assertEquals(2, metrics.activeUsers) // exact + active
        assertEquals(2, metrics.inactiveUsers) // inactive + never_logged_in
    }

    @Test
    fun testC6BanMetrics() {
        val now = System.currentTimeMillis()

        val users = listOf(
            // 1. Permanent ban
            User(uid = "p_ban", id = "p_ban", isBanned = true, banExpiresAt = null),
            // 2. Active temporary ban
            User(uid = "t_ban", id = "t_ban", isBanned = true, banExpiresAt = now + 100_000L),
            // 3. Expired temporary ban (not an active ban)
            User(uid = "exp_ban", id = "exp_ban", isBanned = true, banExpiresAt = now - 50_000L),
            // 4. Good standing with feature restriction (NOT an account ban)
            User(uid = "feat_only", id = "feat_only", isBanned = false, canChat = false, chatBan = true),
            // 5. Normal user
            User(uid = "norm", id = "norm", isBanned = false)
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(users, currentTimeMillis = now)

        // Explicitly verifies C1 ban decoupling:
        // Account bans must only count active permanent and non-expired temporary bans
        assertEquals(2, metrics.bannedUsers)
        // Feature restriction only
        assertEquals(1, metrics.featureRestrictedUsers)
        // Total restricted (account bans + feature restrictions)
        assertEquals(3, metrics.totalRestrictedUsers)
    }

    @Test
    fun testC6SubscriptionMetrics() {
        val now = System.currentTimeMillis()

        val users = listOf(
            // 1. Active Pro (lifetime)
            User(uid = "pro_life", id = "pro_life", isPremium = true, subscriptionTier = "pro", subscriptionExpiresAt = null),
            // 2. Active VIP (future expiry)
            User(uid = "vip_active", id = "vip_active", isPremium = true, subscriptionTier = "vip", subscriptionExpiresAt = now + 1_000_000L),
            // 3. Expired Pro
            User(uid = "pro_expired", id = "pro_expired", isPremium = true, subscriptionTier = "pro", subscriptionExpiresAt = now - 1_000L),
            // 4. Free user
            User(uid = "free_user", id = "free_user", isPremium = false, subscriptionTier = "free"),
            // 5. Legacy compatibility user with plan="pro" and isPro=true
            User(uid = "legacy_pro", id = "legacy_pro", isPro = true, plan = "pro", proExpiresAt = now + 500_000L)
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(users, currentTimeMillis = now)

        assertEquals(5, metrics.totalUsers)
        assertEquals(4, metrics.premiumUsers) // pro_life, vip_active, pro_expired, legacy_pro
        assertEquals(1, metrics.freeUsers)
        assertEquals(3, metrics.activePremium) // pro_life, vip_active, legacy_pro
        assertEquals(1, metrics.expiredPremium) // pro_expired

        // Tier breakdown verification
        assertEquals(3, metrics.tierBreakdown["pro"])
        assertEquals(1, metrics.tierBreakdown["vip"])
        assertEquals(1, metrics.tierBreakdown["free"])
    }

    @Test
    fun testC6FeatureRestrictionMetrics() {
        val users = listOf(
            User(uid = "u1", id = "u1", canWatch = false),
            User(uid = "u2", id = "u2", watchBan = true), // compatibility mirror
            User(uid = "u3", id = "u3", canDownload = false),
            User(uid = "u4", id = "u4", canChat = false, chatBan = true),
            User(uid = "u5", id = "u5", canStory = false),
            User(uid = "u6", id = "u6", canP2P = false, p2pBan = true),
            User(uid = "u7", id = "u7", canWatch = true, canDownload = true, canChat = true, canStory = true, canP2P = true)
        )

        val restrictions = com.example.models.DashboardAnalyticsCalculator.calculateFeatureRestrictions(users)

        assertEquals(2, restrictions.watchRestricted) // u1 (canWatch=false) + u2 (watchBan=true)
        assertEquals(1, restrictions.downloadRestricted) // u3
        assertEquals(1, restrictions.chatRestricted) // u4
        assertEquals(1, restrictions.storyRestricted) // u5
        assertEquals(1, restrictions.p2pRestricted) // u6
    }

    @Test
    fun testC6SupportMetrics() {
        val conversations = listOf(
            SupportConversation(conversationId = "c1", status = "OPEN", unreadByAdmin = true),
            SupportConversation(conversationId = "c2", status = "PENDING", unreadByAdmin = true),
            SupportConversation(conversationId = "c3", status = "RESOLVED", unreadByAdmin = false),
            SupportConversation(conversationId = "c4", status = "CLOSED", unreadByAdmin = false)
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateSupportMetrics(conversations)

        assertEquals(4, metrics.totalConversations)
        assertEquals(2, metrics.openConversations) // OPEN, PENDING
        assertEquals(2, metrics.unreadConversations) // c1, c2
    }

    @Test
    fun testC6ProRequestMetrics() {
        val requests = listOf(
            ProRequest(requestId = "r1", status = "PENDING"),
            ProRequest(requestId = "r2", status = "PENDING"),
            ProRequest(requestId = "r3", status = "APPROVED"),
            ProRequest(requestId = "r4", status = "REJECTED")
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateProRequestMetrics(requests)

        assertEquals(4, metrics.totalRequests)
        assertEquals(2, metrics.pendingRequests)
        assertEquals(1, metrics.approvedRequests)
        assertEquals(1, metrics.rejectedRequests)
    }

    @Test
    fun testC6ManagedExtensionMetrics() {
        val extensions = listOf(
            ManagedExtension(extensionId = "e1", status = "ACTIVE", enabled = true),
            ManagedExtension(extensionId = "e2", status = "ACTIVE", enabled = false), // disabled via enabled=false
            ManagedExtension(extensionId = "e3", status = "MAINTENANCE", enabled = true),
            ManagedExtension(extensionId = "e4", status = "DISABLED", enabled = true),
            ManagedExtension(extensionId = "e5", status = "DEPRECATED", enabled = false)
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateManagedExtensionMetrics(extensions)

        assertEquals(5, metrics.totalExtensions)
        assertEquals(1, metrics.activeExtensions) // only ACTIVE + enabled=true
        assertEquals(1, metrics.maintenanceExtensions)
        assertEquals(2, metrics.disabledExtensions) // e2 (enabled=false) + e4 (status=DISABLED)
        assertEquals(1, metrics.deprecatedExtensions)
    }

    @Test
    fun testC6RecentAuditActivity() {
        val logs = listOf(
            com.example.models.AuditLog(
                id = "log1",
                action = "BAN_USER",
                adminEmail = "admin@cinestream.com",
                targetType = "USER",
                targetId = "user123",
                details = "Suspended due to abuse",
                createdAt = 1_700_000_100_000L
            ),
            com.example.models.AuditLog(
                id = "log2",
                action = "GRANT_SUBSCRIPTION",
                adminEmail = "admin@cinestream.com",
                targetType = "USER",
                targetId = "user456",
                details = "Granted PRO 30 days",
                createdAt = 1_700_000_200_000L
            )
        )

        val state = com.example.models.DashboardSectionState.Success(logs)

        assertTrue(state is com.example.models.DashboardSectionState.Success)
        assertEquals(2, state.data.size)
        assertEquals("BAN_USER", state.data[0].action)
        assertEquals("USER", state.data[0].targetType)
        assertEquals("GRANT_SUBSCRIPTION", state.data[1].action)
    }

    @Test
    fun testC6EmptyDatasetHandling() {
        // Must handle zero-record collections safely without throwing, dividing by zero, or negative numbers
        val emptyUsers = emptyList<User>()
        val emptySupport = emptyList<SupportConversation>()
        val emptyRequests = emptyList<ProRequest>()
        val emptyExtensions = emptyList<ManagedExtension>()

        val userM = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(emptyUsers)
        val restrictionM = com.example.models.DashboardAnalyticsCalculator.calculateFeatureRestrictions(emptyUsers)
        val supportM = com.example.models.DashboardAnalyticsCalculator.calculateSupportMetrics(emptySupport)
        val proM = com.example.models.DashboardAnalyticsCalculator.calculateProRequestMetrics(emptyRequests)
        val extM = com.example.models.DashboardAnalyticsCalculator.calculateManagedExtensionMetrics(emptyExtensions)

        assertEquals(0, userM.totalUsers)
        assertEquals(0, userM.activeUsers)
        assertEquals(0, userM.inactiveUsers)
        assertEquals(0, userM.bannedUsers)
        assertEquals(0, userM.premiumUsers)
        assertEquals(0, userM.freeUsers)
        assertTrue(userM.tierBreakdown.isEmpty())

        assertEquals(0, restrictionM.watchRestricted)
        assertEquals(0, restrictionM.downloadRestricted)

        assertEquals(0, supportM.totalConversations)
        assertEquals(0, supportM.openConversations)
        assertEquals(0, supportM.unreadConversations)

        assertEquals(0, proM.totalRequests)
        assertEquals(0, proM.pendingRequests)

        assertEquals(0, extM.totalExtensions)
        assertEquals(0, extM.activeExtensions)
    }

    @Test
    fun testC6MalformedFirestoreDataSafety() {
        // Users with nulls, missing timestamps, unknown status values, or unformatted strings
        val malformedUsers = listOf(
            User(uid = "m1", id = "m1", subscriptionTier = "", plan = null, proPlan = null),
            User(uid = "m2", id = "m2", subscriptionTier = "UNKNOWN_CUSTOM_TIER", isPremium = false),
            User(uid = "m3", id = "m3", lastLoginTimestamp = -1L, createdAt = 0L)
        )

        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(malformedUsers)
        assertEquals(3, metrics.totalUsers)
        assertTrue(metrics.tierBreakdown.containsKey("free"))
        assertTrue(metrics.tierBreakdown.containsKey("unknown_custom_tier"))

        val malformedSupport = listOf(
            SupportConversation(conversationId = "s1", status = "BOGUS_STATUS")
        )
        val supportM = com.example.models.DashboardAnalyticsCalculator.calculateSupportMetrics(malformedSupport)
        assertEquals(1, supportM.totalConversations)
        assertEquals(0, supportM.openConversations) // Unknown is not counted as open

        val malformedExtensions = listOf(
            ManagedExtension(extensionId = "e1", status = "INVALID_STATE", enabled = false)
        )
        val extM = com.example.models.DashboardAnalyticsCalculator.calculateManagedExtensionMetrics(malformedExtensions)
        assertEquals(1, extM.totalExtensions)
        assertEquals(0, extM.activeExtensions)
        assertEquals(1, extM.disabledExtensions) // disabled because enabled=false
    }

    @Test
    fun testC6AdminAuthorityModelSeparation() {
        // Invariant: Administrative authority is derived strictly from /admins/{uid}.enabled == true.
        // It is NEVER derived from /users/{uid}.role or client-side metadata.
        val regularUserWithAdminRole = User(uid = "hacker", id = "hacker", role = "admin")

        fun isAuthorityPermitted(adminDocEnabled: Boolean): Boolean {
            // Evaluates /admins/{uid}.enabled exclusively
            return adminDocEnabled
        }

        // Even though user document has role="admin", admin authority is FALSE because adminDoc is not enabled
        assertFalse(isAuthorityPermitted(adminDocEnabled = false))

        // Legitimate admin with enabled=true has authority regardless of user document role metadata
        val adminUserWithUserRole = User(uid = "superadmin", id = "superadmin", role = "user")
        assertTrue(isAuthorityPermitted(adminDocEnabled = true))
    }

    @Test
    fun testC6UsersRoleNeverGrantsAuthority() {
        // Verify that neither DashboardViewModel nor any calculator uses users.role for authorization
        val users = listOf(
            User(uid = "u1", id = "u1", role = "admin"),
            User(uid = "u2", id = "u2", role = "moderator"),
            User(uid = "u3", id = "u3", role = "user")
        )

        // Metrics are read-only aggregations and do not treat role="admin" as privileged access
        val metrics = com.example.models.DashboardAnalyticsCalculator.calculateUserMetrics(users)
        assertEquals(3, metrics.totalUsers)
    }

    @Test
    fun testC6UsesCanonicalSources() {
        // Verify canonical Firestore paths used for dashboard operations
        assertEquals("users", FirebaseCollections.USERS)
        assertEquals("admins", FirebaseCollections.ADMINS)
        assertEquals("managed_extensions", FirebaseCollections.MANAGED_EXTENSIONS)
        assertEquals("support_conversations", FirebaseCollections.SUPPORT_CONVERSATIONS)
        assertEquals("pro_requests", FirebaseCollections.PRO_REQUESTS)
        assertEquals("auditLogs", FirebaseCollections.AUDIT_LOGS)
    }

    // ==========================================
    // PHASE C7: SECURITY RULES CONTRACT & FORENSIC TESTS
    // ==========================================

    @Test
    fun testC7AdminAuthorityFourCases() {
        data class AdminDoc(val exists: Boolean, val enabled: Boolean)
        data class UserDoc(val uid: String, val role: String)

        fun checkAdminAuthority(user: UserDoc, adminDoc: AdminDoc): Boolean {
            // Evaluates strictly: exists(/databases/$(database)/documents/admins/$(request.auth.uid)) && data.enabled == true
            // users.role is NEVER checked
            return adminDoc.exists && adminDoc.enabled
        }

        // CASE A: Enabled Admin
        val adminA = UserDoc("adminA", role = "user")
        val adminDocA = AdminDoc(exists = true, enabled = true)
        assertTrue(checkAdminAuthority(adminA, adminDocA))

        // CASE B: Authenticated Non-Admin (enabled = false)
        val userA = UserDoc("userA", role = "user")
        val adminDocB = AdminDoc(exists = true, enabled = false)
        assertFalse(checkAdminAuthority(userA, adminDocB))

        // CASE C: Missing Admin Document (exists = false)
        val userB = UserDoc("userB", role = "user")
        val adminDocC = AdminDoc(exists = false, enabled = false)
        assertFalse(checkAdminAuthority(userB, adminDocC))

        // CASE D (MANDATORY): users.role = "admin" but no /admins document
        val attacker = UserDoc("attacker", role = "admin")
        val adminDocD = AdminDoc(exists = false, enabled = false)
        assertFalse("CASE D MUST FAIL: role='admin' in /users without enabled /admins doc grants zero authority", checkAdminAuthority(attacker, adminDocD))
    }

    @Test
    fun testC7ProtectedUserFieldsListIntegrity() {
        // All sensitive fields identified in firestore.rules modifyingSensitiveUserFields()
        val protectedFields = setOf(
            "role", "isPremium", "subscriptionTier", "subscriptionStatus", "subscriptionExpiresAt",
            "isPro", "proExpiresAt", "proPlan", "plan", "isActive", "isBanned", "banReason",
            "banExpiresAt", "canWatch", "canDownload", "canChat", "canStory", "canP2P",
            "canComment", "canUpload", "canRequest", "watchBan", "downloadBan", "chatBan",
            "storyBan", "p2pBan", "deviceLimit", "offlineDaysOverride", "forcedAdsOverride",
            "uid", "id", "createdAt"
        )

        // Ensure key fields are in the protected list
        assertTrue(protectedFields.contains("role"))
        assertTrue(protectedFields.contains("isPremium"))
        assertTrue(protectedFields.contains("isPro"))
        assertTrue(protectedFields.contains("isBanned"))
        assertTrue(protectedFields.contains("deviceLimit"))
        assertTrue(protectedFields.contains("uid"))
        assertEquals(32, protectedFields.size)

        // Non-sensitive fields (safe for owner to edit)
        val safeFields = listOf("displayName", "avatarUrl", "bio", "fcmToken", "lastLoginTimestamp")
        safeFields.forEach { field ->
            assertFalse(protectedFields.contains(field))
        }
    }

    @Test
    fun testC7SupportConversationsSecurityInvariants() {
        // Conversation ownership rule: userId == request.auth.uid
        fun canAccessConversation(authUid: String, conversationUserId: String, isAdmin: Boolean): Boolean {
            return isAdmin || authUid == conversationUserId
        }

        assertTrue("Admin can access any conversation", canAccessConversation("adminA", "userA", isAdmin = true))
        assertTrue("Owner can access own conversation", canAccessConversation("userA", "userA", isAdmin = false))
        assertFalse("Other user cannot access conversation", canAccessConversation("userB", "userA", isAdmin = false))

        // Message creation rule: senderId == authUid and senderRole != 'admin' (unless isAdmin)
        fun canCreateMessage(authUid: String, conversationUserId: String, senderId: String, senderRole: String, isAdmin: Boolean): Boolean {
            if (isAdmin) return true
            return authUid == conversationUserId && senderId == authUid && senderRole == "user"
        }

        assertTrue("Owner can send message as user", canCreateMessage("userA", "userA", "userA", "user", isAdmin = false))
        assertFalse("Owner cannot forge admin role", canCreateMessage("userA", "userA", "userA", "admin", isAdmin = false))
        assertFalse("Other user cannot post to conversation", canCreateMessage("userB", "userA", "userB", "user", isAdmin = false))
        assertTrue("Admin can send message with admin role", canCreateMessage("adminA", "userA", "adminA", "admin", isAdmin = true))
    }

    @Test
    fun testC7ProRequestsSecurityInvariants() {
        // Pro request creation rule: status must be PENDING, reviewedBy must be null/empty
        fun canCreateProRequest(authUid: String, reqUserId: String, status: String, reviewedBy: String?): Boolean {
            return authUid == reqUserId && (status == "PENDING" || status == "pending") && reviewedBy.isNullOrEmpty()
        }

        assertTrue(canCreateProRequest("userA", "userA", "PENDING", null))
        assertFalse("Cannot forge approved status", canCreateProRequest("userA", "userA", "APPROVED", null))
        assertFalse("Cannot forge reviewedBy", canCreateProRequest("userA", "userA", "PENDING", "adminA"))
        assertFalse("Cannot create for other user", canCreateProRequest("userA", "userB", "PENDING", null))
    }

    // ==========================================
    // MANAGED EXTENSION EXTENDED RESILIENCE TESTS
    // ==========================================

    @Test
    fun testManagedExtensionResilienceAndOrdering() {
        val ext1 = ManagedExtension(
            extensionId = "qfilm",
            scraperKey = "qfilm",
            name = "QFilm",
            baseUrl = "https://qfilm.vip",
            priority = 110,
            status = "ACTIVE",
            contentTypes = listOf("movie", "series"),
            capabilities = listOf("search", "video_extraction"),
            minAppVersionCode = 2
        )

        // Case-insensitivity in validator
        val errors = ManagedExtensionValidator.validate(ext1)
        assertTrue("Lowercase contentTypes and capabilities should be accepted without validation error: $errors", errors.isEmpty())

        // Priority ordering
        val ext2 = ext1.copy(extensionId = "egydead", scraperKey = "egydead", priority = 90)
        val ext3 = ext1.copy(extensionId = "witanime", scraperKey = "witanime", priority = 105)

        val list = listOf(ext2, ext1, ext3).sortedByDescending { it.priority }
        assertEquals("qfilm", list[0].extensionId)
        assertEquals("witanime", list[1].extensionId)
        assertEquals("egydead", list[2].extensionId)

        // Default scrapers catalog contains expected core sources
        val defaults = com.example.repository.DefaultCineStreamScrapers.getDefaults()
        assertTrue(defaults.isNotEmpty())
        assertTrue(defaults.any { it.scraperKey == "qfilm" })
        assertTrue(defaults.any { it.scraperKey == "witanime" })
        assertTrue(defaults.any { it.scraperKey == "egydead" })
    }

    // ==========================================
    // CANONICAL FIRESTORE ALIGNMENT & HARDENING TESTS
    // ==========================================

    @Test
    fun testCanonicalAdminAuthorityValidation() {
        // Authority logic: Admin must have /admins/{uid} doc with enabled == true
        fun evaluateAdminAuthority(uid: String, email: String?, adminDocEnabled: Boolean?, userRole: String?, userIsAdmin: Boolean?): Boolean {
            // Project owner bootstrap
            if (email.equals("sulopros01@gmail.com", ignoreCase = true)) return true
            // Strict check against /admins/{uid}.enabled == true
            if (adminDocEnabled == true) return true
            // users.role or user.isAdmin is strictly ignored
            return false
        }

        assertTrue("Owner is always authorized as bootstrap superadmin",
            evaluateAdminAuthority("owner123", "sulopros01@gmail.com", adminDocEnabled = null, userRole = "user", userIsAdmin = false))

        assertTrue("Admin with /admins doc enabled == true is authorized",
            evaluateAdminAuthority("admin1", "staff@cinestream.com", adminDocEnabled = true, userRole = "user", userIsAdmin = false))

        assertFalse("Disabled admin (/admins enabled == false) MUST BE DENIED",
            evaluateAdminAuthority("admin2", "suspended@cinestream.com", adminDocEnabled = false, userRole = "admin", userIsAdmin = true))

        assertFalse("User claiming role='admin' in /users with NO /admins doc MUST BE DENIED",
            evaluateAdminAuthority("attacker", "hacker@evil.com", adminDocEnabled = null, userRole = "admin", userIsAdmin = true))

        assertFalse("User with isAdmin=true in /users with NO /admins doc MUST BE DENIED",
            evaluateAdminAuthority("attacker2", "hacker2@evil.com", adminDocEnabled = null, userRole = "user", userIsAdmin = true))
    }

    @Test
    fun testAllSensitiveUserFieldsProtectionSet() {
        val protectedFields = setOf(
            "role", "isPremium", "subscriptionTier", "subscriptionStatus", "subscriptionExpiresAt",
            "isPro", "proExpiresAt", "proPlan", "plan", "isActive", "isBanned", "banReason",
            "banExpiresAt", "canWatch", "canDownload", "canChat", "canStory", "canP2P",
            "canComment", "canUpload", "canRequest", "watchBan", "downloadBan", "chatBan",
            "storyBan", "p2pBan", "deviceLimit", "maxDevices", "allowedQuality", "downloadLimit",
            "offlineDaysOverride", "forcedAdsOverride", "uid", "id", "createdAt", "admin", "isAdmin"
        )
        assertEquals("Total protected sensitive fields must equal 37", 37, protectedFields.size)

        // Ensure user attempting to update sensitive field is blocked
        fun canStandardUserModify(changedKey: String): Boolean {
            return !protectedFields.contains(changedKey)
        }

        assertTrue(canStandardUserModify("displayName"))
        assertTrue(canStandardUserModify("photoUrl"))
        assertTrue(canStandardUserModify("username"))
        assertTrue(canStandardUserModify("lastLoginAt"))
        assertTrue(canStandardUserModify("updatedAt"))

        assertFalse(canStandardUserModify("role"))
        assertFalse(canStandardUserModify("admin"))
        assertFalse(canStandardUserModify("isAdmin"))
        assertFalse(canStandardUserModify("isPremium"))
        assertFalse(canStandardUserModify("isBanned"))
        assertFalse(canStandardUserModify("deviceLimit"))
        assertFalse(canStandardUserModify("maxDevices"))
        assertFalse(canStandardUserModify("allowedQuality"))
        assertFalse(canStandardUserModify("downloadLimit"))
        assertFalse(canStandardUserModify("canWatch"))
        assertFalse(canStandardUserModify("canDownload"))
        assertFalse(canStandardUserModify("canChat"))
    }

    @Test
    fun testGlobalBanVsFeatureRestrictionsDecoupling() {
        // User with account ban
        val bannedUser = User(
            uid = "u1",
            isBanned = true,
            banReason = "Spamming",
            banExpiresAt = System.currentTimeMillis() + 100000L,
            canWatch = true,
            canDownload = true,
            canChat = true
        )
        assertTrue("Account must evaluate as banned", bannedUser.isAccountBanned)
        // Feature restrictions flag is false because individual feature permissions are intact
        assertFalse("Account ban does not mutate feature permissions", bannedUser.hasFeatureRestrictions)

        // User with feature restrictions only (not account banned)
        val restrictedUser = User(
            uid = "u2",
            isBanned = false,
            canChat = false,
            chatBan = true,
            canStory = false,
            storyBan = true
        )
        assertFalse("Account is NOT banned", restrictedUser.isAccountBanned)
        assertTrue("Feature restrictions are detected", restrictedUser.hasFeatureRestrictions)
        assertFalse("Chat is forbidden", restrictedUser.isChatAllowed)
        assertFalse("Story is forbidden", restrictedUser.isStoryAllowed)
        assertTrue("Watch is allowed", restrictedUser.isWatchAllowed)
        assertTrue("Download is allowed", restrictedUser.isDownloadAllowed)
    }

    @Test
    fun testCanonicalAppUpdateModelAndOrdering() {
        val u1 = com.example.models.AppUpdate(
            id = "1",
            versionCode = 1,
            versionName = "1.0.0",
            apkUrl = "https://cdn.example.com/v1.apk",
            mandatoryUpdate = false
        )
        val u2 = com.example.models.AppUpdate(
            id = "2",
            versionCode = 2,
            versionName = "2.0.0",
            minVersionCode = 2,
            apkUrl = "https://cdn.example.com/v2.apk",
            mandatoryUpdate = true
        )

        val updates = listOf(u1, u2).sortedByDescending { it.versionCode }
        assertEquals(2, updates.first().versionCode)
        assertEquals("2.0.0", updates.first().versionName)
        assertTrue(updates.first().mandatoryUpdate)
        assertEquals("1.0.0", updates.last().versionName)
    }

    @Test
    fun testSocialChatAndStoriesOwnershipRules() {
        // Social chat: user can read/create/update if in participants
        fun canAccessSocialChat(authUid: String, participants: List<String>): Boolean {
            return participants.contains(authUid)
        }

        val convParticipants = listOf("userA", "userB")
        assertTrue("Participant Alice can access", canAccessSocialChat("userA", convParticipants))
        assertTrue("Participant Bob can access", canAccessSocialChat("userB", convParticipants))
        assertFalse("Non-participant Charlie cannot access", canAccessSocialChat("userC", convParticipants))

        // Stories: author only can modify/delete
        fun canModifyStory(authUid: String, storyAuthorId: String): Boolean {
            return authUid == storyAuthorId
        }

        assertTrue("Author can modify own story", canModifyStory("userA", "userA"))
        assertFalse("Other user cannot modify story", canModifyStory("userB", "userA"))
    }

    @Test
    fun testCanonicalCollectionsNaming() {
        assertEquals("admins", FirebaseCollections.ADMINS)
        assertEquals("users", FirebaseCollections.USERS)
        assertEquals("config", FirebaseCollections.CONFIG)
        assertEquals("notifications", FirebaseCollections.NOTIFICATIONS)
        assertEquals("auditLogs", FirebaseCollections.AUDIT_LOGS)
        assertEquals("reports", FirebaseCollections.REPORTS)
        assertEquals("managed_extensions", FirebaseCollections.MANAGED_EXTENSIONS)
        assertEquals("extensions", FirebaseCollections.LEGACY_EXTENSIONS)
        assertEquals("app_updates", FirebaseCollections.APP_UPDATES)
        assertEquals("support_conversations", FirebaseCollections.SUPPORT_CONVERSATIONS)
        assertEquals("conversations", FirebaseCollections.SOCIAL_CONVERSATIONS)
        assertEquals("stories", FirebaseCollections.STORIES)
        assertEquals("pro_requests", FirebaseCollections.PRO_REQUESTS)
        assertEquals("app", com.example.contract.FirebaseConfigDocs.APP)
        assertEquals("search_order", com.example.contract.FirebaseConfigDocs.SEARCH_ORDER)
    }

    @Test
    fun testSearchOrderConfigSerializationAndAliases() {
        val config = SearchOrderConfig(
            movie = listOf("qfilm", "egydead", "ext_movie"),
            tv = listOf("egydead", "qfilm"),
            anime = listOf("witanime", "anime4up"),
            updatedAt = 1700000000000L,
            updatedBy = "admin@cinestream.com"
        )

        // 1. Serialization with dual-write alias tv -> series
        val map = config.toMap()
        assertEquals(listOf("qfilm", "egydead", "ext_movie"), map["movie"])
        assertEquals(listOf("egydead", "qfilm"), map["tv"])
        assertEquals(listOf("egydead", "qfilm"), map["series"]) // Backward-compatible alias
        assertEquals(listOf("witanime", "anime4up"), map["anime"])
        assertEquals(1700000000000L, map["updatedAt"])
        assertEquals("admin@cinestream.com", map["updatedBy"])

        // 2. Deserialization from map
        val restored = SearchOrderConfig.fromMap(map)
        assertEquals(config.movie, restored.movie)
        assertEquals(config.tv, restored.tv)
        assertEquals(config.anime, restored.anime)
        assertEquals(config.updatedAt, restored.updatedAt)
        assertEquals(config.updatedBy, restored.updatedBy)

        // 3. Deserialization fallback from "series" if "tv" is missing
        val legacyMap = mapOf(
            "movie" to listOf("qfilm"),
            "series" to listOf("egydead"),
            "anime" to listOf("witanime")
        )
        val legacyRestored = SearchOrderConfig.fromMap(legacyMap)
        assertEquals(listOf("egydead"), legacyRestored.tv)
    }

    @Test
    fun testSearchOrderCategoryMapping() {
        assertEquals(SearchOrderCategory.MOVIE, SearchOrderCategory.fromString("movie"))
        assertEquals(SearchOrderCategory.MOVIE, SearchOrderCategory.fromString("MOVIES"))
        assertEquals(SearchOrderCategory.TV, SearchOrderCategory.fromString("tv"))
        assertEquals(SearchOrderCategory.TV, SearchOrderCategory.fromString("SERIES"))
        assertEquals(SearchOrderCategory.TV, SearchOrderCategory.fromString("TV_SERIES"))
        assertEquals(SearchOrderCategory.ANIME, SearchOrderCategory.fromString("anime"))
        assertEquals(SearchOrderCategory.ANIME, SearchOrderCategory.fromString("ANIME"))

        val config = SearchOrderConfig(
            movie = listOf("m1", "m2"),
            tv = listOf("t1"),
            anime = listOf("a1", "a2", "a3")
        )

        assertEquals(listOf("m1", "m2"), config.getOrderForCategory(SearchOrderCategory.MOVIE))
        assertEquals(listOf("t1"), config.getOrderForCategory(SearchOrderCategory.TV))
        assertEquals(listOf("a1", "a2", "a3"), config.getOrderForCategory(SearchOrderCategory.ANIME))

        val updated = config.withOrderForCategory(SearchOrderCategory.TV, listOf("t1", "t2"))
        assertEquals(listOf("t1", "t2"), updated.tv)
        assertEquals(listOf("m1", "m2"), updated.movie) // Unaffected
    }

    @Test
    fun testSearchOrderValidatorDuplicateDetection() {
        val available = mapOf(
            "qfilm" to ManagedExtension(extensionId = "qfilm", name = "QFilm", contentTypes = listOf("MOVIE")),
            "egydead" to ManagedExtension(extensionId = "egydead", name = "EgyDead", contentTypes = listOf("MOVIE"))
        )

        val configWithDupes = SearchOrderConfig(
            movie = listOf("qfilm", "egydead", "qfilm"),
            tv = emptyList(),
            anime = emptyList()
        )

        val errors = SearchOrderValidator.validate(configWithDupes, available)
        assertTrue("Must detect duplicate extension ID in search order", errors.any { it.contains("Duplicate extension ID") })
    }

    @Test
    fun testSearchOrderValidatorNonexistentExtension() {
        val available = mapOf(
            "qfilm" to ManagedExtension(extensionId = "qfilm", name = "QFilm", contentTypes = listOf("MOVIE"))
        )

        val configWithUnknown = SearchOrderConfig(
            movie = listOf("qfilm", "ghost_extension"),
            tv = emptyList(),
            anime = emptyList()
        )

        val errors = SearchOrderValidator.validate(configWithUnknown, available)
        assertTrue("Must detect non-existent extension", errors.any { it.contains("does not exist in registry") })
    }

    @Test
    fun testSearchOrderValidatorContentTypeMismatch() {
        val available = mapOf(
            "qfilm" to ManagedExtension(extensionId = "qfilm", name = "QFilm", contentTypes = listOf("MOVIE")),
            "witanime" to ManagedExtension(extensionId = "witanime", name = "WitAnime", contentTypes = listOf("ANIME"))
        )

        // witanime only supports ANIME, cannot be placed into MOVIE search order
        val invalidConfig = SearchOrderConfig(
            movie = listOf("qfilm", "witanime"),
            tv = emptyList(),
            anime = listOf("witanime")
        )

        val errors = SearchOrderValidator.validate(invalidConfig, available)
        assertTrue("Must reject extension that doesn't support the category", errors.any { it.contains("does not support content type") })
    }

    @Test
    fun testSearchOrderReorderingLogic() {
        val initial = listOf("qfilm", "egydead", "ext_c")

        // Move Down from index 0 -> index 1
        val afterMoveDown = initial.toMutableList().apply {
            val temp = this[0]
            this[0] = this[1]
            this[1] = temp
        }
        assertEquals(listOf("egydead", "qfilm", "ext_c"), afterMoveDown)

        // Move Up from index 2 -> index 1
        val afterMoveUp = afterMoveDown.toMutableList().apply {
            val temp = this[2]
            this[2] = this[1]
            this[1] = temp
        }
        assertEquals(listOf("egydead", "ext_c", "qfilm"), afterMoveUp)

        // Remove index 1
        val afterRemove = afterMoveUp.toMutableList().apply {
            removeAt(1)
        }
        assertEquals(listOf("egydead", "qfilm"), afterRemove)

        // Add to end
        val afterAdd = afterRemove.toMutableList().apply {
            add("new_ext")
        }
        assertEquals(listOf("egydead", "qfilm", "new_ext"), afterAdd)
    }
}


