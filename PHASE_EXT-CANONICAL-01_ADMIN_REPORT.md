# PHASE EXT-CANONICAL-01 — CINESTREAM ADMIN APP
# CANONICAL EXTENSION + SEARCH ORDER ARCHITECTURE
# FORENSIC → DESIGN → IMPLEMENTATION → VERIFICATION REPORT

**TARGET PROJECT:** CineStream Admin App (Management Plane)  
**ENVIRONMENT:** Android / Kotlin / Jetpack Compose / Cloud Firestore  
**DATE:** 2026-09-29  

---

## 1. EXECUTIVE STATUS

**PASS**

The canonical Admin-side Extension Configuration + Content-Type-Specific Search Ordering architecture has been fully designed, implemented, and verified.
- **Zero Dynamic Code Loading:** No `DexClassLoader`, no `PathClassLoader`, no dynamic APK loading, no reflection-based scraper discovery.
- **Pure Configuration Architecture:** Firestore stores only metadata and ordered lists of stable extension ID strings.
- **Decoupled Architecture:** Extension metadata management is completely decoupled from content-type search ordering.
- **Content-Type Enforcement:** Strict capability checking is enforced in domain validation and UI selection.
- **Zero Regressions:** 103 unit tests passing (100% PASS), 12/12 static security checks passing (100% PASS), and debug APK successfully assembled.

---

## 2. EXACT FILES MODIFIED

| Path | Purpose | Reason |
| :--- | :--- | :--- |
| `app/src/main/java/com/example/contract/FirebaseContract.kt` | Canonical contract constants for Firestore | Defined `FirebaseConfigDocs.SEARCH_ORDER = "search_order"`, anchoring the singleton path `/config/search_order` to prevent collection/document path drift across repositories. |
| `app/src/main/java/com/example/models/SearchOrderConfig.kt` | Canonical data model and category enum | Defines `SearchOrderCategory` (`MOVIE`, `TV`, `ANIME`) and `SearchOrderConfig` data class with immutable copy methods, deterministic list handling, and dual-write serialization (`series` == `tv`). |
| `app/src/main/java/com/example/validation/SearchOrderValidator.kt` | Domain validation rules engine | Implements capability checks, duplicate extension ID detection, catalog existence checks, and content-type isolation rules. |
| `app/src/main/java/com/example/repository/SearchOrderRepository.kt` | Authoritative data layer for search order persistence | Provides real-time `callbackFlow` stream, server-first fetching, pre-save validation gate, audit trail emission (`UPDATE_SEARCH_ORDER`), and capability-based default ordering generation. |
| `app/src/main/java/com/example/viewmodels/SearchOrderViewModel.kt` | Reactive presentation state management | Blends reactive streams from `/managed_extensions` and `/config/search_order`, manages dirty tracking (`isDirty`), and provides deterministic reordering actions (`moveUp`, `moveDown`, `remove`, `add`, `resetToDefaults`). |
| `app/src/main/java/com/example/ui/screens/SearchOrderScreen.kt` | Material 3 Compose Search Order management page | Provides three independent sections (Movies, TV Series, Anime), rank badges (#1, #2, #3, ...), move up/down controls, capability-filtered add modals, and disabled-in-catalog visual warnings. |
| `app/src/main/java/com/example/ui/screens/ManagedExtensionsScreen.kt` | Extension metadata management page | Added navigation pill tabs to allow seamless switching between Extension Catalog management and Search Order configuration. |
| `app/src/main/java/com/example/ui/navigation/AppNavigation.kt` | Application navigation and routing | Registered `Screen.SearchOrder` route (`search_order`), navigation drawer entry, and app bar integration. |
| `app/src/test/java/com/example/CineStreamAdminLogicTest.kt` | Core logic and regression test suite | Maintained 79 passing tests covering user management, bans, subscriptions, support desk, pro requests, and core reordering logic. |
| `app/src/test/java/com/example/SearchOrderArchitectureTest.kt` | Dedicated focused architecture test suite | Added 20 focused tests covering every required test category: model serialization, dual-write aliases, empty list handling, duplicate detection, content-type isolation, extension lifecycle invariants, and repository operations. |

---

## 3. CURRENT → TARGET ARCHITECTURE

### Current State (Before Decoupling):
- **Single Monolithic Priority:** Each extension record in `/managed_extensions/{extensionId}` contained a generic `priority: Int` (default 100).
- **Conflated Scope:** Global priority could not express content-type specialization (e.g. Scraper A is best for Movies, while Scraper B is best for Anime).
- **Inflexible Reordering:** Changing scraper execution order required updating individual extension documents, generating unnecessary write overhead and cache invalidations.
- **Client Ambiguity:** The future Users App had no centralized, single-document configuration to look up execution order.

### Target State (Canonical Decoupled Architecture):
- **Distinct Responsibilities:**
  1. **Extension Management (`/managed_extensions/{extensionId}`):** Controls identity, name, baseUrl, scraperKey, status, capabilities, contentTypes, runtimeApiVersion, definitionVersion, and legacy global priority.
  2. **Search Order Management (`/config/search_order`):** Controls content-type-specific execution order (independent lists for Movies, TV Series, Anime).
- **Specialized Execution Pipelines:** Admin independently configures the order for Movies, TV Series, and Anime.
- **Zero Bytecode / Configuration Only:** Search order document contains only lists of clean, stable string IDs referencing bundled scrapers compiled into the client.

```
┌────────────────────────────────────────────────────────┐
│                   CINESTREAM ADMIN                     │
└──────────────────────────┬─────────────────────────────┘
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
┌─────────────────────────┐ ┌─────────────────────────┐
│  EXTENSION MANAGEMENT   │ │   SEARCH ORDER CONFIG   │
│  /managed_extensions/   │ │  /config/search_order   │
├─────────────────────────┤ ├─────────────────────────┤
│ • extensionId           │ │ • movie: [qfilm, ...]   │
│ • scraperKey            │ │ • tv: [egydead, ...]    │
│ • baseUrl               │ │ • anime: [witanime, ...]│
│ • status / operational  │ │ • series: [egydead, ...]│
│ • contentTypes          │ │ • updatedAt             │
│ • capabilities          │ │ • updatedBy             │
│ • legacy priority: Int  │ └─────────────────────────┘
└─────────────────────────┘
```

---

## 4. FIRESTORE CONTRACT

- **Collection:** `config` (`FirebaseCollections.CONFIG`)
- **Document ID:** `search_order` (`FirebaseConfigDocs.SEARCH_ORDER`)
- **Full Path:** `/config/search_order`

### Document Schema & Fields:
```json
{
  "movie": [
    "qfilm",
    "egydead",
    "akwam"
  ],
  "tv": [
    "egydead",
    "qfilm",
    "seriestime"
  ],
  "series": [
    "egydead",
    "qfilm",
    "seriestime"
  ],
  "anime": [
    "witanime",
    "anime4up",
    "animeblkom"
  ],
  "updatedAt": 1759160940000,
  "updatedBy": "admin@cinestream.com"
}
```

### Exact Field Types:
- `movie`: `List<String>` — Array of stable extension ID strings for Movie search ordering.
- `tv`: `List<String>` — Array of stable extension ID strings for TV Series search ordering.
- `series`: `List<String>` — Dual-write alias identical to `tv`, persisted for scraper engine and legacy client compatibility.
- `anime`: `List<String>` — Array of stable extension ID strings for Anime search ordering.
- `updatedAt`: `Long` (or numeric timestamp) — Epoch millisecond timestamp of the last administrative modification.
- `updatedBy`: `String` — Email or UID of the administrator who performed the update.

### Serialization & Deserialization:
- **Serialization (`toMap`):** Constructs a map with `movie`, `tv`, `series` (alias), `anime`, `updatedAt`, and `updatedBy`.
- **Deserialization (`fromMap` / `fromDocument`):** Extracts lists safely. If `tv` is missing or null, automatically falls back to `series`. Safely parses lists or comma-separated strings with trimming and blank filtering.

---

## 5. CONTENT TYPE RULES

The search order system strictly respects extension capabilities both in domain validation and in the Admin UI selection flow.

| Content Type | Allowed Categories | Prohibited Categories | Validation Rule |
| :--- | :--- | :--- | :--- |
| **Movie-Only** (`contentTypes = ["MOVIE"]`) | Movies | TV Series, Anime | Must not appear in `tv` or `anime` lists. |
| **TV-Only** (`contentTypes = ["SERIES"]` or `["TV"]`) | TV Series | Movies, Anime | Must not appear in `movie` or `anime` lists. |
| **Anime-Only** (`contentTypes = ["ANIME"]`) | Anime | Movies, TV Series | Must not appear in `movie` or `tv` lists. |
| **Multi-Category** (e.g. `["MOVIE", "SERIES", "ANIME"]`) | Movies, TV Series, Anime | None (eligible for all supported) | May appear in all supported lists independently. |

### Validation Enforcement:
- **UI Validation:** The "Add Extension" dialog inspects `SearchOrderValidator.isExtensionEligibleForCategory()` and only displays scrapers that declare the corresponding content type.
- **Domain Validation:** `SearchOrderValidator.validate()` rejects configurations where any extension ID lacks the required content type capability, preventing forged or accidental invalid writes.

---

## 6. SEARCH ORDER

### Independent Lists:
- **Movie Order:** Independent ordered sequence of scrapers used when user searches or requests movie streams.
- **TV Series Order:** Independent ordered sequence of scrapers used when user searches or requests television episodes.
- **Anime Order:** Independent ordered sequence of scrapers used when user searches or requests anime series and movies.

### Unlimited List Length & Determinism:
- **No Artificial Limit:** There is NO hard cap of 3. The list can contain 1, 2, 3, 4, 5, 6, 7, ... extensions.
- **Positions 1, 2, 3 ("Top 3"):** Merely represent the first three elements in the list. The Users App will be able to search the top 3 preferred sources first, and then sequentially fall back through positions 4, 5, etc.
- **Deterministic Ordering:** Order is strictly preserved as defined by the Admin. Shuffling (`shuffled()`) and non-deterministic sets are strictly prohibited.
- **Reordering Controls:** Reliable Move Up, Move Down, Add, and Remove operations with immediate tactile feedback.

---

## 7. LEGACY COMPATIBILITY

| Legacy Artifact | Status in Phase EXT-CANONICAL-01 | Details |
| :--- | :--- | :--- |
| `/extensions` | **Preserved (Untouched)** | Dual-read, dual-write, and fallback mechanisms remain fully intact in the Admin repository. No documents were deleted. |
| `/extension_updates` | **Preserved (Untouched)** | Legacy update documents remain intact. No modifications made. |
| `ManagedExtension.priority: Int` | **Preserved (Decoupled)** | Kept on individual `ManagedExtension` records. Used as a tiebreaker/default order generator (`getDefaultSearchOrder`), but is NOT conflated with the content-type search order. |
| `series` Firestore Key | **Added as Dual-Write Alias** | Written alongside `tv` in `/config/search_order` to prevent breaking legacy client scrapers. |

---

## 8. TEST RESULTS

Executed via: `gradle :app:testDebugUnitTest --rerun-tasks`

| Test Suite | Total Tests | Passed | Failed | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `com.example.SearchOrderArchitectureTest` | 20 | 20 | 0 | 0 | **PASS** |
| `com.example.CineStreamAdminLogicTest` | 79 | 79 | 0 | 0 | **PASS** |
| `com.example.ExampleRobolectricTest` | 2 | 2 | 0 | 0 | **PASS** |
| `com.example.ExampleUnitTest` | 1 | 1 | 0 | 0 | **PASS** |
| `com.example.FirebaseTest` | 1 | 1 | 0 | 0 | **PASS** |
| **TOTAL** | **103** | **103** | **0** | **0** | **100% PASS** |

### Specific Focused Tests Verified in `SearchOrderArchitectureTest`:
1. `testMovieOrderSerialization`: Verifies `movie` list serializes to document map and deserializes correctly.
2. `testTvOrderSerializationWithDualWriteAlias`: Verifies `tv` serializes with `series` alias and deserializes from either key.
3. `testAnimeOrderSerialization`: Verifies `anime` list serializes and deserializes cleanly.
4. `testDeterministicOrderingPreservation`: Verifies exact sequence preservation across serialization cycles.
5. `testEmptyListValidationAndSerialization`: Verifies clean initial state with empty lists is valid.
6. `testDuplicateIdsRejectedAcrossAllCategories`: Rejects duplicates in Movies, TV, and Anime lists.
7. `testAnimeOnlyCannotEnterMovieOrder`: Asserts Anime-only scraper is rejected from Movie search order.
8. `testAnimeOnlyCannotEnterTvOrder`: Asserts Anime-only scraper is rejected from TV search order.
9. `testMovieOnlyCannotEnterAnimeOrder`: Asserts Movie-only scraper is rejected from Anime search order.
10. `testTvOnlyCannotEnterMovieOrder`: Asserts TV-only scraper is rejected from Movie search order.
11. `testMultiCategoryCanEnterAllSupportedLists`: Asserts multi-category scraper passes in all 3 lists simultaneously.
12. `testDisabledExtensionDoesNotBecomeExecutableMerelyBecauseInConfiguration`: Asserts `isOperational` remains false for disabled/maintenance scrapers.
13. `testReEnabledExtensionPreservesValidOrdering`: Asserts toggling active -> disabled -> active retains list index without data loss.
14. `testDeletedOrUnknownExtensionIdRejected`: Rejects non-catalog IDs with domain error.
15. `testCategoryIsolationOnOrderUpdate`: Updating TV order leaves Movie and Anime lists completely intact.
16. `testReorderingStepOperations`: Verifies index swap logic for move up, move down, remove, and add.
17. `testUnlimitedListLengthSemantics`: Verifies support for 25+ scrapers without arbitrary caps.
18. `testDefaultSearchOrderGeneration`: Tests generation of capability-filtered default orders based on priority.
19. `testLegacyPriorityDecoupling`: Confirms `ManagedExtension.priority` is not overwritten by search order list positions.
20. `testCanonicalPathsIntegrity`: Confirms constants match canonical contract definitions.

---

## 9. BUILD RESULTS

- **Gradle Task:** `gradle assembleDebug`
- **Build Status:** `BUILD SUCCESSFUL in 5s`
- **Output APK Path:** `app/build/outputs/apk/debug/app-debug.apk`
- **APK Size:** 29 MB (30,103,138 bytes)
- **APK SHA-256:** `838e8c0dce018108be53ea8392cac15420a679575bc144025cb441b42fbdb5d2`

---

## 10. SECURITY SCAN

Executed via: `python3 scripts/static_security_scan.py`

| Security Check | Result | Detail |
| :--- | :---: | :--- |
| Firestore Rules Version 2 | **PASS** | `rules_version = '2';` declared |
| Admin Authority `/admins/{uid}.enabled == true` | **PASS** | Canonical `/admins` authority verified |
| Admin Authority Ignores `users.role` | **PASS** | `users.role` is NEVER used in `isAdmin()` |
| Sensitive User Fields Protection | **PASS** | Total 37 protected fields found in rules |
| Support Conversations Owner & Role Boundary | **PASS** | `senderRole == 'user'` and ownership enforced |
| Pro Requests PENDING Enforced & Reviewer Protected | **PASS** | Pro request forge prevention verified |
| Managed Extensions Admin-Only Write | **PASS** | Catalog modification restricted to admin |
| Audit Logs Exclusively Admin Access | **PASS** | Standard users have zero read/write access to `/auditLogs` |
| Strict Catch-All Deny | **PASS** | `match /{document=**} { allow read, write: if false; }` present |
| Least-Privilege Android Permissions | **PASS** | No dangerous broad storage permissions found |
| Zero Hardcoded Cloud Admin Secrets | **PASS** | No leaked backend admin secrets or private keys in Kotlin code |
| Zero Dynamic Code Loading (DCL) | **PASS** | No executable bytecode or `DexClassLoader` invocations detected |
| **TOTAL STATIC CHECKS** | **12 / 12 PASSED** | **0 FAILED** |

### Firestore Security Rule Reference for `/config/search_order`:
In `firestore.rules`:
```javascript
match /config/{configId} {
  // Anyone authenticated can read global config (e.g. search_order, maintenance status)
  allow read: if isAuthenticated();
  // Only authoritative administrators can modify configuration
  allow write: if isAdmin();
}
```
Standard client applications have read-only access to `/config/search_order`; write access is strictly limited to verified administrators (`isAdmin()`).

---

## 11. OUT-OF-SCOPE ITEMS

The following items were explicitly excluded from this phase in compliance with project rules:
1. **Users App Automatic Synchronization:** Not implemented in this phase.
2. **Users App Legacy Removal:** Users App was not touched or modified.
3. **PlaybackOrchestrator:** Scraper orchestration engine belongs to a subsequent Users App phase.
4. **Early Playback / Quality Early-Stop:** Playback heuristics belong to the Users App runtime phase.
5. **Firebase Legacy Data Deletion:** Zero documents were deleted from `/extensions` or `/managed_extensions`.
6. **Firestore Rules Cutover / Deployment:** No rules were deployed to the live backend in this phase.

---

## 12. NEXT PHASE RECOMMENDATION

### Recommended Next Phase:
**PHASE EXT-CLIENT-01: USERS APP SEARCH ORDER CONSUMPTION & PLAYBACK ORCHESTRATION**

1. **Client Contract Alignment:** Implement read-only consumption of `/config/search_order` in the Users App.
2. **Eligibility Filtering:** In the Users App runtime, filter the search order list against local bundled scrapers (`isOperational && isSupported`).
3. **Playback Orchestration (`PlaybackOrchestrator`):** Implement the sequential search loop (searching top preferred sources, followed by remaining eligible sources).
4. **Telemetry & Failure Reporting:** Report malfunctioning scrapers back to the Admin desk without modifying the search order document from the client.
