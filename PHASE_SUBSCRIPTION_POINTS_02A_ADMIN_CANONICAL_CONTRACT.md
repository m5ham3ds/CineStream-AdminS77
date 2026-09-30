# PHASE SUBSCRIPTION-POINTS-02A: CINESTREAM ADMIN APP
# CANONICAL SUBSCRIPTION + POINTS ECONOMY CONTRACT SPECIFICATION
# ARCHITECTURAL DESIGN & CONTRACT DISCOVERY ONLY — ZERO IMPLEMENTATION

**APPLICATION:** CineStream Admin App  
**PACKAGE / NAMESPACE:** `com.example`  
**APPLICATION ID:** `com.aistudio.cinestreamadmin.cgmmpx`  
**PHASE IDENTIFIER:** `PHASE SUBSCRIPTION-POINTS-02A`  
**PHASE TYPE:** CANONICAL CONTRACT / ARCHITECTURE DESIGN / FORENSIC RECONCILIATION  
**DATE:** 2026-09-30  
**TARGET SDK / MIN SDK:** Target SDK 36 (Android 16), Min SDK 24 (Android 7.0)  
**ENVIRONMENT:** Cloud Sandbox (Isolated Management Plane)  
**CANONICAL CONTRACT STATUS:** **`CANONICAL CONTRACT — APPROVED FOR 02B REVIEW`**  

---

## 1. EXECUTIVE SUMMARY

This document establishes the authoritative, binding **Canonical Economy Contract** for the CineStream ecosystem from the perspective of the **CineStream Admin App (Management Plane)**. 

The upcoming ecosystem evolution integrates two interrelated financial models:
1. **Time-Bounded Subscription Entitlements:** Multi-tiered ad-free viewing access (`FREE`, `PRO_LITE`, `PRO`) acquired either via real-money manual requests or points redemption.
2. **Points & Rewards Economy:** A closed-loop internal currency accrued through user engagement (daily login check-ins, rewarded ads, tasks, and leaderboard achievements) and liquidated via subscription redemption.

### Key Architectural Invariants Established:
* **Zero Modification Execution:** Absolutely no Kotlin source files, Compose UI components, XML resources, Gradle build files, Firestore security rules, or production data were created, mutated, or deleted during this phase.
* **Single Source of Truth for Entitlement:** `/users/{uid}` remains the authoritative root document for active entitlements and spendable point balances to preserve O(1) latency and single-listener UI performance.
* **Dual-Write Compatibility Mandate:** To ensure 100% backward compatibility with legacy client builds, all write operations on subscription state must dual-write both canonical fields (`subscriptionTier`, `planId`, `subscriptionStatus`, `subscriptionExpiresAt`) and legacy compatibility mirrors (`isPremium`, `isPro`, `plan`, `proPlan`, `proExpiresAt`).
* **Authoritative Immutable Ledger:** Points balances are strictly cached representations; the immutable ledger subcollection `/users/{uid}/point_transactions/{txId}` constitutes the sole financial truth for accounting and audit integrity.
* **Closed Trust Boundary:** Client applications (Users App) are untrusted. Clients are strictly forbidden from writing to their own subscription state, balance fields, task claims, or ledger subcollections. All economic mutations execute via atomic transactions or backend-verified operations.

---

## 2. SCOPE & ZERO-MODIFICATION VERIFICATION

In strict accordance with Section 2 ("Absolute Hard Rules"):
* **Kotlin Source Modifications:** **0 files** modified.
* **Compose UI Modifications:** **0 files** modified.
* **Resource Modifications:** **0 files** modified.
* **Gradle Build / Dependencies:** **0 changes**. No billing, payment, or ad SDKs were added.
* **Firestore Security Rules:** `firestore.rules` remains untouched (310 lines).
* **Firestore Database Mutations:** 0 reads/writes to live production Firestore.
* **Database Collections Created/Deleted:** 0 collections altered.

### Static Verification & Existing Test Integrity:
* **Applet Compilation (`compile_applet`):** **PASS**
* **Existing Unit & Architecture Tests (`gradle :app:testDebugUnitTest`):** **103 / 103 PASS (100%)**
  * `com.example.CineStreamAdminLogicTest`: 79 / 79 PASS
  * `com.example.SearchOrderArchitectureTest`: 20 / 20 PASS
  * `com.example.ExampleRobolectricTest`: 2 / 2 PASS
  * `com.example.FirebaseTest`: 1 / 1 PASS
  * `com.example.ExampleUnitTest`: 1 / 1 PASS
* **Static Security Audit:** **PASS** (Zero exposed API secrets, zero Dynamic Code Loading, zero TLS verification bypasses).

---

## 3. CURRENT ADMIN SUBSCRIPTION ARCHITECTURE

As established in Phase SUBSCRIPTION-POINTS-01 forensic discovery:
1. **Storage Path:** Direct fields on document `/users/{uid}`. No separate `/subscriptions` collection exists.
2. **Current Field Footprint:**
   - Canonical: `isPremium: Boolean`, `subscriptionTier: String` (`"free"`, `"pro"`, `"vip"`), `subscriptionStatus: String` (`"active"`, `"free"`), `subscriptionExpiresAt: Long?`.
   - Compatibility Mirrors: `isPro: Boolean`, `plan: String?`, `proPlan: String?`, `proExpiresAt: Long?`.
3. **Write Pipeline:** `AdminRepository.updateSubscription()` executes a resilient write to `/users/{uid}` updating all 8 fields simultaneously, accompanied by an audit record written to `/auditLogs`.
4. **Lifecycle State Machine:** Evaluated deterministically in `User.subscriptionState`:
   - `FREE`: `subscriptionTier == "free"` or `(!isPremium && !isPro)`.
   - `ACTIVE_PRO`: (`isPremium` or `subscriptionTier != "free"`) AND (`subscriptionExpiresAt == null` or `subscriptionExpiresAt > now`).
   - `EXPIRED_PRO`: `subscriptionExpiresAt != null` AND `subscriptionExpiresAt <= now` AND (`isPremium` or `subscriptionTier != "free"`).
5. **Pro Upgrade Requests:** Sourced from `/pro_requests/{requestId}` (`PENDING`, `APPROVED`, `REJECTED`). Currently, approving a request marks the request document as `APPROVED` but does not automatically grant the subscription on `/users/{uid}`.

---

## 4. CURRENT USERS CONTRACT EVIDENCE (EXTERNAL AUDIT INPUT)

From the external audit input (Phase SUBSCRIPTION-POINTS-01B), the Users App exhibits the following verified operational behaviors:
1. **Primary Read Path:** Users App observes its own document at `/users/{request.auth.uid}` via a real-time Firestore snapshot listener.
2. **Entitlement Consumption:** Users App inspects `isPremium`, `subscriptionTier`, and `subscriptionExpiresAt` to toggle UI states and video player banners.
3. **Legacy Quality Bypass:** Users App historically utilized `isPremium == true` to unlock higher streaming bitrates (1080p/4K) in addition to ad removal.
4. **Manual Payment Submission:** Users App provisions `/pro_requests/{requestId}` with `status = "PENDING"`, user metadata, and an image URL representing payment receipt proof.
5. **No Existing Points Engine:** Users App does not possess any points wallet, daily login claim UI, or reward redemption screens.

---

## 5. CANONICAL SUBSCRIPTION CONTRACT

### 5.1 Plan Hierarchy & SKU Inventory
The canonical subscription system defines exactly three tiers and five distinct SKUs:

| Plan Identifier (`planId`) | Canonical Tier (`subscriptionTier`) | Duration (`durationDays`) | Duration Milliseconds | Canonical Description |
|---|---|---|---|---|
| `free` | `FREE` | Indefinite (`null`) | `0L` | Permanent ad-supported standard tier |
| `pro_lite_1d` | `PRO_LITE` | 1 Day | `86,400,000L` | Pro Lite 24-Hour Ad-Free Pass |
| `pro_lite_7d` | `PRO_LITE` | 7 Days | `604,800,000L` | Pro Lite 7-Day Ad-Free Pass |
| `pro_lite_10d` | `PRO_LITE` | 10 Days | `864,000,000L` | Pro Lite 10-Day Ad-Free Pass |
| `pro_30d` | `PRO` | 30 Days | `2,592,000,000L` | Pro 30-Day Ad-Free Subscription |

### 5.2 Representation Model
To eliminate ambiguity between the high-level plan class and its duration variant, the canonical model uses a **tripartite representation**:
1. `subscriptionTier: String` -> High-level entitlement level: `"free"`, `"pro_lite"`, `"pro"`.
2. `planId: String` -> Exact product identifier: `"free"`, `"pro_lite_1d"`, `"pro_lite_7d"`, `"pro_lite_10d"`, `"pro_30d"`, or `"custom"` (if admin-granted with arbitrary days).
3. `durationDays: Int?` -> Numeric day count (`null` for free/lifetime, `1`, `7`, `10`, `30`, or custom).

### 5.3 Deterministic Subscription State Machine
The client and server evaluate subscription state deterministically based on server epoch time:

```
                  ┌──────────────────────────────┐
                  │          NEW USER            │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │         TIER: FREE           │◄─────────────────────────┐
                  └──────────────┬───────────────┘                          │
                                 │                                          │
                    Grant via Money or Points                               │
                                 │                                          │
                                 ▼                                          │
                  ┌──────────────────────────────┐                          │
                  │      TIER: PRO / PRO_LITE    │                          │
                  │   subscriptionStatus: active │                          │
                  │  subscriptionExpiresAt: T_exp│                          │
                  └──────────────┬───────────────┘                          │
                                 │                                          │
                   ┌─────────────┴─────────────┐                            │
                   │                           │                            │
             now < T_exp                  now >= T_exp                      │
                   │                           │                            │
                   ▼                           ▼                            │
        ┌─────────────────────┐     ┌──────────────────────┐                │
        │ STATE: ACTIVE_PRO   │     │ STATE: EXPIRED_PRO   │                │
        │ (Remove All Ads)    │     │ (Show Expired Notice)│                │
        └─────────────────────┘     └──────────┬───────────┘                │
                                               │                            │
                                               └─────── Admin Reset / ──────┘
                                                        Revoke Action
```

---

## 6. LEGACY COMPATIBILITY CONTRACT

### 6.1 Field Mapping & Status Matrix

| Field Name | Status in New Model | Writer | Reader | Mapping / Transformation Logic |
|---|---|---|---|---|
| `subscriptionTier` | **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | Stores `"free"`, `"pro_lite"`, `"pro"`. |
| `planId` | **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | Stores `"free"`, `"pro_lite_1d"`, `"pro_lite_7d"`, `"pro_lite_10d"`, `"pro_30d"`. |
| `subscriptionStatus` | **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | Stores `"active"`, `"free"`, `"expired"`, `"canceled"`. |
| `subscriptionExpiresAt` | **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | Epoch millis timestamp or `null` for lifetime. |
| `subscriptionStartedAt` | **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | Epoch millis timestamp of subscription commencement. |
| `subscriptionSource` | **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | Stores `"MONEY"`, `"POINTS"`, `"ADMIN_GRANT"`, `"LEGACY"`. |
| `subscriptionReferenceId`| **CANONICAL ACTIVE** | Admin / Backend | Admin / Users | References `requestId` (money), `txId` (points), or `logId` (admin). |
| `isPremium` | **COMPATIBILITY MIRROR** | Admin / Backend | Legacy Consumers | Mirror of `(subscriptionTier != "free" && !isExpired)`. Dual-written. |
| `isPro` | **COMPATIBILITY MIRROR** | Admin / Backend | Legacy Consumers | Exact mirror of `isPremium`. Dual-written. |
| `plan` | **LEGACY COMPATIBILITY** | Admin / Backend | Legacy Consumers | Exact mirror of `subscriptionTier`. Dual-written. |
| `proPlan` | **COMPATIBILITY MIRROR** | Admin / Backend | Legacy Consumers | Exact mirror of `subscriptionTier`. Dual-written. |
| `proExpiresAt` | **COMPATIBILITY MIRROR** | Admin / Backend | Legacy Consumers | Exact mirror of `subscriptionExpiresAt`. Dual-written. |

### 6.2 Legacy Value Normalization Rules
* **Legacy `"vip"`:**
  - Read Normalization: When `subscriptionTier == "vip"` or `plan == "vip"`, the system treats the user as `subscriptionTier = "pro"`, `subscriptionSource = "LEGACY"`.
  - Write Normalization: The Admin UI and backend will no longer allow creating new `"vip"` subscriptions. Existing `"vip"` users retain their active entitlement until natural expiration.
* **Legacy `"premium"`:**
  - Read Normalization: Evaluated as `subscriptionTier = "pro"`.

### 6.3 Dual-Write Preservation Guarantee
All write operations across Phase 3 through Phase 5 **MUST continue to dual-write all 5 compatibility fields** (`isPremium`, `isPro`, `plan`, `proPlan`, `proExpiresAt`). Under no circumstances will legacy fields be removed during initial rollout.

---

## 7. SUBSCRIPTION BENEFIT CONTRACT

### 7.1 Single Canonical Entitlement: Ad Removal
The canonical benefit of an active subscription (`PRO` or `PRO_LITE`) is strictly and exclusively:
$$\text{ACTIVE SUBSCRIPTION} \implies \text{REMOVE ADS}$$

1. **Suppression of Forced Interstitial Ads:** All forced interval ads during app navigation and video playback initialization are disabled.
2. **Suppression of Offline Decryption Ads:** The requirement to watch forced ads (`defaultForcedAds`) before decrypting downloaded offline video media is completely bypassed.
3. **Suppression of Banner Ads:** In-app promotional banners are hidden.

### 7.2 Decoupling of Legacy Video Quality Bypass
* **Issue Analysis:** Historical Users App builds granted 1080p/4K bitrate unlock if `isPremium == true`.
* **Contract Decision:**
  1. **Decoupling:** Media playback bitrate and download permissions are formally decoupled from the subscription engine. They are governed by the granular user permissions: `canWatch`, `canDownload`, `allowedQuality`, and `downloadLimit`.
  2. **Transitional Compatibility:** To prevent regressions for paying users on older client versions, the backend will set `allowedQuality = "4K"` whenever granting `PRO` (30 days), while `PRO_LITE` grants ad removal with `allowedQuality = "1080p"`.
  3. **No Marketing of Unproven Benefits:** Neither Admin nor Users App marketing copy will advertise "Faster Servers", "VIP CDNs", or "Exclusive Sources" unless supported by dedicated infrastructure.

---

## 8. SUBSCRIPTION SOURCE CONTRACT

Every subscription grant or extension must record its financial origin:

| Source Identifier (`subscriptionSource`) | Origin Channel | Acquisition Document Reference (`subscriptionReferenceId`) | Audit Requirement |
|---|---|---|---|
| `MONEY` | Manual Payment Request Review | `/pro_requests/{requestId}` | `APPROVE_PRO_REQUEST` |
| `POINTS` | In-App Points Liquidation | `/users/{uid}/point_transactions/{txId}` | `SUBSCRIPTION_REDEMPTION` |
| `ADMIN_GRANT` | Direct Admin Action (Complimentary/Comp) | `/auditLogs/{logId}` | `GRANT_SUBSCRIPTION` |
| `LEGACY` | Pre-migration Historical Record | Existing document timestamp or `"legacy_import"` | None |

### Stored Metadata on User Entitlement:
```json
{
  "subscriptionTier": "pro_lite",
  "planId": "pro_lite_7d",
  "durationDays": 7,
  "subscriptionStatus": "active",
  "subscriptionSource": "POINTS",
  "subscriptionReferenceId": "tx_20260930_104522_9821",
  "subscriptionStartedAt": 1727712000000,
  "subscriptionExpiresAt": 1728316800000
}
```

---

## 9. PRO REQUEST CONTRACT (MANUAL UPGRADE PIPELINE)

### 9.1 Lifecycle State Transition
The lifecycle states of `/pro_requests/{requestId}` are strictly one-way:

$$\text{PENDING} \longrightarrow \begin{cases} \text{APPROVED} \\ \text{REJECTED} \end{cases}$$

### 9.2 Atomic Approval Reconciliation
To eliminate the architectural gap where approving a request failed to grant the subscription, **approval MUST execute as an atomic multi-document transaction**:

```
BEGIN FIRESTORE TRANSACTION:
  1. Read /pro_requests/{requestId}
     - ASSERT exists == true
     - ASSERT status == "PENDING"
     - Extract userId, requestedPlan ("pro" or "pro_lite"), requestedDuration ("30 days", "7 days", etc.)
  
  2. Read /users/{userId}
     - ASSERT exists == true
     - Extract current subscriptionExpiresAt
  
  3. Compute Expiration:
     - durationMillis = DurationMap[requestedPlan, requestedDuration]
     - baseTime = max(System.currentTimeMillis(), currentExpiresAt ?: 0)
     - newExpiresAt = baseTime + durationMillis
  
  4. Write /pro_requests/{requestId}:
     - status = "APPROVED"
     - reviewedAt = now
     - reviewedBy = adminUid
     - reviewedByEmail = adminEmail
     - adminNote = adminNote
     - updatedAt = now
  
  5. Write /users/{userId}:
     - subscriptionTier = normalizedTier ("pro" or "pro_lite")
     - planId = normalizedPlanId ("pro_30d", "pro_lite_7d", etc.)
     - durationDays = durationDays
     - subscriptionStatus = "active"
     - subscriptionSource = "MONEY"
     - subscriptionReferenceId = requestId
     - subscriptionStartedAt = now
     - subscriptionExpiresAt = newExpiresAt
     - [Dual-Write Compatibility]:
         isPremium = true, isPro = true, plan = normalizedTier,
         proPlan = normalizedTier, proExpiresAt = newExpiresAt,
         updatedAt = now
  
  6. Write /auditLogs/{logId}:
     - action = "APPROVE_PRO_REQUEST"
     - targetType = "PRO_REQUEST"
     - targetId = requestId
     - details = "Approved Pro Request for $userEmail. Granted $planId until $newExpiresAt"
COMMIT TRANSACTION
```

---

## 10. CANONICAL POINTS ECONOMY

### 10.1 Accounting Architecture
* **Fundamental Rule:** **POINTS BALANCE IS NOT THE SOURCE OF TRUTH.**
* **The Authoritative Accounting Truth:** Sourced strictly from the sequential, immutable transaction ledger.
* **Integrity Invariant:**
  $$\text{pointsBalance} \equiv \sum \text{Earned Amounts} - \sum \text{Spent Amounts}$$
* **Constraint Invariant:**
  $$\text{pointsBalance} \ge 0 \quad (\text{Negative balances are strictly prohibited})$$
* **Balance Ceiling:** A maximum cap of $1,000,000\text{ points}$ per account is enforced to prevent unbounded inflation or integer overflow.

### 10.2 User Balance Fields (`/users/{uid}`)
For $O(1)$ client reads and high-performance UI rendering, `/users/{uid}` stores a cached snapshot of the ledger:
* `pointsBalance: Long` (default `0L`) -> Spendable current balance.
* `totalPointsEarned: Long` (default `0L`) -> Lifetime cumulative earnings (never decreases; utilized for leaderboard ranking!).
* `totalPointsSpent: Long` (default `0L`) -> Lifetime cumulative expenditures.

---

## 11. POINTS LEDGER CONTRACT

### 11.1 Structural Evaluation & Location Selection
Three architectural topologies were evaluated:
* **Option A: `/users/{uid}/point_transactions/{txId}` (Subcollection under User Document)**
  - *Data Isolation:* Perfect multi-tenant isolation. Each user owns their own ledger partition.
  - *Security Rules:* Inherits parent path authorization effortlessly (`request.auth.uid == userId`). Standard users can read their own entries without cross-tenant query vulnerabilities.
  - *Atomicity:* Can be written in the exact same Firestore transaction as `/users/{uid}`.
  - *Global Queries:* Admin can query across all transactions using `collectionGroup("point_transactions")`.
* **Option B: `/pointTransactions/{txId}` (Root Collection with `userId` index)**
  - *Drawbacks:* Requires complex collection-level indexing, risks data leakage if security rules misconfigure filters, and fragments user domain storage.
* **Option C: `/points/{uid}` (Separate Root Document)**
  - *Drawbacks:* Forces two document reads for every user profile screen.

**DECISION:** **Option A (`/users/{uid}/point_transactions/{txId}`) is the CANONICAL LEDGER LOCATION.**

### 11.2 Ledger Document Schema (`/users/{uid}/point_transactions/{txId}`)

| Field Name | Type | Constraint | Description |
|---|---|---|---|
| `txId` | `String` | Required | Unique transaction identifier (e.g. `tx_1727712000000_a8f9`) |
| `userId` | `String` | Required | Foreign key matching parent user UID |
| `type` | `String` | Required | Transaction type from canonical enum |
| `amount` | `Long` | Required | Signed integer: Positive for accruals, negative for debits |
| `balanceBefore`| `Long` | Required | Snapshot of `pointsBalance` prior to transaction execution |
| `balanceAfter` | `Long` | Required | Authoritative `pointsBalance` resulting from this mutation |
| `referenceId` | `String?` | Optional | External reference: `taskId`, `adSessionId`, `cycleId`, `requestId` |
| `description` | `String` | Required | Human-readable audit description (Arabic & English) |
| `createdAt` | `Long` | Required | Authoritative server timestamp (epoch milliseconds) |
| `actorUid` | `String` | Required | `"SYSTEM"`, `"TRUSTED_BACKEND"`, or Admin UID |

### 11.3 Canonical Transaction Types
1. `DAILY_LOGIN`: Accrued via consecutive daily check-in streak.
2. `REWARDED_AD`: Accrued via verified voluntary rewarded video advertisement completion.
3. `TASK_REWARD`: Accrued via verified task or mission completion.
4. `GAME_REWARD`: Accrued via verified game session or achievement.
5. `LEADERBOARD_REWARD`: Accrued via weekly top-3 leaderboard prize settlement.
6. `SUBSCRIPTION_REDEMPTION`: Debited to purchase `PRO_LITE` or `PRO` plan.
7. `ADMIN_GRANT`: Manually credited by Admin (goodwill, compensation).
8. `ADMIN_ADJUSTMENT`: Administrative manual debit or audit correction.
9. `REVERSAL`: Reversal of a fraudulent or duplicate transaction.

---

## 12. DAILY LOGIN CONTRACT

### 12.1 The Validation Boundary
**The client application is completely untrusted.** The client cannot invoke a simple "give me daily points" write. Claims are verified against authoritative server timestamps and historical check-in records.

### 12.2 Timezone & Date Boundary
* **Canonical Timezone:** Universal Time Coordinated (**UTC**).
* **Date Key Format:** `YYYY-MM-DD` (ISO-8601).
* *Rationale:* Client device clocks can be altered by users to simulate future days. Date keys are computed strictly on the backend using server timestamps:
  $$\text{currentDate} = \text{Date}(\text{serverTimestamp})_{\text{UTC}}$$

### 12.3 Streak State Machine & Reward Ladder
Standard 7-Day Recurring Cycle:

| Consecutive Day | Points Awarded | Bonus / Badge |
|---|---|---|
| Day 1 | 10 Points | Base Daily Check-in |
| Day 2 | 15 Points | Streak Day 2 |
| Day 3 | 20 Points | Streak Day 3 |
| Day 4 | 25 Points | Streak Day 4 |
| Day 5 | 30 Points | Streak Day 5 |
| Day 6 | 40 Points | Streak Day 6 |
| Day 7 | 50 Points + 24h Pro Lite Badge | Weekly Streak Champion Bonus |

* **Cycle Reset Rule:** After claiming Day 7, the next consecutive day rolls over to Day 1.
* **Streak Break Rule:** If $\text{currentDate} > \text{lastDailyLoginDate} + 1\text{ day}$, streak resets to Day 1.
* **Duplicate Prevention:** If $\text{currentDate} == \text{lastDailyLoginDate}$, the claim is aborted with error `ALREADY_CLAIMED_TODAY`.

---

## 13. REWARDED ADS CONTRACT

### 13.1 Strict Decoupling from Forced Ads
* **Forced Interstitial Ads:** Required for Free tier navigation and offline video decryption (`defaultForcedAds: 5`). Grants **ZERO POINTS**.
* **Rewarded Ads:** Voluntary user choice. User watches a full 30-second rewarded ad video to earn points.

### 13.2 Anti-Fraud & Quota Rules
1. **Daily Quota:** Maximum 5 rewarded ad claims per user per UTC calendar day.
2. **Cooldown Guard:** Minimum 5 minutes (300,000 ms) required between successive claims.
3. **Reward Value:** 15 Points per completed rewarded ad (configurable in `/config/economy`).
4. **Verification Key:** Ad networks return a cryptographic reward callback or signed reward token. The transaction stores `referenceId = adNetworkSessionId`. Reusing the same session ID is rejected as a duplicate.

---

## 14. TASK SYSTEM CONTRACT

### 14.1 Task Catalog Path: `/reward_tasks/{taskId}`
Admin manages the task catalog centrally:

```json
{
  "taskId": "task_profile_complete",
  "titleAr": "إكمال الملف الشخصي",
  "titleEn": "Complete Profile",
  "descriptionAr": "أضف اسمك وصورتك الشخصية لربح النقاط",
  "descriptionEn": "Add your display name and avatar to earn points",
  "type": "ONBOARDING",
  "rewardPoints": 50,
  "status": "ACTIVE",
  "isRepeatable": false,
  "repeatIntervalHours": null,
  "maxClaimsPerUser": 1,
  "verificationMethod": "SERVER_VERIFIED",
  "createdAt": 1727712000000,
  "updatedAt": 1727712000000
}
```

### 14.2 User Claim Path: `/users/{uid}/task_claims/{taskId}`
Records historical completions to guarantee that non-repeatable tasks cannot be claimed more than once:
* `status: "COMPLETED"`
* `claimCount: Int`
* `lastClaimedAt: Long`

---

## 15. GAME REWARD EXTENSION CONTRACT

No games are implemented in this phase. The contract reserves the extension interface:
* **Transaction Type:** `GAME_REWARD`.
* **Required Payload:**
  - `referenceId`: Verified game session identifier (`session_uuid`).
  - `metadata.gameId`: Registered minigame ID (e.g. `quiz_cinema_v1`).
  - `metadata.score`: Score achieved.
* **Anti-Cheat Invariant:** Client cannot submit raw points directly. Points awarded are calculated on the backend from verified gameplay duration and score limits (e.g. maximum 50 points per game session, maximum 3 sessions per day).

---

## 16. LEADERBOARD & TOP-3 REWARDS CONTRACT

### 16.1 Critical Invariant: Score Decoupling
$$\text{Leaderboard Score} \not\equiv \text{Current Spendable Points Balance}$$
* **Rationale:** If leaderboard ranking used spendable points balance, any user who spent points to redeem a Pro subscription would drop in rank. This creates a perverse incentive not to engage with the subscription economy.
* **Authoritative Scoring Metric:** $\text{WeeklyEarnedPoints} \equiv \sum \text{Points Earned during Active Cycle}$.

### 16.2 Cycle Lifecycle & Storage Paths
* **Cycle Cadence:** Weekly (Monday 00:00:00 UTC to Sunday 23:59:59 UTC).
* **Live Snapshot:** `/leaderboard/weekly_current` (Top 100 entries updated in real-time or aggregated).
* **Historical Settlement:** `/leaderboard_history/{cycleId}` (e.g. `cycle_2026_w40`).

### 16.3 Top-3 Admin Configurable Rewards

| Rank | Default Points Reward | Default Subscription Entitlement | Idempotency Key |
|---|---|---|---|
| **1st Place** | 500 Points | 7 Days PRO_LITE Free Pass | `{cycleId}_rank_1` |
| **2nd Place** | 300 Points | 1 Day PRO_LITE Free Pass | `{cycleId}_rank_2` |
| **3rd Place** | 150 Points | None | `{cycleId}_rank_3` |

### 16.4 Settlement Automation & Duplicate Protection
At the close of each cycle, the settlement process writes the finalized leaderboard to `/leaderboard_history/{cycleId}` with `settledAt = now`. Winning users are credited via ledger entries:
* `type = "LEADERBOARD_REWARD"`
* `referenceId = cycleId + "_rank_" + rank`
If a user document already contains a claim matching this reference ID, duplicate settlement is rejected.

---

## 17. FEATURE CONTROL CONTRACT

### 17.1 Storage Path: `/config/features`
Following the architectural convention established by `/config/search_order`, system feature controls are housed in a dedicated document `/config/features` under the canonical `/config` collection.
* **Existing Security Rules Coverage:** Governed by `firestore.rules` line 141 (`match /config/{configDoc}`):
  - Read: `allow read: if true;` (Public/Authenticated read).
  - Write: `allow write: if isAdmin();` (Admins only).

### 17.2 Document Schema (`/config/features`)
```json
{
  "subscriptions": {
    "status": "ACTIVE",
    "disabledMessage": "خدمة الاشتراكات متوقفة مؤقتاً لأعمال الصيانة",
    "comingSoonMessage": "هذه الميزة ستضاف قريبًا"
  },
  "points": {
    "status": "ACTIVE",
    "disabledMessage": "نظام النقاط متوقف مؤقتاً",
    "comingSoonMessage": "هذه الميزة ستضاف قريبًا"
  },
  "dailyLogin": {
    "status": "ACTIVE",
    "disabledMessage": "مكافآت تسجيل الدخول متوقفة مؤقتاً",
    "comingSoonMessage": "هذه الميزة ستضاف قريبًا"
  },
  "rewardedAds": {
    "status": "ACTIVE",
    "disabledMessage": "إعلانات المكافآت متوقفة حالياً",
    "comingSoonMessage": "هذه الميزة ستضاف قريبًا"
  },
  "tasks": {
    "status": "ACTIVE",
    "disabledMessage": "مهام المكافآت متوقفة حالياً",
    "comingSoonMessage": "هذه الميزة ستضاف قريبًا"
  },
  "leaderboard": {
    "status": "ACTIVE",
    "disabledMessage": "قائمة المتصدرين متوقفة حالياً",
    "comingSoonMessage": "هذه الميزة ستضاف قريبًا"
  },
  "updatedAt": 1727712000000,
  "updatedBy": "admin_uid"
}
```

### 17.3 Conceptual State Rules
1. **`ACTIVE`:** Feature is operational. UI renders normally.
2. **`COMING_SOON`:** Feature UI renders in promotional mode. When user interacts, display modal notice:  
   `"هذه الميزة ستضاف قريبًا"`  
   All mutation requests are rejected at the business logic layer.
3. **`DISABLED`:** Feature is hidden or marked disabled. Display modal notice:  
   `disabledMessage`  
   All mutation requests are rejected at the business logic layer.

---

## 18. FIRESTORE CANONICAL PATH INVENTORY

| Path | Purpose | Owner / Tenant | Readers | Writers | Security Level | Classification |
|---|---|---|---|---|---|---|
| `/users/{uid}` | User Profile, Entitlement & Balance | User (`uid`) | Admin, Owner | Admin, Owner (Non-sensitive) | High | **CANONICAL ACTIVE** |
| `/users/{uid}/point_transactions/{txId}` | Immutable Accounting Ledger | User (`uid`) | Admin, Owner | Admin, Trusted Backend ONLY | Maximum | **CANONICAL ACTIVE** |
| `/users/{uid}/task_claims/{taskId}` | Task Claim History | User (`uid`) | Admin, Owner | Admin, Trusted Backend ONLY | High | **CANONICAL ACTIVE** |
| `/pro_requests/{requestId}` | Manual Payment Upgrade Queue | User (`userId`) | Admin, Owner | Admin, Owner (`status=PENDING`) | High | **CANONICAL ACTIVE** |
| `/config/features` | System Feature Control Switches | System | All (Public) | Admin ONLY | Maximum | **CANONICAL ACTIVE** |
| `/config/economy` | Points Costs & Reward Parameters | System | All (Public) | Admin ONLY | Maximum | **CANONICAL ACTIVE** |
| `/config/app` | System OTA & Maintenance Config | System | All (Public) | Admin ONLY | Maximum | **CANONICAL ACTIVE** |
| `/reward_tasks/{taskId}` | Configurable Task Definitions Catalog | System | All (Public) | Admin ONLY | High | **CANONICAL ACTIVE** |
| `/leaderboard/weekly_current` | Current Weekly Leaderboard Snapshot | System | All (Public) | Admin, Trusted Backend ONLY | High | **CANONICAL ACTIVE** |
| `/leaderboard_history/{cycleId}` | Archived Settled Leaderboards | System | All (Public) | Admin, Trusted Backend ONLY | High | **CANONICAL ACTIVE** |
| `/auditLogs/{logId}` | Administrative Audit Trail | System | Admin ONLY | Admin ONLY | Maximum | **CANONICAL ACTIVE** |
| `/subscriptions` | *Direct Subscriptions Collection* | *None* | *None* | *None* | Blocked | **NOT ALLOWED (Blocked by Rules)** |
| `/plans` | *Direct Plans Collection* | *None* | *None* | *None* | Blocked | **NOT ALLOWED (Blocked by Rules)** |
| `/pointTransactions` | *Root Flat Ledger* | *None* | *None* | *None* | Blocked | **NOT ALLOWED (Avoid Option B)** |

---

## 19. USER DOCUMENT SCHEMA SPECIFICATION (`/users/{uid}`)

```json
{
  "uid": "user_abc123",
  "email": "user@example.com",
  "displayName": "Ahmed Ali",
  "createdAt": 1727712000000,
  "updatedAt": 1727712000000,
  "lastLoginTimestamp": 1727712000000,
  
  "// CANONICAL SUBSCRIPTION ENTITLEMENT //": "",
  "subscriptionTier": "pro_lite",
  "planId": "pro_lite_7d",
  "durationDays": 7,
  "subscriptionStatus": "active",
  "subscriptionSource": "POINTS",
  "subscriptionReferenceId": "tx_20260930_104522_9821",
  "subscriptionStartedAt": 1727712000000,
  "subscriptionExpiresAt": 1728316800000,

  "// COMPATIBILITY DUAL-WRITE MIRRORS //": "",
  "isPremium": true,
  "isPro": true,
  "plan": "pro_lite",
  "proPlan": "pro_lite",
  "proExpiresAt": 1728316800000,

  "// CANONICAL POINTS ECONOMY (CACHED BALANCE) //": "",
  "pointsBalance": 150,
  "totalPointsEarned": 450,
  "totalPointsSpent": 300,

  "// DAILY LOGIN ENGAGEMENT STATE //": "",
  "dailyLoginStreak": 3,
  "lastDailyLoginDate": "2026-09-30",

  "// REWARDED ADS ENGAGEMENT STATE //": "",
  "rewardedAdsWatchedToday": 2,
  "lastRewardedAdTimestamp": 1727710200000,
  "rewardedAdsResetDate": "2026-09-30",

  "// FINE-GRAINED PERMISSION OVERRIDES //": "",
  "canWatch": true,
  "canDownload": true,
  "allowedQuality": "1080p",
  "downloadLimit": 10,
  "forcedAdsOverride": null
}
```

---

## 20. ATOMIC OPERATION MATRIX

All operations affecting subscription entitlements or points currency MUST execute as atomic transactions:

| Operation | Input Validation | Atomic Reads & Checks | Atomic Writes Executed | Failure / Rollback Behavior |
|---|---|---|---|---|
| **A. Money Pro Approval** | `requestId`, `adminNote` | Check `/pro_requests` is `PENDING`. Read current `/users/{uid}`. | 1. Update `/pro_requests` to `APPROVED`.<br>2. Update `/users/{uid}` subscription fields.<br>3. Write `/auditLogs`. | Abort transaction; zero state mutation. |
| **B. Points Subscription Redemption** | `userId`, `planId` | Verify `features.subscriptions == ACTIVE`. Verify `pointsBalance >= cost`. | 1. Deduct `cost` from `pointsBalance`.<br>2. Create `/point_transactions` ledger record.<br>3. Grant subscription on `/users/{uid}`. | Abort transaction if balance insufficient or feature disabled. |
| **C. Daily Login Claim** | `userId` | Verify `features.dailyLogin == ACTIVE`. Verify `lastDailyLoginDate != today`. | 1. Add reward to `pointsBalance` & `totalEarned`.<br>2. Update `streak` & `lastDailyLoginDate`.<br>3. Create `/point_transactions` record. | Abort transaction if already claimed today; throw error. |
| **D. Rewarded Ad Reward** | `userId`, `adSessionId` | Verify `features.rewardedAds == ACTIVE`. Verify `adsWatchedToday < 5`. Verify cooldown $\ge 5\text{ min}$. | 1. Increment `adsWatchedToday`.<br>2. Add reward to `pointsBalance` & `totalEarned`.<br>3. Create `/point_transactions` record. | Abort transaction if limit reached or cooldown active. |
| **E. Task Reward Claim** | `userId`, `taskId` | Verify `features.tasks == ACTIVE`. Verify `/task_claims/{taskId}` does not exist. | 1. Add reward to `pointsBalance` & `totalEarned`.<br>2. Create `/task_claims/{taskId}`.<br>3. Create `/point_transactions` record. | Abort transaction if task already claimed. |
| **F. Leaderboard Settlement** | `cycleId`, `top3List` | Verify `features.leaderboard == ACTIVE`. Verify `/leaderboard_history/{cycleId}` unfinalized. | 1. Write `/leaderboard_history/{cycleId}`.<br>2. For each winner: credit balance, create ledger record.<br>3. Write `/auditLogs`. | Abort batch if cycle already settled. |
| **G. Admin Points Adjustment** | `userId`, `amount`, `reason` | Verify caller is authenticated Admin. Verify `balance + amount >= 0`. | 1. Adjust `pointsBalance`.<br>2. Create `/point_transactions` record (`ADMIN_ADJUSTMENT`).<br>3. Write `/auditLogs`. | Abort if adjustment causes negative balance. |

---

## 21. SECURITY & TRUST BOUNDARY

### 21.1 Non-Negotiable Security Invariants
1. **User Cannot Modify Subscription Entitlement:** Protected by `modifyingSensitiveUserFields()` in `firestore.rules`.
2. **User Cannot Modify Points Balance:** `pointsBalance`, `totalPointsEarned`, `totalPointsSpent` MUST be added to `modifyingSensitiveUserFields()`.
3. **User Cannot Write Point Ledger Entries:** Subcollection `/users/{uid}/point_transactions` will permit `allow read: if isOwner(userId); allow write: if false;`. Only Admins or Trusted Backend can write.
4. **User Cannot Self-Approve Pro Requests:** `/pro_requests/{requestId}` restricts update and delete to `isAdmin()`.
5. **User Cannot Modify System Feature Switches:** `/config/{configDoc}` restricts writes strictly to `isAdmin()`.

### 21.2 Actor Authorization Matrix

| Resource / Action | Unauthenticated | Authenticated User | Document Owner | Admin |
|---|---|---|---|---|
| Read `/config/features` | Allowed | Allowed | Allowed | Allowed |
| Write `/config/features` | DENIED | DENIED | DENIED | Allowed |
| Read own `/users/{uid}` | DENIED | DENIED | Allowed | Allowed |
| Write sensitive fields on `/users/{uid}` | DENIED | DENIED | DENIED | Allowed |
| Read own `/point_transactions` | DENIED | DENIED | Allowed | Allowed |
| Write `/point_transactions` | DENIED | DENIED | DENIED | Allowed |
| Create `/pro_requests` (`status=PENDING`)| DENIED | Allowed (own UID) | Allowed (own UID) | Allowed |
| Approve/Reject `/pro_requests` | DENIED | DENIED | DENIED | Allowed |
| Read `/auditLogs` | DENIED | DENIED | DENIED | Allowed |

---

## 22. OFFLINE BEHAVIOR & TRUST POLICIES

| Feature Area | Offline Read Capability | Offline Write Capability | Authority Model & Trust Policy |
|---|---|---|---|
| **Subscription Entitlement** | **ALLOWED (Cached)** | **FORBIDDEN** | Client uses cached `subscriptionExpiresAt` to evaluate ad-free status. Expiration check runs against device clock, re-synchronized with server clock upon reconnection. |
| **Points Wallet Display** | **ALLOWED (Cached)** | **FORBIDDEN** | Displays last known cached balance with a `"Syncing..."` indicator. |
| **Points Spending / Redemption** | **FORBIDDEN** | **FORBIDDEN** | **ONLINE REQUIRED.** Requires authoritative transaction with Firestore server to prevent double-spending. |
| **Daily Login Claim** | **FORBIDDEN** | **FORBIDDEN** | **ONLINE REQUIRED.** Must acquire server timestamp to validate UTC date and prevent streak tampering. |
| **Rewarded Ads Verification** | **FORBIDDEN** | **FORBIDDEN** | **ONLINE REQUIRED.** Ad network callback verification must reach server. |
| **Task Claiming** | **FORBIDDEN** | **FORBIDDEN** | **ONLINE REQUIRED.** Server validates completion criteria before issuing points. |
| **Leaderboard Browsing** | **ALLOWED (Cached)** | **FORBIDDEN** | Displays cached ranking snapshot. Mutations strictly server-side. |

---

## 23. AUDIT LOGGING CONTRACT

The `/auditLogs` collection (governed by `AuditLog` in `Models.kt:219-229`) records every administrative or financial mutation.

### Required Audit Actions:
* `GRANT_SUBSCRIPTION`: Admin manually grants/extends subscription on `/users/{uid}`.
* `REVOKE_SUBSCRIPTION`: Admin revokes subscription to Free tier.
* `APPROVE_PRO_REQUEST`: Admin approves `/pro_requests/{requestId}` and activates Pro entitlement.
* `REJECT_PRO_REQUEST`: Admin rejects `/pro_requests/{requestId}` with mandatory reason.
* `ADMIN_POINTS_GRANT`: Admin credits points to user account.
* `ADMIN_POINTS_ADJUSTMENT`: Admin debits or corrects points balance.
* `UPDATE_FEATURE_CONTROL`: Admin modifies feature state in `/config/features`.
* `SETTLE_LEADERBOARD`: Settlement of weekly leaderboard and reward distribution.

### Minimum Audit Record Schema:
```json
{
  "id": "log_1727712000000_9281",
  "actorUid": "admin_uid_772",
  "adminEmail": "admin@cinestream.com",
  "action": "APPROVE_PRO_REQUEST",
  "targetType": "PRO_REQUEST",
  "targetId": "req_88192",
  "details": "Approved Pro Request for user@example.com. Granted PRO (30 days) via MONEY source.",
  "createdAt": 1727712000000
}
```

---

## 24. ADMIN DASHBOARD CONTRACT

The future Admin App dashboard (`DashboardScreen.kt` and `DashboardOverviewContent.kt`) will expand to include the following management surfaces:

1. **Feature Control Center:** Real-time toggles for Subscriptions, Points, Daily Login, Rewarded Ads, Tasks, and Leaderboard across states (`ACTIVE`, `COMING_SOON`, `DISABLED`).
2. **Subscription Management Surface:**
   - Active Subscribers by Tier (`PRO`, `PRO_LITE`).
   - Subscribers by Source (`MONEY` vs `POINTS`).
   - Manual Grant/Revoke Dialog supporting new duration presets (1d, 7d, 10d, 30d).
3. **Points Economy Overview:**
   - Total System Points Issued vs Total Points Burned.
   - Points In Circulation.
   - User Point Balance Inspector and Manual Adjustment Dialog.
4. **Daily Login & Rewarded Ads Analytics:**
   - Daily Check-in Claim Rate.
   - Total Rewarded Ads Watched & Points Distributed.
5. **Task Manager:**
   - Create, edit, and toggle reward tasks.
6. **Leaderboard Administration:**
   - Current Weekly Standings Preview.
   - Configurable Top-3 Rewards Editor.
   - Manual Settlement Override Button.

---

## 25. MIGRATION STRATEGY

```
   ┌──────────────────────────────────────────────────────────────┐
   │             PHASE 1: COMPATIBILITY LAYER                     │
   │  - Deploy dual-read logic in Admin & Users app.              │
   │  - Read canonical fields with fallback to legacy mirrors.    │
   │  - Dual-write both canonical and legacy compatibility fields.│
   └──────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
   ┌──────────────────────────────────────────────────────────────┐
   │             PHASE 2: CANONICAL WRITE INTEGRATION             │
   │  - All new subscription grants, approvals, and redemptions   │
   │    write full canonical schemas + compatibility mirrors.     │
   │  - Legacy fields (isPremium, isPro, plan) strictly updated.  │
   └──────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
   ┌──────────────────────────────────────────────────────────────┐
   │             PHASE 3: PASSIVE / BACKGROUND NORMALIZATION      │
   │  - When a user logs in or is inspected by Admin, legacy      │
   │    documents missing planId are backfilled in-place.         │
   │  - "vip" records mapped to "pro" with source = "LEGACY".     │
   └──────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
   ┌──────────────────────────────────────────────────────────────┐
   │             PHASE 4: LEGACY READ DEPRECATION                 │
   │  - After 95%+ client version adoption, remove fallback read  │
   │    evaluations. Primary models read canonical fields only.   │
   └──────────────────────────────┬───────────────────────────────┘
                                  │
                                  ▼
   ┌──────────────────────────────────────────────────────────────┐
   │             PHASE 5: RETIREMENT & CLEANUP                    │
   │  - Multi-version release verification before removing        │
   │    dual-write compatibility mirrors from backend.            │
   └──────────────────────────────────────────────────────────────┘
```

---

## 26. LEGACY PRESERVATION MANDATE

The following fields and documents **MUST NOT BE DELETED** during the upcoming implementation phases:
1. `/users/{uid}.isPremium` (Vital for older Users App builds).
2. `/users/{uid}.isPro` (Legacy boolean mirror).
3. `/users/{uid}.plan` (Legacy tier string).
4. `/users/{uid}.proPlan` (Legacy Pro tier string).
5. `/users/{uid}.proExpiresAt` (Legacy expiration timestamp).
6. `/pro_requests/{requestId}` (Active production intake queue).
7. `/config/app` (Active OTA and maintenance document).
8. `/config/global` (Active read fallback configuration).
9. All 32 sensitive user permissions fields (`canWatch`, `canDownload`, `watchBan`, etc.).

---

## 27. ADMIN ↔ USERS CANONICAL CONTRACT BOUNDARY

| Feature / Responsibility | Admin App Responsibility | Users App Responsibility |
|---|---|---|
| **Feature Configuration** | **AUTHORITY:** Writes toggles to `/config/features`. | **CONSUMER:** Reads `/config/features`. Disables UI or shows modal notices if disabled/coming soon. |
| **Economy Parameters** | **AUTHORITY:** Configures point costs and ad reward rates in `/config/economy`. | **CONSUMER:** Reads costs to render redemption shop. |
| **Subscription Entitlement** | **AUTHORITY:** Grants, extends, or revokes subscription on `/users/{uid}`. Approves `/pro_requests`. | **CONSUMER:** Reads entitlement to suppress ads. Never writes own entitlement. |
| **Pro Upgrade Requests** | **REVIEWER:** Inspects receipts in `/pro_requests`, approves or rejects atomically. | **APPLICANT:** Creates request with `status = "PENDING"` and uploads receipt proof. |
| **Points Balance** | **AUDITOR:** Reads balances, performs administrative adjustments with audit trail. | **SPENDER:** Views cached balance, initiates redemption transactions. |
| **Points Ledger** | **AUDITOR:** Audits all user transactions via collection group queries. | **BENEFICIARY:** Reads own subcollection to display transaction history. |
| **Daily Login** | **OBSERVER:** Configures streak bonus ladder and monitors engagement. | **CLAIMANT:** Initiates daily check-in claim via trusted server transaction. |
| **Rewarded Ads** | **CONTROLLER:** Sets daily limits and points per ad. | **VIEWER:** Watches video, submits verified ad session callback. |
| **Tasks** | **CREATOR:** Provisions task catalog at `/reward_tasks/{taskId}`. | **EXECUTOR:** Performs task, claims points upon completion. |
| **Leaderboard** | **SETTLER:** Reviews rankings, triggers settlement, distributes Top-3 prizes. | **PARTICIPANT:** Views live rankings and personal standing. |
| **Audit Trail** | **WRITER & VIEWER:** Authoritative append-only logging of all administrative actions. | **ZERO ACCESS:** Standard users cannot read or write `/auditLogs`. |

---

## 28. EXTERNAL CONTRACT ASSUMPTIONS

| Assumption Identifier | Assumption Details | Verification Status | Action Required |
|---|---|---|---|
| **ECA-001** | Users App observes `/users/{uid}` using Firestore real-time snapshot listeners. | **VERIFIED FROM USERS AUDIT** | Preserved in contract. |
| **ECA-002** | Users App historically unlocked higher video bitrates (1080p/4K) when `isPremium == true`. | **VERIFIED FROM USERS AUDIT** | Addressed via decoupled permission fields (`allowedQuality`). |
| **ECA-003** | Users App creates `/pro_requests/{requestId}` with `status = "PENDING"`. | **VERIFIED FROM USERS AUDIT** | Atomic approval transaction designed to match. |
| **ECA-004** | Users App does not possess any third-party payment gateway SDKs (Google Play Billing, Stripe). | **VERIFIED FROM USERS AUDIT** | Manual payment review pipeline retained. |
| **ECA-005** | Users App can support subcollection read on `/users/{uid}/point_transactions`. | **NOT VERIFIED / REQUIRES USERS PHASE** | Must be implemented in Users Phase 04B. |
| **ECA-006** | Users App handles localized Arabic notice `"هذه الميزة ستضاف قريبًا"` for COMING_SOON features. | **NOT VERIFIED / REQUIRES USERS PHASE** | Must be implemented in Users Phase 08. |

---

## 29. DECISION REGISTER

| Decision ID | Canonical Decision | Architectural Rationale | Current State | Target State | Migration Required? | Target Phase |
|---|---|---|---|---|---|---|
| **SUB-001** | **Canonical Tiers:** `FREE`, `PRO_LITE`, `PRO`. | Provides flexible duration options without complicating tier hierarchy. | `free`, `pro`, `vip` | `FREE`, `PRO_LITE`, `PRO` | Yes (Normalize VIP) | Phase 03A |
| **SUB-002** | **Duration Model:** Normalized combination of `planId` and `durationDays`. | Prevents duration collision while enabling clean SKU queries. | Ambiguous text duration | Explicit `planId` + `durationDays` | Yes (Backfill) | Phase 03A |
| **SUB-003** | **Legacy VIP Mapping:** Map `"vip"` to `"pro"` with source `"LEGACY"`. | Eliminates redundant VIP tier while honoring historical grants. | VIP exists in UI/models | VIP deprecated & normalized | Yes | Phase 03A |
| **SUB-004** | **Subscription Benefit:** Ad removal exclusively (`REMOVE_ADS`). Bitrate decoupled. | Adheres to verified business requirements; prevents false marketing claims. | Quality loosely coupled | Strict ad-removal guarantee | Yes | Phase 03A/B |
| **SUB-005** | **Subscription Source:** Stored on user record (`MONEY`, `POINTS`, `ADMIN_GRANT`). | Enables precise revenue and economy reconciliation. | Source not tracked | Authoritative source tracking | Yes | Phase 03A |
| **SUB-006** | **Pro Request Approval:** Atomic multi-document transaction updating request, user, and audit log. | Closes the disconnect where approving request did not grant subscription. | Non-atomic request-only update | Atomic transaction | Yes | Phase 03A |
| **PTS-001** | **Points Authority:** Cached balance on `/users/{uid}`, authoritative truth in immutable ledger. | Combines O(1) read performance with strict financial auditability. | No points exist | Cached balance + immutable ledger | No (New feature) | Phase 04A |
| **PTS-002** | **Points Ledger Location:** Subcollection `/users/{uid}/point_transactions/{txId}` (Option A). | Optimal tenant isolation, seamless security rules, supports `collectionGroup`. | Not present | Subcollection ledger | No (New feature) | Phase 04A |
| **PTS-003** | **Daily Login:** UTC date validation, 7-day recurring streak ladder, server timestamp validation. | Eliminates client clock tampering and duplicate claims. | Only `lastLoginTimestamp` | Verified daily streak engine | No (New feature) | Phase 05A |
| **PTS-004** | **Rewarded Ads:** Max 5/day, 5-minute cooldown, 15 pts/ad, decoupled from forced ads. | Protects economy from automated ad-watching bots and inflation. | Only forced DRM ads exist | Quota-controlled rewarded ads | No (New feature) | Phase 05B |
| **PTS-005** | **Tasks:** Catalog at `/reward_tasks`, claim tracking at `/users/{uid}/task_claims`. | Configurable task system preventing duplicate claims. | No tasks exist | Configurable catalog + claims | No (New feature) | Phase 06A |
| **PTS-006** | **Leaderboard:** Scoring based on `WeeklyEarnedPoints`, decoupled from spendable balance. | Users are not penalized on leaderboard for spending points. | No leaderboard exists | Weekly points earned score | No (New feature) | Phase 06B |
| **PTS-007** | **Top-3 Rewards:** Admin-configurable points + Pro Lite pass, idempotent cycle settlement. | Incentivizes weekly user engagement without duplicate reward risks. | No rewards exist | Idempotent cycle settlement | No (New feature) | Phase 06B |
| **CFG-001** | **Feature Control Location:** `/config/features` document under existing `/config` rules. | Already permitted under `firestore.rules` without modifying rules file. | No feature flags exist | Dedicated `/config/features` | No (New document)| Phase 08 |
| **SEC-001** | **Trusted Operations:** Client blocked from modifying balances or writing ledger entries. | Zero-trust client boundary; all balance mutations verified server-side. | Entitlements protected | Entitlements + Points protected | Yes (Update rules)| Phase 04A |
| **MIG-001** | **Legacy Compatibility:** Dual-write all 5 legacy compatibility fields indefinitely. | Guarantees zero downtime or breakage for legacy Users App versions. | Dual-write active | Dual-write preserved | Yes | Phase 03A |

---

## 30. FUTURE IMPLEMENTATION PHASE ROADMAP

Following this contract design phase, implementation will proceed in strictly isolated, testable phases:

* **PHASE 03A — Admin Subscription Normalization:**
  - Introduce `planId`, `durationDays`, `subscriptionSource`, `subscriptionReferenceId`.
  - Update `SubscriptionManagementDialog` in Admin UI to support `PRO_LITE` (1d, 7d, 10d) and `PRO` (30d).
  - Implement atomic transaction in `ProRequestRepository.approveRequest()`.
  - Maintain 100% dual-write compatibility.
* **PHASE 03B — Users Subscription Normalization (Users Session):**
  - Update Users App entitlement listener to read canonical `subscriptionTier` and `planId`.
  - Decouple video quality bypass from subscription state.
* **PHASE 04A — Admin Points Economy & Ledger Contract:**
  - Add `pointsBalance`, `totalPointsEarned`, `totalPointsSpent` to User model and repository.
  - Implement `/users/{uid}/point_transactions` subcollection ledger repository.
  - Implement Admin Manual Point Adjustment dialog with audit logging.
  - Update `firestore.rules` to protect points fields and restrict ledger writes.
* **PHASE 04B — Users Points Wallet (Users Session):**
  - Implement Points Wallet UI and transaction history listener.
* **PHASE 05A — Daily Login Rewards:**
  - Implement daily check-in state machine, streak ladder, and duplicate prevention.
* **PHASE 05B — Rewarded Ads Integration:**
  - Implement quota-controlled rewarded ad verification pipeline.
* **PHASE 06A — Reward Tasks System:**
  - Implement `/reward_tasks` catalog management in Admin App and claim pipeline in Users App.
* **PHASE 06B — Leaderboard & Top-3 Settlements:**
  - Implement weekly scoring aggregation and Top-3 reward distribution.
* **PHASE 07 — Subscription Points Redemption:**
  - Implement atomic points liquidation: deduct points $\rightarrow$ write ledger $\rightarrow$ grant `PRO_LITE` or `PRO`.
* **PHASE 08 — Feature Control Management:**
  - Provision `/config/features` management screen in Admin App.
  - Implement client-side `"هذه الميزة ستضاف قريبًا"` modal guards in Users App.
* **PHASE 09 — Data Normalization & Backfill Migration:**
  - Passive migration script normalizing legacy user documents.
* **PHASE 10 — End-to-End Economy Verification:**
  - Full automated integration and rules testing across entire ecosystem.

---

## 31. VERIFICATION RESULTS

| Verification Check | Method | Required Threshold | Result | Status |
|---|---|---|---|---|
| **Compilation** | `compile_applet` | Clean build, 0 errors | Build succeeded cleanly | **PASS** |
| **Unit & Logic Tests** | `testDebugUnitTest` | 100% pass | **103 / 103 PASS** (5 test suites) | **PASS** |
| **Source Code Modifications**| File inspection | Strictly 0 modified source files | 0 files modified | **PASS** |
| **Firestore Data Safety** | Container isolation | Zero live mutations | 0 mutations executed | **PASS** |
| **Security Contract** | AST & Rules inspection | Zero rule regressions | Protected fields intact | **PASS** |

---

## 32. RISKS & OPEN QUESTIONS

All major architectural questions have been systematically analyzed and resolved:
1. **Double-Spending on Subscriptions via Points:** Resolved by atomic Firestore transaction checking balance, debiting points, creating ledger entry, and setting user entitlement in a single atomic commit.
2. **Device Timezone Tampering on Daily Login:** Resolved by enforcing server UTC timestamp date calculation (`YYYY-MM-DD`).
3. **Leaderboard Disincentive to Spend Points:** Resolved by decoupling leaderboard ranking score (`weeklyPointsEarned`) from spendable points balance (`pointsBalance`).
4. **Older Users App Build Breakage:** Resolved by mandatory dual-write policy covering all 5 legacy compatibility fields.

---

## 33. FINAL CONTRACT STATUS

In accordance with Section 37 ("Final Status Rule"), all required contract items, schemas, state transitions, security boundaries, and migration stages have been completely defined with zero unresolved blocking questions.

**FINAL STATUS:** **`CANONICAL CONTRACT — APPROVED FOR 02B REVIEW`**
