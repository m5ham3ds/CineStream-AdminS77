package com.example.models

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Content-Type Categories supported for independent Search Ordering (Phase EXT-CANONICAL-01).
 *
 * Each category maps to standard contentTypes defined in [ManagedExtensionContentTypes].
 */
enum class SearchOrderCategory(val canonicalType: String, val labelAr: String, val labelEn: String) {
    MOVIE(ManagedExtensionContentTypes.MOVIE, "الأفلام", "Movies"),
    TV(ManagedExtensionContentTypes.SERIES, "المسلسلات", "TV Series"),
    ANIME(ManagedExtensionContentTypes.ANIME, "الأنمي", "Anime");

    companion object {
        fun fromString(value: String?): SearchOrderCategory {
            return when (value?.trim()?.uppercase()) {
                "MOVIE", "MOVIES" -> MOVIE
                "TV", "SERIES", "TV_SERIES", "SHOWS" -> TV
                "ANIME" -> ANIME
                else -> MOVIE
            }
        }
    }
}

/**
 * Canonical Search Order Configuration (Phase EXT-CANONICAL-01).
 *
 * Stored at: /config/search_order
 *
 * Invariant: Pure configuration containing ordered lists of stable extension IDs.
 * Independent ordering for MOVIE, TV (Series), and ANIME.
 * List lengths are unlimited (positions 1, 2, 3, 4, ...).
 * No executable bytecode, no dynamic class loading.
 */
@IgnoreExtraProperties
data class SearchOrderConfig(
    val movie: List<String> = emptyList(),
    val tv: List<String> = emptyList(),
    val anime: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    val updatedBy: String = ""
) {
    /**
     * Retrieve the ordered list of extension IDs for a given category.
     */
    fun getOrderForCategory(category: SearchOrderCategory): List<String> {
        return when (category) {
            SearchOrderCategory.MOVIE -> movie
            SearchOrderCategory.TV -> tv
            SearchOrderCategory.ANIME -> anime
        }
    }

    /**
     * Return a new [SearchOrderConfig] with updated ordering for the specified category.
     */
    fun withOrderForCategory(category: SearchOrderCategory, newOrder: List<String>): SearchOrderConfig {
        val sanitized = newOrder.map { it.trim() }.filter { it.isNotBlank() }
        return when (category) {
            SearchOrderCategory.MOVIE -> copy(movie = sanitized, updatedAt = System.currentTimeMillis())
            SearchOrderCategory.TV -> copy(tv = sanitized, updatedAt = System.currentTimeMillis())
            SearchOrderCategory.ANIME -> copy(anime = sanitized, updatedAt = System.currentTimeMillis())
        }
    }

    /**
     * Serialize to Firestore document map.
     * Dual-writes "tv" and "series" for absolute backward/forward compatibility.
     */
    fun toMap(adminEmail: String = ""): Map<String, Any?> {
        val now = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
        val email = adminEmail.ifBlank { updatedBy }
        return mapOf(
            "movie" to movie,
            "tv" to tv,
            "series" to tv, // Alias for scraper engine compatibility
            "anime" to anime,
            "updatedAt" to now,
            "updatedBy" to email
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): SearchOrderConfig {
            if (map == null) return SearchOrderConfig()

            val rawMovie = map["movie"]
            val rawTv = map["tv"] ?: map["series"]
            val rawAnime = map["anime"]

            val movieIds = parseIdList(rawMovie)
            val tvIds = parseIdList(rawTv)
            val animeIds = parseIdList(rawAnime)

            val updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
            val updatedBy = (map["updatedBy"] as? String) ?: ""

            return SearchOrderConfig(
                movie = movieIds,
                tv = tvIds,
                anime = animeIds,
                updatedAt = updatedAt,
                updatedBy = updatedBy
            )
        }

        fun fromDocument(doc: DocumentSnapshot?): SearchOrderConfig {
            if (doc == null || !doc.exists()) return SearchOrderConfig()
            return fromMap(doc.data)
        }

        @Suppress("UNCHECKED_CAST")
        private fun parseIdList(raw: Any?): List<String> {
            return when (raw) {
                is List<*> -> raw.mapNotNull { it?.toString()?.trim() }.filter { it.isNotBlank() }
                is String -> raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                else -> emptyList()
            }
        }
    }
}
