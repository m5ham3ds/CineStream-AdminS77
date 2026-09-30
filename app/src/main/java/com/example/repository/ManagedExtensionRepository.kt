package com.example.repository

import com.example.contract.FirebaseCollections
import com.example.contract.FirebaseConfigDocs
import com.example.diagnostics.AppLogger
import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionStatus
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/**
 * Standard default CineStream bundled scrapers catalog.
 */
object DefaultCineStreamScrapers {
    fun getDefaults(): List<ManagedExtension> = listOf(
        ManagedExtension(
            extensionId = "qfilm",
            scraperKey = "qfilm",
            name = "QFilm (كيو فيلم)",
            description = "سيرفر البث الرئيسي العربي للأفلام والمسلسلات بجودات متعددة",
            baseUrl = "https://qfilm.vip",
            searchUrl = "https://qfilm.vip/search?q=%s",
            priority = 110,
            status = ManagedExtensionStatus.ACTIVE.name,
            enabled = true,
            minAppVersionCode = 1,
            contentTypes = listOf("MOVIE", "SERIES"),
            capabilities = listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
        ),
        ManagedExtension(
            extensionId = "witanime",
            scraperKey = "witanime",
            name = "WitAnime (ويت أنمي)",
            description = "المصدر الأول للأنمي المترجم بجودة عالية وسيرفرات سريعة",
            baseUrl = "https://witanime.pics",
            searchUrl = "https://witanime.pics/?search_rule=search&s=%s",
            priority = 105,
            status = ManagedExtensionStatus.ACTIVE.name,
            enabled = true,
            minAppVersionCode = 1,
            contentTypes = listOf("ANIME"),
            capabilities = listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
        ),
        ManagedExtension(
            extensionId = "egydead",
            scraperKey = "egydead",
            name = "EgyDead (إيجي ديد)",
            description = "مكتبة ضخمة للأفلام والمسلسلات العربية والأجنبية والأنمي",
            baseUrl = "https://egydead.vip",
            searchUrl = "https://egydead.vip/?s=%s",
            priority = 100,
            status = ManagedExtensionStatus.ACTIVE.name,
            enabled = true,
            minAppVersionCode = 1,
            contentTypes = listOf("MOVIE", "SERIES", "ANIME"),
            capabilities = listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
        ),
        ManagedExtension(
            extensionId = "akwam",
            scraperKey = "akwam",
            name = "Akwam (أكوام)",
            description = "تحميل وبث مباشر للأفلام والمسلسلات والبرامج التلفزيونية",
            baseUrl = "https://akwam.to",
            searchUrl = "https://akwam.to/search?q=%s",
            priority = 95,
            status = ManagedExtensionStatus.ACTIVE.name,
            enabled = true,
            minAppVersionCode = 1,
            contentTypes = listOf("MOVIE", "SERIES"),
            capabilities = listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
        ),
        ManagedExtension(
            extensionId = "arabseed",
            scraperKey = "arabseed",
            name = "ArabSeed (عرب سيد)",
            description = "موقع عربي شامل للأفلام والمسلسلات الحصرية",
            baseUrl = "https://arabseed.show",
            searchUrl = "https://arabseed.show/find/?find=%s",
            priority = 90,
            status = ManagedExtensionStatus.ACTIVE.name,
            enabled = true,
            minAppVersionCode = 1,
            contentTypes = listOf("MOVIE", "SERIES"),
            capabilities = listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
        ),
        ManagedExtension(
            extensionId = "mycima",
            scraperKey = "mycima",
            name = "MyCima (ماي سيما / وي سيما)",
            description = "سيرفرات مشاهدة مباشرة وتحميل لجميع الأعمال السينمائية",
            baseUrl = "https://wecima.show",
            searchUrl = "https://wecima.show/search/%s",
            priority = 85,
            status = ManagedExtensionStatus.ACTIVE.name,
            enabled = true,
            minAppVersionCode = 1,
            contentTypes = listOf("MOVIE", "SERIES"),
            capabilities = listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
        )
    )
}

/**
 * Authoritative Repository for Modern Managed Extensions (Phase C2).
 * Dual Source & Dual Write:
 * - /managed_extensions/{extensionId}
 * - /extensions/{extensionId}
 * Invariant: Pure configuration datastore. Zero executable code.
 */
class ManagedExtensionRepository(
    private val adminRepository: AdminRepository = AdminRepository()
) {
    private fun ensureFirebase() {
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(com.example.MyApplication.instance)
            if (apps.isEmpty()) {
                com.example.diagnostics.FirebaseInitializer.init(com.example.MyApplication.instance)
            }
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "ensureFirebase check notice: ${e.message}")
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        ensureFirebase()
        FirebaseFirestore.getInstance()
    }

    private val managedCollection get() = firestore.collection(FirebaseCollections.MANAGED_EXTENSIONS)
    private val legacyCollection get() = firestore.collection(FirebaseCollections.LEGACY_EXTENSIONS)

    private fun parseDocument(doc: DocumentSnapshot): ManagedExtension? {
        try {
            val docId = doc.id
            if (docId.isBlank()) return null

            val id = doc.getString("extensionId")
                ?: doc.getString("id")
                ?: doc.getString("key")
                ?: docId

            val scraperKey = doc.getString("scraperKey")
                ?: doc.getString("key")
                ?: doc.getString("scraper")
                ?: docId.trim().lowercase()

            val name = doc.getString("name")
                ?: doc.getString("title")
                ?: doc.getString("label")
                ?: docId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            val baseUrl = doc.getString("baseUrl")
                ?: doc.getString("url")
                ?: doc.getString("siteUrl")
                ?: doc.getString("domain")
                ?: ""

            val searchUrl = doc.getString("searchUrl") ?: doc.getString("search_url") ?: ""

            val rawPriority = doc.get("priority")
            val priority = when (rawPriority) {
                is Number -> rawPriority.toInt()
                is String -> rawPriority.toIntOrNull() ?: 100
                else -> 100
            }

            val rawMinAppVersion = doc.get("minAppVersionCode") ?: doc.get("minVersionCode")
            val minAppVersionCode = when (rawMinAppVersion) {
                is Number -> rawMinAppVersion.toInt()
                is String -> rawMinAppVersion.toIntOrNull() ?: 1
                else -> 1
            }

            val rawEnabled = doc.get("enabled")
            val rawStatus = doc.getString("status")

            val enabled = when (rawEnabled) {
                is Boolean -> rawEnabled
                is String -> rawEnabled.equals("true", ignoreCase = true) || rawEnabled.equals("active", ignoreCase = true)
                is Number -> rawEnabled.toInt() != 0
                else -> {
                    if (!rawStatus.isNullOrBlank()) {
                        rawStatus.equals("ACTIVE", ignoreCase = true) || rawStatus.equals("ENABLED", ignoreCase = true)
                    } else true
                }
            }

            val safeStatus = if (!rawStatus.isNullOrBlank()) {
                ManagedExtensionStatus.fromString(rawStatus).name
            } else {
                if (enabled) ManagedExtensionStatus.ACTIVE.name else ManagedExtensionStatus.DISABLED.name
            }

            val rawCaps = doc.get("capabilities")
            val caps = when (rawCaps) {
                is List<*> -> rawCaps.mapNotNull { it?.toString()?.trim()?.uppercase() }
                is String -> rawCaps.split(",").map { it.trim().uppercase() }
                else -> listOf("SEARCH", "DETAILS", "EPISODES", "SERVER_DISCOVERY", "VIDEO_EXTRACTION")
            }

            val rawTypes = doc.get("contentTypes")
            val types = when (rawTypes) {
                is List<*> -> rawTypes.mapNotNull { it?.toString()?.trim()?.uppercase() }
                is String -> rawTypes.split(",").map { it.trim().uppercase() }
                else -> listOf("MOVIE", "SERIES")
            }

            val description = doc.getString("description") ?: doc.getString("desc") ?: ""
            val runtimeApiVersion = (doc.get("runtimeApiVersion") as? Number)?.toInt() ?: 1
            val definitionVersion = (doc.get("definitionVersion") as? Number)?.toInt() ?: 1
            val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: 0L
            val updatedAt = (doc.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis()

            return ManagedExtension(
                extensionId = id,
                scraperKey = scraperKey,
                name = name,
                description = description,
                baseUrl = baseUrl,
                searchUrl = searchUrl,
                runtimeApiVersion = runtimeApiVersion,
                definitionVersion = definitionVersion,
                minAppVersionCode = minAppVersionCode,
                status = safeStatus,
                priority = priority,
                capabilities = caps,
                contentTypes = types,
                enabled = enabled && safeStatus == ManagedExtensionStatus.ACTIVE.name,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Failed to deserialize doc ${doc.id}: ${e.message}")
            return null
        }
    }

    private fun listenCollection(col: CollectionReference, name: String): Flow<Map<String, ManagedExtension>> = callbackFlow {
        val listener = col.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                AppLogger.w("ManagedExtensionRepo", "Listener notice for $name: ${error.message}")
                trySend(emptyMap())
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(error)
                }
                return@addSnapshotListener
            }
            val map = mutableMapOf<String, ManagedExtension>()
            snapshot?.documents?.forEach { doc ->
                parseDocument(doc)?.let { ext ->
                    map[ext.extensionId] = ext
                }
            }
            trySend(map)
        }
        awaitClose { listener.remove() }
    }.onStart {
        emit(emptyMap())
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    fun getAllManagedExtensions(): Flow<List<ManagedExtension>> {
        return combine(
            listenCollection(managedCollection, "managed_extensions"),
            listenCollection(legacyCollection, "extensions")
        ) { managedMap, legacyMap ->
            // Merge maps: start with legacy, overlay managed_extensions
            val mergedMap = HashMap<String, ManagedExtension>(legacyMap)
            managedMap.forEach { (id, ext) ->
                mergedMap[id] = ext
            }
            // Return authentic persistent data without auto-resurrecting deleted extensions
            mergedMap.values.sortedByDescending { it.priority }
        }
    }

    suspend fun forceRefresh() {
        try {
            managedCollection.get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "forceRefresh managed notice: ${e.message}")
        }
        try {
            legacyCollection.get(com.google.firebase.firestore.Source.SERVER).await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "forceRefresh legacy notice: ${e.message}")
        }
    }

    suspend fun getManagedExtension(id: String): ManagedExtension? {
        val doc1 = try {
            managedCollection.document(id).get().await()
        } catch (e: Exception) {
            null
        }
        if (doc1 != null && doc1.exists()) {
            return parseDocument(doc1)
        }
        val doc2 = try {
            legacyCollection.document(id).get().await()
        } catch (e: Exception) {
            null
        }
        if (doc2 != null && doc2.exists()) {
            return parseDocument(doc2)
        }
        return null
    }

    suspend fun saveManagedExtension(extension: ManagedExtension, isNew: Boolean) {
        val now = System.currentTimeMillis()
        val id = extension.extensionId.trim().ifBlank {
            extension.scraperKey.trim().ifBlank { "ext_${System.currentTimeMillis()}" }
        }
        val isEnabled = extension.enabled && extension.status.trim().uppercase() == "ACTIVE"

        val payload = hashMapOf<String, Any?>(
            "extensionId" to id,
            "id" to id,
            "scraperKey" to extension.scraperKey.trim().lowercase().ifBlank { id.lowercase() },
            "name" to extension.name.trim(),
            "description" to extension.description.trim(),
            "baseUrl" to extension.baseUrl.trim(),
            "searchUrl" to extension.searchUrl.trim(),
            "runtimeApiVersion" to extension.runtimeApiVersion,
            "definitionVersion" to extension.definitionVersion,
            "minAppVersionCode" to extension.minAppVersionCode,
            "status" to extension.status.trim().uppercase(),
            "priority" to extension.priority,
            "capabilities" to extension.capabilities,
            "contentTypes" to extension.contentTypes,
            "enabled" to isEnabled,
            "updatedAt" to now
        )
        if (isNew || extension.createdAt == 0L) {
            payload["createdAt"] = now
        }

        // Dual-write to BOTH collections
        managedCollection.document(id).set(payload, SetOptions.merge()).await()
        try {
            legacyCollection.document(id).set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Dual write to legacy extensions notice: ${e.message}")
        }

        val action = if (isNew) "CREATE_MANAGED_EXTENSION" else "UPDATE_MANAGED_EXTENSION"
        adminRepository.logAudit(
            action = action,
            targetType = "EXTENSION",
            targetId = id,
            details = "$action: ${extension.name} (baseUrl=${extension.baseUrl}, priority=${extension.priority}, enabled=$isEnabled)"
        )
    }

    suspend fun updateStatus(id: String, status: ManagedExtensionStatus) {
        val now = System.currentTimeMillis()
        val enabled = status == ManagedExtensionStatus.ACTIVE
        val updates = mapOf(
            "status" to status.name,
            "enabled" to enabled,
            "updatedAt" to now
        )
        managedCollection.document(id).set(updates, SetOptions.merge()).await()
        try {
            legacyCollection.document(id).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Dual write updateStatus notice: ${e.message}")
        }
        adminRepository.logAudit(
            action = "CHANGE_MANAGED_EXTENSION_STATUS",
            targetType = "EXTENSION",
            targetId = id,
            details = "Status changed to ${status.name} (enabled=$enabled)"
        )
    }

    suspend fun updatePriority(id: String, priority: Int) {
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "priority" to priority,
            "updatedAt" to now
        )
        managedCollection.document(id).set(updates, SetOptions.merge()).await()
        try {
            legacyCollection.document(id).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Dual write updatePriority notice: ${e.message}")
        }
        adminRepository.logAudit(
            action = "CHANGE_MANAGED_EXTENSION_PRIORITY",
            targetType = "EXTENSION",
            targetId = id,
            details = "Priority changed to $priority"
        )
    }

    suspend fun updateBaseUrl(id: String, newBaseUrl: String) {
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "baseUrl" to newBaseUrl.trim(),
            "updatedAt" to now
        )
        managedCollection.document(id).set(updates, SetOptions.merge()).await()
        try {
            legacyCollection.document(id).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Dual write updateBaseUrl notice: ${e.message}")
        }
        adminRepository.logAudit(
            action = "CHANGE_MANAGED_EXTENSION_BASE_URL",
            targetType = "EXTENSION",
            targetId = id,
            details = "Base URL rotated to: $newBaseUrl"
        )
    }

    suspend fun deleteManagedExtension(id: String) {
        val cleanId = id.trim()
        if (cleanId.isBlank()) return

        // 1. Delete direct document in /managed_extensions
        try {
            managedCollection.document(cleanId).delete().await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Direct managed delete notice: ${e.message}")
        }

        // 2. Delete any matching documents by extensionId or scraperKey in /managed_extensions
        try {
            val q1 = managedCollection.whereEqualTo("extensionId", cleanId).get().await()
            for (doc in q1.documents) { doc.reference.delete() }
            val q2 = managedCollection.whereEqualTo("scraperKey", cleanId).get().await()
            for (doc in q2.documents) { doc.reference.delete() }
        } catch (e: Exception) {
            // non-fatal
        }

        // 3. Delete direct document in /extensions (legacy)
        try {
            legacyCollection.document(cleanId).delete().await()
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Direct legacy delete notice: ${e.message}")
        }

        // 4. Delete any matching documents by extensionId or scraperKey in /extensions
        try {
            val q3 = legacyCollection.whereEqualTo("extensionId", cleanId).get().await()
            for (doc in q3.documents) { doc.reference.delete() }
            val q4 = legacyCollection.whereEqualTo("scraperKey", cleanId).get().await()
            for (doc in q4.documents) { doc.reference.delete() }
        } catch (e: Exception) {
            // non-fatal
        }

        // 5. Delete from extension_updates
        try {
            firestore.collection(FirebaseCollections.LEGACY_EXTENSION_UPDATES).document(cleanId).delete().await()
        } catch (e: Exception) {
            // non-fatal
        }

        // 6. Clean up /config/search_order
        try {
            val searchOrderDoc = firestore.collection(FirebaseCollections.CONFIG).document(FirebaseConfigDocs.SEARCH_ORDER)
            val snap = searchOrderDoc.get().await()
            if (snap != null && snap.exists()) {
                val movie = (snap.get("movie") as? List<*>)?.mapNotNull { it?.toString() }?.filterNot { it.equals(cleanId, ignoreCase = true) } ?: emptyList()
                val tv = (snap.get("tv") as? List<*>)?.mapNotNull { it?.toString() }?.filterNot { it.equals(cleanId, ignoreCase = true) } ?: emptyList()
                val anime = (snap.get("anime") as? List<*>)?.mapNotNull { it?.toString() }?.filterNot { it.equals(cleanId, ignoreCase = true) } ?: emptyList()
                searchOrderDoc.set(
                    mapOf(
                        "movie" to movie,
                        "tv" to tv,
                        "anime" to anime,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
            }
        } catch (e: Exception) {
            AppLogger.w("ManagedExtensionRepo", "Search order clean notice: ${e.message}")
        }

        adminRepository.logAudit(
            action = "DELETE_MANAGED_EXTENSION",
            targetType = "EXTENSION",
            targetId = cleanId,
            details = "Permanently deleted managed extension: $cleanId"
        )
    }

    suspend fun seedDefaultExtensions(): Int {
        var count = 0
        val defaults = DefaultCineStreamScrapers.getDefaults()
        for (ext in defaults) {
            saveManagedExtension(ext, isNew = false)
            count++
        }
        return count
    }
}
