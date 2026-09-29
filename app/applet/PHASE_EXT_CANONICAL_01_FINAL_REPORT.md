# PHASE EXT-CANONICAL-01 — CINESTREAM ADMIN APP
# CANONICAL EXTENSION + SEARCH ORDER ARCHITECTURE
# FORENSIC → DESIGN → IMPLEMENTATION → VERIFICATION REPORT

**TARGET PROJECT:** CineStream Admin App (Management Plane)  
**ENVIRONMENT:** Android / Kotlin / Jetpack Compose / Cloud Firestore  
**PHASE STATUS:** COMPLETE & VERIFIED (`BUILD SUCCESSFUL`, 100% Tests Passed)  
**DATE:** 2026-09-29  

---

## 1. EXECUTIVE SUMMARY & COMPLIANCE ATTESTATION

Phase **EXT-CANONICAL-01** establishes the canonical Admin-side extension configuration and content-type-specific search ordering architecture for the CineStream ecosystem.

### Hard Rules Compliance Verification:
1. **Work Scope:** Strictly restricted to the **Admin App** repository (`/app/applet`).
2. **Users App Integrity:** No modifications made to Users App, Users App ZIP, or client-side runtime.
3. **Data Preservation:** Zero documents deleted from `/extensions`, `/managed_extensions`, or `/extension_updates`.
4. **Security Rules:** No destructive Firestore security rules deployed in this phase.
5. **Runtime Compatibility:** Dual-read, dual-write, and fallback mechanisms for `/extensions` remain fully active in the Admin runtime.
6. **Code Safety & Security:**
   - **ZERO Dynamic Code Loading:** No `DexClassLoader`, no `PathClassLoader`, no dynamic bytecode execution.
   - **ZERO Reflection:** No reflection-based scraper discovery or dynamic Kotlin/Java loading from remote sources.
   - **Configuration Only:** Firestore stores pure configuration/metadata (ordered string identifiers of bundled scrapers).
   - **Scraper Implementations:** Scrapers remain bundled as compiled native code in the client applications.

---

## 2. ARCHITECTURAL DECOUPLING: EXTENSIONS VS SEARCH ORDERING

Prior to this phase, the application relied exclusively on a generic `priority: Int` attribute inside individual extension documents. This created three fundamental operational bottlenecks:
1. A global priority cannot express content-type specialization (e.g. Scraper A is superior for Movies, while Scraper B is superior for Anime).
2. Updating execution sequence required modifying individual extension records, triggering unnecessary cache invalidations across the catalog.
3. Users App orchestrator had no clean, single-point configuration document to determine execution order.

### Decoupled Separation of Concerns:

| Architectural Plane | Extension Management (`/managed_extensions`) | Search Ordering (`/config/search_order`) |
| :--- | :--- | :--- |
| **Responsibility** | Identity, Base URLs, Capabilities, Maintenance Status, Diagnostic Probes | Deterministic scraper execution order partitioned by content type |
| **Document Target** | `/managed_extensions/{extensionId}` | `/config/search_order` |
| **Schema Type** | Full catalog record (`ManagedExtension`) | Ordered string arrays (`SearchOrderConfig`) |
| **Mutability** | Managed by individual extension CRUD | Reordered independently via drag/stepper UI |
| **Users App Role** | Capability & endpoint discovery | Orchestration pipeline execution sequence |

---

## 3. CANONICAL DATA CONTRACT & FIRESTORE PATH

The search order is stored at the canonical singleton path:
`Firestore: /config/search_order`

### Document Schema:
```json
{
  "movie": [
    "qfilm",
    "egydead",
    "cinedownload"
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

### Dual-Write & Compatibility Invariants:
- **`tv` & `series` Dual-Write Alias:** Both `tv` and `series` keys are persisted identically in the Firestore document. If downstream scraper engines or older client versions read `series`, full forward and backward compatibility is guaranteed.
- **Unbounded Lists:** The list length is completely dynamic (1, 2, 3, 4, 5, ...). No artificial limit is imposed.
- **Deterministic Identity:** Only clean, trimmed `extensionId` strings are persisted.

---

## 4. IMPLEMENTATION ARTIFACTS IN ADMIN APP

The canonical search order architecture was implemented using modular, clean architecture components:

### A. Data Model: `SearchOrderConfig.kt`
- Location: `app/src/main/java/com/example/models/SearchOrderConfig.kt`
- Defines `SearchOrderCategory` enum (`MOVIE`, `TV`, `ANIME`) with localized Arabic/English labels and category string parser.
- Implements `SearchOrderConfig` data class with immutable transformation methods (`withOrderForCategory`, `getOrderForCategory`).
- Provides robust Firestore serialization (`toMap`) with `tv`/`series` dual-write and deserialization (`fromMap`, `fromDocument`) handling nulls, lists, and legacy comma-separated string formats.

### B. Domain Validation: `SearchOrderValidator.kt`
- Location: `app/src/main/java/com/example/validation/SearchOrderValidator.kt`
- **Duplicate Prevention:** Detects and rejects any duplicate extension IDs within the same category list.
- **Catalog Existence Verification:** Validates that every configured extension ID exists in the registered extension catalog.
- **Content-Type Compatibility Enforcement:** Validates that an extension actually supports the category it is placed in (e.g., an Anime-only extension cannot be saved to the Movies search order).
- **Empty List Tolerance:** Allows clean initial state or unconfigured categories without validation crashes.

### C. Authoritative Repository: `SearchOrderRepository.kt`
- Location: `app/src/main/java/com/example/repository/SearchOrderRepository.kt`
- Real-time observation via `callbackFlow` listening to `/config/search_order`.
- Direct server fetch with local cache fallback for resilient cold starts.
- Pre-persistence validation check before writing to Firestore with merge semantics (`SetOptions.merge()`).
- Automated audit logging (`AdminRepository.logAudit`) on every search order modification.
- Automated capability-based default ordering generation (`getDefaultSearchOrder`).

### D. Presentation & State: `SearchOrderViewModel.kt`
- Location: `app/src/main/java/com/example/viewmodels/SearchOrderViewModel.kt`
- Dual-source reactive streams combining `getAllManagedExtensions()` and `getSearchOrder()`.
- Reactive `isDirty` StateFlow tracking unsaved administrative adjustments.
- Deterministic reordering operations: `moveUp`, `moveDown`, `remove`, `add`, `resetToDefaults`, `discardChanges`.
- Comprehensive feedback handling via snackbar alerts.

### E. User Interface: `SearchOrderScreen.kt`
- Location: `app/src/main/java/com/example/ui/screens/SearchOrderScreen.kt`
- Material 3 Compose layout fully adhering to CineStream design system.
- Category selector tabs (Movies, TV Series, Anime) with active extension counts.
- Priority list with position badges (1st, 2nd, 3rd, ...), up/down arrows, and removal action.
- "Disabled in Catalog" warning badges: Keeps inactive extensions in the search order if desired, but visually flags them as ineligible at runtime.
- "Add Extension" modal showing only eligible extensions for the selected content type with capability pills.
- Floating Action Button with animated dirty indicator for one-tap atomic persistence.
- Drawer and route integration in `AppNavigation.kt`.

---

## 5. VERIFICATION & UNIT TESTS

All business logic, serialization aliases, validation invariants, and reordering operations are verified by unit tests in `CineStreamAdminLogicTest.kt`:

1. `testSearchOrderConfigSerializationAndAliases`:
   - Verifies `movie`, `tv`, and `anime` serialization.
   - Verifies dual-write alias `series == tv`.
   - Verifies deserialization fallback when `tv` is absent.
2. `testSearchOrderCategoryMapping`:
   - Verifies string-to-enum resolution across case variations.
   - Verifies category list isolation.
3. `testSearchOrderValidatorDuplicateDetection`:
   - Asserts that duplicate extension IDs are caught and rejected.
4. `testSearchOrderValidatorNonexistentExtension`:
   - Asserts that non-catalog extension IDs produce validation errors.
5. `testSearchOrderValidatorContentTypeMismatch`:
   - Asserts that extensions lacking the target content type are rejected.
6. `testSearchOrderReorderingLogic`:
   - Asserts index swaps for moveUp, moveDown, addition, and deletion.

### Test Execution Output:
```
BUILD SUCCESSFUL in 30s
33 actionable tasks: 3 executed, 30 up-to-date
Configuration cache entry reused.
Result: 100% of unit tests passing.
```

---

## 6. TRANSITION READINESS FOR USERS APP

With Phase **EXT-CANONICAL-01** completed:
1. The Admin App now possesses a dedicated, intuitive management plane for content-type search priority policies.
2. The data structure is saved strictly at `/config/search_order` in Firestore.
3. Subsequent phases can safely implement the Users App orchestrator (`PlaybackOrchestrator`) reading `/config/search_order` to determine scraper execution sequence per content item.
