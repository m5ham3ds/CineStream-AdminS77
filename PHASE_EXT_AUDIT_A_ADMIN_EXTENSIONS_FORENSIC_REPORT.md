# PHASE EXT-AUDIT-A — ADMIN APP
# FULL EXTENSIONS FORENSIC AUDIT REPORT
# STRICTLY READ-ONLY — ZERO CODE / RULES / DATA MODIFICATIONS

**PROJECT:** CineStream Admin App (Management Plane)  
**ECOSYSTEM:** CineStream Admin App + CineStream Users App (Shared Google Cloud Firestore)  
**PHASE TYPE:** FORENSIC AUDIT ONLY — READ-ONLY  
**DATE:** 2026-09-29  
**AUDIT TARGETS:** `/managed_extensions`, `/extensions`, `/extension_updates`, `/config/app`, `/config/global`  

---

## 1. COMPLETE EXTENSION INVENTORY

The following table provides the exhaustive, forensic catalog of every file, class, function, model, dialog, and contract constant related to extensions and scraper management within the CineStream Admin App codebase:

| FILE | CLASS / OBJECT | FUNCTION / MEMBER | ROLE | FIREBASE PATH | OPERATION | STATUS |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `contract/FirebaseContract.kt:59` | `FirebaseCollections` | `LEGACY_EXTENSIONS` | Contract constant (`"extensions"`) | `/extensions` | Constant definition | CANONICAL CONTRACT |
| `contract/FirebaseContract.kt:60` | `FirebaseCollections` | `LEGACY_EXTENSION_UPDATES` | Contract constant (`"extension_updates"`) | `/extension_updates` | Constant definition | CANONICAL CONTRACT (DEAD) |
| `contract/FirebaseContract.kt:68` | `FirebaseCollections` | `MANAGED_EXTENSIONS` | Contract constant (`"managed_extensions"`) | `/managed_extensions` | Constant definition | CANONICAL CONTRACT |
| `contract/FirebaseContract.kt:31` | `FirebaseCollections` | `CONFIG` | Contract constant (`"config"`) | `/config/app`, `/config/global` | Constant definition | CANONICAL CONTRACT |
| `contract/FirebaseContract.kt:45` | `FirebaseCollections` | `AUDIT_LOGS` | Contract constant (`"auditLogs"`) | `/auditLogs` | Constant definition | CANONICAL CONTRACT |
| `models/ManagedExtension.kt:9-26` | `ManagedExtensionStatus` | `fromString(value)` | Lifecycle enum (ACTIVE, MAINTENANCE, DISABLED, DEPRECATED) | N/A (In-memory model) | Validation & fallback parsing | CANONICAL ACTIVE |
| `models/ManagedExtension.kt:31-47` | `ManagedExtensionCapabilities` | `ALL` | Capability flags (SEARCH, DETAILS, EPISODES, etc.) | Stored in `/managed_extensions/{id}.capabilities` | Model validation | CANONICAL ACTIVE |
| `models/ManagedExtension.kt:52-64` | `ManagedExtensionContentTypes` | `ALL` | Content type flags (MOVIE, SERIES, ANIME, ASIAN_DRAMA) | Stored in `/managed_extensions/{id}.contentTypes` | Model validation | CANONICAL ACTIVE |
| `models/ManagedExtension.kt:76-100` | `ManagedExtension` | Data class entity | Modern pure configuration scraper definition model | `/managed_extensions/{extensionId}` | Entity model definition | CANONICAL ACTIVE |
| `models/Models.kt:133-155` | `AppConfig` | `providersJson` | Dynamic JSON fallback catalog for providers & scrapers | `/config/app.providersJson` | Config entity field | CANONICAL ACTIVE |
| `models/Models.kt:160-175` | `ExtensionItem` | Data class entity | Legacy APK-based scraper plugin model | `/extensions/{id}` | Entity model definition | CURRENT LEGACY |
| `models/DashboardAnalytics.kt:186-215` | `DashboardAnalyticsCalculator` | `calculateManagedExtensionMetrics` | Computes Total, Active, Maint, Disabled, Deprecated KPI | Consumes `/managed_extensions` + `/extensions` | KPI aggregation | CANONICAL ACTIVE |
| `validation/ManagedExtensionValidator.kt:18-100` | `ManagedExtensionValidator` | `validate(ext)` | Full schema, regex, enum, and SSRF validation | Applied before writing to `/managed_extensions` | Client validation gate | CANONICAL ACTIVE |
| `validation/ManagedExtensionValidator.kt:105-164` | `ManagedExtensionValidator` | `validateHttpsUrl(url, isBaseUrl)` | SSRF defense: enforces HTTPS, rejects private IPs, loopback, ::1 | Validates `baseUrl` & `searchUrl` | Security validation | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:19-106` | `DefaultCineStreamScrapers` | `getDefaults()` | Bundled seed scraper catalog (qfilm, witanime, egydead, akwam, arabseed, mycima) | Seeded to `/managed_extensions` & `/extensions` | In-memory catalog | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:134-135` | `ManagedExtensionRepository` | `managedCollection`, `legacyCollection` | Firestore collection references | `/managed_extensions`, `/extensions` | Collection accessors | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:137-241` | `ManagedExtensionRepository` | `parseDocument(doc)` | Tolerant deserializer with field alias mapping | Parses docs from `/managed_extensions` & `/extensions` | Deserialization | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:243-264` | `ManagedExtensionRepository` | `listenCollection(col, name)` | Realtime `callbackFlow` snapshot listener with retry | `/managed_extensions`, `/extensions` | Realtime listener | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:266-278` | `ManagedExtensionRepository` | `getAllManagedExtensions()` | **Dual-read merge**: combines managed and legacy streams | `/managed_extensions` + `/extensions` | Realtime combine query | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:280-291` | `ManagedExtensionRepository` | `forceRefresh()` | Server cache bypass query (`Source.SERVER`) | `/managed_extensions`, `/extensions` | Direct read (server) | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:293-311` | `ManagedExtensionRepository` | `getManagedExtension(id)` | Document read with automatic legacy fallback | `/managed_extensions/{id}` ↓ `/extensions/{id}` | Single doc read | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:313-357` | `ManagedExtensionRepository` | `saveManagedExtension(ext, isNew)` | **Dual-write**: writes to managed and syncs to legacy | `/managed_extensions/{id}` & `/extensions/{id}` | Merge set (`SetOptions.merge()`) | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:359-379` | `ManagedExtensionRepository` | `updateStatus(id, status)` | **Dual-write**: updates lifecycle status and kill switch | `/managed_extensions/{id}` & `/extensions/{id}` | Merge set (`SetOptions.merge()`) | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:381-399` | `ManagedExtensionRepository` | `updatePriority(id, priority)` | **Dual-write**: updates priority ordering | `/managed_extensions/{id}` & `/extensions/{id}` | Merge set (`SetOptions.merge()`) | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:401-419` | `ManagedExtensionRepository` | `updateBaseUrl(id, newBaseUrl)` | **Dual-write**: dynamic domain rotation | `/managed_extensions/{id}` & `/extensions/{id}` | Merge set (`SetOptions.merge()`) | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:421-434` | `ManagedExtensionRepository` | `deleteManagedExtension(id)` | **Dual-delete**: removes document from both collections | `/managed_extensions/{id}` & `/extensions/{id}` | Document delete | CANONICAL ACTIVE |
| `repository/ManagedExtensionRepository.kt:436-444` | `ManagedExtensionRepository` | `seedDefaultExtensions()` | Seeds default scrapers via `saveManagedExtension` | `/managed_extensions` & `/extensions` | Batch sync | CANONICAL ACTIVE |
| `repository/AdminRepository.kt:47` | `AdminRepository` | `extensionsCollection` | Firestore collection reference | `/extensions` | Collection accessor | CURRENT LEGACY |
| `repository/AdminRepository.kt:750` | `AdminRepository` | `forceRefreshExtensions()` | Server cache bypass query on legacy collection | `/extensions` | Direct read (server) | CURRENT LEGACY |
| `repository/AdminRepository.kt:796-865` | `AdminRepository` | `getExtensions()` | Realtime snapshot listener on legacy collection | `/extensions` | Realtime listener | CURRENT LEGACY |
| `repository/AdminRepository.kt:867-885` | `AdminRepository` | `saveExtension(extension)` | Direct write to legacy extensions collection | `/extensions/{id}` | Merge set (`SetOptions.merge()`) | CURRENT LEGACY |
| `repository/AdminRepository.kt:887-895` | `AdminRepository` | `deleteExtension(extensionId)` | Direct deletion from legacy extensions collection | `/extensions/{extensionId}` | Document delete | CURRENT LEGACY |
| `viewmodels/ManagedExtensionsViewModel.kt:47-54` | `ManagedExtensionsViewModel` | `allExtensions` | StateFlow observing merged stream | Consumes `getAllManagedExtensions()` | ViewModel State | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:55-74` | `ManagedExtensionsViewModel` | `filteredExtensions` | Search query and status filter combination | Consumes `allExtensions` | UI State flow | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:96-112` | `ManagedExtensionsViewModel` | `saveExtension(ext, isNew)` | Validates and dispatches save to repository | Triggers dual-write | User action | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:114-123` | `ManagedExtensionsViewModel` | `updateStatus(id, status)` | Updates lifecycle status | Triggers dual-write | User action | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:125-138` | `ManagedExtensionsViewModel` | `updatePriority(id, priority)` | Updates execution priority | Triggers dual-write | User action | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:140-153` | `ManagedExtensionsViewModel` | `updateBaseUrl(id, newBaseUrl)` | Rotates scraper domain | Triggers dual-write | User action | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:155-167` | `ManagedExtensionsViewModel` | `seedDefaultScrapers()` | Imports and seeds 6 default scrapers | Triggers dual-write batch | User action | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:169-171` | `ManagedExtensionsViewModel` | `deprecateExtension(id)` | Sets status to DEPRECATED (soft retirement) | Triggers dual-write | User action | CANONICAL ACTIVE |
| `viewmodels/ManagedExtensionsViewModel.kt:173-183` | `ManagedExtensionsViewModel` | `deleteExtension(id)` | Hard destructive delete | Triggers dual-delete | User action | CANONICAL ACTIVE |
| `viewmodels/ViewModels.kt:898-964` | `ExtensionsViewModel` | `extensions`, `saveExtension`, `toggleExtension`, `deleteExtension` | Legacy ViewModel managing `ExtensionItem` | `/extensions` | Legacy CRUD | CURRENT LEGACY |
| `viewmodels/ViewModels.kt:105-115` | `DashboardViewModel` | `extensionMetrics` | Binds dashboard KPI to `getAllManagedExtensions()` | `/managed_extensions` + `/extensions` | KPI observation | CANONICAL ACTIVE |
| `ui/screens/ManagedExtensionsScreen.kt:50-387` | `ManagedExtensionsScreen` | Composable screen | Primary modern administrative console | `/managed_extensions` + `/extensions` | Full UI management | CANONICAL ACTIVE |
| `ui/screens/ManagedExtensionsScreen.kt:632-943` | `ManagedExtensionCard` | Composable component | Card with status, domain, priority, kill switch, menu | N/A | UI component | CANONICAL ACTIVE |
| `ui/screens/ManagedExtensionsScreen.kt:947-1196` | `ManagedExtensionEditorDialog` | Composable dialog | Form dialog for adding/editing `ManagedExtension` | Validates via validator | UI dialog | CANONICAL ACTIVE |
| `ui/screens/ExtensionsUpdatesScreen.kt:42-373` | `ExtensionsUpdatesScreen` | Composable screen | Hybrid screen managing `/extensions` + `providersJson` | `/extensions`, `/config/app` | Full UI management | CURRENT LEGACY |
| `ui/screens/ExtensionsUpdatesScreen.kt:432-475` | Direct JSON Editor | Composable dialog | Direct JSON editor for `config.providersJson` | `/config/app.providersJson` | Config update | CANONICAL ACTIVE |
| `ui/screens/ExtensionsScreen.kt:33-187` | `ExtensionsScreen` | Composable screen | Standalone unrouted legacy extensions screen | `/extensions` | Legacy UI | UNROUTED LEGACY |
| `ui/navigation/AppNavigation.kt:51` | `Screen.ManagedExtensions` | Route `"managed_extensions"` | Navigation destination to `ManagedExtensionsScreen` | N/A | App routing | CANONICAL ACTIVE |
| `ui/navigation/AppNavigation.kt:52` | `Screen.ExtensionsUpdates` | Route `"extensions_updates"` | Navigation destination to `ExtensionsUpdatesScreen` | N/A | App routing | CANONICAL ACTIVE |
| `firestore.rules:147-156` | Security Rule | `match /managed_extensions/{extensionId}` | Read: `isAuthenticated()`, Write: `isAdmin()` | `/managed_extensions` | Security policy | CANONICAL ACTIVE |
| `firestore.rules:159-163` | Security Rule | `match /extensions/{extensionId}` | Read: `isAuthenticated()`, Write: `isAdmin()` | `/extensions` | Security policy | CURRENT LEGACY |
| `firestore.rules:164-167` | Security Rule | `match /extension_updates/{updateId}` | Read: `isAuthenticated()`, Write: `isAdmin()` | `/extension_updates` | Security policy | CANONICAL (DEAD) |

---

## 2. FIRESTORE COLLECTION INVENTORY

This section details every Firestore operation performed on paths related to extensions:

### 2.1 `/managed_extensions`
- **READ (Realtime Listener):**  
  `ManagedExtensionRepository.listenCollection()` (`ManagedExtensionRepository.kt:243-264`). Attaches `addSnapshotListener(MetadataChanges.INCLUDE)`. Deserializes snapshots via `parseDocument()`.
- **READ (Direct / Single Document):**  
  `ManagedExtensionRepository.getManagedExtension(id)` (`ManagedExtensionRepository.kt:294-301`). Calls `managedCollection.document(id).get().await()`.
- **READ (Server Cache Bypass):**  
  `ManagedExtensionRepository.forceRefresh()` (`ManagedExtensionRepository.kt:280-285`). Calls `managedCollection.get(Source.SERVER).await()`.
- **CREATE / UPDATE (Full Write):**  
  `ManagedExtensionRepository.saveManagedExtension()` (`ManagedExtensionRepository.kt:313-343`). Calls `managedCollection.document(id).set(payload, SetOptions.merge()).await()`.
- **UPDATE (Partial Updates):**  
  - `updateStatus()` (`ManagedExtensionRepository.kt:367`): updates `status`, `enabled`, `updatedAt`.
  - `updatePriority()` (`ManagedExtensionRepository.kt:387`): updates `priority`, `updatedAt`.
  - `updateBaseUrl()` (`ManagedExtensionRepository.kt:407`): updates `baseUrl`, `updatedAt`.
- **DELETE (Document Deletion):**  
  `ManagedExtensionRepository.deleteManagedExtension()` (`ManagedExtensionRepository.kt:422`). Calls `managedCollection.document(id).delete().await()`.
- **QUERY / ORDERING:**  
  In-memory sorting in `getAllManagedExtensions()` (`ManagedExtensionRepository.kt:276`): `sortedByDescending { it.priority }`.
- **BATCH / TRANSACTION:**  
  `seedDefaultExtensions()` iterates through 6 defaults, calling `saveManagedExtension()` sequentially. No Firestore `WriteBatch` or transaction is used.
- **FALLBACK / MERGE ROLE:**  
  Primary canonical target. Overlays legacy collection in `combine()` merge.

### 2.2 `/extensions`
- **READ (Realtime Listener #1 - Managed Pipeline):**  
  `ManagedExtensionRepository.listenCollection(legacyCollection, "extensions")` (`ManagedExtensionRepository.kt:269`). Emits map of legacy items converted into `ManagedExtension`.
- **READ (Realtime Listener #2 - Legacy Pipeline):**  
  `AdminRepository.getExtensions()` (`AdminRepository.kt:796-865`). Attaches `addSnapshotListener` and deserializes into `List<ExtensionItem>`.
- **READ (Direct / Single Document Fallback):**  
  `ManagedExtensionRepository.getManagedExtension(id)` (`ManagedExtensionRepository.kt:302-309`). If document is absent in `/managed_extensions`, executes `legacyCollection.document(id).get().await()`.
- **READ (Server Cache Bypass):**  
  - `ManagedExtensionRepository.forceRefresh()` (`ManagedExtensionRepository.kt:286-290`).
  - `AdminRepository.forceRefreshExtensions()` (`AdminRepository.kt:749-755`).
- **CREATE / UPDATE (Dual-Write from Managed Repository):**  
  `ManagedExtensionRepository.saveManagedExtension()`, `updateStatus()`, `updatePriority()`, `updateBaseUrl()` (`ManagedExtensionRepository.kt:345, 369, 389, 409`). Mirrors every change to `legacyCollection.document(id).set(..., SetOptions.merge())` wrapped in `try/catch`.
- **CREATE / UPDATE (Direct Write from Legacy Repository):**  
  `AdminRepository.saveExtension(extension: ExtensionItem)` (`AdminRepository.kt:867-885`). Writes `ExtensionItem` payload with `SetOptions.merge()`.
- **DELETE (Dual-Delete from Managed Repository):**  
  `ManagedExtensionRepository.deleteManagedExtension()` (`ManagedExtensionRepository.kt:424`). Calls `legacyCollection.document(id).delete().await()`.
- **DELETE (Direct Delete from Legacy Repository):**  
  `AdminRepository.deleteExtension(extensionId)` (`AdminRepository.kt:887-895`). Calls `extensionsCollection.document(extensionId).delete().await()`.
- **FALLBACK / MERGE ROLE:**  
  Active legacy source. Acts as base catalog in `ManagedExtensionRepository.getAllManagedExtensions()` and standalone catalog in `ExtensionsUpdatesScreen`.

### 2.3 `/extension_updates`
- **READ:** NONE (Zero occurrences in code).
- **CREATE / UPDATE:** NONE (Zero occurrences in code).
- **DELETE:** NONE (Zero occurrences in code).
- **QUERY / LISTENER:** NONE (Zero occurrences in code).
- **CONTRACT / RULES:**  
  Defined as `FirebaseCollections.LEGACY_EXTENSION_UPDATES = "extension_updates"` in `FirebaseContract.kt:60` and covered in `firestore.rules:164-167`. Dead runtime path.

### 2.4 `/config/app` (Extensions Context)
- **READ (Realtime Listener):**  
  `AdminRepository.getAppConfig()` (`AdminRepository.kt:518-551`). Observes `configCollection.document("app")`.
- **UPDATE (`providersJson` Field):**  
  `ConfigViewModel.updateProvidersJson()` (`ViewModels.kt:672-697`) triggered from `ExtensionsUpdatesScreen.kt:432-475`. Writes raw JSON string to `providersJson` field using `SetOptions.merge()`.
- **ROLE:**  
  Alternative dynamic providers/scrapers catalog stored as an embedded JSON string inside the system configuration document.

### 2.5 `/config/global` (Extensions Context)
- **READ (Fallback):**  
  `AdminRepository.getAppConfig()` (`AdminRepository.kt:539-548`). If `/config/app` does not exist, reads `/config/global` to retrieve fallback `providersJson`.
- **WRITE:** Discontinued.

### 2.6 `/auditLogs` (Extensions Context)
- **CREATE (Audit Trail):**  
  Both repositories invoke `adminRepository.logAudit()` whenever an extension is created, updated, status toggled, priority changed, domain rotated, or deleted:
  - `CREATE_MANAGED_EXTENSION` (`ManagedExtensionRepository.kt:351`)
  - `UPDATE_MANAGED_EXTENSION` (`ManagedExtensionRepository.kt:351`)
  - `CHANGE_MANAGED_EXTENSION_STATUS` (`ManagedExtensionRepository.kt:374`)
  - `CHANGE_MANAGED_EXTENSION_PRIORITY` (`ManagedExtensionRepository.kt:394`)
  - `CHANGE_MANAGED_EXTENSION_BASE_URL` (`ManagedExtensionRepository.kt:414`)
  - `DELETE_MANAGED_EXTENSION` (`ManagedExtensionRepository.kt:429`)
  - `SAVE_EXTENSION` (`AdminRepository.kt:880`)
  - `DELETE_EXTENSION` (`AdminRepository.kt:890`)

---

## 3. MANAGED EXTENSIONS (`/managed_extensions`)

### 3.1 Read Mechanism
Managed extensions are read via two independent pathways:
1. **Real-time Flow Stream (`getAllManagedExtensions`):**  
   Uses `callbackFlow` listening to `col.addSnapshotListener(MetadataChanges.INCLUDE)`. The raw documents are parsed through `parseDocument(doc)` into `ManagedExtension` objects.
2. **Direct Asynchronous Document Fetch (`getManagedExtension`):**  
   Direct document lookup `managedCollection.document(id).get().await()`.

### 3.2 Write Mechanism
All write operations (`saveManagedExtension`, `updateStatus`, `updatePriority`, `updateBaseUrl`) utilize `SetOptions.merge()`:
```kotlin
managedCollection.document(id).set(payload, SetOptions.merge()).await()
```
This guarantees that existing document attributes not explicitly passed in the write map are preserved.

### 3.3 Data Model Architecture
- **Model Class:** `com.example.models.ManagedExtension` annotated with `@IgnoreExtraProperties`.
- **16 Core Fields:**
  1. `extensionId` (String): Unique identifier.
  2. `scraperKey` (String): Opaque identifier matching bundled Kotlin scraper classes in the Users App.
  3. `name` (String): Human-readable display label.
  4. `description` (String): Summary of content and features.
  5. `baseUrl` (String): Authoritative HTTP base endpoint.
  6. `searchUrl` (String): Search query pattern template (e.g. `/search?q=%s`).
  7. `runtimeApiVersion` (Int, default: 1): Client-scraper API protocol contract version.
  8. `definitionVersion` (Int, default: 1): Monotonically increasing definition version.
  9. `minAppVersionCode` (Int, default: 1): Minimum Users App build required.
  10. `status` (String, default: "ACTIVE"): Lifecycle state (`ACTIVE`, `MAINTENANCE`, `DISABLED`, `DEPRECATED`).
  11. `priority` (Int, default: 100): Execution and fallback order.
  12. `capabilities` (List<String>): Functional capabilities (`SEARCH`, `DETAILS`, `EPISODES`, etc.).
  13. `contentTypes` (List<String>): Content categories (`MOVIE`, `SERIES`, `ANIME`, `ASIAN_DRAMA`).
  14. `enabled` (Boolean, default: true): Global kill switch flag.
  15. `createdAt` (Long): Epoch milliseconds creation timestamp.
  16. `updatedAt` (Long): Epoch milliseconds modification timestamp.
- **Pure Configuration Invariant:**  
  `ManagedExtension` contains **zero** executable bytecode fields (`apkUrl`, `apkSha256`, `dexPath`, `scriptBody`, or `classLoader` do not exist).

### 3.4 Identifier Resolution (`id` vs `extensionId` vs `documentId`)
- When creating a document:
  ```kotlin
  val id = extension.extensionId.trim().ifBlank {
      extension.scraperKey.trim().ifBlank { "ext_${System.currentTimeMillis()}" }
  }
  ```
  The document ID in Firestore is set to `id`. Inside the document payload, both `"extensionId"` and `"id"` are stored with this exact same value.
- When reading/parsing a document:
  ```kotlin
  val id = doc.getString("extensionId")
      ?: doc.getString("id")
      ?: doc.getString("key")
      ?: doc.id
  ```
  The parser tolerates legacy documents missing `extensionId` by inspecting `id`, `key`, or the Firestore document key (`doc.id`).

### 3.5 Validation & Security (SSRF Protection)
Before any write operation is dispatched, `ManagedExtensionValidator.validate(ext)` enforces:
1. **Identifier Syntax:**  
   `extensionId`: `^[a-zA-Z0-9_-]{2,64}$`  
   `scraperKey`: `^[a-z0-9_-]{2,64}$` (strictly lowercase).
2. **Name Bounds:** Length between 2 and 100 characters.
3. **SSRF URL Validation (`validateHttpsUrl`):**
   - Scheme must be strictly `https://`.
   - Host must be a valid FQDN containing at least one dot.
   - Forbids `localhost`, `.localhost`, and `0.0.0.0`.
   - Forbids IPv4 loopback (`127.0.0.0/8`).
   - Forbids private IPv4 ranges: `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`.
   - Forbids link-local IPv4 (`169.254.0.0/16`).
   - Forbids IPv6 loopback (`::1`), link-local (`fe80::/10`), and unique local (`fc00::/7`).
4. **Lifecycle Status Validation:** Must strictly be one of `ACTIVE`, `MAINTENANCE`, `DISABLED`, `DEPRECATED`. Unknown values fall back safely to `DISABLED` via `ManagedExtensionStatus.fromString()`.
5. **Capabilities & Content Types:** Must be subsets of `ManagedExtensionCapabilities.ALL` and `ManagedExtensionContentTypes.ALL`.

---

## 4. LEGACY EXTENSIONS (`/extensions`)

### 4.1 Does Admin App read from `/extensions`?
**YES.**  
1. `ManagedExtensionRepository.getAllManagedExtensions()` actively attaches a real-time snapshot listener to `/extensions` and merges it into the unified catalog.
2. `ManagedExtensionRepository.getManagedExtension(id)` performs a fallback read to `/extensions/{id}` if the document is absent in `/managed_extensions`.
3. `AdminRepository.getExtensions()` actively listens to `/extensions` to populate `ExtensionsViewModel.extensions`.
4. `AdminRepository.forceRefreshExtensions()` executes a server query on `/extensions`.

### 4.2 Does Admin App write to, modify, or delete from `/extensions`?
**YES.**  
1. **Dual-Write on Save:** Whenever a managed extension is created or updated in `ManagedExtensionRepository.saveManagedExtension()`, the identical payload is written to `/extensions/{id}`.
2. **Dual-Write on Quick Actions:** `updateStatus()`, `updatePriority()`, and `updateBaseUrl()` all mirror their partial updates to `/extensions/{id}`.
3. **Dual-Delete:** `ManagedExtensionRepository.deleteManagedExtension(id)` deletes the document from `/extensions/{id}` as well as `/managed_extensions/{id}`.
4. **Direct Legacy CRUD:** `AdminRepository.saveExtension()` and `deleteExtension()` write and delete directly on `/extensions` when executed from `ExtensionsScreen` or `ExtensionsUpdatesScreen`.

### 4.3 Dual-Read Merge Algorithm & Precedence
The real-time merge in `ManagedExtensionRepository.getAllManagedExtensions()` operates as follows:
```kotlin
combine(
    listenCollection(managedCollection, "managed_extensions"),
    listenCollection(legacyCollection, "extensions")
) { managedMap, legacyMap ->
    // Merge maps: start with legacy, overlay managed_extensions
    val mergedMap = HashMap<String, ManagedExtension>(legacyMap)
    managedMap.forEach { (id, ext) ->
        mergedMap[id] = ext
    }
    mergedMap.values.sortedByDescending { it.priority }
}
```
**Merge Precedence Rules:**
- **Overlay Strategy:** `legacyMap` is populated first. Then `managedMap` is iterated, overwriting any matching key in `mergedMap`.
- **Precedence:** **`/managed_extensions` takes absolute precedence over `/extensions`**. If an extension exists in both collections under the same ID, the modern `/managed_extensions` definition is displayed.
- **Inclusion of Legacy-Only Records:** If a document exists **only** in `/extensions`, it is deserialized into a `ManagedExtension` and included in the catalog.
- **Ordering:** The combined set is sorted descending by `priority`.

### 4.4 UI Visibility & Admin Editability
- **Visibility:** Legacy extensions appear seamlessly in both `ManagedExtensionsScreen` (via the merge) and `ExtensionsUpdatesScreen` (via `ExtensionsViewModel`). They also count toward the total KPI on `DashboardScreen`.
- **Editability:** An administrator can edit, toggle, rotate domains, deprecate, or delete legacy extensions from either screen. Editing a legacy item from `ManagedExtensionsScreen` promotes/writes it into `/managed_extensions` while keeping `/extensions` synchronized.

---

## 5. EXTENSION UPDATES (`/extension_updates`)

### 5.1 Usage Assessment
- **Code References:**
  - `contract/FirebaseContract.kt:60`: `const val LEGACY_EXTENSION_UPDATES = "extension_updates"`.
  - `firestore.rules:164-167`:
    ```
    match /extension_updates/{updateId} {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }
    ```
  - `CineStreamAdminLogicTest.kt`: Unit test verifying contract constant string equality.
- **Runtime Operations:**
  - **READ:** 0
  - **CREATE:** 0
  - **UPDATE:** 0
  - **DELETE:** 0
  - **LISTENERS:** 0
  - **VIEWMODEL BINDINGS:** 0
  - **UI REFERENCES:** 0

### 5.2 Architectural Destiny & Purpose
- **Why it exists:** Originally planned during initial development for tracking OTA updates to standalone APK-based scrapers (parallel to `/app_updates` for the main CineStream application).
- **Why it is dormant:** With the migration to pure configuration scrapers (Phase C2), scraper updates occur dynamically via Firestore document updates in `/managed_extensions` (instant domain rotation, status change, priority update). There are no standalone APK binaries to update.
- **Destiny:** Dead runtime path. Safe to retire from security rules and contracts during the next phase.

---

## 6. UI & WORKFLOWS

### 6.1 Screen & Component Hierarchy

```
AppNavigation
  ├── Drawer & Bottom Nav
  │     ├── Screen.Dashboard ──> DashboardScreen ──> (Consumes combined extension metrics)
  │     ├── Screen.ManagedExtensions ──> ManagedExtensionsScreen (Canonical C2 Console)
  │     └── Screen.ExtensionsUpdates ──> ExtensionsUpdatesScreen (Legacy Hub + JSON Editor)
  │                                           │
  │                                           └── Banner link to Screen.ManagedExtensions
  └── Standalone (Unrouted)
        └── ExtensionsScreen (Legacy APK registration)
```

### 6.2 Screen Functionality Breakdown

#### 1. `ManagedExtensionsScreen.kt` (Canonical Modern Console)
- **Observed ViewModel:** `ManagedExtensionsViewModel`.
- **Data Source:** `repository.getAllManagedExtensions()` (Dual-read merge of `/managed_extensions` and `/extensions`).
- **Header Actions:**
  - **Seed Default Scrapers Button (`CloudSync` icon):** Calls `viewModel.seedDefaultScrapers()`, syncing 6 standard scrapers (`qfilm`, `witanime`, `egydead`, `akwam`, `arabseed`, `mycima`).
  - **Refresh Button (`CineStreamRefreshButton`):** Triggers `repository.forceRefresh()` against Firestore server cache.
- **KPI Summary Pills:** Displays Total, Active, Maintenance, and Disabled counts.
- **Search & Filter Controls:**
  - Real-time text search across `name`, `scraperKey`, `extensionId`, and `baseUrl`.
  - Horizontal filter chips for `ALL`, `ACTIVE`, `MAINTENANCE`, `DISABLED`, `DEPRECATED`.
- **Card Actions on Each Extension:**
  - **Kill Switch Toggle:** Instant toggle between `ACTIVE` and `DISABLED`.
  - **Priority Chip & Dialog:** Quick modal to edit execution priority integer.
  - **Domain Rotation (`Language` icon & Dialog):** Quick modal to update `baseUrl` with full HTTPS/SSRF validation.
  - **Options Dropdown Menu:**
    - Edit Definition (opens full `ManagedExtensionEditorDialog`).
    - Rotate Domain / URL.
    - Set Priority.
    - Set Status: ACTIVE, MAINTENANCE, DISABLED.
    - DEPRECATE (Retain without deletion).
    - Delete (Destructive hard delete).

#### 2. `ExtensionsUpdatesScreen.kt` (Legacy Hub & Dynamic JSON Editor)
- **Observed ViewModels:** `ExtensionsViewModel` (observes `/extensions`), `ConfigViewModel` (observes `/config/app`).
- **Data Source:** `AdminRepository.getExtensions()`.
- **Components:**
  - **Cross-Navigation Banner:** Displays prominent card navigating to `ManagedExtensionsScreen`.
  - **Direct JSON Editor Button (`Code` icon):** Opens dialog editing `config.providersJson`. Validates JSON syntax before saving to `/config/app`.
  - **Legacy Extension Cards:** Displays package name, version code/name, direct APK URL, and toggle switch.
  - **Edit & Delete Modals:** Edits or deletes records from `/extensions`.

#### 3. `ExtensionsScreen.kt` (Unrouted Legacy CRUD Screen)
- Standalone screen providing registration dialog for APK URL, package name, checksum, and release notes. Not connected to active drawer routes.

### 6.3 Action & Lifecycle Matrix

| Action | User Trigger | Validation | Target Paths | Audit Log Action |
| :--- | :--- | :--- | :--- | :--- |
| **Add Managed Extension** | FAB on `ManagedExtensionsScreen` | `ManagedExtensionValidator.validate()` | `/managed_extensions/{id}` & `/extensions/{id}` | `CREATE_MANAGED_EXTENSION` |
| **Edit Managed Extension** | "Edit Definition" menu item | `ManagedExtensionValidator.validate()` | `/managed_extensions/{id}` & `/extensions/{id}` | `UPDATE_MANAGED_EXTENSION` |
| **Rotate Domain** | Domain edit icon / menu item | `validateHttpsUrl(baseUrl)` | `/managed_extensions/{id}` & `/extensions/{id}` | `CHANGE_MANAGED_EXTENSION_BASE_URL` |
| **Set Priority** | Priority pill / menu item | Non-negative integer check | `/managed_extensions/{id}` & `/extensions/{id}` | `CHANGE_MANAGED_EXTENSION_PRIORITY` |
| **Toggle Kill Switch** | Switch on card | None (boolean toggle) | `/managed_extensions/{id}` & `/extensions/{id}` | `CHANGE_MANAGED_EXTENSION_STATUS` |
| **Deprecate** | "DEPRECATE" menu item | Confirmation dialog | `/managed_extensions/{id}` & `/extensions/{id}` | `CHANGE_MANAGED_EXTENSION_STATUS` |
| **Delete Managed Extension** | "Delete" menu item | Destructive confirmation dialog | `/managed_extensions/{id}` & `/extensions/{id}` | `DELETE_MANAGED_EXTENSION` |
| **Seed Defaults** | Sync icon on header | Built-in defaults | `/managed_extensions/{id}` & `/extensions/{id}` | `CREATE_MANAGED_EXTENSION` (x6) |
| **Save Legacy Extension** | Dialog on `ExtensionsUpdatesScreen` | Basic non-blank checks | `/extensions/{id}` | `SAVE_EXTENSION` |
| **Delete Legacy Extension** | Delete button on legacy card | Confirmation dialog | `/extensions/{id}` | `DELETE_EXTENSION` |
| **Deploy Dynamic JSON** | JSON dialog on `ExtensionsUpdatesScreen` | `JSONObject` / `JSONArray` parse | `/config/app.providersJson` | Config update log |

---

## 7. COMPATIBILITY & DUAL-PATH MATRIX

| Firestore Path | Read | Write | Delete | UI Display | Admin Editable | Fallback Role | Risk Level If Deleted Immediately |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`/managed_extensions`** | **YES** (Realtime + Server) | **YES** (`SetOptions.merge()`) | **YES** | Primary in `ManagedExtensionsScreen` & Dashboard | **YES** | Primary Canonical Surface | **CRITICAL (Catastrophic)** — Breaks modern scraper configuration across the ecosystem. |
| **`/extensions`** | **YES** (Dual-read merge in `ManagedExtensionRepository` + `AdminRepository`) | **YES** (Dual-write mirror + direct legacy write) | **YES** (Dual-delete + direct legacy delete) | Merged in `ManagedExtensionsScreen`; direct in `ExtensionsUpdatesScreen` | **YES** | Active base layer for scraper catalog; legacy scraper fallback | **HIGH (Severe Regression Risk)** — Immediate deletion breaks legacy scrapers not yet copied to `managed_extensions` and legacy Users App builds. |
| **`/extension_updates`** | **NO** (0 callers) | **NO** (0 callers) | **NO** (0 callers) | **NO** | **NO** | Dead runtime path | **NONE (Zero Impact)** — Can be removed from rules/contracts with zero runtime side effects. |
| **`/config/app` (`providersJson`)** | **YES** (Realtime snapshot) | **YES** (`SetOptions.merge()`) | **NO** | Direct JSON editor in `ExtensionsUpdatesScreen` | **YES** | Embedded fallback catalog for dynamic providers | **HIGH** — Older app versions parse `providersJson` directly for stream endpoints. |
| **`/config/global`** | **YES** (Fallback read only) | **NO** (Discontinued) | **NO** | Invisible | **NO** | Read fallback when `/config/app` is missing | **LOW-MEDIUM** — Only needed if `/config/app` document is ever absent or corrupt. |

---

## 8. VERDICT & RECOMMENDATIONS FOR MIGRATION

### 8.1 Executive Verdict

1. **Dual-Read Dependency is Real and Active:**  
   The CineStream Admin App does **not** rely solely on `/managed_extensions`. In `ManagedExtensionRepository.kt`, `getAllManagedExtensions()` actively combines `/managed_extensions` and `/extensions` in real-time. Any scraper that exists exclusively in `/extensions` is actively surfaced, counted in Dashboard KPIs, and delivered to consumer components.
2. **Dual-Write Synchronization is Active:**  
   Every management action on a managed extension (create, update, domain rotation, priority change, status change, delete) actively mirrors the operation to `/extensions` via `SetOptions.merge()`.
3. **Can `/extensions` be safely deleted now?**  
   **NO.**  
   Deleting `/extensions` right now would:
   - Permanently destroy any historical scraper definition documents that reside exclusively in `/extensions` and have not yet been copied to `/managed_extensions`.
   - Cause any older CineStream Users App versions in production that read exclusively from `/extensions` to immediately lose all scraper configurations.
   - Break the `AdminRepository.getExtensions()` stream and `ExtensionsUpdatesScreen`.
4. **Can writes to `/extensions` be stopped now?**  
   **NOT YET.**  
   Dual-writes should remain in place until an authoritative one-way migration/backfill has verified that all documents in `/extensions` have been promoted to `/managed_extensions`, and the Users App has been verified to read exclusively from `/managed_extensions`.

### 8.2 Proposed Phased Migration Roadmap

```
Phase EXT-1 (Read-Only Inventory Verification)
  └── Query production Firestore (once credentials provided) to count & compare documents
      in /managed_extensions vs /extensions.

Phase EXT-2 (One-Way Backfill & Promotion)
  └── Execute an idempotent migration script:
      For each doc in /extensions:
        if doc.id not in /managed_extensions:
          write to /managed_extensions/{doc.id} with safe status defaults.

Phase EXT-3 (Users App Verification & Cutover)
  └── Verify that the Users App reads exclusively from /managed_extensions.
  └── Deprecate legacy reads in the Users App.

Phase EXT-4 (Decouple Dual-Write in Admin App)
  └── Remove dual-write logic in ManagedExtensionRepository.kt (write ONLY to /managed_extensions).
  └── Deprecate ExtensionsUpdatesScreen legacy card list and point all navigation to ManagedExtensionsScreen.

Phase EXT-5 (Decommissioning & Cleanup)
  └── Remove /extension_updates and /extensions from firestore.rules.
  └── Remove legacy methods from AdminRepository.kt.
  └── Archive /extensions collection in Firestore.
```

---

## 9. ZERO-MUTATIONS ATTESTATION

In strict compliance with Hard Rules #1 through #12:
- **Zero code changes:** No Kotlin, Java, XML, Compose UI, or Gradle files were edited, created, or deleted.
- **Zero Firestore Rules changes:** `firestore.rules` was inspected in read-only mode and remains unaltered.
- **Zero database changes:** No Firestore documents, collections, or records were created, modified, deleted, moved, or migrated.
- **Strict Evidence Standard:** Every finding, line reference, class, and method listed in this report has been verified directly from the source code.
