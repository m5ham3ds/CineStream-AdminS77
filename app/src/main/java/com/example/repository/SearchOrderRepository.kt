package com.example.repository

import com.example.contract.FirebaseCollections
import com.example.contract.FirebaseConfigDocs
import com.example.diagnostics.AppLogger
import com.example.models.ManagedExtension
import com.example.models.SearchOrderCategory
import com.example.models.SearchOrderConfig
import com.example.validation.SearchOrderValidator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

/**
 * Authoritative Repository for Content-Type-Specific Search Ordering (Phase EXT-CANONICAL-01).
 *
 * Stored at: /config/search_order
 *
 * Invariant: Pure configuration containing ordered lists of extension IDs for MOVIE, TV, and ANIME.
 * Enforces domain validation before persistence.
 */
class SearchOrderRepository(
    private val adminRepository: AdminRepository = AdminRepository()
) {
    private fun ensureFirebase() {
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(com.example.MyApplication.instance)
            if (apps.isEmpty()) {
                com.example.diagnostics.FirebaseInitializer.init(com.example.MyApplication.instance)
            }
        } catch (e: Exception) {
            AppLogger.w("SearchOrderRepo", "ensureFirebase notice: ${e.message}")
        }
    }

    private val firestore: FirebaseFirestore by lazy {
        ensureFirebase()
        FirebaseFirestore.getInstance()
    }

    private val configCollection get() = firestore.collection(FirebaseCollections.CONFIG)
    private val searchOrderDocRef get() = configCollection.document(FirebaseConfigDocs.SEARCH_ORDER)

    /**
     * Real-time stream of the canonical [SearchOrderConfig].
     */
    fun getSearchOrder(): Flow<SearchOrderConfig> = callbackFlow {
        val listener = searchOrderDocRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                AppLogger.w("SearchOrderRepo", "Search order listener notice: ${error.message}")
                if (error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    close(error)
                }
                return@addSnapshotListener
            }

            val config = if (snapshot != null && snapshot.exists()) {
                SearchOrderConfig.fromDocument(snapshot)
            } else {
                SearchOrderConfig()
            }
            trySend(config)
        }
        awaitClose { listener.remove() }
    }.retryWhen { cause, attempt ->
        delay(minOf(1000L * (attempt + 1), 5000L))
        true
    }

    /**
     * Fetch the search order directly from the server, bypassing local cache if possible.
     */
    suspend fun fetchSearchOrder(): SearchOrderConfig {
        return try {
            val snap = searchOrderDocRef.get(Source.SERVER).await()
            if (snap != null && snap.exists()) {
                SearchOrderConfig.fromDocument(snap)
            } else {
                SearchOrderConfig()
            }
        } catch (e: Exception) {
            AppLogger.w("SearchOrderRepo", "fetchSearchOrder server fetch notice: ${e.message}")
            try {
                val cachedSnap = searchOrderDocRef.get().await()
                SearchOrderConfig.fromDocument(cachedSnap)
            } catch (e2: Exception) {
                SearchOrderConfig()
            }
        }
    }

    /**
     * Persist the canonical [SearchOrderConfig] after rigorous domain validation.
     */
    suspend fun saveSearchOrder(
        config: SearchOrderConfig,
        availableExtensions: Map<String, ManagedExtension>,
        adminEmail: String = ""
    ): Result<Unit> {
        val errors = SearchOrderValidator.validate(config, availableExtensions)
        if (errors.isNotEmpty()) {
            val msg = errors.first()
            AppLogger.e("SearchOrderRepo", "Validation failed for search order: $msg")
            return Result.failure(IllegalArgumentException(msg))
        }

        return try {
            val payload = config.toMap(adminEmail)
            searchOrderDocRef.set(payload, SetOptions.merge()).await()

            adminRepository.logAudit(
                action = "UPDATE_SEARCH_ORDER",
                targetType = "CONFIG",
                targetId = FirebaseConfigDocs.SEARCH_ORDER,
                details = "Updated search order: Movie=${config.movie.size}, TV=${config.tv.size}, Anime=${config.anime.size}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e("SearchOrderRepo", "Failed to save search order: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate recommended default search orders based on registered extensions and their capabilities.
     */
    fun getDefaultSearchOrder(availableExtensions: List<ManagedExtension>): SearchOrderConfig {
        val movieOrder = availableExtensions
            .filter { SearchOrderValidator.isExtensionEligibleForCategory(it, SearchOrderCategory.MOVIE) }
            .sortedByDescending { it.priority }
            .map { it.extensionId }

        val tvOrder = availableExtensions
            .filter { SearchOrderValidator.isExtensionEligibleForCategory(it, SearchOrderCategory.TV) }
            .sortedByDescending { it.priority }
            .map { it.extensionId }

        val animeOrder = availableExtensions
            .filter { SearchOrderValidator.isExtensionEligibleForCategory(it, SearchOrderCategory.ANIME) }
            .sortedByDescending { it.priority }
            .map { it.extensionId }

        return SearchOrderConfig(
            movie = movieOrder,
            tv = tvOrder,
            anime = animeOrder,
            updatedAt = System.currentTimeMillis()
        )
    }
}
