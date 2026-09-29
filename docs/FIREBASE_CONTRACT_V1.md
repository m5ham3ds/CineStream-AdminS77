# CineStream Canonical Firebase Contract (v1)

**Ecosystem:** CineStream Dual-App Architecture (`CineStream Users App` & `CineStream Admin App`)  
**Backend Platform:** Google Firebase (Firebase Authentication, Cloud Firestore, Cloud Messaging) & Cloudinary CDN  
**Contract Version:** 1.1.0 (Phase C1 User Management, Account Ban & Subscription Authority)  
**Status:** Canonical Authority Document  

---

## 1. Executive Contract Principles

1. **Single Source of Truth:**
   Both `CineStream Users App` and `CineStream Admin App` interface with the same Firebase Cloud Firestore and Authentication instance. All document paths, field identifiers, data types, and permission boundaries defined in this document are binding.

2. **Zero Executable Code in Firestore:**
   Cloud Firestore is strictly a data and configuration datastore. It MUST NEVER store executable bytecode, Dex files, APK binaries, dynamic classloaders, or executable script code. All media scraping and playback logic resides in bundled, audited Kotlin code.

3. **Media Storage Separation:**
   Cloudinary is the authoritative media CDN for CineStream (posters, backdrops, avatars). Firebase Storage is strictly forbidden for application media. Firestore documents store only HTTPS CDN URLs, dimensions, and Cloudinary public IDs.

4. **Strict Authorization vs Data Role:**
   Administrative authorization is derived solely from `/admins/{uid}` with `enabled == true`. The field `/users/{uid}.role` is application metadata only and is NEVER an authorization source.

---

## 2. Canonical Collections & Schema Specifications

### 2.1 Admin Authority Collection: `/admins/{uid}`
- **Document Path:** `/admins/{uid}`
- **Document ID:** Equal to the Firebase Authentication UID (`request.auth.uid`).
- **Authorization Rule:** Sole authoritative mechanism for administrative privileges in both Firestore Security Rules and Admin Client runtime.
- **Rule Verification:**
  ```javascript
  exists(/databases/$(database)/documents/admins/$(request.auth.uid)) &&
  get(/databases/$(database)/documents/admins/$(request.auth.uid)).data.enabled == true
  ```
- **Runtime Requirement:** Admin App client MUST check this document with `enabled == true`. The previous fallback to `/users/{uid}.role == "admin"` is STRICTLY REMOVED and PROHIBITED.

#### Field Specifications:
| Field Name | Type | Allowed Values | Description |
|---|---|---|---|
| `uid` | String | Matches doc ID | Firebase Auth UID of the administrator |
| `email` | String | Valid email | Registered administrator email address |
| `role` | String | `"admin"`, `"superadmin"` | Administrative privilege tier |
| `enabled` | Boolean | `true`, `false` | Authoritative toggle; MUST be `true` for access |
| `createdAt` | Long / Timestamp | Server timestamp | Epoch milliseconds of privilege grant |

---

### 2.2 Users Collection: `/users/{uid}`
- **Document Path:** `/users/{uid}`
- **Document ID:** Equal to the Firebase Authentication UID (`request.auth.uid`). Random IDs are strictly prohibited.
- **Data Ownership & Merging:**
  - Standard users can only update non-sensitive profile fields (`displayName`, `photoUrl`, `username`, `lastLoginAt`, `updatedAt`, `appVersion`).
  - Sensitive administrative and security fields are read-only for standard users and can only be updated by verified administrators.
  - Partial Updates: Admin App must NEVER use unmerged `set()`. All writes must use `update()` or `set(..., SetOptions.merge())` to prevent accidental deletion of user-owned state or subcollections.

#### Field Specifications & Authority Matrix:
| Field Name | Type | Default | Description | Status |
|---|---|---|---|---|
| `uid` | String | Document ID | Firebase Authentication UID | CANONICAL |
| `id` | String | Document ID | Identity mirror for serialization compatibility | CANONICAL |
| `username` | String | `""` | User handle / username | CANONICAL |
| `email` | String | `""` | Primary email address | CANONICAL |
| `displayName` | String | `""` | Public display name | CANONICAL |
| `photoUrl` | String | `""` | Avatar URL (Cloudinary CDN reference) | CANONICAL |
| `role` | String | `"user"` | User role: `"user"`, `"moderator"`, `"admin"`, `"superadmin"`. **Note: Metadata only, NOT an authorization source.** | CANONICAL (Data only) |
| `isActive` | Boolean | `true` | Account active state | CANONICAL |
| `isBanned` | Boolean | `false` | Global account-level ban toggle (Enforced in Rules) | CANONICAL SECURITY |
| `banReason` | String? | `null` | Explanation for global account ban | CANONICAL SECURITY |
| `banExpiresAt` | Long? | `null` | Expiration epoch millis (null = permanent ban) | CANONICAL SECURITY |
| `isPremium` | Boolean | `false` | Premium / PRO subscription active indicator | CANONICAL ACTIVE |
| `subscriptionTier` | String | `"free"` | Subscription plan level: `"free"`, `"pro"`, `"vip"` | CANONICAL ACTIVE |
| `subscriptionStatus` | String | `"free"` | Subscription state: `"active"`, `"free"`, `"expired"`, `"canceled"` | CANONICAL ACTIVE |
| `subscriptionExpiresAt` | Long? | `null` | Epoch millis of subscription expiration (null = lifetime) | CANONICAL ACTIVE |
| `isPro` | Boolean | `false` | Compatibility mirror of `isPremium` | COMPATIBILITY MIRROR |
| `plan` | String? | `"free"` | Legacy Users App alias of `subscriptionTier` | LEGACY COMPATIBILITY |
| `proPlan` | String? | `"free"` | Compatibility mirror of `subscriptionTier` | COMPATIBILITY MIRROR |
| `proExpiresAt` | Long? | `null` | Compatibility mirror of `subscriptionExpiresAt` | COMPATIBILITY MIRROR |
| `canWatch` | Boolean | `true` | Media playback permission | CANONICAL (Feature) |
| `canDownload` | Boolean | `true` | Offline media download permission | CANONICAL (Feature) |
| `canChat` | Boolean | `true` | Community / chat access permission | CANONICAL (Feature) |
| `canStory` | Boolean | `true` | Stories / clips posting permission | CANONICAL (Feature) |
| `canP2P` | Boolean | `true` | P2P direct sharing permission | CANONICAL (Feature) |
| `canComment` | Boolean | `true` | Media commenting permission | CANONICAL (Feature) |
| `canUpload` | Boolean | `false` | Content upload permission | CANONICAL (Feature) |
| `canRequest` | Boolean | `true` | Movie/Show request permission | CANONICAL (Feature) |
| `watchBan` | Boolean | `false` | Explicit streaming restriction flag | COMPATIBILITY / FEATURE BAN |
| `downloadBan` | Boolean | `false` | Explicit download restriction flag | COMPATIBILITY / FEATURE BAN |
| `chatBan` | Boolean | `false` | Explicit chat restriction flag | COMPATIBILITY / FEATURE BAN |
| `storyBan` | Boolean | `false` | Explicit story restriction flag | COMPATIBILITY / FEATURE BAN |
| `p2pBan` | Boolean | `false` | Explicit P2P restriction flag | COMPATIBILITY / FEATURE BAN |
| `deviceLimit` | Int | `2` | Max concurrent active devices | CANONICAL |
| `offlineDaysOverride` | Int? | `null` | DRM offline cache validity override (days) | CANONICAL |
| `forcedAdsOverride` | Int? | `null` | Ad frequency interval override | CANONICAL |
| `appVersion` | String | `""` | User client build version string | CANONICAL |
| `createdAt` | Long | Server timestamp | Account creation epoch millis | CANONICAL |
| `updatedAt` | Long | Server timestamp | Profile last update epoch millis | CANONICAL |
| `lastLoginAt` | Long | Server timestamp | Last authentication epoch millis | CANONICAL |
| `lastLoginTimestamp` | Long | - | Alias for `lastLoginAt` | LEGACY COMPATIBILITY |
| `lastActiveAt` | Long | - | Alias for `updatedAt` | LEGACY COMPATIBILITY |

---

### 2.3 Managed Extensions Contract & Conflict Resolution

#### 2.3.1 Path Conflict Status & Governance:
- **CURRENT LEGACY CONTRACT:** `/extensions/{extensionId}`
  - Legacy APK-oriented catalog (`packageName`, `apkUrl`, `apkSha256`, `minAppVersionCode`).
  - Preserved as **CURRENT LEGACY** in `ExtensionsScreen.kt` and `ExtensionsUpdatesScreen.kt` to maintain backwards compatibility.
  - Production documents are NOT overwritten or deleted.
- **CANONICAL ACTIVE CONTRACT (Phase C2):** `/managed_extensions/{extensionId}`
  - Modern canonical collection for CineStream Users App dynamic scraper configuration.
  - Admin App authoritative management implemented via `ManagedExtensionRepository`, `ManagedExtensionsViewModel`, and `ManagedExtensionsScreen`.
  - Authoritative Firestore Security Rules deployed at `match /managed_extensions/{extensionId}` with admin write and authenticated user read.

#### 2.3.2 Canonical Specification: `/managed_extensions/{extensionId}`
- **Document Path:** `/managed_extensions/{extensionId}`
- **Status:** **CANONICAL ACTIVE (Phase C2)**
- **Nature of Collection:** PURE DATA CONFIGURATION.
  - Does NOT contain executable code or scripts.
  - Does NOT contain APK download URLs or installation instructions.
  - Does NOT enable dynamic code loading (no `DexClassLoader`, `PathClassLoader`).
  - `scraperKey` is strictly an identifier mapping to a trusted Kotlin scraper bundled inside the Users App.
- **Validation & Security Invariants:**
  - **SSRF Protection:** Enforced by `ManagedExtensionValidator`. All URLs must use HTTPS scheme and reject `localhost`, `0.0.0.0`, IPv4 private/loopback/link-local blocks (`10/8`, `172.16/12`, `192.168/16`, `169.254/16`), and IPv6 loopback (`::1`, `fe80::/10`, `fc00::/7`).
  - **Identifiers:** `extensionId` (2-64 chars, `[a-zA-Z0-9_-]`), `scraperKey` (2-64 chars, strictly lowercase `[a-z0-9_-]`).
  - **Lifecycle States:** `ACTIVE`, `MAINTENANCE`, `DISABLED`, `DEPRECATED`. Safe fallback to `DISABLED` on unknown or malformed input.
  - **Operational Invariant:** Scraper is operational only if `enabled == true` AND `status == "ACTIVE"`.
  - **Controlled Capabilities:** Validated against closed set (`SEARCH`, `DETAILS`, `EPISODES`, `SERVER_DISCOVERY`, `VIDEO_EXTRACTION`, `CLOUDFLARE_CHALLENGE`).
  - **Controlled Content Types:** Validated against closed set (`MOVIE`, `SERIES`, `ANIME`, `ASIAN_DRAMA`).
- **Field Specifications:**
  | Field Name | Type | Description | Authority / Validation |
  |---|---|---|---|
  | `extensionId` | String | Unique scraper document identifier | Required, alphanumeric + `_-` |
  | `scraperKey` | String | Bundled Kotlin scraper identifier | Required, lowercase `[a-z0-9_-]` |
  | `name` | String | Human-readable extension display name | 2-100 characters |
  | `description` | String | Summary of source provider | Optional string |
  | `baseUrl` | String | Remote base URL for provider endpoint | HTTPS only, strict SSRF checked |
  | `searchUrl` | String | Search endpoint pattern URL | Optional, HTTPS or relative path |
  | `runtimeApiVersion` | Int | API runtime contract level (e.g., `1`) | >= 1 |
  | `definitionVersion` | Int | Monotonically increasing revision | >= 1 |
  | `status` | String | Lifecycle state | `ACTIVE`, `MAINTENANCE`, `DISABLED`, `DEPRECATED` |
  | `priority` | Int | Search cascade priority | >= 0 (higher = prioritized first) |
  | `capabilities` | List<String> | Supported features | Controlled closed set |
  | `contentTypes` | List<String> | Supported content types | Controlled closed set |
  | `enabled` | Boolean | Master enable flag | Boolean |
  | `createdAt` | Long | Creation epoch millis | Server/Admin timestamp |
  | `updatedAt` | Long | Last update epoch millis | Server/Admin timestamp |

---

### 2.4 Configuration Collection: `/config/{configId}`
- **Canonical Document Path:** `/config/app`
- **Legacy Fallback Path:** `/config/global` (Dual-written by Admin, fallback read)
- **Data Ownership:** Admin writes; all authenticated users have read access.

#### Field Specifications:
| Field Name | Type | Default | Description |
|---|---|---|---|
| `maintenanceEnabled` | Boolean | `false` | Master maintenance toggle (blocks standard user streaming) |
| `maintenanceTitle` | String | `"Under Scheduled Maintenance"` | Title displayed on user maintenance screen |
| `maintenanceMessage` | String | `"..."` | Explanatory message for users |
| `minimumVersionCode` | Int | `1` | Minimum supported app build code (forced update cutoff) |
| `latestVersionCode` | Int | `1` | Current release version code for OTA |
| `latestVersionName` | String | `"1.0.0"` | Current release semantic version string |
| `apkUrl` | String | `""` | Direct download link for the latest CineStream app APK |
| `apkSha256` | String | `""` | SHA-256 integrity hash of the update APK |
| `mandatoryUpdate` | Boolean | `false` | If true, user cannot dismiss update prompt |
| `releaseNotes` | String | `""` | Changelog notes for update dialog |
| `providersJson` | String | `"{}"` | Dynamic JSON catalog of media providers and scrapers |
| `defaultOfflineDays` | Int | `2` | Default days offline video licenses remain valid |
| `defaultForcedAds` | Int | `5` | Default number of media starts between interstitial ads |
| `cloudinaryCloudName` | String | `"cinestream"` | Client Cloudinary cloud identifier (safe parameter) |
| `cloudinaryUploadPreset` | String | `"cinestream_unsigned"` | Client unsigned upload preset (safe parameter) |
| `updatedAt` | Long | Server timestamp | Timestamp of last configuration publish |

---

### 2.5 Notifications Collection: `/notifications/{notificationId}`
- **Document Path:** `/notifications/{notificationId}`
- **Data Ownership:** Admin writes; all authenticated users have read access.
- **FCM Architecture:**
  - Admin App writes notification document to `/notifications/{notificationId}`.
  - A server-side Firebase Cloud Function triggers on `document.onCreate` and invokes `admin.messaging().sendEachForMulticast()` for push delivery.

#### Field Specifications:
| Field Name | Type | Allowed Values | Description |
|---|---|---|---|
| `id` | String | Document ID | Auto-generated document ID |
| `title` | String | Non-empty | Notification headline |
| `body` | String | Non-empty | Notification body text (canonical name; `message` alias supported) |
| `message` | String | Non-empty | Alias for `body` |
| `type` | String | `"SYSTEM"`, `"ANNOUNCEMENT"`, `"ALERT"`, `"PROMO"`, `"UPDATE"` | Category of notification |
| `target` | String | `"ALL"`, `"PRO"`, `"UID"` | Delivery audience selector |
| `targetType` | String | `"ALL"`, `"PRO"`, `"UID"` | Delivery audience type |
| `targetUid` | String | Empty or user UID | Recipient UID when `target == "UID"` |
| `createdBy` | String | Admin UID | UID of admin who dispatched broadcast |
| `createdAt` | Long | Server timestamp | Creation epoch millis |
| `expiresAt` | Long? | `null` | Optional expiration date |
| `isActive` | Boolean | `true` | Display active state |
| `status` | String | `"PENDING"`, `"SENT"` | Delivery processing status |

---

### 2.6 User Reports Collection: `/reports/{reportId}`
- **Document Path:** `/reports/{reportId}`
- **Data Ownership:** Standard users create reports for their own UID; Admins read all reports, update status, and manage resolution.

#### Field Specifications:
| Field Name | Type | Allowed Values | Description |
|---|---|---|---|
| `id` | String | Document ID | Auto-generated document ID |
| `userId` | String | Valid UID | UID of submitting user |
| `userEmail` | String | Valid email | Email of submitting user |
| `type` | String | `"ISSUE"`, `"STREAM"`, `"BUG"`, `"CONTENT"` | Malfunction classification |
| `targetType` | String | `"STREAM"`, `"MEDIA"`, `"EXTENSION"`, `"USER"` | Affected target category |
| `targetId` | String | Non-empty | Identifier of affected media/source |
| `title` | String | Non-empty | Short summary of issue |
| `description` | String | Non-empty | Detailed description |
| `status` | String | `"PENDING"`, `"RESOLVED"`, `"DISMISSED"` | Current ticket status |
| `resolutionNotes` | String | `""` | Admin outcome or resolution notes |
| `createdAt` | Long | Server timestamp | Submission epoch millis |
| `resolvedAt` | Long? | `null` | Resolution epoch millis |
| `resolvedBy` | String | `""` | Admin UID who resolved/dismissed ticket |

---

### 2.7 Audit Logs Collection: `/auditLogs/{logId}`
- **Document Path:** `/auditLogs/{logId}` (Canonical camelCase. `/audit_logs` is strictly prohibited).
- **Data Ownership:** Admin writes append-only; only Admins can read.

#### Field Specifications:
| Field Name | Type | Description |
|---|---|---|
| `id` | String | Document ID |
| `actorUid` | String | UID of administrator performing the action (canonical name) |
| `adminUid` | String | Alias for `actorUid` |
| `adminEmail` | String | Email address of acting administrator |
| `action` | String | Operation code (e.g., `CREATE_USER`, `UPDATE_USER`, `GRANT_ADMIN`, `REVOKE_ADMIN`, `UPDATE_APP_CONFIG`, `SAVE_EXTENSION`, `DISPATCH_NOTIFICATION`, `RESOLVE_REPORT`) |
| `targetType` | String | Entity category: `"USER"`, `"CONFIG"`, `"NOTIFICATION"`, `"EXTENSION"`, `"REPORT"`, `"AUTH"` |
| `targetId` | String | ID of affected entity |
| `details` | String | Human-readable change summary |
| `createdAt` | Long | Action epoch millis |

---

### 2.8 Support Conversations Collection: `/support_conversations/{conversationId}`
- **Document Path:** `/support_conversations/{conversationId}`
- **Subcollection:** `/support_conversations/{conversationId}/messages/{messageId}`
- **Backwards Compatibility Alias:** In `FirebaseContract.kt`, `FirebaseCollections.CONVERSATIONS` directly aliases `SUPPORT_CONVERSATIONS` (`"support_conversations"`). The contradictory `"conversations"` path draft has been reconciled to match deployed Firestore Rules.
- **Contract & Implementation Status (Phase C3):**
  - **Path Reconciliation:** Reconciled from early `/conversations` working draft to canonical path `/support_conversations`.
  - **Firestore Rules:** **DEPLOYED & ACTIVE** (`firestore.rules` lines 196–207). Authenticated users can create conversations for their own UID and read their own messages. Administrators have full read, update, and deletion access.
  - **Admin UI Implementation:** **CANONICAL ACTIVE (Phase C3)** via `SupportInboxScreen`, `SupportChatScreen`, `SupportViewModel`, and `SupportRepository`.
  - **Security Invariant:** User ownership anchored strictly to `userId == request.auth.uid`. Admin messages enforce `senderRole = "admin"` and authenticated admin credentials.

#### Schema Specifications:
- `/support_conversations/{conversationId}`:
  | Field Name | Type | Description |
  |---|---|---|
  | `conversationId` | String | Thread document identifier |
  | `userId` | String | Owner user account UID |
  | `userEmail` | String | Owner user email address |
  | `userName` | String | Display name of the user |
  | `subject` | String | Inquiry topic / title |
  | `status` | String | Lifecycle state: `"OPEN"`, `"PENDING"`, `"RESOLVED"`, `"CLOSED"` |
  | `lastMessage` | String | Preview snippet of latest message |
  | `lastMessageAt` | Long | Epoch millis of latest message |
  | `lastSenderRole` | String | Role of last author (`"user"`, `"admin"`) |
  | `unreadByAdmin` | Boolean | True when pending admin review |
  | `unreadByUser` | Boolean | True when admin replied and user hasn't seen it |
  | `createdAt` | Long | Epoch millis of conversation creation |
  | `updatedAt` | Long | Epoch millis of last update |

- `/support_conversations/{conversationId}/messages/{messageId}`:
  | Field Name | Type | Description |
  |---|---|---|
  | `messageId` | String | Message document identifier |
  | `conversationId` | String | Parent conversation thread identifier |
  | `senderId` | String | Authenticated sender UID |
  | `senderRole` | String | Author role: `"user"` or `"admin"` |
  | `senderEmail` | String | Author email address |
  | `text` | String | Message text content (non-empty) |
  | `mediaUrl` | String? | Optional media attachment link |
  | `timestamp` | Long | Epoch millis of dispatch |
  | `read` | Boolean | Read receipt status |

---

### 2.9 Pro Upgrade Requests: `/pro_requests/{requestId}`
- **Document Path:** `/pro_requests/{requestId}`
- **Contract & Implementation Status (Phase C4):**
  - **CANONICAL ACTIVE (Phase C4)** via `ProRequestsScreen`, `ProRequestsViewModel`, `ProRequestRepository`, and `ProRequest` model.
  - **Firestore Rules:** **DEPLOYED & ACTIVE** (`firestore.rules` lines 219–223). Authenticated users can create requests for their own UID and read their own requests. Administrators have full read, update (approval/rejection), and deletion access.
  - **State Machine:** Strictly enforces one-way review lifecycle (`PENDING` -> `APPROVED` or `REJECTED`). Terminal states cannot be accidentally mutated without administrative action.
  - **Audit Logging:** Every approval, rejection, and deletion operation emits an audit log event (`APPROVE_PRO_REQUEST`, `REJECT_PRO_REQUEST`, `DELETE_PRO_REQUEST`) to `/auditLogs`.

#### Schema Specifications:
- `/pro_requests/{requestId}`:
  | Field Name | Type | Classification | Description |
  |---|---|---|---|
  | `requestId` | String | REQUIRED | Unique Firestore request document ID |
  | `userId` | String | REQUIRED | Owner user account UID |
  | `userEmail` | String | REQUIRED | Owner user email address |
  | `userName` | String | OPTIONAL | User display name |
  | `requestedPlan` | String | REQUIRED | Tier requested (`"pro"`, `"vip"`, etc.) |
  | `requestedDuration` | String | REQUIRED | Duration requested (`"1 month"`, `"1 year"`, etc.) |
  | `paymentMethod` | String? | OPTIONAL | Payment channel used (e.g. `"Bank"`, `"Cash"`, etc.) |
  | `paymentProofUrl` | String? | OPTIONAL | Image URL or link to payment receipt proof |
  | `userNote` | String | OPTIONAL | User note or transaction reference identifier |
  | `status` | String | DEFAULTED | Current review status: `"PENDING"`, `"APPROVED"`, `"REJECTED"` |
  | `createdAt` | Long | SYSTEM-GENERATED | Epoch millis of request submission |
  | `updatedAt` | Long | SYSTEM-GENERATED | Epoch millis of last update |
  | `reviewedAt` | Long? | ADMIN-MUTABLE | Epoch millis when admin reviewed request |
  | `reviewedBy` | String? | ADMIN-MUTABLE | Admin UID who approved/rejected |
  | `reviewedByEmail` | String? | ADMIN-MUTABLE | Admin email address |
  | `rejectionReason` | String? | ADMIN-MUTABLE | Reason provided upon rejection |
  | `adminNote` | String? | ADMIN-MUTABLE | Optional internal admin note |

---

## 3. Account-Level Bans vs Feature Restrictions (Phase C1 Authority)

### 3.1 Distinct Semantics & Operational Separation:
1. **Global Account Ban (`isBanned`, `banReason`, `banExpiresAt`):**
   - Authoritative security boundary defined in Firestore Rules:
     Standard users are strictly prohibited from writing or modifying these fields.
   - When active (`isBanned == true` and `banExpiresAt == null || banExpiresAt > now`), the user account is suspended from all platform access.
   - Permanent Ban: `banExpiresAt == null`.
   - Temporary Ban: `banExpiresAt == epoch_millis` (where `epoch_millis > now`).
   - **Automatic Expired-Ban Interpretation:** If `banExpiresAt != null && banExpiresAt <= now`, the ban duration has lapsed. The system computes `isAccountBanned = false` and `isBanExpired = true`. The Admin UI surfaces this status as "BAN EXPIRED" with options to lift the record or renew suspension.
   - **Decoupled State Mutation:** Account-level bans modify only `isBanned`, `banReason`, `banExpiresAt`, `isActive`, and `updatedAt`. They NEVER overwrite or reset individual feature permissions (`canWatch`, `canDownload`, etc.), ensuring prior restriction configurations are safely preserved upon unbanning.
2. **Feature Restrictions (`canWatch`, `canDownload`, `canChat`, `canStory`, `canP2P`, `canComment`, `canUpload`, `canRequest`):**
   - Fine-grained restriction flags that selectively block specific features (streaming, offline caching, chat rooms, comments, uploading, requests) without disabling the user account.
   - Explicitly decoupled from Global Account Ban: Toggling a feature restriction DOES NOT mark the account as banned.
   - Dual-written with negative compatibility flags (`watchBan`, `downloadBan`, `chatBan`, `storyBan`, `p2pBan`) for legacy consumer app protection.

### 3.2 Phase C1 Architecture & Implementation:
- Conflation resolved: `DashboardViewModel.kt`, `DashboardScreen.kt`, and `UserDetailScreen.kt` separate global bans (`user.isAccountBanned`) from feature restrictions (`user.hasFeatureRestrictions`).
- Admin UI features dedicated duration selectors (24h, 3d, 7d, 30d, permanent, custom), predefined policy reasons, custom notes, and unban restoration with full audit logging (`BAN_USER`, `UNBAN_USER`).
- Safe confirmation guards in both Dashboard and User Details prevent destructive one-tap ban/unban mutations.

---

## 4. Subscription Contract, Dual-Write Reconciliation & State Machine (Phase C1)

### 4.1 Authoritative Subscription Field Matrix:
| Field Name | Type | Canonical? | Admin Writes? | Admin Reads? | Users App Reads? | Rules Protected? | Classification |
|---|---|---|---|---|---|---|---|
| `isPremium` | Boolean | **YES** | Yes | Yes | Yes | Yes | **CANONICAL ACTIVE** |
| `subscriptionTier` | String | **YES** | Yes | Yes | Yes | Yes | **CANONICAL ACTIVE** (`"free"`, `"pro"`, `"vip"`) |
| `subscriptionStatus` | String | **YES** | Yes | Yes | Yes | Yes | **CANONICAL ACTIVE** (`"active"`, `"free"`, `"expired"`, `"canceled"`) |
| `subscriptionExpiresAt` | Long? | **YES** | Yes | Yes | Yes | Yes | **CANONICAL ACTIVE** (epoch millis or null for lifetime) |
| `isPro` | Boolean | No | Yes (compat) | Yes (fallback) | Yes | Yes | **COMPATIBILITY MIRROR** (mirrors `isPremium`) |
| `plan` | String? | No | Yes (compat) | Yes (fallback) | Yes | Yes | **LEGACY COMPATIBILITY** (mirrors `subscriptionTier`) |
| `proPlan` | String? | No | Yes (compat) | Yes (fallback) | Yes | Yes | **COMPATIBILITY MIRROR** (mirrors `subscriptionTier`) |
| `proExpiresAt` | Long? | No | Yes (compat) | Yes (fallback) | Yes | Yes | **COMPATIBILITY MIRROR** (mirrors `subscriptionExpiresAt`) |

### 4.2 Authoritative Admin Single Write Model:
To prevent fragmentation and consumer drift, the Admin App enforces **one unified write model** across all subscription operations:
- When granting/extending:
  - Canonical: `isPremium = true`, `subscriptionTier = tier`, `subscriptionStatus = "active"`, `subscriptionExpiresAt = expiresAt`
  - Compatibility mirrors: `isPro = true`, `plan = tier`, `proPlan = tier`, `proExpiresAt = expiresAt`
- When revoking:
  - Canonical: `isPremium = false`, `subscriptionTier = "free"`, `subscriptionStatus = "free"`, `subscriptionExpiresAt = null`
  - Compatibility mirrors: `isPro = false`, `plan = "free"`, `proPlan = "free"`, `proExpiresAt = null`
- Action audit logging: `GRANT_SUBSCRIPTION` or `REVOKE_SUBSCRIPTION` recorded in `/auditLogs`.

### 4.3 Subscription State Machine:
The application deterministically computes subscription state without relying solely on raw boolean flags:
1. **`FREE`**: `subscriptionTier == "free"` or `!isPremium` (and not active pro).
2. **`ACTIVE_PRO`**: (`isPremium == true` or `subscriptionTier != "free"`) AND (`subscriptionExpiresAt == null` [lifetime] or `subscriptionExpiresAt > now`).
3. **`EXPIRED_PRO`**: `subscriptionExpiresAt != null` AND `subscriptionExpiresAt <= now` AND (`isPremium == true` or `subscriptionTier != "free"`).
   - **Deterministic Expiration Rule:** Even if a user document contains `isPremium == true`, if `subscriptionExpiresAt <= now`, the system authoritative state is `EXPIRED_PRO`.
   - Historical subscription tier and expiration timestamps are preserved (never silently deleted) until an explicit Admin action or subscription grant occurs.

---

## 5. Security & Safety Invariants

1. **NO Dynamic Code Loading (DCL):**
   The application strictly avoids `DexClassLoader`, `PathClassLoader`, `dalvik.system.*`, reflection plugin loading, and runtime script execution.
2. **NO SSL / Hostname Verification Bypass:**
   All network requests through OkHttp enforce standard system TLS and certificate verification. No `TrustAll`, `UnsafeHostnameVerifier`, or `handler.proceed()` bypasses exist.
3. **No Client API Secrets:**
   Cloudinary uploads operate exclusively through unsigned upload presets (`cinestream_unsigned`). The Cloudinary API Secret is NEVER stored or referenced in code or configuration.
