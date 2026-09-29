package com.example.contract

/**
 * CineStream Canonical Firebase Collections Contract (v1).
 *
 * Defines canonical collection paths, legacy paths, and future reserved surfaces.
 * Used across repositories to prevent path drift and maintain contract integrity.
 */
object FirebaseCollections {
    /**
     * Canonical authoritative administrator records.
     * Document ID: {uid}
     * Authority requirement: enabled == true
     * Status: CANONICAL ACTIVE
     */
    const val ADMINS = "admins"

    /**
     * User profiles and feature permission/restriction states.
     * Document ID: {uid}
     * Status: CANONICAL ACTIVE
     */
    const val USERS = "users"

    /**
     * System configuration, OTA settings, and maintenance toggles.
     * Canonical doc: /config/app
     * Legacy fallback doc: /config/global
     * Status: CANONICAL ACTIVE
     */
    const val CONFIG = "config"

    /**
     * In-app broadcast announcements and FCM push notification trigger logs.
     * Document ID: {notificationId}
     * Status: CANONICAL ACTIVE
     */
    const val NOTIFICATIONS = "notifications"

    /**
     * System and administrative audit trail.
     * Document ID: {logId}
     * Status: CANONICAL ACTIVE
     */
    const val AUDIT_LOGS = "auditLogs"

    /**
     * User submitted malfunction/playback reports.
     * Document ID: {reportId}
     * Status: CANONICAL ACTIVE
     */
    const val REPORTS = "reports"

    /**
     * LEGACY CURRENT: APK-based extensions catalog.
     * Used by current Admin App and current firestore.rules.
     * Status: CURRENT LEGACY (Migration not yet executed in Phase C0)
     */
    const val LEGACY_EXTENSIONS = "extensions"
    const val LEGACY_EXTENSION_UPDATES = "extension_updates"

    /**
     * Modern managed scraper definitions catalog (Phase C2).
     * Configuration data only (no APKs, no DCL, no dynamic scripts).
     * Path: /managed_extensions/{extensionId}
     * Status: CANONICAL ACTIVE
     */
    const val MANAGED_EXTENSIONS = "managed_extensions"

    /**
     * App updates catalog.
     * Path: /app_updates/{updateId}
     * Status: CANONICAL ACTIVE
     */
    const val APP_UPDATES = "app_updates"

    /**
     * Support & user communication threads.
     * Path: /support_conversations/{conversationId}
     * Subcollection: /support_conversations/{conversationId}/messages/{messageId}
     * Canonical deployed path matching firestore.rules lines 196-207.
     */
    const val SUPPORT_CONVERSATIONS = "support_conversations"
    const val CONVERSATIONS = SUPPORT_CONVERSATIONS

    /**
     * Social User-to-User Conversations (Users App ecosystem).
     * Path: /conversations/{conversationId}
     * Subcollection: /conversations/{conversationId}/messages/{messageId}
     * Status: CANONICAL ACTIVE (User Plane)
     */
    const val SOCIAL_CONVERSATIONS = "conversations"

    /**
     * User Stories & Short Video Clips (Users App ecosystem).
     * Path: /stories/{storyId}
     * Status: CANONICAL ACTIVE (User Plane)
     */
    const val STORIES = "stories"

    /**
     * User Pro upgrade requests & verification queue (Phase C4).
     * Path: /pro_requests/{requestId}
     * Status: CANONICAL ACTIVE
     */
    const val PRO_REQUESTS = "pro_requests"
}

object FirebaseSubcollections {
    /**
     * Subcollection for conversation messages.
     * Path: /support_conversations/{conversationId}/messages/{messageId}
     */
    const val MESSAGES = "messages"
}

object FirebaseConfigDocs {
    /**
     * Primary canonical configuration document.
     */
    const val APP = "app"

    /**
     * Legacy global configuration document (retained for backward compatibility reads/writes).
     */
    const val GLOBAL = "global"

    /**
     * Canonical search order configuration across content types (Movie, TV Series, Anime).
     * Document path: /config/search_order
     * Status: CANONICAL ACTIVE (Phase EXT-CANONICAL-01)
     */
    const val SEARCH_ORDER = "search_order"
}
