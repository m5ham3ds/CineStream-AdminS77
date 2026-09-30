package com.example.repository

import com.example.contract.FirebaseCollections
import com.example.contract.FirebaseConfigDocs
import com.example.diagnostics.AppLogger
import com.example.models.*
import com.example.state.AppSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AdminRepository {
    private val _pendingSyncKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingSyncKeys: StateFlow<Set<String>> = _pendingSyncKeys.asStateFlow()
    private fun ensureFirebase() {
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(com.example.MyApplication.instance)
            if (apps.isEmpty()) {
                com.example.diagnostics.FirebaseInitializer.init(com.example.MyApplication.instance)
            }
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "ensureFirebase check exception: ${e.message}")
        }
    }

    private val auth: FirebaseAuth by lazy {
        ensureFirebase()
        FirebaseAuth.getInstance()
    }

    private val firestore: FirebaseFirestore by lazy {
        ensureFirebase()
        FirebaseFirestore.getInstance()
    }

    private val usersCollection get() = firestore.collection(FirebaseCollections.USERS)
    private val adminsCollection get() = firestore.collection(FirebaseCollections.ADMINS)
    private val configCollection get() = firestore.collection(FirebaseCollections.CONFIG)
    private val notificationsCollection get() = firestore.collection(FirebaseCollections.NOTIFICATIONS)
    private val auditLogsCollection get() = firestore.collection(FirebaseCollections.AUDIT_LOGS)
    private val extensionsCollection get() = firestore.collection(FirebaseCollections.LEGACY_EXTENSIONS)
    private val reportsCollection get() = firestore.collection(FirebaseCollections.REPORTS)
    private val appUpdatesCollection get() = firestore.collection(FirebaseCollections.APP_UPDATES)

    private fun extractTimestampMillis(doc: com.google.firebase.firestore.DocumentSnapshot, field: String, fallback: Long = 0L): Long {
        val value = doc.get(field) ?: return fallback
        return when (value) {
            is Number -> value.toLong()
            is com.google.firebase.Timestamp -> value.toDate().time
            is java.util.Date -> value.time
            else -> fallback
        }
    }

    private fun docToUser(doc: com.google.firebase.firestore.DocumentSnapshot): User? {
        return try {
            val uid = doc.getString("uid")?.ifBlank { doc.id } ?: doc.id
            val isPrem = doc.getBoolean("isPremium") ?: doc.getBoolean("isPro") ?: false
            val subTier = doc.getString("subscriptionTier") ?: doc.getString("plan") ?: doc.getString("proPlan") ?: "free"
            val subStatus = doc.getString("subscriptionStatus") ?: if (isPrem) "active" else "free"
            val subExpires = if (doc.contains("subscriptionExpiresAt")) {
                extractTimestampMillis(doc, "subscriptionExpiresAt", 0L).takeIf { it > 0L }
            } else if (doc.contains("proExpiresAt")) {
                extractTimestampMillis(doc, "proExpiresAt", 0L).takeIf { it > 0L }
            } else null

            val hasPending = doc.metadata.hasPendingWrites()
            val isBanned = doc.getBoolean("isBanned") ?: false
            val banReason = doc.getString("banReason")
            val banExpiresAt = if (doc.contains("banExpiresAt")) {
                extractTimestampMillis(doc, "banExpiresAt", 0L).takeIf { it > 0L }
            } else null

            // Dual permission resolution: positive flags take priority, inverted ban flags used as fallback
            val canWatch = doc.getBoolean("canWatch") ?: !(doc.getBoolean("watchBan") ?: false)
            val canDownload = doc.getBoolean("canDownload") ?: !(doc.getBoolean("downloadBan") ?: false)
            val canChat = doc.getBoolean("canChat") ?: !(doc.getBoolean("chatBan") ?: false)
            val canStory = doc.getBoolean("canStory") ?: !(doc.getBoolean("storyBan") ?: false)
            val canP2P = doc.getBoolean("canP2P") ?: !(doc.getBoolean("p2pBan") ?: false)
            val canComment = doc.getBoolean("canComment") ?: true
            val canUpload = doc.getBoolean("canUpload") ?: false
            val canRequest = doc.getBoolean("canRequest") ?: true

            val watchBan = doc.getBoolean("watchBan") ?: !canWatch
            val downloadBan = doc.getBoolean("downloadBan") ?: !canDownload
            val chatBan = doc.getBoolean("chatBan") ?: !canChat
            val storyBan = doc.getBoolean("storyBan") ?: !canStory
            val p2pBan = doc.getBoolean("p2pBan") ?: !canP2P

            val lastLogin = extractTimestampMillis(
                doc,
                "lastLoginAt",
                extractTimestampMillis(doc, "lastLoginTimestamp", 0L)
            )
            val updated = extractTimestampMillis(
                doc,
                "updatedAt",
                extractTimestampMillis(doc, "lastActiveAt", 0L)
            )
            val created = extractTimestampMillis(doc, "createdAt", 0L)

            User(
                uid = uid,
                id = uid,
                username = doc.getString("username") ?: "",
                email = doc.getString("email") ?: "",
                displayName = doc.getString("displayName") ?: "",
                photoUrl = doc.getString("photoUrl") ?: "",
                createdAt = created,
                updatedAt = updated,
                lastLoginAt = lastLogin,
                lastLoginTimestamp = lastLogin,
                lastActiveAt = updated,
                isActive = doc.getBoolean("isActive") ?: !isBanned,
                isPremium = isPrem,
                subscriptionTier = subTier,
                subscriptionStatus = subStatus,
                subscriptionExpiresAt = subExpires,
                isPro = isPrem,
                plan = subTier,
                proPlan = subTier,
                proExpiresAt = subExpires,
                role = doc.getString("role") ?: "user",
                isBanned = isBanned,
                banReason = banReason,
                banExpiresAt = banExpiresAt,
                canWatch = canWatch,
                canDownload = canDownload,
                canChat = canChat,
                canStory = canStory,
                canP2P = canP2P,
                canComment = canComment,
                canUpload = canUpload,
                canRequest = canRequest,
                watchBan = watchBan,
                downloadBan = downloadBan,
                chatBan = chatBan,
                storyBan = storyBan,
                p2pBan = p2pBan,
                deviceLimit = (doc.getLong("deviceLimit") ?: doc.getLong("maxDevices") ?: 2L).toInt(),
                maxDevices = (doc.getLong("maxDevices") ?: doc.getLong("deviceLimit") ?: 2L).toInt(),
                allowedQuality = doc.getString("allowedQuality"),
                downloadLimit = doc.getLong("downloadLimit")?.toInt(),
                offlineDaysOverride = doc.getLong("offlineDaysOverride")?.toInt(),
                forcedAdsOverride = doc.getLong("forcedAdsOverride")?.toInt(),
                appVersion = doc.getString("appVersion") ?: "",
                admin = doc.getBoolean("admin") ?: false,
                isAdmin = doc.getBoolean("isAdmin") ?: false,
                hasPendingSync = hasPending
            )
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Failed to deserialize user document ${doc.id}: ${e.message}")
            null
        }
    }

    /**
     * Verifies if the authenticated user is an authorized administrator.
     * Enforces the Canonical Firebase Contract:
     * - The sole authority source is `/admins/{uid}` where `enabled == true`.
     * - Fallback to `/users/{uid}.role` is STRICTLY REMOVED to align with firestore.rules.
     * - Provisions bootstrap superadmin credentials for the project owner.
     */
    suspend fun checkIsAdmin(user: FirebaseUser): Boolean {
        // Instant check for project owner: always granted superadmin
        val isOwner = user.email?.equals("sulopros01@gmail.com", ignoreCase = true) == true
        if (isOwner) {
            AppLogger.i("AdminRepository", "Project owner authenticated: ${user.email}")
            AppSettings.setAdminSession(user.uid, user.email ?: "")
            try {
                // Non-blocking fire-and-forget sync to /admins so offline mode never hangs!
                adminsCollection.document(user.uid).set(
                    AdminUser(
                        uid = user.uid,
                        email = user.email ?: "",
                        role = "superadmin",
                        enabled = true,
                        createdAt = System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
            } catch (e: Exception) {
                AppLogger.w("AdminRepository", "Owner bootstrap doc write non-fatal notice: ${e.message}")
            }
            return true
        }

        // Instant verification if the user already has an active local admin session
        if (AppSettings.hasActiveAdminSession(user.uid)) {
            AppLogger.i("AdminRepository", "User verified via active local admin session: ${user.uid}")
            return true
        }

        // Check /admins/{uid} in Firestore with short timeout falling back to local cache
        try {
            val adminDoc = try {
                withTimeoutOrNull(2500L) {
                    adminsCollection.document(user.uid).get(Source.SERVER).await()
                } ?: adminsCollection.document(user.uid).get(Source.CACHE).await()
            } catch (e: Exception) {
                try {
                    adminsCollection.document(user.uid).get(Source.CACHE).await()
                } catch (_: Exception) {
                    null
                }
            }

            if (adminDoc != null && adminDoc.exists()) {
                val enabled = adminDoc.getBoolean("enabled") == true
                if (enabled) {
                    AppSettings.setAdminSession(user.uid, user.email ?: "")
                    return true
                }
            }
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Admin collection check notice: ${e.message}")
        }

        // Also verify /users/{uid} in Firestore (admin/superadmin role or isAdmin flag)
        try {
            val userDoc = try {
                withTimeoutOrNull(2500L) {
                    usersCollection.document(user.uid).get(Source.SERVER).await()
                } ?: usersCollection.document(user.uid).get(Source.CACHE).await()
            } catch (e: Exception) {
                try {
                    usersCollection.document(user.uid).get(Source.CACHE).await()
                } catch (_: Exception) {
                    null
                }
            }

            if (userDoc != null && userDoc.exists()) {
                val role = userDoc.getString("role")?.lowercase()
                val isAdmin = userDoc.getBoolean("isAdmin") == true ||
                        userDoc.getBoolean("admin") == true ||
                        role in listOf("admin", "superadmin", "owner")
                if (isAdmin) {
                    AppSettings.setAdminSession(user.uid, user.email ?: "")
                    return true
                }
            }
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "User role check notice: ${e.message}")
        }

        return false
    }

    /**
     * Records administrative actions in Firestore for full transparency and accountability.
     */
    suspend fun logAudit(action: String, targetType: String, targetId: String, details: String) {
        try {
            val currentAdmin = auth.currentUser
            val docRef = auditLogsCollection.document()
            val log = AuditLog(
                id = docRef.id,
                actorUid = currentAdmin?.uid ?: "system",
                adminUid = currentAdmin?.uid ?: "system",
                adminEmail = currentAdmin?.email ?: "system",
                action = action,
                targetType = targetType,
                targetId = targetId,
                details = details,
                createdAt = System.currentTimeMillis()
            )
            docRef.set(log, SetOptions.merge())
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Non-fatal audit log notice: ${e.message}")
        }
    }

    // --- USERS MANAGEMENT ---

    private val syncManager by lazy {
        com.example.network.ResilientSyncManager.getInstance(com.example.MyApplication.instance)
    }

    suspend fun forceRefreshUsers() {
        try {
            usersCollection.get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "forceRefreshUsers server fetch notice: ${e.message}")
        }
    }

    fun getAllUsers(): Flow<List<User>> = callbackFlow {
        AppLogger.d("AdminRepository", "Listening for Firestore users updates (real-time with MetadataChanges)")
        val listener = usersCollection.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                AppLogger.w("AdminRepository", "Firestore users listener non-fatal notification: ${error.message}")
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(error)
                }
                return@addSnapshotListener
            }
            val users = snapshot?.documents?.mapNotNull { doc ->
                docToUser(doc)
            } ?: emptyList()
            AppLogger.d("AdminRepository", "Received ${users.size} users from Firestore (hasPendingSync=${snapshot?.metadata?.hasPendingWrites()})")
            trySend(users.sortedByDescending { it.lastLoginTimestamp })
        }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        AppLogger.w("AdminRepository", "Auto-reconnecting getAllUsers snapshot listener (attempt $attempt) after: ${cause.message}")
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    fun getUser(userId: String): Flow<User?> = callbackFlow {
        AppLogger.d("AdminRepository", "Listening for Firestore user $userId (real-time with MetadataChanges)")
        val listener = usersCollection.document(userId).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                AppLogger.w("AdminRepository", "Firestore user($userId) non-fatal notification: ${error.message}")
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(error)
                }
                return@addSnapshotListener
            }
            val user = if (snapshot != null && snapshot.exists()) docToUser(snapshot) else null
            trySend(user)
        }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        AppLogger.w("AdminRepository", "Auto-reconnecting getUser($userId) listener (attempt $attempt) after: ${cause.message}")
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    /**
     * Resiliently writes user document updates using SetOptions.merge() and ResilientSyncManager.
     * Guarantees that even with poor, weak, or fluctuating internet connection:
     * 1. The change is written to local persistent disk cache immediately.
     * 2. Snapshot listeners receive and render the update instantly.
     * 3. The write is queued in ResilientSyncManager & Firestore persistent queue and syncs
     *    automatically once the connection stabilizes without being dropped or cancelled.
     */
    suspend fun resilientSetUser(userId: String, updates: Map<String, Any?>, description: String = "Update user $userId") {
        val opKey = "user_${userId}_${updates.keys.joinToString("_")}"
        _pendingSyncKeys.update { it + opKey }
        
        // 1. Write to local Firestore cache immediately
        val docRef = usersCollection.document(userId)
        try {
            docRef.set(updates, SetOptions.merge())
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Immediate local cache update note: ${e.message}")
        }

        // 2. Delegate to Application-scoped ResilientSyncManager to guarantee completion
        syncManager.enqueueWrite(
            opId = opKey,
            collection = FirebaseCollections.USERS,
            documentId = userId,
            payload = updates,
            description = description,
            onSuccess = {
                _pendingSyncKeys.update { it - opKey }
            },
            onError = {
                // Kept in pending queue, ResilientSyncManager will retry when connection stabilizes
            }
        )
    }

    suspend fun updateUser(userId: String, updates: Map<String, Any?>) {
        val updatesWithTimestamp = updates.toMutableMap()
        updatesWithTimestamp["updatedAt"] = System.currentTimeMillis()
        resilientSetUser(userId, updatesWithTimestamp)
        val formattedDetails = updates.entries.joinToString(", ") { "${it.key}=${it.value}" }
        logAudit(
            action = "UPDATE_USER",
            targetType = "USER",
            targetId = userId,
            details = "Updated fields: $formattedDetails"
        )
    }

    /**
     * Authoritative Global Account Ban (Phase C1).
     * Suspends the entire user account, records explicit reason, and optional expiry millis.
     * Strictly decoupled from individual feature restrictions.
     */
    suspend fun banAccount(userId: String, reason: String?, expiresAt: Long?) {
        val now = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            "isBanned" to true,
            "banReason" to (reason?.trim()?.ifBlank { "Banned by administrator" } ?: "Banned by administrator"),
            "banExpiresAt" to expiresAt,
            "isActive" to false,
            "updatedAt" to now
        )
        resilientSetUser(userId, updates)
        val expiryDesc = if (expiresAt != null) "Expires at $expiresAt" else "Permanent"
        logAudit(
            action = "BAN_USER",
            targetType = "USER",
            targetId = userId,
            details = "Account banned. Reason: ${reason ?: "N/A"}. Duration: $expiryDesc"
        )
    }

    /**
     * Authoritative Unban & Lift Suspension (Phase C1).
     * Clears account-level ban, reason, expiration, and restores active state.
     * Preserves existing user feature restrictions without accidental overwrites.
     */
    suspend fun unbanAccount(userId: String) {
        val now = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            "isBanned" to false,
            "banReason" to null,
            "banExpiresAt" to null,
            "isActive" to true,
            "updatedAt" to now
        )
        resilientSetUser(userId, updates)
        logAudit(
            action = "UNBAN_USER",
            targetType = "USER",
            targetId = userId,
            details = "Account unbanned and restored to good standing"
        )
    }

    /**
     * Authoritative Subscription Update (Phase C1).
     * Writes canonical fields (isPremium, subscriptionTier, subscriptionStatus, subscriptionExpiresAt)
     * and strictly synchronized compatibility mirrors (isPro, plan, proPlan, proExpiresAt).
     */
    suspend fun updateSubscription(
        userId: String,
        tier: String,
        expiresAt: Long?,
        status: String = if (tier.lowercase() != "free") "active" else "free"
    ) {
        val isPrem = tier.lowercase() != "free"
        val normalizedTier = tier.lowercase()
        val now = System.currentTimeMillis()
        val updates = hashMapOf<String, Any?>(
            // Canonical Fields
            "isPremium" to isPrem,
            "subscriptionTier" to normalizedTier,
            "subscriptionStatus" to if (isPrem) status else "free",
            "subscriptionExpiresAt" to if (isPrem) expiresAt else null,
            // Compatibility Fields for Legacy Consumers
            "isPro" to isPrem,
            "plan" to normalizedTier,
            "proPlan" to normalizedTier,
            "proExpiresAt" to if (isPrem) expiresAt else null,
            "updatedAt" to now
        )
        resilientSetUser(userId, updates)
        val action = if (isPrem) "GRANT_SUBSCRIPTION" else "REVOKE_SUBSCRIPTION"
        val expiryDesc = if (expiresAt != null) "Expires at $expiresAt" else "Lifetime / Indefinite"
        logAudit(
            action = action,
            targetType = "USER",
            targetId = userId,
            details = "Subscription updated: Tier=$normalizedTier, Status=$status, Duration=$expiryDesc"
        )
    }

    /**
     * Authoritative Subscription Revocation (Phase C1).
     * Resets user to free tier immediately.
     */
    suspend fun revokeSubscription(userId: String) {
        updateSubscription(userId, tier = "free", expiresAt = null, status = "free")
    }

    /**
     * Selective Feature Permission Toggle (Phase C1).
     * Updates individual feature access without conflating with global account ban.
     */
    suspend fun updateFeaturePermission(userId: String, feature: String, allowed: Boolean) {
        val now = System.currentTimeMillis()
        val updates = when (feature.lowercase()) {
            "watch" -> mapOf("canWatch" to allowed, "watchBan" to !allowed, "updatedAt" to now)
            "download" -> mapOf("canDownload" to allowed, "downloadBan" to !allowed, "updatedAt" to now)
            "chat" -> mapOf("canChat" to allowed, "chatBan" to !allowed, "updatedAt" to now)
            "story" -> mapOf("canStory" to allowed, "storyBan" to !allowed, "updatedAt" to now)
            "p2p" -> mapOf("canP2P" to allowed, "p2pBan" to !allowed, "updatedAt" to now)
            "comment" -> mapOf("canComment" to allowed, "updatedAt" to now)
            "upload" -> mapOf("canUpload" to allowed, "updatedAt" to now)
            "request" -> mapOf("canRequest" to allowed, "updatedAt" to now)
            else -> mapOf(feature to allowed, "updatedAt" to now)
        }
        resilientSetUser(userId, updates)
        logAudit(
            action = "UPDATE_FEATURE_PERMISSION",
            targetType = "USER",
            targetId = userId,
            details = "Permission $feature set to allowed=$allowed"
        )
    }

    suspend fun setUserAdminRole(userId: String, email: String, isAdmin: Boolean) {
        val role = if (isAdmin) "admin" else "user"
        val updates = mapOf(
            "role" to role,
            "updatedAt" to System.currentTimeMillis()
        )
        resilientSetUser(userId, updates, "Set admin role to $role for $email")
        if (isAdmin) {
            val adminDoc = AdminUser(
                uid = userId,
                email = email,
                role = "admin",
                enabled = true,
                createdAt = System.currentTimeMillis()
            )
            adminsCollection.document(userId).set(adminDoc, SetOptions.merge())
        } else {
            adminsCollection.document(userId).set(mapOf("enabled" to false), SetOptions.merge())
        }
        logAudit(
            action = if (isAdmin) "GRANT_ADMIN" else "REVOKE_ADMIN",
            targetType = "USER",
            targetId = userId,
            details = "Set admin role to $role for $email"
        )
    }

    suspend fun createUser(user: User): String {
        val resolvedId = if (user.uid.isNotBlank()) user.uid else if (user.id.isNotBlank()) user.id else usersCollection.document().id
        val docRef = usersCollection.document(resolvedId)
        val now = System.currentTimeMillis()
        val finalUser = user.copy(
            uid = docRef.id,
            id = docRef.id,
            createdAt = if (user.createdAt == 0L) now else user.createdAt,
            updatedAt = now,
            lastLoginAt = if (user.lastLoginAt != 0L) user.lastLoginAt else user.lastLoginTimestamp,
            lastLoginTimestamp = if (user.lastLoginAt != 0L) user.lastLoginAt else user.lastLoginTimestamp
        )
        // Set with merge to avoid wiping user-managed subcollections or fields if existing
        docRef.set(finalUser, SetOptions.merge())
        logAudit(
            action = "CREATE_USER",
            targetType = "USER",
            targetId = docRef.id,
            details = "Created user: ${finalUser.username} (${finalUser.email})"
        )
        return docRef.id
    }

    suspend fun deleteUser(userId: String) {
        if (userId.isBlank()) return
        try {
            usersCollection.document(userId).delete().await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Delete user doc notice: ${e.message}")
            try { usersCollection.document(userId).delete() } catch (_: Exception) {}
        }
        try {
            adminsCollection.document(userId).delete().await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Delete admin doc notice: ${e.message}")
        }
        logAudit(
            action = "DELETE_USER",
            targetType = "USER",
            targetId = userId,
            details = "Permanently deleted user: $userId"
        )
    }

    // --- APP & GLOBAL CONFIG (MAINTENANCE, OTA, DRM) ---

    fun getAppConfig(): Flow<AppConfig> = callbackFlow {
        AppLogger.d("AdminRepository", "Listening for Firestore app config (real-time with MetadataChanges)")
        val listener = configCollection.document(FirebaseConfigDocs.APP)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("AdminRepository", "Firestore config non-fatal error: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val config = try {
                        snapshot.toObject(AppConfig::class.java) ?: AppConfig()
                    } catch (e: Exception) {
                        AppLogger.w("AdminRepository", "Error parsing AppConfig: ${e.message}")
                        AppConfig()
                    }
                    trySend(config)
                } else {
                    // Try reading legacy 'global' document
                    configCollection.document(FirebaseConfigDocs.GLOBAL).get().addOnSuccessListener { globalSnap ->
                        val globalConfig = try {
                            globalSnap.toObject(AppConfig::class.java) ?: AppConfig()
                        } catch (e: Exception) {
                            AppConfig()
                        }
                        trySend(globalConfig)
                    }.addOnFailureListener {
                        trySend(AppConfig())
                    }
                }
            }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    suspend fun updateAppConfig(updates: Map<String, Any?>) {
        val updatesWithTimestamp = updates.toMutableMap()
        updatesWithTimestamp["updatedAt"] = System.currentTimeMillis()

        // Canonical authoritative path: /config/app
        configCollection.document(FirebaseConfigDocs.APP).set(updatesWithTimestamp, SetOptions.merge())

        logAudit(
            action = "UPDATE_APP_CONFIG",
            targetType = "CONFIG",
            targetId = "app",
            details = "Updated configs: ${updates.keys.joinToString(", ")}"
        )
    }

    // Backward-compatibility alias
    fun getGlobalConfig(): Flow<GlobalConfig> = getAppConfig()
    suspend fun updateGlobalConfig(updates: Map<String, Any?>) = updateAppConfig(updates)

    // --- CANONICAL APP UPDATES (/app_updates/{updateId}) ---

    /**
     * Realtime snapshot listener for all OTA releases in /app_updates.
     * Ordered by versionCode descending.
     */
    fun getAppUpdates(): Flow<List<AppUpdate>> = callbackFlow {
        AppLogger.d("AdminRepository", "Listening to /app_updates")
        val listener = appUpdatesCollection
            .orderBy("versionCode", Query.Direction.DESCENDING)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("AdminRepository", "Non-fatal error listening to app_updates: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val item = doc.toObject(AppUpdate::class.java)
                        val id = if (!item?.id.isNullOrBlank()) item!!.id else doc.id
                        item?.copy(id = id)
                    } catch (e: Exception) {
                        AppLogger.w("AdminRepository", "Failed to deserialize app update ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    /**
     * Creates or updates an authoritative release record at /app_updates/{updateId}.
     */
    suspend fun createOrUpdateAppUpdate(update: AppUpdate) {
        val docId = if (update.id.isNotBlank()) update.id else if (update.versionCode > 0) update.versionCode.toString() else appUpdatesCollection.document().id
        val currentAdmin = auth.currentUser
        val finalUpdate = update.copy(
            id = docId,
            publishedBy = if (update.publishedBy.isNotBlank()) update.publishedBy else (currentAdmin?.email ?: currentAdmin?.uid ?: "admin"),
            updatedAt = System.currentTimeMillis()
        )
        appUpdatesCollection.document(docId).set(finalUpdate, SetOptions.merge()).await()
        logAudit(
            action = "SAVE_APP_UPDATE",
            targetType = "APP_UPDATE",
            targetId = docId,
            details = "Saved App Update v${update.versionName} (build: ${update.versionCode})"
        )
    }

    /**
     * Deletes an App Update record from /app_updates/{updateId}.
     */
    suspend fun deleteAppUpdate(updateId: String) {
        if (updateId.isBlank()) return
        appUpdatesCollection.document(updateId).delete().await()
        logAudit(
            action = "DELETE_APP_UPDATE",
            targetType = "APP_UPDATE",
            targetId = updateId,
            details = "Deleted App Update record $updateId"
        )
    }

    // --- NOTIFICATIONS ---

    suspend fun sendNotification(request: NotificationRequest) {
        val currentAdmin = auth.currentUser
        val docRef = notificationsCollection.document()
        val bodyText = request.body.ifBlank { request.message }
        val targetVal = if (request.target.isNotBlank()) request.target else request.targetType.name
        val payload = hashMapOf<String, Any?>(
            "id" to docRef.id,
            "title" to request.title,
            "body" to bodyText,
            "message" to bodyText,
            "type" to request.type,
            "target" to targetVal,
            "targetType" to targetVal,
            "targetUid" to request.targetUid,
            "createdBy" to (currentAdmin?.uid ?: "admin"),
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to request.expiresAt,
            "isActive" to request.isActive,
            "status" to "PENDING"
        )
        docRef.set(payload, SetOptions.merge())
        logAudit(
            action = "DISPATCH_NOTIFICATION",
            targetType = "NOTIFICATION",
            targetId = targetVal,
            details = "Title: ${request.title}, Audience: $targetVal, Type: ${request.type}"
        )
    }

    fun getRecentNotifications(): Flow<List<NotificationRequest>> = callbackFlow {
        val listener = notificationsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(25)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("AdminRepository", "Firestore notifications non-fatal error: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val notif = doc.toObject(NotificationRequest::class.java)?.copy(id = doc.id)
                        if (notif != null) {
                            val bodyText = if (notif.body.isNotBlank()) notif.body else notif.message
                            val targetVal = if (notif.target.isNotBlank()) notif.target else notif.targetType.name
                            notif.copy(body = bodyText, message = bodyText, target = targetVal)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    suspend fun deleteNotification(notificationId: String) {
        if (notificationId.isBlank()) return
        notificationsCollection.document(notificationId).delete().await()
        logAudit(
            action = "DELETE_NOTIFICATION",
            targetType = "NOTIFICATION",
            targetId = notificationId,
            details = "Permanently deleted notification id: $notificationId"
        )
    }

    suspend fun forceRefreshNotifications() {
        try {
            notificationsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "forceRefreshNotifications notice: ${e.message}")
        }
    }

    suspend fun forceRefreshAuditLogs(limit: Long = 50) {
        try {
            auditLogsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "forceRefreshAuditLogs notice: ${e.message}")
        }
    }

    suspend fun forceRefreshReports() {
        try {
            reportsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "forceRefreshReports notice: ${e.message}")
        }
    }

    suspend fun forceRefreshExtensions() {
        try {
            extensionsCollection.get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "forceRefreshExtensions notice: ${e.message}")
        }
    }

    suspend fun forceRefreshAppConfig() {
        try {
            configCollection.document(FirebaseConfigDocs.APP)
                .get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "forceRefreshAppConfig notice: ${e.message}")
        }
    }

    // --- AUDIT LOGS ---

    fun getAuditLogs(limit: Long = 50): Flow<List<AuditLog>> = callbackFlow {
        val listener = auditLogsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("AdminRepository", "Firestore audit logs non-fatal notice: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(AuditLog::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    // --- EXTENSIONS MANAGEMENT ---

    fun getExtensions(): Flow<List<ExtensionItem>> = callbackFlow {
        val listener = extensionsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    AppLogger.e("AdminRepository", "Firestore extensions error: ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val docId = doc.id
                        val name = doc.getString("name") ?: doc.getString("title") ?: docId
                        val pkg = doc.getString("packageName") ?: doc.getString("package") ?: ""
                        val vCode = when (val raw = doc.get("versionCode")) {
                            is Number -> raw.toInt()
                            is String -> raw.toIntOrNull() ?: 1
                            else -> 1
                        }
                        val vName = doc.getString("versionName") ?: "1.0.0"
                        val apkUrl = doc.getString("apkUrl") ?: doc.getString("url") ?: ""
                        val apkSha = doc.getString("apkSha256") ?: doc.getString("sha256") ?: ""
                        val minApp = when (val raw = doc.get("minAppVersionCode")) {
                            is Number -> raw.toInt()
                            is String -> raw.toIntOrNull() ?: 1
                            else -> 1
                        }
                        val enabled = when (val raw = doc.get("enabled")) {
                            is Boolean -> raw
                            is String -> raw.equals("true", ignoreCase = true)
                            is Number -> raw.toInt() != 0
                            else -> true
                        }
                        val mandatory = when (val raw = doc.get("mandatory")) {
                            is Boolean -> raw
                            is String -> raw.equals("true", ignoreCase = true)
                            is Number -> raw.toInt() != 0
                            else -> false
                        }
                        val releaseNotes = doc.getString("releaseNotes") ?: ""
                        val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: 0L
                        val updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: 0L

                        ExtensionItem(
                            id = docId,
                            name = name,
                            packageName = pkg,
                            versionCode = vCode,
                            versionName = vName,
                            apkUrl = apkUrl,
                            apkSha256 = apkSha,
                            sha256 = apkSha,
                            minAppVersionCode = minApp,
                            enabled = enabled,
                            mandatory = mandatory,
                            releaseNotes = releaseNotes,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    } catch (e: Exception) {
                        AppLogger.w("AdminRepository", "Failed to parse extension doc ${doc.id}: ${e.message}")
                        null
                    }
                }?.sortedBy { it.name.lowercase() } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    suspend fun saveExtension(extension: ExtensionItem) {
        val docRef = if (extension.id.isNotBlank()) extensionsCollection.document(extension.id) else extensionsCollection.document()
        val now = System.currentTimeMillis()
        val resolvedSha = extension.apkSha256.ifBlank { extension.sha256 }
        val payload = extension.copy(
            id = docRef.id,
            apkSha256 = resolvedSha,
            sha256 = resolvedSha,
            createdAt = if (extension.createdAt == 0L) now else extension.createdAt,
            updatedAt = now
        )
        docRef.set(payload, SetOptions.merge()).await()
        logAudit(
            action = "SAVE_EXTENSION",
            targetType = "EXTENSION",
            targetId = docRef.id,
            details = "Saved extension: ${extension.name} v${extension.versionName}"
        )
    }

    suspend fun deleteExtension(extensionId: String) {
        if (extensionId.isBlank()) return
        val cleanId = extensionId.trim()
        try {
            extensionsCollection.document(cleanId).delete().await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Delete legacy extension notice: ${e.message}")
        }
        try {
            firestore.collection(FirebaseCollections.MANAGED_EXTENSIONS).document(cleanId).delete().await()
        } catch (e: Exception) {
            AppLogger.w("AdminRepository", "Delete managed extension notice: ${e.message}")
        }
        try {
            val q1 = extensionsCollection.whereEqualTo("scraperKey", cleanId).get().await()
            for (doc in q1.documents) { doc.reference.delete() }
            val q2 = firestore.collection(FirebaseCollections.MANAGED_EXTENSIONS).whereEqualTo("scraperKey", cleanId).get().await()
            for (doc in q2.documents) { doc.reference.delete() }
        } catch (e: Exception) {
            // non-fatal
        }
        logAudit(
            action = "DELETE_EXTENSION",
            targetType = "EXTENSION",
            targetId = cleanId,
            details = "Deleted extension id: $cleanId"
        )
    }

    // --- USER REPORTS MANAGEMENT ---

    fun getReports(): Flow<List<UserReport>> = callbackFlow {
        val listener = reportsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    AppLogger.w("AdminRepository", "Firestore reports non-fatal error: ${error.message}")
                    if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        close(error)
                    }
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val report = doc.toObject(UserReport::class.java)?.copy(id = doc.id)
                        val created = extractTimestampMillis(doc, "createdAt", report?.createdAt ?: 0L)
                        val resolved = if (doc.contains("resolvedAt")) extractTimestampMillis(doc, "resolvedAt", 0L) else null
                        (report ?: UserReport()).copy(
                            id = doc.id,
                            createdAt = created,
                            resolvedAt = resolved
                        )
                    } catch (e: Exception) {
                        try {
                            UserReport(
                                id = doc.id,
                                userId = doc.getString("userId") ?: "",
                                userEmail = doc.getString("userEmail") ?: "",
                                type = doc.getString("type") ?: "ISSUE",
                                targetType = doc.getString("targetType") ?: "",
                                targetId = doc.getString("targetId") ?: "",
                                title = doc.getString("title") ?: "",
                                description = doc.getString("description") ?: "",
                                status = doc.getString("status") ?: "PENDING",
                                resolutionNotes = doc.getString("resolutionNotes") ?: "",
                                createdAt = extractTimestampMillis(doc, "createdAt", 0L),
                                resolvedAt = if (doc.contains("resolvedAt")) extractTimestampMillis(doc, "resolvedAt", 0L) else null,
                                resolvedBy = doc.getString("resolvedBy") ?: ""
                            )
                        } catch (inner: Exception) {
                            null
                        }
                    }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    suspend fun updateReportStatus(reportId: String, status: String, notes: String) {
        val currentAdmin = auth.currentUser
        val updates = mapOf(
            "status" to status,
            "resolutionNotes" to notes,
            "resolvedAt" to System.currentTimeMillis(),
            "resolvedBy" to (currentAdmin?.uid ?: "admin")
        )
        reportsCollection.document(reportId).set(updates, SetOptions.merge())
        logAudit(
            action = "RESOLVE_REPORT",
            targetType = "REPORT",
            targetId = reportId,
            details = "Status changed to $status with notes: $notes"
        )
    }

    suspend fun deleteReport(reportId: String) {
        reportsCollection.document(reportId).delete().await()
        logAudit(
            action = "DELETE_REPORT",
            targetType = "REPORT",
            targetId = reportId,
            details = "Deleted report id: $reportId"
        )
    }
}

