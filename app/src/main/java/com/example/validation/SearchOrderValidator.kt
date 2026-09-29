package com.example.validation

import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionContentTypes
import com.example.models.SearchOrderCategory
import com.example.models.SearchOrderConfig

/**
 * Domain Validator for Search Ordering by Content Type (Phase EXT-CANONICAL-01).
 *
 * Enforces strict capability checking:
 * 1. Extension IDs must exist in the canonical registry.
 * 2. Extension IDs must be unique within each category list (no duplicates).
 * 3. Extensions must support the specific content type of the list they belong to.
 * 4. Empty lists are permissible (e.g. fresh installation or unconfigured category).
 */
object SearchOrderValidator {

    /**
     * Determine if a [ManagedExtension] is functionally eligible for a [SearchOrderCategory].
     */
    fun isExtensionEligibleForCategory(ext: ManagedExtension, category: SearchOrderCategory): Boolean {
        val types = ext.contentTypes.map { it.trim().uppercase() }
        return when (category) {
            SearchOrderCategory.MOVIE -> {
                types.contains(ManagedExtensionContentTypes.MOVIE) || types.contains("MOVIES")
            }
            SearchOrderCategory.TV -> {
                types.contains(ManagedExtensionContentTypes.SERIES) ||
                types.contains("TV") ||
                types.contains("SERIES") ||
                types.contains("SHOWS")
            }
            SearchOrderCategory.ANIME -> {
                types.contains(ManagedExtensionContentTypes.ANIME)
            }
        }
    }

    /**
     * Validate a complete [SearchOrderConfig] against the provided catalog of available extensions.
     *
     * Returns a list of error strings. If the list is empty, the configuration is valid.
     */
    fun validate(
        config: SearchOrderConfig,
        availableExtensions: Map<String, ManagedExtension>
    ): List<String> {
        val errors = mutableListOf<String>()

        validateCategoryList(
            category = SearchOrderCategory.MOVIE,
            ids = config.movie,
            availableExtensions = availableExtensions,
            errors = errors
        )

        validateCategoryList(
            category = SearchOrderCategory.TV,
            ids = config.tv,
            availableExtensions = availableExtensions,
            errors = errors
        )

        validateCategoryList(
            category = SearchOrderCategory.ANIME,
            ids = config.anime,
            availableExtensions = availableExtensions,
            errors = errors
        )

        return errors
    }

    private fun validateCategoryList(
        category: SearchOrderCategory,
        ids: List<String>,
        availableExtensions: Map<String, ManagedExtension>,
        errors: MutableList<String>
    ) {
        val categoryLabel = category.labelEn

        // 1. Check for duplicates
        val seen = mutableSetOf<String>()
        val duplicates = mutableSetOf<String>()
        for (id in ids) {
            val normalized = id.trim()
            if (!seen.add(normalized)) {
                duplicates.add(normalized)
            }
        }
        if (duplicates.isNotEmpty()) {
            errors.add("Duplicate extension ID in $categoryLabel search order: ${duplicates.joinToString()}")
        }

        // 2. Validate existence and content-type capability
        for (id in ids) {
            val normalized = id.trim()
            val ext = availableExtensions[normalized]
            if (ext == null) {
                errors.add("Extension '$normalized' in $categoryLabel search order does not exist in registry")
                continue
            }

            if (!isExtensionEligibleForCategory(ext, category)) {
                val displayName = ext.name.ifBlank { normalized }
                val supported = if (ext.contentTypes.isEmpty()) "NONE" else ext.contentTypes.joinToString()
                errors.add("Extension '$displayName' ($normalized) does not support content type $categoryLabel (Supported: $supported)")
            }
        }
    }
}
