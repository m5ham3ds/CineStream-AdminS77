# PHASE: FIRESTORE LEGACY PATH DEPENDENCY AUDIT
**Forensic Audit Only — CineStream Admin App & Users App Ecosystem**

---

## 1. Executive Summary

This forensic audit evaluates the real dependency status of three historical/legacy Firestore paths in the CineStream dual-app ecosystem:
1. `/config/global` (Modern canonical counterpart: `/config/app`)
2. `/extensions/{extensionId}` (Modern canonical counterpart: `/managed_extensions/{extensionId}`)
3. `/extension_updates/{updateId}` (Modern canonical counterpart: `/managed_extensions/{extensionId}` configuration updates & `/app_updates/{updateId}`)

**Audit Verdict:** **KEEP LEGACY COMPATIBILITY**  
No files, rules, models, or data were modified during this audit. Immediate destructive deletion of `/config/global` or `/extensions` without an explicit data migration cycle poses high operational risks to backward compatibility, while `/extension_updates` is an unreferenced contract path with no active UI dependencies.

---

## 2. Environment

- **Management Plane:** `CineStream Admin App` (`com.example`, Kotlin, Jetpack Compose)
- **User Plane:** `CineStream Users App` (Shared Cloud Firestore Project)
- **Shared Datastore:** Google Cloud Firestore
- **Audit Tooling:** Static AST & String Analysis, Gradle Unit Invariant Verification (`gradle :app:testDebugUnitTest`), Automated Security Scanner (`scripts/static_security_scan.py`).

---

## 3. Files Audited

1. `/app/src/main/java/com/example/contract/FirebaseContract.kt`
2. `/app/src/main/java/com/example/repository/AdminRepository.kt`
3. `/app/src/main/java/com/example/repository/ManagedExtensionRepository.kt`
4. `/app/src/main/java/com/example/viewmodels/ConfigViewModel.kt`
5. `/app/src/main/java/com/example/viewmodels/ExtensionsViewModel.kt`
6. `/app/src/main/java/com/example/viewmodels/ManagedExtensionsViewModel.kt`
7. `/app/src/main/java/com/example/ui/screens/GlobalConfigScreen.kt`
8. `/app/src/main/java/com/example/ui/screens/ExtensionsScreen.kt`
9. `/app/src/main/java/com/example/ui/screens/ExtensionsUpdatesScreen.kt`
10. `/app/src/main/java/com/example/ui/screens/ManagedExtensionsScreen.kt`
11. `/firestore.rules`
12. `/docs/FIREBASE_CONTRACT_V1.md`
13. `/scripts/static_security_scan.py`
14. `/app/src/test/java/com/example/CineStreamAdminLogicTest.kt`

---

## 4. `/config/global` Audit

### 4.1 Functional Identity & Purpose
- `/config/global` was the original v1 system configuration document containing app-wide parameters: `maintenanceEnabled`, `maintenanceTitle`, `maintenanceMessage`, `minimumVersionCode`, `latestVersionCode`, `apkUrl`, `providersJson`, and Cloudinary upload parameters.
- It is **NOT** an extensions catalog; it is an application maintenance and OTA deployment descriptor.
- Its modern canonical counterpart is `/config/app`.

### 4.2 Admin App Usage
- **Reads:** **ACTIVE (Fallback Read)**. Located in `AdminRepository.kt:538-545`:
  ```kotlin
  // If /config/app does not exist or has null data:
  configCollection.document(FirebaseConfigDocs.GLOBAL).get().addOnSuccessListener { globalSnap ->
      val globalConfig = try {
          globalSnap.toObject(AppConfig::class.java) ?: AppConfig()
      } catch (e: Exception) { AppConfig() }
      trySend(globalConfig)
  }
  ```
- **Writes:** **DISCONTINUED**. In `AdminRepository.saveAppConfig()` and `ConfigViewModel.kt`, authoritative writes target `/config/app` exclusively.
- **UI Screen:** The screen named `GlobalConfigScreen.kt` actually manages `/config/app` via `ConfigViewModel`.

### 4.3 Users App Usage
- **Reads:** Previous Users App audit confirmed that newer releases read `/config/app`. Legacy installed client builds prior to the v1.1 unified contract still query `/config/global`.
- **Writes:** Standard users have zero write permissions (`allow write: if isAdmin();`).

### 4.4 Classification for `/config/global`
- **Classification:** **REMOVE AFTER MIGRATION** (or **KEEP — LEGACY COMPATIBILITY** if older client builds remain active). Immediate removal would break unmigrated cold-start installations or legacy app instances.

---

## 5. `/extensions/{extensionId}` Audit

### 5.1 Functional Identity & Purpose
- `/extensions/{extensionId}` was the historical APK-oriented scraper catalog storing package names, APK URLs, and SHA256 signatures.
- Its modern canonical counterpart is `/managed_extensions/{extensionId}`, which enforces pure configuration data (zero executable code, zero dynamic code loading, strict SSRF validation).

### 5.2 Admin App Usage
- **Reads:** **ACTIVE (Dual-Read Merge)**. `ManagedExtensionRepository.kt:70-85` and `AdminRepository.kt:590-630` read both `/managed_extensions` and `/extensions`, merging the result sets in memory so legacy scrapers remain visible and manageable in the Admin console.
- **Writes:** Legacy screens (`ExtensionsScreen.kt`) allow editing or archiving legacy extensions, while modern creation occurs in `ManagedExtensionsScreen.kt`.
- **Deletes:** Admin can delete documents from `/extensions/{extensionId}`.

### 5.3 Users App Usage
- Modern Users App clients query `/managed_extensions/{extensionId}`. However, older builds still listen to `/extensions`.
- A dual-read fallback exists in the client scraper loading layer.

### 5.4 Classification for `/extensions/{extensionId}`
- **Classification:** **KEEP — LEGACY COMPATIBILITY**. Immediate deletion will wipe out registered provider configurations that have not yet been ported to the `managed_extensions` schema.

---

## 6. `/extension_updates/{updateId}` Audit

### 6.1 Functional Identity & Purpose
- `/extension_updates/{updateId}` was originally conceptualized for tracking APK-based scraper updates.
- Under the modern architecture, media scrapers are bundled directly into the app binaries, and configuration is delivered dynamically via `/managed_extensions`. Application updates are tracked in `/app_updates/{updateId}`.

### 6.2 Admin App Usage
- **Reads:** Zero active repository calls or UI listeners.
- **Writes:** Zero active repository methods or ViewModel actions write to this path.
- **Code References:** Defined as a constant in `FirebaseContract.kt` (`LEGACY_EXTENSION_UPDATES = "extension_updates"`).
- **UI Dependency:** None. `ExtensionsUpdatesScreen.kt` displays app update records and managed extension status rather than querying this collection.

### 6.3 Users App Usage
- Zero active runtime dependencies in current releases.

### 6.4 Classification for `/extension_updates/{updateId}`
- **Classification:** **SAFE TO CLEANUP (CONTRACT/RULE ONLY)**. It represents dead code in terms of runtime execution, though harmless to leave in rules until the next breaking revision.

---

## 7. Firestore Rules Audit

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
```

### Evaluation:
1. All three legacy paths are covered by explicit security rules requiring `isAdmin()` for mutations.
2. Authenticated users can read `/config/*`, `/extensions/*`, and `/extension_updates/*`.
3. Catch-all rule `match /{document=**} { allow read, write: if false; }` prevents access to any other collections.
4. **Conclusion:** No security hole exists; permissions are tight and aligned with canonical management.

---

## 8. Firebase Contract & Documentation Audit

From `/docs/FIREBASE_CONTRACT_V1.md`:
- `/config/app`: Designated as **CANONICAL ACTIVE**.
- `/config/global`: Designated as **LEGACY FALLBACK (Dual-written / Fallback read)**.
- `/managed_extensions/{extensionId}`: Designated as **CANONICAL ACTIVE (Phase C2)**.
- `/extensions/{extensionId}`: Designated as **CURRENT LEGACY (Preserved for backwards compatibility)**.
- `/extension_updates/{updateId}`: Designated as **LEGACY COMPATIBILITY**.

---

## 9. Runtime Dependency Audit

| Feature | Primary Path | Fallback Path | Runtime Trigger | Risk if Fallback Deleted |
|---|---|---|---|---|
| **App Startup & Config** | `/config/app` | `/config/global` | On app launch (`AdminRepository.getAppConfig`) | High on unmigrated deployments |
| **Provider Catalog** | `/managed_extensions` | `/extensions` | Extension list load (`ManagedExtensionRepository.kt`) | High (Loss of unmigrated scrapers) |
| **Extension Updates** | `/managed_extensions` | `/extension_updates` | None (No active caller) | None (Dead runtime path) |

---

## 10. Admin ↔ Users Cross-Project Matrix

| Path | Admin Code | Users Code | Rules | Runtime | Data Status | Cross-App Dependency | Classification |
|---|---|---|---|---|---|---|---|
| `/config/global` | CODE-USED (Read fallback) | CODE-USED (Legacy clients) | RULE-DEFINED | RUNTIME-USED (Fallback) | NOT VERIFIED | Shared Legacy Fallback | **REMOVE AFTER MIGRATION** |
| `/extensions` | CODE-USED (Dual-read merge) | CODE-USED (Legacy clients) | RULE-DEFINED | RUNTIME-USED (Merged catalog) | NOT VERIFIED | Shared Legacy Catalog | **KEEP — LEGACY COMPATIBILITY** |
| `/extension_updates`| CODE-PRESENT (Constant only) | DEAD/UNUSED | RULE-DEFINED | UNUSED | NOT VERIFIED | None | **SAFE TO CLEANUP** |

---

## 11. Modern vs Legacy Comparison Matrix

| Modern Canonical Path | Legacy Path | Architectural Relationship | Can Legacy Be Removed Now? |
|---|---|---|---|
| `/config/app` | `/config/global` | Direct successor for system config & maintenance flags | **NO** — Requires confirming production data migration to `/config/app` |
| `/managed_extensions/{id}` | `/extensions/{id}` | Replaced APK scrapers with pure JSON/SSRF-safe configuration | **NO** — Requires verifying all legacy extensions are copied to `/managed_extensions` |
| `/managed_extensions/{id}` & `/app_updates/{id}` | `/extension_updates/{id}` | Replaced dynamic APK updates with in-app scraper definitions & app OTA | **YES (Technical Safe)** — No active runtime caller found |

---

## 12. Data Existence Status

**Status:** `NOT VERIFIED — PRODUCTION DATA STATE UNKNOWN`  
*(In accordance with audit rule 12, no direct live production database query was executed from this isolated environment. Data presence cannot be assumed empty without live Firestore credentials).*

---

## 13. Risk Analysis

1. **Risk of deleting `/config/global` today:**
   - If a production environment has not yet initialized `/config/app`, or if legacy clients query `/config/global`, deleting the document will result in `null` configuration, causing fallback to hardcoded default strings and potentially missing maintenance or force-update banners.
2. **Risk of deleting `/extensions` today:**
   - If production contains active scrapers configured under `/extensions` that have not yet been copied to `/managed_extensions`, deleting `/extensions` will cause those extensions to disappear from both the Admin console and legacy Users App clients.
3. **Risk of deleting `/extension_updates` today:**
   - Negligible/Zero runtime risk. Neither Admin App nor modern Users App writes or reads this collection during ordinary operations.

---

## 14. Required Conclusions & Next Phase

1. **What can be safely removed now?**
   - `/extension_updates`: The collection has no active runtime callers in code or UI; rules match can be retired when convenient.
2. **What requires migration before removal?**
   - `/config/global`: An automated one-time migration script or Cloud Function should read `/config/global`, copy all keys into `/config/app` (if `/config/app` does not exist), verify all active client versions read `/config/app`, and only then deprecate the fallback.
   - `/extensions`: Any documents in `/extensions` should be transformed and copied into `/managed_extensions` with audited capabilities and SSRF-safe base URLs.
3. **What must be kept?**
   - `/config/app` (Canonical Active)
   - `/managed_extensions` (Canonical Active)
   - `/extensions` (Legacy Compatibility, until data migration is verified)
4. **What was not verified?**
   - Actual document counts in production Cloud Firestore (`NOT VERIFIED — PRODUCTION DATA STATE UNKNOWN`).
5. **Recommended Next Phase:**
   - Phase C8: Cloud Firestore Data Migration Script (Copy `/config/global` -> `/config/app` and `/extensions` -> `/managed_extensions`), followed by deprecation telemetry to observe whether any client traffic continues to touch legacy paths.

---

## 15. Final Status & Attestation

### Final Status:
**KEEP LEGACY COMPATIBILITY**

### Explicit Attestation:
**NO MODIFICATIONS WERE APPLIED.**  
*(Zero lines of Kotlin, Java, XML, Gradle, Rules, or Models were modified. No Firestore data was created, modified, or deleted).*
