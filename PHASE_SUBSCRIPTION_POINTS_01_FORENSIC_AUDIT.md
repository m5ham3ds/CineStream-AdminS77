# PHASE SUBSCRIPTION-POINTS-01: CINESTREAM ADMIN APP
# CANONICAL SUBSCRIPTION + POINTS ECONOMY FORENSIC AUDIT REPORT

**APPLICATION:** CineStream Admin App  
**PACKAGE / NAMESPACE:** `com.example`  
**APPLICATION ID:** `com.aistudio.cinestreamadmin.cgmmpx`  
**TARGET SDK / MIN SDK:** Target SDK 36 (Android 16), Min SDK 24 (Android 7.0)  
**PROGRAMMING LANGUAGE:** Kotlin (100%)  
**UI FRAMEWORK:** Jetpack Compose with Material Design 3 (M3)  
**PHASE TYPE:** FORENSIC AUDIT + CONTRACT DISCOVERY ONLY  
**EXECUTION DATE:** 2026-09-30  
**AUDIT VERDICT:** **`CURRENT SUBSCRIPTION SYSTEM MAPPED | POINTS ECONOMY = NOT IMPLEMENTED`**  

---

## 1. ATTESTATION OF ZERO MUTATIONS & BOUNDARY GUARANTEES

In strict compliance with the Phase Stop Conditions and Primary Objective:
1. **Zero System Modifications:** The existing subscription codebase was **NOT** modified.
2. **Zero Users App Modifications:** The Users App was not touched (isolated session).
3. **Zero Security Rules Modifications:** `firestore.rules` was **NOT** modified or deployed.
4. **Zero Production Data Mutations:** No writes, updates, deletions, or reads against production Firestore occurred.
5. **Zero New Collections Created:** No collections (`/subscriptions`, `/points`, `/tasks`, `/rewards`, `/leaderboard`, etc.) were provisioned.
6. **Zero Migrations Executed:** No data migration scripts or field transformations were executed.
7. **Zero Legacy Deletions:** All legacy and compatibility fields remain 100% intact.

---

## 2. EXECUTIVE SUMMARY & INVENTORY DASHBOARD

| Component / Requirement | Target Future State | Discovered Current State | Exact Classification |
|---|---|---|---|
| **Free Plan** | Ad-supported basic access | Present on `/users/{uid}` (`subscriptionTier = "free"`) | **IMPLEMENTED** |
| **Pro Plan** | Ad-free access (30 days / custom) | Present on `/users/{uid}` (`subscriptionTier = "pro"`, 30d preset) | **IMPLEMENTED** |
| **Pro Lite Plan** | Ad-free access (1d, 7d, 10d) | Zero occurrences across entire codebase/models/UI | **NOT PRESENT** |
| **Pro Lite Durations (1d, 7d, 10d)** | Presets for 1, 7, 10 days | Presets in Admin UI are 30d, 90d, 180d, 365d, Lifetime, Custom | **NOT PRESENT** |
| **Intended Pro Benefit** | Remove ads for active period | Ad-free entitlement derived from `isSubscriptionActive` | **CONFIRMED EXCLUSIVE BENEFIT** |
| **Payment Gateways (Play Billing/Stripe)** | Automated payment processing | Zero gateway SDKs; manual proof workflow via `/pro_requests` | **MANUAL REVIEW ONLY** |
| **Points Balance & Ledger** | User points balance & ledger | Zero models, repositories, UI, or Firestore rules | **NOT IMPLEMENTED** |
| **Daily Login Rewards** | Streak & daily claim rewards | Only `lastLoginTimestamp` exists (for 30-day activity tracking) | **NOT IMPLEMENTED** |
| **Reward Tasks / Missions** | Admin-configured reward tasks | Zero task definitions, claim pipelines, or admin UI | **NOT IMPLEMENTED** |
| **Rewarded Advertisements** | Watch ad -> Earn points | Only `defaultForcedAds: 5` in DRM config (offline decryption) | **NOT IMPLEMENTED** |
| **Mini-Games / Game Rewards** | Games earning points | Zero game engines, models, or point hooks | **NOT IMPLEMENTED** |
| **Leaderboard / Ranking** | Top users points ranking | Zero ranking models, leaderboards, or score calculators | **NOT IMPLEMENTED** |
| **Feature Control Switches** | Admin toggle (ACTIVE / COMING_SOON / DISABLED) | Zero feature flag models; only app maintenance mode exists | **NOT IMPLEMENTED** |
| **Canonical Subscription Path** | Centralized storage path | Stored directly in `/users/{uid}` document | **CANONICAL ACTIVE: `/users/{uid}`** |
| **Upgrade Requests Queue** | Manual user review queue | Stored at `/pro_requests/{requestId}` | **CANONICAL ACTIVE: `/pro_requests`** |

---

## 3. EXISTING SUBSCRIPTION SYSTEM FORENSIC AUDIT

A complete forensic search was conducted across all Kotlin source files, layout/screens, viewmodels, repositories, security rules, models, and configuration files.

### 3.1 Source File Inventory

The subscription architecture is implemented across the following specific files:

1. **Model Layer:**
   - `app/src/main/java/com/example/models/Models.kt`: Lines 5–9 (`SubscriptionState`), Lines 12–121 (`User` model with canonical and compatibility subscription fields, and `subscriptionState` deterministic state machine).
   - `app/src/main/java/com/example/models/ProRequest.kt`: Lines 12–27 (`ProRequestStatus` enum: `PENDING`, `APPROVED`, `REJECTED`), Lines 34–67 (`ProRequest` model).
   - `app/src/main/java/com/example/models/DashboardAnalytics.kt`: Lines 15–27 (`UserMetrics` breakdown of free, pro, active, expired), Lines 43–48 (`ProRequestMetrics`), Lines 79–132 (`DashboardAnalyticsCalculator.calculateUserMetrics`).

2. **Repository Layer:**
   - `app/src/main/java/com/example/repository/AdminRepository.kt`:
     - Lines 63–163: `docToUser()` deserializes canonical fields (`isPremium`, `subscriptionTier`, `subscriptionStatus`, `subscriptionExpiresAt`) with fallbacks to compatibility fields (`isPro`, `plan`, `proPlan`, `proExpiresAt`).
     - Lines 436–471: `updateSubscription(userId, tier, expiresAt, status)` writes both canonical and compatibility fields to `/users/{userId}` and writes an audit log to `/auditLogs`.
     - Lines 474–479: `revokeSubscription(userId)` resets user to `tier = "free"`, `isPremium = false`, `expiresAt = null`.
   - `app/src/main/java/com/example/repository/ProRequestRepository.kt`:
     - Lines 45–82: `getAllProRequests()` real-time listener on `/pro_requests`.
     - Lines 97–158: `approveRequest(requestId, adminNote)` atomic transaction updating `/pro_requests/{requestId}.status = "APPROVED"`.
     - Lines 165–228: `rejectRequest(requestId, reason, adminNote)` atomic transaction updating `/pro_requests/{requestId}.status = "REJECTED"`.
     - Lines 233–247: `deleteRequest(requestId)` deletes request document.

3. **ViewModel Layer:**
   - `app/src/main/java/com/example/viewmodels/ViewModels.kt`:
     - Lines 152, 178: `UsersViewModel` filtering by `SubscriptionState.ACTIVE_PRO` and KPI counting.
     - Lines 269–275: Quick toggle subscription from users list.
     - Lines 373–409: `UserDetailViewModel.grantSubscription(tier, durationDays)` and `revokeSubscription()`.
   - `app/src/main/java/com/example/viewmodels/ProRequestsViewModel.kt`:
     - Lines 107–123: `approveRequest()`.
     - Lines 125–144: `rejectRequest()`.

4. **UI Layer:**
   - `app/src/main/java/com/example/ui/screens/UserDetailScreen.kt`:
     - Lines 177–188: Dialog trigger for `SubscriptionManagementDialog`.
     - Lines 1363–1435: `SubscriptionManagementDialog` with Tier selection (`"pro"`, `"vip"`) and Duration presets (30d, 90d, 180d, 365d, Lifetime, Custom).
     - Lines 191–225: Subscription Revocation confirmation dialog.
   - `app/src/main/java/com/example/ui/screens/ProRequestsScreen.kt`:
     - Full operational screen reviewing `/pro_requests`, proof inspection, approval dialog with optional admin note, rejection dialog with mandatory reason.
   - `app/src/main/java/com/example/ui/screens/DashboardOverviewContent.kt`:
     - Lines 251–344: `OperationalCard` for "Subscriptions & Plans" displaying `premiumUsers`, `freeUsers`, `activePremium`, `expiredPremium`, and `tierBreakdown` horizontal chip list.
     - Lines 396–442: `OperationalCard` for "Pro Upgrade Requests" displaying pending, approved, and rejected counts.
   - `app/src/main/java/com/example/ui/screens/NotificationScreen.kt`:
     - Lines 285–290: Audience targeting filter for `TargetType.PRO` ("مشتركو Pro" / "PRO Users").

5. **Security Contract & Rules:**
   - `firestore.rules`:
     - Lines 30–68: `modifyingSensitiveUserFields()` protects all subscription fields from client forgery.
     - Lines 71–111: `hasSafeUserCreationDefaults()` mandates `isPremium == false`, `subscriptionTier == 'free'`, `subscriptionExpiresAt == null`.
     - Lines 120–140: `match /users/{userId}` enforces that only admins can modify subscription fields.
     - Lines 247–259: `match /pro_requests/{requestId}` restricts status modification to admins only.
   - `app/src/main/java/com/example/contract/FirebaseContract.kt`:
     - Lines 23, 106: Declares `USERS = "users"` and `PRO_REQUESTS = "pro_requests"`.
   - `docs/FIREBASE_CONTRACT_V1.md`:
     - Section 4: Authoritative subscription field matrix, single-write model, and deterministic state machine.

---

## 4. CURRENT CANONICAL FIRESTORE PATHS

No separate `/subscriptions` or `/plans` collection exists in the database. Subscription data lives exclusively in two canonical collections:

### Path 1: `/users/{uid}` (Authoritative User Subscription Entitlement)
- **Path:** `/users/{uid}`
- **Purpose:** Stores the single-document user record including account credentials, profile, permissions, and subscription entitlement state.
- **Read Locations:**
  - Admin App: `AdminRepository.kt` (`getUsers()`, `getUser(uid)` via real-time listeners and server fetches).
  - Users App: Reads own document `/users/{request.auth.uid}`.
- **Write Locations:**
  - Admin App: `AdminRepository.updateSubscription()` and `revokeSubscription()`.
  - Users App: Registration creation only via `hasSafeUserCreationDefaults()`.
- **Owner Access:**
  - Read: Allowed (`isOwner(userId)`).
  - Create: Allowed on initial registration ONLY if safe defaults are met (`isPremium == false`, `subscriptionTier == "free"`, `subscriptionExpiresAt == null`).
  - Update: FORBIDDEN from updating any subscription fields via `modifyingSensitiveUserFields()`.
  - Delete: FORBIDDEN.
- **Admin Access:**
  - Read: Full read access to all user documents.
  - Create: Full provisioning capability.
  - Update: Full update capability (can modify `isPremium`, `subscriptionTier`, `subscriptionExpiresAt`, etc.).
  - Delete: Allowed (soft-decoupled or full delete).

### Path 2: `/pro_requests/{requestId}` (Manual Upgrade Verification Queue)
- **Path:** `/pro_requests/{requestId}`
- **Purpose:** Intake queue for user subscription upgrade requests accompanied by manual payment proofs (receipt screenshot URL and payment method).
- **Read Locations:**
  - Admin App: `ProRequestRepository.kt` (`getAllProRequests()`).
  - Users App: Read allowed only if `resource.data.userId == request.auth.uid`.
- **Write Locations:**
  - Admin App: `ProRequestRepository.approveRequest()`, `rejectRequest()`, `deleteRequest()`.
  - Users App: Create request with `status == "PENDING"`.
- **Owner Access:**
  - Read: Allowed for own requests.
  - Create: Allowed, strictly enforced to `status == "PENDING"`, and cannot set reviewer fields.
  - Update / Delete: FORBIDDEN.
- **Admin Access:**
  - Read: Full access.
  - Update: Full access (transitions `PENDING` -> `APPROVED` or `PENDING` -> `REJECTED`).
  - Delete: Full access.

### Path 3: `/auditLogs/{logId}` (Administrative Audit Trail)
- **Path:** `/auditLogs/{logId}`
- **Purpose:** Immutable append-only audit trail logging every administrative subscription change (`GRANT_SUBSCRIPTION`, `REVOKE_SUBSCRIPTION`, `APPROVE_PRO_REQUEST`, `REJECT_PRO_REQUEST`).
- **Read Locations:** Admin App only (`AuditLogsScreen.kt`). Standard users have zero access.

### Path 4: `/config/app` (System & Ad Rules Configuration)
- **Path:** `/config/app`
- **Purpose:** Holds system-wide defaults including `defaultForcedAds: Int = 5` and `defaultOfflineDays: Int = 2`.
- **Read Locations:** Admin App and Users App (`getAppConfig()`).
- **Write Locations:** Admin App only (`ConfigViewModel.updateDrmSettings()`).

### Non-Existent Paths (Checked & Confirmed Denied by Security Rules):
- `/subscriptions`: **NOT PRESENT** (Strictly blocked by fallback `match /{document=**} { allow read, write: if false; }`)
- `/userSubscriptions`: **NOT PRESENT** (Blocked by rules wildcard)
- `/plans`: **NOT PRESENT** (Blocked by rules wildcard)
- `/points`: **NOT PRESENT** (Blocked by rules wildcard)
- `/point_transactions`: **NOT PRESENT** (Blocked by rules wildcard)
- `/rewards`: **NOT PRESENT** (Blocked by rules wildcard)
- `/tasks`: **NOT PRESENT** (Blocked by rules wildcard)
- `/leaderboard`: **NOT PRESENT** (Blocked by rules wildcard)

---

## 5. CURRENT SUBSCRIPTION SCHEMA

### Document Surface 1: `/users/{uid}`

| Field Name | Type | Required / Optional | Writer | Reader | Purpose | Classification |
|---|---|---|---|---|---|---|
| `isPremium` | `Boolean` | Required (default `false`) | Admin | Admin / Owner | Authoritative binary entitlement flag | **CANONICAL ACTIVE** |
| `subscriptionTier` | `String` | Required (default `"free"`) | Admin | Admin / Owner | Authoritative plan identifier (`"free"`, `"pro"`, `"vip"`) | **CANONICAL ACTIVE** |
| `subscriptionStatus` | `String` | Required (default `"free"`) | Admin | Admin / Owner | Authoritative lifecycle status (`"active"`, `"free"`, `"expired"`) | **CANONICAL ACTIVE** |
| `subscriptionExpiresAt`| `Long?` | Optional (`null` for lifetime) | Admin | Admin / Owner | Expiration timestamp in epoch milliseconds | **CANONICAL ACTIVE** |
| `isPro` | `Boolean` | Required (mirrors `isPremium`) | Admin | Admin / Owner | Backward-compatible mirror for legacy consumers | **COMPATIBILITY MIRROR** |
| `plan` | `String?` | Optional (mirrors `subscriptionTier`)| Admin | Admin / Owner | Legacy plan field mirror | **LEGACY COMPATIBILITY** |
| `proPlan` | `String?` | Optional (mirrors `subscriptionTier`)| Admin | Admin / Owner | Legacy Pro plan field mirror | **COMPATIBILITY MIRROR** |
| `proExpiresAt` | `Long?` | Optional (mirrors `subscriptionExpiresAt`)| Admin | Admin / Owner | Legacy expiration field mirror | **COMPATIBILITY MIRROR** |
| `updatedAt` | `Long` | Required | Admin / Owner | Admin / Owner | Document last update timestamp in epoch millis | **CANONICAL ACTIVE** |
| `forcedAdsOverride` | `Int?` | Optional | Admin | Admin / Owner | Per-user override for forced ads count before decryption | **CANONICAL ACTIVE** |
| `offlineDaysOverride` | `Int?` | Optional | Admin | Admin / Owner | Per-user override for offline DRM valid days | **CANONICAL ACTIVE** |

*Note: Fields such as `durationDays`, `source`, `price`, `currency`, `transactionId`, `orderId`, and `paymentGateway` are **NOT PRESENT** on the user document.*

---

### Document Surface 2: `/pro_requests/{requestId}`

| Field Name | Type | Required / Optional | Writer | Reader | Purpose | Classification |
|---|---|---|---|---|---|---|
| `requestId` | `String` | Required | User / Client | Admin / Owner | Unique document identifier | **CANONICAL ACTIVE** |
| `userId` | `String` | Required | User | Admin / Owner | Foreign key to `/users/{uid}` | **CANONICAL ACTIVE** |
| `userEmail` | `String` | Required | User | Admin / Owner | Applicant user email | **CANONICAL ACTIVE** |
| `userName` | `String` | Required | User | Admin / Owner | Applicant user display name | **CANONICAL ACTIVE** |
| `requestedPlan` | `String` | Required (default `"pro"`) | User | Admin / Owner | Plan requested by user (currently hardcoded `"pro"`) | **CANONICAL ACTIVE** |
| `requestedDuration` | `String` | Required (default `"1 month"`)| User | Admin / Owner | Duration requested (e.g., `"1 month"`) | **CANONICAL ACTIVE** |
| `paymentMethod` | `String?` | Optional | User | Admin / Owner | Stated payment method (e.g. "Vodafone Cash", "USDT") | **CANONICAL ACTIVE** |
| `paymentProofUrl` | `String?` | Optional | User | Admin / Owner | Image URL to uploaded payment receipt screenshot | **CANONICAL ACTIVE** |
| `userNote` | `String` | Optional | User | Admin / Owner | User message to reviewer | **CANONICAL ACTIVE** |
| `status` | `String` | Required (`"PENDING"`) | User / Admin | Admin / Owner | Lifecycle state: `PENDING`, `APPROVED`, `REJECTED` | **CANONICAL ACTIVE** |
| `createdAt` | `Long` | Required | User | Admin / Owner | Request submission epoch millis | **CANONICAL ACTIVE** |
| `updatedAt` | `Long` | Required | User / Admin | Admin / Owner | Last review epoch millis | **CANONICAL ACTIVE** |
| `reviewedAt` | `Long?` | Optional | Admin | Admin / Owner | Admin review decision epoch millis | **CANONICAL ACTIVE** |
| `reviewedBy` | `String?` | Optional | Admin | Admin / Owner | UID of reviewing administrator | **CANONICAL ACTIVE** |
| `reviewedByEmail` | `String?` | Optional | Admin | Admin / Owner | Email of reviewing administrator | **CANONICAL ACTIVE** |
| `rejectionReason` | `String?` | Optional (Mandatory on reject) | Admin | Admin / Owner | Reason provided by admin when rejected | **CANONICAL ACTIVE** |
| `adminNote` | `String?` | Optional | Admin | Admin / Owner | Internal administrative remark | **CANONICAL ACTIVE** |

---

## 6. CURRENT PLAN INVENTORY

| Plan Key | Target Status in Requirements | Current Codebase Status | Evidence / Location |
|---|---|---|---|
| **`FREE`** | Active base plan | **IMPLEMENTED** | `Models.kt:27`, `AdminRepository.kt:478`, `firestore.rules:81` |
| **`PRO`** | Active 30-day ad-free plan | **IMPLEMENTED** | `Models.kt:39`, `UserDetailScreen.kt:1391`, `AdminRepository.kt:440` |
| **`VIP`** | Not requested in new requirements | **REFERENCED ONLY** | `UserDetailScreen.kt:1400`, `DashboardAnalytics.kt:22`. Sits alongside Pro without unique code entitlements. |
| **`PRO_LITE`** | Target plan (1d, 7d, 10d) | **NOT PRESENT** | Zero occurrences in models, code, UI, or rules. |

### Pro Lite Durations Audit:
- **1 DAY:** **NOT PRESENT** for subscriptions.
- **7 DAYS:** **NOT PRESENT** for subscriptions. (7 days only exists in `BanAccountDialog` for bans).
- **10 DAYS:** **NOT PRESENT** anywhere in codebase.
- **Existing Presets in `SubscriptionManagementDialog`:**
  - 30 Days (1 Month)
  - 90 Days (3 Months)
  - 180 Days (6 Months)
  - 365 Days (1 Year)
  - Lifetime (`null`)
  - Custom Days (text field)

---

## 7. CURRENT SUBSCRIPTION LIFECYCLE

```
[Account Creation in Users App]
       │
       ▼
[Enforce Safe Defaults via firestore.rules]
  isPremium = false, subscriptionTier = "free", subscriptionExpiresAt = null
       │
       ▼
[Plan: FREE (Ad-supported)]
       │
       ├─────────────────────────────────────────────────┐
       ▼                                                 ▼
[Direct Admin Grant (UserDetailScreen)]          [User Submits Upgrade Request]
  Admin selects: Tier ("pro"), Duration (30d)      User uploads payment receipt screenshot
  Writes to /users/{uid}:                          Writes to /pro_requests/{requestId}:
    isPremium = true, subscriptionTier = "pro",      status = "PENDING"
    subscriptionStatus = "active",                   paymentMethod, paymentProofUrl
    subscriptionExpiresAt = now + 30d                    │
    (plus all compatibility mirror fields)               ▼
       │                                         [Admin Reviews in ProRequestsScreen]
       │                                           Approves (PENDING -> APPROVED)
       │                                           or Rejects with Reason
       │                                                 │
       ├─────────────────────────────────────────────────┘
       ▼
[Active Pro Entitlement]
  Deterministic state: ACTIVE_PRO
  Benefit: Ad-free experience during active period
       │
       ├──────────────────────────────────────┐
       ▼                                      ▼
[Natural Expiration]                  [Admin Revocation (UserDetailScreen)]
  subscriptionExpiresAt <= now          Admin clicks Revoke Subscription
  Deterministically evaluates to:       Resets /users/{uid}:
  EXPIRED_PRO in memory.                 isPremium = false, subscriptionTier = "free",
  Firestore fields remain untouched      subscriptionStatus = "free", expiresAt = null
  until explicit Admin action.           State evaluates immediately to: FREE
```

### Critical Discovery on `ProRequestsScreen` Disconnection:
In `ProRequestRepository.kt:97-158`, approving a request (`approveRequest()`) executes a transaction on `/pro_requests/{requestId}` that sets `status = "APPROVED"`. **However, it does NOT write to `/users/{userId}`.**  
Currently, granting the actual subscription to the user's profile requires the administrator to navigate to `UserDetailScreen` and click "Grant / Extend Subscription". In the upcoming architecture, the approval of a request or points redemption must atomically update the user's subscription entitlement on `/users/{uid}`.

---

## 8. CURRENT AD ENTITLEMENT ARCHITECTURE

- **Classification:** **`FIREBASE + LOCAL DETERMINISTIC HYBRID`**
- **Decision Engine Trace:**
  1. **Firebase Entitlement Storage:** `/users/{uid}` stores `isPremium: Boolean`, `subscriptionTier: String`, and `subscriptionExpiresAt: Long?`.
  2. **In-Memory State Computation:** `User.subscriptionState` (in `Models.kt:97-117`) checks:
     ```kotlin
     val now = System.currentTimeMillis()
     val effectiveExpiresAt = subscriptionExpiresAt ?: proExpiresAt
     val hasProPlan = (subscriptionTier != "free" && subscriptionTier.isNotBlank()) || ...
     val hasProFlag = isPremium || isPro

     if (effectiveExpiresAt != null && effectiveExpiresAt <= now) {
         return if (hasProFlag || hasProPlan) SubscriptionState.EXPIRED_PRO else SubscriptionState.FREE
     }
     if (hasProFlag || hasProPlan) {
         return SubscriptionState.ACTIVE_PRO
     }
     return SubscriptionState.FREE
     ```
  3. **Ad Entitlement Evaluation:** `isSubscriptionActive` returns `true` if and only if `subscriptionState == SubscriptionState.ACTIVE_PRO`.
  4. **Ad Decision:** When `isSubscriptionActive == true`, advertisements are suppressed for the active period.
- **Admin App Ad Implementation:** The Admin App does **NOT** contain any Google Mobile Ads (AdMob) or third-party ad SDKs. It only configures DRM ad policy for the Users App ecosystem:
  - System default in `/config/app`: `defaultForcedAds: Int = 5` ("Number of rewarded ads required for offline decryption").
  - Per-user override in `/users/{uid}`: `forcedAdsOverride: Int?`.

---

## 9. PAYMENT SYSTEM AUDIT

A deep scan for payment systems, SDKs, and billing logic was conducted:

| Provider / Keyword | Current Status | Findings |
|---|---|---|
| **Google Play Billing (`BillingClient`)** | **NOT PRESENT** | Zero references in `build.gradle.kts`, `libs.versions.toml`, or code. |
| **Stripe SDK** | **NOT PRESENT** | Zero references in dependencies or source code. |
| **PayPal SDK** | **NOT PRESENT** | Zero references in dependencies or source code. |
| **RevenueCat (`purchases-android`)** | **NOT PRESENT** | Zero references in dependencies or source code. |
| **Purchase Token / Webhooks / Order Receipts** | **NOT PRESENT** | No automated order or receipt verification endpoints. |
| **Current Reality** | **MANUAL PENDING REVIEW** | Users upload off-band payment screenshots (`paymentProofUrl`) with a description (`paymentMethod`) via `/pro_requests`. Admin visually inspects and approves. |

---

## 10. POINTS ECONOMY & REWARDS FORENSIC AUDIT

A complete keyword search for `points`, `coins`, `credits`, `wallet`, `balance`, `dailyLogin`, `streak`, `tasks`, `missions`, `quests`, and `leaderboard` was conducted across all files.

### 10.1 Points System Audit Result
- **Status:** **`POINTS SYSTEM = NOT IMPLEMENTED`**
- **Points Balance:** NOT PRESENT (No `points` field on `/users/{uid}` or subcollections).
- **Points Earning / Spending Engine:** NOT PRESENT.
- **Points Transaction Ledger:** NOT PRESENT (No `/point_transactions` collection exists).
- **Admin Points Adjustments:** NOT PRESENT.

### 10.2 Daily Login Rewards Audit
- **Status:** **`DAILY LOGIN REWARD = NOT IMPLEMENTED`**
- **Existing Login State:** `lastLoginAt` and `lastLoginTimestamp` on `/users/{uid}` record the timestamp of the last login. This is used exclusively for active user categorization (30-day activity threshold in `DashboardAnalyticsCalculator`).
- **Login Streak:** NOT PRESENT.
- **Claim Mechanism / Duplicate Protection:** NOT PRESENT.
- **Server Time / Timezone Handling:** NOT PRESENT.

### 10.3 Reward Tasks / Missions Audit
- **Status:** **`REWARD TASKS = NOT IMPLEMENTED`**
- **Existing Tasks:** Zero task entities, mission models, or quest completion tracking exist in the repository or UI.
- **Admin Configuration:** The Admin App cannot currently create, edit, or toggle reward tasks.

### 10.4 Rewarded Advertisements for Points Audit
- **Status:** **`REWARDED ADS FOR POINTS = NOT IMPLEMENTED`**
- **Ad Provider:** No Mobile Ads SDK present in Admin App.
- **Offline DRM Forced Ads Reference:** The only ad-related logic is `defaultForcedAds: 5` in `/config/app`, representing offline playback video unlock ads, completely decoupled from points.

### 10.5 Game Rewards Audit
- **Status:** **`GAME REWARDS = NOT IMPLEMENTED`**
- Zero games, minigame hooks, game reward models, or game APIs exist in the codebase.

### 10.6 Leaderboard Audit
- **Status:** **`LEADERBOARD = NOT IMPLEMENTED`**
- Zero ranking models, leaderboard collections, score aggregation jobs, or top-user UI components exist in the codebase.

---

## 11. EXISTING ADMIN DASHBOARD AUDIT

The Admin App contains an operational analytics and overview dashboard (`DashboardOverviewContent.kt` and `DashboardScreen.kt`).

### Current Dashboard Cards:
1. **Users Overview Card:** Displays `totalUsers`, `activeUsers`, `inactiveUsers`, `bannedUsers`, `restrictedUsers`.
2. **Subscriptions & Plans KPI Card:**
   - Displays `premiumUsers` (total PRO/VIP users).
   - Displays `freeUsers` (users on Free tier).
   - Displays `activePremium` (users with `ACTIVE_PRO`).
   - Displays `expiredPremium` (users with `EXPIRED_PRO`).
   - Displays `tierBreakdown` (interactive horizontal chips showing user count per tier: `"free"`, `"pro"`, `"vip"`).
3. **Support Desk KPI Card:** Open, pending, and unread customer support inquiries.
4. **Pro Upgrade Requests KPI Card:** Displays `totalRequests`, `pendingRequests`, `approvedRequests`, `rejectedRequests`.
5. **Managed Scrapers / Extensions KPI Card:** Scraper health and catalog status.
6. **Reports & Malfunctions KPI Card:** User playback issue reports.
7. **System & OTA Config KPI Card:** Maintenance mode toggle, minimum app version, latest release.

### Widgets NOT PRESENT in Current Dashboard:
- Revenue / MRR widgets: **NOT PRESENT**
- Points Economy KPI: **NOT PRESENT**
- Daily Login Claims KPI: **NOT PRESENT**
- Reward Tasks KPI: **NOT PRESENT**
- Leaderboard ranking preview: **NOT PRESENT**
- Feature Control Switches: **NOT PRESENT**

---

## 12. EXISTING FIREBASE CONFIGURATION AUDIT

### Document 1: `/config/app` (Canonical System Configuration)
Stored fields (from `AppConfig` in `Models.kt:133-155`):
- `maintenanceEnabled: Boolean` (default `false`)
- `maintenanceTitle: String` ("Under Scheduled Maintenance")
- `maintenanceMessage: String`
- `minimumVersionCode: Int`
- `latestVersionCode: Int`
- `latestVersionName: String`
- `apkUrl: String`
- `apkSha256: String`
- `mandatoryUpdate: Boolean`
- `releaseNotes: String`
- `providersJson: String` (Scraper providers catalog)
- `defaultOfflineDays: Int` (default `2`)
- `defaultForcedAds: Int` (default `5`)
- `cloudinaryCloudName: String`
- `cloudinaryUploadPreset: String`
- `updatedAt: Long`

### Document 2: `/config/global` (Legacy Fallback)
Maintained as a read fallback in `AdminRepository.getAppConfig()` if `/config/app` does not exist.

### Document 3: `/config/search_order` (Search Catalog Order)
Stores scraping search order preferences for Movies, Series, and Anime.

### Feature Configuration Reality:
There is **NO** `/config/features` or feature-control system. No toggles currently exist for Subscriptions, Points, Daily Login, Tasks, or Leaderboard.

---

## 13. SECURITY RULES AUDIT (`firestore.rules`)

Inspection of `firestore.rules` reveals key enforcement patterns and barriers:

1. **Strict Admin Verification (`isAdmin()`):**
   Evaluates against `/admins/{uid}.enabled == true` or owner email `sulopros01@gmail.com`. Standard users cannot escalate privileges.
2. **Sensitive Fields Immutability:**
   Lines 30–68: `modifyingSensitiveUserFields()` protects 32 fields from user modification on `/users/{uid}`, including:
   `role`, `isPremium`, `subscriptionTier`, `subscriptionStatus`, `subscriptionExpiresAt`, `isPro`, `proExpiresAt`, `proPlan`, `plan`, `isActive`, `isBanned`, `forcedAdsOverride`, etc.
   *Implication:* When Points fields (e.g. `pointsBalance`, `pointsSpent`) are introduced, they MUST be added to `modifyingSensitiveUserFields()` or secured via subcollections to prevent client manipulation.
3. **Safe Registration Defaults:**
   Lines 71–111: `hasSafeUserCreationDefaults(userId)` mandates `isPremium == false`, `subscriptionTier == 'free'`.
4. **Deny-All Wildcard Barrier:**
   Line 304: `match /{document=**} { allow read, write: if false; }`.
   *Implication:* Any new collection (`/point_transactions`, `/reward_tasks`, `/leaderboard`, etc.) will be **immediately blocked** by Firestore security rules until explicitly declared.

---

## 14. GAP ANALYSIS FOR THE UPCOMING IMPLEMENTATION

To implement the upcoming **SUBSCRIPTION + POINTS + REWARDS + LEADERBOARD SYSTEM**, the following gaps must be bridged:

| Area | Current Reality | Required Canonical Architecture |
|---|---|---|
| **Subscription Plans** | `free`, `pro`, `vip` supported on `/users/{uid}` | Introduce canonical enum: `FREE`, `PRO_LITE`, `PRO`. Deprecate `VIP` gracefully. |
| **Plan Durations** | UI offers 30, 90, 180, 365, Lifetime, Custom | Add dedicated presets: `PRO_LITE` -> 1 Day, 7 Days, 10 Days; `PRO` -> 30 Days. |
| **Feature Control** | Only Maintenance Mode exists in `/config/app` | Create `/config/features` with flags: `subscriptions`, `points`, `dailyLogin`, `rewardTasks`, `leaderboard`. Support states: `ACTIVE`, `COMING_SOON`, `DISABLED`. |
| **Points Datastore** | Zero points data exists | Store `pointsBalance: Long` on `/users/{uid}` (rules-protected) + Immutable ledger in `/users/{uid}/point_transactions/{txId}`. |
| **Daily Login System** | Only `lastLoginTimestamp` exists | Store `streakCount: Int`, `lastDailyRewardDate: String` (YYYY-MM-DD), and server-timestamp validation. |
| **Reward Tasks** | Zero task data exists | Create catalog at `/reward_tasks/{taskId}` with metadata (points reward, cooldown, type) and user claim subcollection. |
| **Leaderboard** | Zero ranking data exists | Real-time aggregate query or periodic cloud document `/leaderboard/points_weekly` / `/leaderboard/points_all_time`. |
| **Subscription via Points** | Not possible | Atomically deduct points and grant `PRO_LITE` or `PRO` on `/users/{uid}`. |

---

## 15. CANONICAL BLUEPRINT PROPOSED FOR NEXT PHASE

### Proposed Schema 1: `/config/features` (System Feature Control)
```json
{
  "subscriptions": {
    "status": "ACTIVE",
    "disabledMessage": "هذه الميزة ستضاف قريبًا"
  },
  "points": {
    "status": "COMING_SOON",
    "disabledMessage": "هذه الميزة ستضاف قريبًا"
  },
  "dailyLogin": {
    "status": "COMING_SOON",
    "disabledMessage": "هذه الميزة ستضاف قريبًا"
  },
  "rewardTasks": {
    "status": "COMING_SOON",
    "disabledMessage": "هذه الميزة ستضاف قريبًا"
  },
  "leaderboard": {
    "status": "COMING_SOON",
    "disabledMessage": "هذه الميزة ستضاف قريبًا"
  },
  "updatedAt": 1727712000000,
  "updatedBy": "admin_uid"
}
```

### Proposed Schema 2: Points Extension on `/users/{uid}`
```json
{
  "pointsBalance": 150,
  "totalPointsEarned": 450,
  "totalPointsSpent": 300,
  "dailyLoginStreak": 3,
  "lastDailyLoginDate": "2026-09-30",
  "subscriptionTier": "pro_lite",
  "isPremium": true,
  "subscriptionStatus": "active",
  "subscriptionExpiresAt": 1728316800000
}
```

### Proposed Schema 3: Points Ledger `/users/{uid}/point_transactions/{txId}`
```json
{
  "txId": "tx_abc123",
  "userId": "uid_456",
  "type": "EARN_DAILY_LOGIN | EARN_REWARDED_AD | EARN_TASK | SPEND_SUBSCRIPTION | ADMIN_ADJUSTMENT",
  "amount": 50,
  "balanceAfter": 150,
  "description": "Daily login reward day 3",
  "referenceId": "daily_2026_09_30",
  "createdAt": 1727712000000
}
```

---

## 16. CONCLUSION

The forensic audit of the **CineStream Admin App** is complete.
1. The **Current Subscription System** is fully operational and mapped: it uses `/users/{uid}` as the single source of truth for entitlements, accompanied by the manual proof review pipeline at `/pro_requests/{requestId}`.
2. The **Points, Rewards, Daily Login, Tasks, and Leaderboard** economy does **NOT** exist in the codebase today.
3. The codebase is clean, compiles successfully, and provides a solid foundation for the subsequent implementation phases without risk of data corruption or contract regression.
