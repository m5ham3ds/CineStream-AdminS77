package com.example.validation

import com.example.models.ManagedExtension
import com.example.models.ManagedExtensionCapabilities
import com.example.models.ManagedExtensionContentTypes
import com.example.models.ManagedExtensionStatus
import java.net.URI

object ManagedExtensionValidator {

    private val EXTENSION_ID_REGEX = Regex("^[a-zA-Z0-9_-]{2,64}$")
    private val SCRAPER_KEY_REGEX = Regex("^[a-z0-9_-]{2,64}$")

    /**
     * Comprehensive validation of a Managed Extension configuration.
     * Returns a list of error messages. If empty, the configuration is valid.
     */
    fun validate(ext: ManagedExtension): List<String> {
        val errors = mutableListOf<String>()

        // 1. extensionId validation
        if (ext.extensionId.isBlank()) {
            errors.add("Extension ID is required")
        } else if (!EXTENSION_ID_REGEX.matches(ext.extensionId)) {
            errors.add("Extension ID must be 2-64 alphanumeric characters, underscores, or hyphens")
        }

        // 2. scraperKey validation
        if (ext.scraperKey.isBlank()) {
            errors.add("Scraper Key is required")
        } else if (!SCRAPER_KEY_REGEX.matches(ext.scraperKey)) {
            errors.add("Scraper Key must be lowercase alphanumeric characters, underscores, or hyphens (2-64 chars)")
        }

        // 3. Name validation
        if (ext.name.trim().length < 2) {
            errors.add("Extension Name must be at least 2 characters")
        } else if (ext.name.length > 100) {
            errors.add("Extension Name cannot exceed 100 characters")
        }

        // 4. Base URL validation (SSRF Protection)
        val baseUrlError = validateHttpsUrl(ext.baseUrl, isBaseUrl = true)
        if (baseUrlError != null) {
            errors.add("Base URL: $baseUrlError")
        }

        // 5. Search URL validation
        if (ext.searchUrl.isNotBlank()) {
            if (ext.searchUrl.startsWith("/")) {
                // Relative URL pattern is allowed
                if (ext.searchUrl.contains(" ")) {
                    errors.add("Search URL path cannot contain spaces")
                }
            } else {
                val searchUrlError = validateHttpsUrl(ext.searchUrl, isBaseUrl = false)
                if (searchUrlError != null) {
                    errors.add("Search URL: $searchUrlError")
                }
            }
        }

        // 6. Versions validation
        if (ext.runtimeApiVersion < 1) {
            errors.add("Runtime API Version must be at least 1")
        }
        if (ext.definitionVersion < 1) {
            errors.add("Definition Version must be at least 1")
        }

        // 7. Priority validation
        if (ext.priority < 0) {
            errors.add("Priority must be 0 or greater")
        }

        // 8. Lifecycle Status validation
        val statusValid = try {
            ManagedExtensionStatus.valueOf(ext.status.trim().uppercase())
            true
        } catch (e: Exception) {
            false
        }
        if (!statusValid) {
            errors.add("Invalid status: ${ext.status}. Must be one of ACTIVE, MAINTENANCE, DISABLED, DEPRECATED")
        }

        // 9. Capabilities validation
        val invalidCaps = ext.capabilities.filter { it.trim().uppercase() !in ManagedExtensionCapabilities.ALL }
        if (invalidCaps.isNotEmpty()) {
            errors.add("Unknown capabilities: ${invalidCaps.joinToString()}")
        }

        // 10. Content Types validation
        val invalidTypes = ext.contentTypes.filter { it.trim().uppercase() !in ManagedExtensionContentTypes.ALL }
        if (invalidTypes.isNotEmpty()) {
            errors.add("Unknown content types: ${invalidTypes.joinToString()}")
        }

        return errors
    }

    /**
     * Strict HTTPS URL and host validation to prevent SSRF and unsafe endpoint configuration.
     */
    fun validateHttpsUrl(url: String, isBaseUrl: Boolean): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            return if (isBaseUrl) "URL cannot be blank" else null
        }

        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return "Must use HTTPS scheme (e.g. https://provider.com)"
        }

        // For search pattern URLs, template query placeholders like {q}, %s, etc. are valid.
        // Normalize template placeholders to alphanumeric tokens before URI parsing.
        val sanitizedForUri = trimmed
            .replace(Regex("%[a-zA-Z]"), "param")
            .replace(Regex("\\{[^}]*\\}"), "param")

        val uri = try {
            URI(sanitizedForUri)
        } catch (e: Exception) {
            return "Malformed URL syntax: ${e.message}"
        }

        val host = uri.host
        if (host.isNullOrBlank()) {
            return "URL must contain a valid domain host"
        }

        val lowerHost = host.lowercase()

        // Reject localhost
        if (lowerHost == "localhost" || lowerHost.endsWith(".localhost") || lowerHost == "localhost.localdomain") {
            return "Localhost domains are forbidden"
        }

        // Reject 0.0.0.0
        if (lowerHost == "0.0.0.0") {
            return "0.0.0.0 address is forbidden"
        }

        // Reject IPv4 loopback / private / link-local addresses
        if (isPrivateOrLoopbackIpv4(lowerHost)) {
            return "Private and loopback IP addresses are forbidden ($lowerHost)"
        }

        // Reject IPv6 loopback / unique local / link-local addresses
        if (isPrivateOrLoopbackIpv6(lowerHost)) {
            return "Private and loopback IPv6 addresses are forbidden ($lowerHost)"
        }

        // Must have at least one dot in host unless mock testing host
        if (!lowerHost.contains(".") && !lowerHost.startsWith("[")) {
            return "Host must be a fully qualified domain name with at least one dot"
        }

        if (lowerHost.contains(" ")) {
            return "Host cannot contain spaces"
        }

        return null
    }

    private fun isPrivateOrLoopbackIpv4(host: String): Boolean {
        // Strip port if present
        val cleanHost = host.substringBefore(":")
        val parts = cleanHost.split(".")
        if (parts.size != 4) return false

        val octets = parts.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4) return false
        if (octets.any { it !in 0..255 }) return false

        val (o1, o2, _, _) = octets

        // 127.0.0.0/8 (Loopback)
        if (o1 == 127) return true

        // 10.0.0.0/8 (Private)
        if (o1 == 10) return true

        // 172.16.0.0/12 (Private)
        if (o1 == 172 && o2 in 16..31) return true

        // 192.168.0.0/16 (Private)
        if (o1 == 192 && o2 == 168) return true

        // 169.254.0.0/16 (Link-Local)
        if (o1 == 169 && o2 == 254) return true

        // 0.0.0.0/8
        if (o1 == 0) return true

        return false
    }

    private fun isPrivateOrLoopbackIpv6(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]").lowercase()
        if (clean == "::1" || clean == "0:0:0:0:0:0:0:1") return true
        if (clean.startsWith("fe80:") || clean.startsWith("fe8") || clean.startsWith("fe9") || clean.startsWith("fea") || clean.startsWith("feb")) return true
        if (clean.startsWith("fc00:") || clean.startsWith("fd")) return true
        return false
    }
}
