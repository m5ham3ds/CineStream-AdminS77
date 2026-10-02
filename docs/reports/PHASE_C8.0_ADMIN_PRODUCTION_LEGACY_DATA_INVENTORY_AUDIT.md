# PHASE C8.0 — ADMIN APP
# PRODUCTION LEGACY DATA INVENTORY & MIGRATION READINESS AUDIT

**PROJECT:** CineStream Admin App  
**ECOSYSTEM:** CineStream Admin App + CineStream Users App (Shared Google Cloud Firestore)  
**PHASE TYPE:** FORENSIC AUDIT ONLY — STRICTLY READ-ONLY — ZERO MUTATIONS  
**DATE:** 2026-09-29  

---

## 1. EXECUTIVE SUMMARY

This forensic audit evaluates the real dependency and migration readiness of historical, legacy, and canonical Firestore collections and documents from the perspective of the **CineStream Admin App** (Management Plane).

### Primary Target Paths Audited:
1. `/config/app` (Modern canonical system configuration)
2. `/config/global` (Legacy fallback configuration)
3. `/managed_extensions` (Modern canonical scraper definitions catalog)
4. `/extensions` (Historical APK-based scraper extensions catalog)
5. `/extension_updates` (Historical scraper update tracking collection)
6. `/app_updates` (Modern canonical application OTA release collection)

### Key Audit Findings:
1. **Live Production Access Limitation:** The execution container is an isolated sandbox environment. It does not possess live production Google Cloud credentials, service account keys, or external network write/read connectivity to live production Firestore. As mandated by Step 2 and the Phase Stop Conditions, the production data presence must be recorded strictly as **`PRODUCTION DATA NOT VERIFIED`**.
2. **`/config/app` vs `/config/global`:** 
   - `/config/app` is the active, canonical configuration surface. It is observed via a real-time snapshot listener and written authoritatively whenever administrative updates occur.
   - `/config/global` remains an **ACTIVE FALLBACK READ**. `AdminRepository.getAppConfig()` queries `/config/global` if `/config/app` does not exist or yields null data. Writes to `/config/global` have been discontinued on the Admin side.
3. **`/managed_extensions` vs `/extensions`:**
   - `/managed_extensions` is the active canonical catalog. It enforces pure configuration datastore semantics (zero dynamic code loading, zero APKs, SSRF validation).
   - `/extensions` is actively bound via a **DUAL-READ MERGE** and **DUAL-WRITE** synchronization in `ManagedExtensionRepository.kt`, as well as standalone legacy CRUD methods in `AdminRepository.kt` consumed by `ExtensionsViewModel.kt` and `ExtensionsScreen.kt`. Immediate deletion of `/extensions` would break legacy scrapers that have not been copied to `managed_extensions`.
4. **`/extension_updates`:**
   - Retained only as a constant in `FirebaseContract.kt` (`LEGACY_EXTENSION_UPDATES = "extension_updates"`) and in `firestore.rules`.
   - Has **zero** active repository callers, **zero** ViewModel actions, and **zero** UI dependencies.
5. **`/app_updates`:**
   - Active canonical OTA releases collection. `AdminRepository` provides real-time ordering and CRUD operations; `ConfigViewModel.updateOta()` writes release records to `/app_updates/{versionCode}`.
6. **Zero Mutations Attestation:** No Kotlin code, Java, XML, Compose UI, Gradle files, Models, ViewModels, Repositories, Firebase contracts, Firestore rules, or database records were modified, created, or deleted during this phase.

**Audit Verdict:** **`PRODUCTION STATE NOT VERIFIED`**

---

## 2. ENVIRONMENT

- **Management Plane Application:** CineStream Admin App
- **Android Package / Namespace:** `com.example`
- **Application ID:** `com.aistudio.cinestreamadmin.oizucj`
- **Target SDK / Min SDK:** Target SDK 36 (Android 16), Min SDK 24 (Android 7.0)
- **Programming Language:** Kotlin (100%)
- **UI Framework:** Jetpack Compose with Material Design 3 (M3)
- **Local Tooling Executed:**
  - Gradle (Local JVM test execution: `:app:testDebugUnitTest`)
  - Gradle (Local debug APK compilation: `:app:assembleDebug`)
  - Python 3 (`scripts/static_security_scan.py`)
- **Isolation Boundary:** Cloud container execution sandbox without live external production cloud credentials.

---

## 3. FIREBASE PROJECT IDENTITY

Inspection of the Android configuration file `/app/google-services.json` yields the following parameters:

| Configuration Parameter | Registered Value | Classification |
| :--- | :--- | :--- |
| **Configuration Source** | `/app/google-services.json` | STATICALLY VERIFIED |
| **Project ID** | `remixed-project-id` | CONTAINER PLACEHOLDER |
| **Project Number** | `1234567890` | CONTAINER PLACEHOLDER |
| **Mobile SDK App ID** | `1:1234567890:android:remixedappid` | CONTAINER PLACEHOLDER |
| **Package Name** | `com.aistudio.cinestreamadmin.oizucj` | STATICALLY VERIFIED |
| **Client Type** | `3` (Web Client) | STATICALLY VERIFIED |
| **Client ID** | `1234567890-examplewebclientid.apps.googleusercontent.com` | CONTAINER PLACEHOLDER |
| **API Key** | `remixed-api-key` | CONTAINER PLACEHOLDER |
| **Firestore Database ID** | `(default)` | STATICALLY VERIFIED |
| **Environment** | Local Sandbox / CI Build Container | STATICALLY VERIFIED |

**Analysis:**  
The project identity in the container repository contains placeholder tokens (`remixed-project-id`, `remixed-api-key`). Neither `.env` nor `.env.example` contains live Google Cloud production credentials. Therefore, project identity for the external production environment cannot be authenticated from within this container.

---

## 4. PRODUCTION ACCESS VERIFICATION

In accordance with Phase C8.0 Step 2:

- **Authentication Method:** N/A — No service account key (`GOOGLE_APPLICATION_CREDENTIALS`), OAuth refresh token, or production Firebase Auth credentials are present in the environment.
- **Access Status:** **DISCONNECTED / UNAVAILABLE**
- **Firestore Live Read Capability:** **NONE** (External production project cannot be queried without credentials).
- **Mandated Stop / Verification Flag:** **`PRODUCTION DATA NOT VERIFIED`**

Per Hard Rule #1 and Step 2, permissions were not broadened, no network bypass was attempted, and no fake production data was generated.

---

## 5. ADMIN APP SOURCE CODE AUDIT

Every path reference across the entire CineStream Admin codebase was analyzed down to the file and line number:

### 5.1 `/config/app`
- **References:**
  - `FirebaseContract.kt:27-31`: Document constant `FirebaseConfigDocs.APP = "app"`, Collection `FirebaseCollections.CONFIG = "config"`.
  - `AdminRepository.kt:44`: `private val configCollection get() = firestore.collection(FirebaseCollections.CONFIG)`.
  - `AdminRepository.kt:520`: `configCollection.document(FirebaseConfigDocs.APP).addSnapshotListener(...)`.
  - `AdminRepository.kt:562`: `configCollection.document(FirebaseConfigDocs.APP).set(updatesWithTimestamp, SetOptions.merge())`.
  - `AdminRepository.kt:759`: `configCollection.document(FirebaseConfigDocs.APP).get(Source.SERVER).await()`.
  - `ConfigViewModel.kt:467`: Calls `repository.getAppConfig()`.
  - `ConfigViewModel.kt:554, 592, 657, 689, 703`: Calls `repository.updateAppConfig(...)`.
  - `GlobalConfigScreen.kt`, `ConfigScreen.kt`, `AppUpdatesScreen.kt`: UI screens consuming `/config/app`.
- **Classification:** **ACTIVE READ**, **ACTIVE WRITE**

### 5.2 `/config/global`
- **References:**
  - `FirebaseContract.kt:28, 126`: Document constant `FirebaseConfigDocs.GLOBAL = "global"`.
  - `AdminRepository.kt:539-548`: Fallback read inside `getAppConfig()`:
    ```kotlin
    configCollection.document(FirebaseConfigDocs.GLOBAL).get().addOnSuccessListener { globalSnap ->
        val globalConfig = try {
            globalSnap.toObject(AppConfig::class.java) ?: AppConfig()
        } catch (e: Exception) { AppConfig() }
        trySend(globalConfig)
    }.addOnFailureListener {
        trySend(AppConfig())
    }
    ```
  - `AdminRepository.kt:573-574`: Legacy aliases `getGlobalConfig()` and `updateGlobalConfig()`, which redirect internally to `getAppConfig()` and `updateAppConfig()`.
- **Classification:** **ACTIVE FALLBACK READ**, **CONSTANT ONLY** (writes discontinued)

### 5.3 `/managed_extensions`
- **References:**
  - `FirebaseContract.kt:63-68`: Collection constant `FirebaseCollections.MANAGED_EXTENSIONS = "managed_extensions"`.
  - `ManagedExtensionRepository.kt:134`: `private val managedCollection get() = firestore.collection(FirebaseCollections.MANAGED_EXTENSIONS)`.
  - `ManagedExtensionRepository.kt:243, 268`: Real-time listener in `getAllManagedExtensions()`.
  - `ManagedExtensionRepository.kt:295`: Primary document read in `getManagedExtension(id)`.
  - `ManagedExtensionRepository.kt:343`: Authoritative write in `saveManagedExtension(...)`.
  - `ManagedExtensionRepository.kt:367, 387, 407`: Partial updates for `status`, `priority`, and `baseUrl`.
  - `ManagedExtensionRepository.kt:422`: Deletion in `deleteManagedExtension(id)`.
  - `ManagedExtensionsViewModel.kt`: Binds to `repository.getAllManagedExtensions()`.
  - `DashboardViewModel.kt:106`: Consumes `extensionRepository.getAllManagedExtensions()` for `extensionMetrics`.
  - `ManagedExtensionsScreen.kt`: Primary administrative UI console.
- **Classification:** **ACTIVE READ**, **ACTIVE WRITE**

### 5.4 `/extensions`
- **References:**
  - `FirebaseContract.kt:55-59`: Collection constant `FirebaseCollections.LEGACY_EXTENSIONS = "extensions"`.
  - `AdminRepository.kt:47`: `private val extensionsCollection get() = firestore.collection(FirebaseCollections.LEGACY_EXTENSIONS)`.
  - `AdminRepository.kt:750`: `extensionsCollection.get(Source.SERVER).await()`.
  - `AdminRepository.kt:797`: Real-time snapshot listener in `getExtensions()`.
  - `AdminRepository.kt:868`: Standalone write in `saveExtension(extension)`.
  - `AdminRepository.kt:888`: Standalone deletion in `deleteExtension(extensionId)`.
  - `ManagedExtensionRepository.kt:135`: `private val legacyCollection get() = firestore.collection(FirebaseCollections.LEGACY_EXTENSIONS)`.
  - `ManagedExtensionRepository.kt:269`: Combined into `getAllManagedExtensions()` via dual-read merge.
  - `ManagedExtensionRepository.kt:303`: Secondary fallback read in `getManagedExtension(id)`.
  - `ManagedExtensionRepository.kt:345`: Dual-write mirror in `saveManagedExtension(...)`.
  - `ManagedExtensionRepository.kt:369, 389, 409`: Dual-write mirror in `updateStatus()`, `updatePriority()`, and `updateBaseUrl()`.
  - `ManagedExtensionRepository.kt:424`: Dual-delete mirror in `deleteManagedExtension(id)`.
  - `ExtensionsViewModel.kt`: Consumes `AdminRepository.getExtensions()`, `saveExtension()`, `deleteExtension()`.
  - `ExtensionsScreen.kt`, `ExtensionsUpdatesScreen.kt`: UI screens managing `/extensions`.
- **Classification:** **ACTIVE READ (DUAL READ / MERGE)**, **ACTIVE WRITE (DUAL WRITE)**, **LEGACY READ**

### 5.5 `/extension_updates`
- **References:**
  - `FirebaseContract.kt:60`: Constant `FirebaseCollections.LEGACY_EXTENSION_UPDATES = "extension_updates"`.
  - `CineStreamAdminLogicTest.kt:207, 710`: Contract constant assertions.
  - Repositories (`AdminRepository`, `ManagedExtensionRepository`): **ZERO CALLS**.
  - ViewModels (`ViewModels.kt`, `ManagedExtensionsViewModel.kt`): **ZERO CALLS**.
  - UI Screens: **ZERO CALLS**.
- **Classification:** **CONSTANT ONLY**, **DEAD / UNUSED IN RUNTIME**

### 5.6 `/app_updates`
- **References:**
  - `FirebaseContract.kt:71-75`: Collection constant `FirebaseCollections.APP_UPDATES = "app_updates"`.
  - `AdminRepository.kt:49`: `private val appUpdatesCollection get() = firestore.collection(FirebaseCollections.APP_UPDATES)`.
  - `AdminRepository.kt:584`: Real-time listener in `getAppUpdates()` ordered by `versionCode` DESC.
  - `AdminRepository.kt:616`: Authoritative write in `createOrUpdateAppUpdate(...)`.
  - `AdminRepository.kt:637`: Deletion in `deleteAppUpdate(updateId)`.
  - `ConfigViewModel.kt:609`: Writes `AppUpdate` entity when publishing an OTA release.
  - `Models.kt:178-195`: Canonical `AppUpdate` data class.
  - `AppUpdatesScreen.kt`: Primary UI surface for release management.
- **Classification:** **ACTIVE READ**, **ACTIVE WRITE**

---

## 6. `/config/app` PRODUCTION AUDIT

- **Existence in Production:** `NOT VERIFIED` (Live database not accessible from container)
- **Document ID:** `app`
- **Admin App Consumption:**
  - Primary read: `AdminRepository.getAppConfig()` (real-time listener)
  - Primary write: `AdminRepository.updateAppConfig()` (invoked by `ConfigViewModel`)
- **Schema Defined in `Models.kt:133-155` (`AppConfig`):**

| Field Name | Type | Purpose | Sensitive? |
| :--- | :--- | :--- | :---: |
| `maintenanceEnabled` | Boolean | Global kill-switch blocking playback | Public Client Safe |
| `maintenanceTitle` | String | Maintenance banner title | Public Client Safe |
| `maintenanceMessage` | String | Detailed user notice | Public Client Safe |
| `minimumVersionCode` | Int | Forced upgrade cutoff version | Public Client Safe |
| `latestVersionCode` | Int | Latest published OTA build | Public Client Safe |
| `latestVersionName` | String | SemVer release name (e.g., "1.2.0") | Public Client Safe |
| `apkUrl` | String | Direct HTTPS APK download link | Public Client Safe |
| `apkSha256` | String | Cryptographic integrity hash | Public Client Safe |
| `mandatoryUpdate` | Boolean | Mandatory update enforcement toggle | Public Client Safe |
| `releaseNotes` | String | Changelog and release notes | Public Client Safe |
| `providersJson` | String | Dynamic scraper endpoints/JSON | Public Client Safe |
| `defaultOfflineDays` | Int | Default DRM offline playback quota | Public Client Safe |
| `defaultForcedAds` | Int | Default forced rewarded ads threshold | Public Client Safe |
| `cloudinaryCloudName` | String | Cloudinary storage bucket | Public Client Safe |
| `cloudinaryUploadPreset`| String | Unsigned client upload preset | Public Client Safe |
| `updatedAt` | Long | Epoch timestamp of last update | Public Client Safe |

*Security Confirmation:* `/config/app` contains zero backend admin secrets, zero private keys, and zero master credentials.

---

## 7. `/config/global` PRODUCTION AUDIT

- **Existence in Production:** `NOT VERIFIED` (Live database not accessible from container)
- **Document ID:** `global`
- **Admin App Consumption:**
  - `AdminRepository.kt:539-548` executes an explicit **ACTIVE FALLBACK READ**.
  - If a production instance is cold-booted and `/config/app` does not exist, the repository queries `/config/global` to hydrate initial system settings.
- **Meaningful Data Presence:** Unknown in production. Cannot be assumed empty.
- **Admin Writes:** Discontinued. All writes route authoritatively to `/config/app`.
- **Classification:** **ACTIVE FALLBACK READ**

---

## 8. CONFIG COMPARISON (`/config/global` vs `/config/app`)

Because live production data could not be inspected, static schema reconciliation was conducted:

| Comparison Dimension | `/config/global` (Legacy) | `/config/app` (Canonical) | Compatibility State |
| :--- | :--- | :--- | :--- |
| **Document Path** | `/config/global` | `/config/app` | Separate document paths in same collection |
| **Object Mapping** | `AppConfig` class | `AppConfig` class | Identical model schema |
| **Maintenance Flags** | Supported | Supported | 100% Compatible |
| **OTA Release Flags** | Supported | Supported | 100% Compatible |
| **DRM & Monetization** | Supported | Supported | 100% Compatible |
| **Media Storage** | Supported | Supported | 100% Compatible |
| **Production Divergence** | Unknown | Unknown | **CONFLICT REQUIRES HUMAN REVIEW** (if values differ in production) |

**Decision Rule:** Neither document should be deleted or overwritten automatically without live verification of values.

---

## 9. `/managed_extensions` PRODUCTION AUDIT

- **Existence in Production:** `NOT VERIFIED` (Live database not accessible from container)
- **Document Path Structure:** `/managed_extensions/{extensionId}`
- **Admin App Consumers:**
  - `ManagedExtensionRepository` (Primary management repository)
  - `ManagedExtensionsViewModel` (Reactive UI state holder)
  - `DashboardViewModel` (Extension metrics computation)
  - `ManagedExtensionsScreen` (Catalog UI)
- **Schema Defined in `ManagedExtension.kt:76-94` (`ManagedExtension`):**

| Field Name | Type | Description |
| :--- | :--- | :--- |
| `extensionId` | String | Canonical alphanumeric unique identifier |
| `scraperKey` | String | Scraper mapping key for client bundled class |
| `name` | String | Display name of the provider/scraper |
| `description` | String | Detailed provider description |
| `baseUrl` | String | HTTPS root URL (SSRF validated) |
| `searchUrl` | String | Formatted search query endpoint template |
| `runtimeApiVersion` | Int | Protocol version (default: 1) |
| `definitionVersion` | Int | Scraper definition schema version |
| `minAppVersionCode` | Int | Minimum client version required |
| `status` | String | `ACTIVE`, `MAINTENANCE`, `DISABLED`, `DEPRECATED` |
| `priority` | Int | Execution order priority (higher = first) |
| `capabilities` | List<String> | `SEARCH`, `DETAILS`, `EPISODES`, `SERVER_DISCOVERY`, `VIDEO_EXTRACTION`, etc. |
| `contentTypes` | List<String> | `MOVIE`, `SERIES`, `ANIME`, `ASIAN_DRAMA` |
| `enabled` | Boolean | Master operational toggle |
| `createdAt` | Long | Creation epoch timestamp |
| `updatedAt` | Long | Last modification epoch timestamp |

*Architecture Invariant:* Pure configuration only. Contains ZERO executable bytecode, APK downloads, DexClassLoader invocations, or arbitrary JavaScript.

---

## 10. `/extensions` PRODUCTION AUDIT

- **Existence in Production:** `NOT VERIFIED` (Live database not accessible from container)
- **Document Path Structure:** `/extensions/{extensionId}`
- **Admin App Consumption:**
  - **Dual-Read Merge:** `ManagedExtensionRepository.getAllManagedExtensions()` reads both `/managed_extensions` and `/extensions`, merging the result sets in memory (legacy entries are preserved and overlaid by managed extension definitions).
  - **Dual-Write:** Any update or creation in `ManagedExtensionRepository` writes to `/managed_extensions` and mirrors to `/extensions`.
  - **Standalone Legacy CRUD:** `AdminRepository` exposes `getExtensions()`, `saveExtension()`, `deleteExtension()`, consumed by `ExtensionsViewModel` and `ExtensionsScreen`.
- **Legacy Schema (`ExtensionItem` in `Models.kt:160-175`):**
  - Fields: `id`, `name`, `packageName`, `versionCode`, `versionName`, `apkUrl`, `apkSha256`, `sha256`, `minAppVersionCode`, `enabled`, `mandatory`, `releaseNotes`, `createdAt`, `updatedAt`.
- **Classification:** **ACTIVE READ (DUAL READ / MERGE)**, **ACTIVE WRITE (DUAL WRITE)**, **LEGACY READ**

---

## 11. EXTENSION MATCHING (`/extensions` vs `/managed_extensions`)

Because production Firestore data is unverified in this isolated environment, document-by-document matching results:

- **Matching State:** **`NOT DETERMINABLE (PRODUCTION DATA NOT ACCESSIBLE)`**
- **Runtime Reconciliation Logic:**
  - Handled in `ManagedExtensionRepository.kt:267-277`:
    ```kotlin
    val mergedMap = HashMap<String, ManagedExtension>(legacyMap)
    managedMap.forEach { (id, ext) ->
        mergedMap[id] = ext
    }
    ```
  - If a document exists in both collections under the same ID, the canonical `/managed_extensions` version supersedes the legacy `/extensions` record.

---

## 12. EXTENSION FIELD COMPATIBILITY

Evaluating whether legacy configuration can theoretically be mapped to `managed_extensions`:

| Legacy Field (`ExtensionItem`) | Canonical Field (`ManagedExtension`) | Compatibility Analysis |
| :--- | :--- | :--- |
| `id` | `extensionId` / `id` | Direct mapping |
| `name` | `name` | Direct mapping |
| `apkUrl` | `baseUrl` (if website) | **Incompatible if APK binary link** |
| `packageName` | `scraperKey` | Requires mapping to internal bundled scraper class |
| `versionCode` | `definitionVersion` | Partial mapping |
| `enabled` | `enabled` / `status` | Direct mapping (`ACTIVE` vs `DISABLED`) |
| *Missing* | `capabilities` | **MIGRATION BLOCKER** (Requires defaults or human input) |
| *Missing* | `contentTypes` | **MIGRATION BLOCKER** (Requires defaults or human input) |
| *Missing* | `searchUrl` | **MIGRATION BLOCKER** (Requires defaults or human input) |
| `apkSha256` | *None* | Deprecated (Dynamic code loading forbidden) |

**Conclusion:** Pure APK-based scraper entries cannot be automatically migrated without human review and bundling corresponding audited Kotlin scraper classes in the Users App.

---

## 13. `/extension_updates` PRODUCTION AUDIT

- **Existence in Production:** `NOT VERIFIED` (Live database not accessible from container)
- **Admin App Source Analysis:**
  - Repository calls: **0**
  - Listeners: **0**
  - ViewModel actions: **0**
  - Screen dependencies: **0**
  - Defined in: `FirebaseContract.kt:60` and `firestore.rules:164`
- **Classification:** **CONSTANT ONLY / UNUSED IN RUNTIME**

---

## 14. `/app_updates` PRODUCTION AUDIT

- **Existence in Production:** `NOT VERIFIED` (Live database not accessible from container)
- **Admin App Source Analysis:**
  - Real-time listener: `AdminRepository.getAppUpdates()` (`appUpdatesCollection.orderBy("versionCode", Query.Direction.DESCENDING)`)
  - Creation / Update: `AdminRepository.createOrUpdateAppUpdate(update)`
  - Deletion: `AdminRepository.deleteAppUpdate(updateId)`
  - Publishing Trigger: `ConfigViewModel.updateOta()`
  - UI Management: `AppUpdatesScreen.kt`
- **Classification:** **CANONICAL ACTIVE**

---

## 15. ADMIN RUNTIME DEPENDENCY MATRIX

| Path | Source Usage | Production Data | Runtime State | Dependency Classification |
| :--- | :--- | :--- | :--- | :--- |
| `/config/app` | Snapshot Listener & Set | NOT VERIFIED | RUNTIME-USED (Primary) | **CANONICAL ACTIVE** |
| `/config/global` | Fallback Read on boot | NOT VERIFIED | RUNTIME-USED (Fallback) | **ACTIVE FALLBACK READ** |
| `/managed_extensions` | Snapshot Listener & CRUD | NOT VERIFIED | RUNTIME-USED (Primary) | **CANONICAL ACTIVE** |
| `/extensions` | Dual-read merge & Dual-write | NOT VERIFIED | RUNTIME-USED (Merged) | **ACTIVE LEGACY COMPATIBILITY** |
| `/extension_updates` | Constant only | NOT VERIFIED | UNUSED | **CONSTANT ONLY (DEAD RUNTIME)** |
| `/app_updates` | Snapshot Listener & CRUD | NOT VERIFIED | RUNTIME-USED (Primary) | **CANONICAL ACTIVE** |

---

## 16. FIRESTORE RULES ANALYSIS

Static analysis of `/firestore.rules`:

```javascript
// --- App & Global System Configuration ---
match /config/{configId} {
  allow read: if isAuthenticated();
  allow write: if isAdmin();
}

// --- Managed Extensions (Canonical v1) ---
match /managed_extensions/{extensionId} {
  allow read: if isAuthenticated();
  allow write: if isAdmin();
  match /{subcollection}/{docId} {
    allow read: if isAuthenticated();
    allow write: if isAdmin();
  }
}

// --- Legacy Extensions Management (Deprecation phase) ---
match /extensions/{extensionId} {
  allow read: if isAuthenticated();
  allow write: if isAdmin();
}

match /extension_updates/{updateId} {
  allow read: if isAuthenticated();
  allow write: if isAdmin();
}

// --- App Updates ---
match /app_updates/{updateId} {
  allow read: if isAuthenticated();
  allow write: if isAdmin();
}
```

### Path Rule Status Table:

| Target Path | Read Rule | Write Rule | Rule Status |
| :--- | :--- | :--- | :---: |
| `/config/{configId}` | `isAuthenticated()` | `isAdmin()` | **EXPLICITLY ALLOWED** |
| `/managed_extensions/{id}` | `isAuthenticated()` | `isAdmin()` | **EXPLICITLY ALLOWED** |
| `/extensions/{id}` | `isAuthenticated()` | `isAdmin()` | **EXPLICITLY ALLOWED** |
| `/extension_updates/{id}` | `isAuthenticated()` | `isAdmin()` | **EXPLICITLY ALLOWED** |
| `/app_updates/{id}` | `isAuthenticated()` | `isAdmin()` | **EXPLICITLY ALLOWED** |
| Unspecified collections | Denied | Denied | **CATCH-ALL DENIED** (`match /{document=**}`) |

---

## 17. SHARED USERS-APP RISK

Because the Google Cloud Firestore datastore is shared across both the Admin App and the Users App:

1. **Legacy Client Versions:** Previous architectural audits confirm that older installed builds of the CineStream Users App query `/config/global` for maintenance status and `/extensions` for scraper definitions.
2. **Shared Legacy Dependency:**
   - `/config/global`: **`SHARED LEGACY DEPENDENCY`**
   - `/extensions`: **`SHARED LEGACY DEPENDENCY`**
3. **No Unilateral Deletion:** Deleting `/config/global` or `/extensions` based solely on Admin App modernization would cause unmigrated Users App installations to experience cold-start failures or lose scraper connectivity.

---

## 18. MIGRATION READINESS

| Path | Data Migration Needed? | Code Fallback in Place? | Migration Readiness State |
| :--- | :---: | :---: | :--- |
| `/config/app` | No (Destination) | Yes | Ready for Production Use |
| `/config/global` | **YES** | Yes (Fallback Read) | **MIGRATION REQUIRED** before removal |
| `/managed_extensions` | No (Destination) | Yes | Ready for Production Use |
| `/extensions` | **YES** | Yes (Dual-Read Merge) | **MIGRATION REQUIRED** before removal |
| `/extension_updates` | No | N/A | **READY FOR REMOVAL FROM RULES/CONTRACT** |
| `/app_updates` | No | Yes | Ready for Production Use |

---

## 19. DELETION READINESS

In accordance with Phase C8.0 Step 17, each legacy path is assigned exactly one standardized classification:

1. **`/config/global`:** **`KEEP — LEGACY COMPATIBILITY`**  
   *(Removal requires verified copy of production keys to `/config/app` and monitoring of legacy client traffic).*
2. **`/extensions`:** **`KEEP — LEGACY COMPATIBILITY`**  
   *(Removal requires verifying that all production scrapers exist in `/managed_extensions`).*
3. **`/extension_updates`:** **`PRODUCTION STATE NOT VERIFIED`**  
   *(While the code is completely unused, final rule deletion must verify whether production documents exist).*

---

## 20. TESTS

The existing test suite was executed without modification:

- **Command Executed:** `gradle :app:testDebugUnitTest`
- **Total Tests:** **77**
- **Passed:** **77**
- **Failed:** **0**
- **Skipped:** **0**
- **Ignored:** `IGNORED: NOT REPORTED BY TEST RUNNER`
- **Test Classes Breakdown:**
  - `com.example.CineStreamAdminLogicTest`: 73 tests (Passed: 73)
  - `com.example.ExampleRobolectricTest`: 2 tests (Passed: 2)
  - `com.example.ExampleUnitTest`: 1 test (Passed: 1)
  - `com.example.FirebaseTest`: 1 test (Passed: 1)
- **Evidence Level:** **`UNIT TEST VERIFIED`** (Distinct from `PRODUCTION VERIFIED`)

---

## 21. BUILD

The build was verified using Gradle:

- **Command Executed:** `gradle assembleDebug`
- **Build Status:** **BUILD PASS**
- **Result Output:** `BUILD SUCCESSFUL in 5s (29 actionable tasks: 29 up-to-date)`
- **Evidence Level:** **`STATICALLY VERIFIED`**

---

## 22. SECURITY SCAN

The automated static security scanner was executed:

- **Command Executed:** `python3 scripts/static_security_scan.py`
- **Total Checks:** **12**
- **Passed:** **12**
- **Failed:** **0**
- **Checks Summary:**
  1. `[PASS]` Firestore Rules Version 2 declared
  2. `[PASS]` Canonical Admin Authority `/admins/{uid}.enabled == true` verified
  3. `[PASS]` `users.role` is NEVER used in `isAdmin()`
  4. `[PASS]` 37 Sensitive User Fields protected against modification
  5. `[PASS]` Support Conversations Owner & Role Boundary enforced
  6. `[PASS]` Pro Requests PENDING Enforced & Reviewer Protected
  7. `[PASS]` Managed Extensions Admin-Only Write enforced
  8. `[PASS]` Audit Logs Exclusively Admin Access enforced
  9. `[PASS]` Strict Catch-All Deny present (`match /{document=**}`)
  10. `[PASS]` Least-Privilege Android Permissions (zero broad storage permissions)
  11. `[PASS]` Zero Hardcoded Cloud Admin Secrets in source code
  12. `[PASS]` Zero Dynamic Code Loading (DCL) or DexClassLoader calls

---

## 23. EVIDENCE CLASSIFICATION

Every finding in this audit is classified according to the mandatory taxonomy:

| Finding / Component | Scope | Verification Classification |
| :--- | :--- | :--- |
| **Admin Source Code Contracts** | AST & Kotlin References | **STATICALLY VERIFIED** |
| **Firestore Security Rules** | Rules Syntax & Match Boundaries | **STATICALLY VERIFIED** |
| **Android JVM Logic & State Machines** | 77 Unit Tests | **UNIT TEST VERIFIED** |
| **Static Security Scanner** | 12 Automated Security Checks | **STATICALLY VERIFIED** |
| **Local Debug APK Build** | Gradle assembleDebug | **STATICALLY VERIFIED** |
| **Live Production Firestore Document Counts** | Live Cloud Datastore | **NOT VERIFIED** |
| **Live Production Read/Write Operations** | Live Cloud Project | **NOT VERIFIED** |

---

## 24. BLOCKERS

1. **Isolated Build Container:** The build container does not hold credentials to connect directly to the live production Google Cloud Firestore database.
2. **Unknown Document Counts:** Document counts in `/config/global`, `/extensions`, and `/extension_updates` cannot be retrieved until read-only production credentials are provided.
3. **Users App Legacy Clients:** The proportion of active users running legacy client versions that depend on `/config/global` or `/extensions` cannot be determined from Admin code alone.

---

## 25. RECOMMENDED NEXT PHASE

### Proposed Phase C8.1: Live Production Read-Only Probing & Data Audit
1. Execute a dedicated read-only probe script using authenticated production credentials to retrieve:
   - Existence and field values of `/config/global` vs `/config/app`.
   - Document count and IDs in `/extensions` vs `/managed_extensions`.
   - Document count in `/extension_updates`.
2. Generate the reconciliation mapping between legacy and modern collections.
3. Only after Phase C8.1 verifies data counts should a migration script (Phase C8.2) be prepared.

---

## 26. FINAL VERDICT

Strictly selected from the allowed choices:

# **PRODUCTION STATE NOT VERIFIED**

**Reasoning:**  
While source code analysis, unit test suites, security scans, and build verifications are 100% complete and passing, direct read-only access to the live production Google Cloud Firestore project could not be established from this container environment. In strict adherence to Step 2 and the Phase Stop Conditions, the verdict must accurately state that the production data state has not been verified.

---

## FINAL ATTESTATION

**NO MODIFICATIONS WERE APPLIED.**

- No Kotlin, Java, XML, Gradle, or Compose UI files were modified.
- No Firestore Rules were modified or deployed.
- No Firebase configuration was changed.
- No Models, ViewModels, or Repositories were altered.
- No Production Firestore data was created, updated, copied, migrated, renamed, or deleted.

==================================================  
**END PHASE C8.0 — ADMIN APP AUDIT REPORT**  
==================================================  
