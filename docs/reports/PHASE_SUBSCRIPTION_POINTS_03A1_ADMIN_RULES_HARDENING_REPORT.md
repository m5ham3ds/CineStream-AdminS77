# PHASE SUBSCRIPTION-POINTS-03A.1
# CINESTREAM ADMIN APP — FIRESTORE RULES & ECONOMY CONTRACT HARDENING REPORT

============================================================
1. EXECUTIVE SUMMARY
============================================================

Phase: SUBSCRIPTION-POINTS-03A.1
Project: CineStream Admin App
Phase Category: Security Rules / Contract Hardening / Verification
Execution Status: COMPLETED — VERIFIED
Final Verdict: PASS

This phase hardens the Firestore Security Rules (`firestore.rules`) and reconciles them with the canonical Subscription + Points Economy contract established in Phase 03A.

Key achievements in this hardening phase:
1. Absolute Rule Enforced: Subscription benefits are strictly confined to ad removal (`isAdFree`). No rule links `subscriptionTier` with `allowedQuality` or `downloadLimit`. Video quality remains unconstrained for all users.
2. Zero-Trust Admin Authority: Removed legacy dependency on `/users/{uid}.role`. Admin authority is derived strictly from `/admins/{uid}.enabled == true` alongside the approved owner bootstrap email (`sulopros01@gmail.com`).
3. Points Wallet & Ledger Hardened: All points summary fields (`pointsBalance`, `totalPointsEarned`, `totalPointsSpent`) on `/users/{uid}` are classified as sensitive. Standard user writes to `/users/{uid}/point_transactions/{txId}` and `/users/{uid}/task_claims/{taskId}` are strictly blocked at the Firestore engine level.
4. Canonical Config Control: Added strict authenticated read and administrative write rules for `/config/features` and `/config/economy`. Wildcard and unknown config paths (`/config/global`, `/config/random`) remain explicitly denied.
5. Catalog & Leaderboard Paths: Protected `/reward_tasks/{taskId}`, `/leaderboard/weekly_current`, and `/leaderboard_history/{cycleId}` (read-only for authenticated users, write restricted to admin/trusted settlement).
6. Rigorous Verification: 36 out of 36 test cases passed on the local Firebase Firestore Emulator test harness (`phase03a1-rules.test.js`), and all Android JVM unit tests compiled and passed (`gradle :app:testDebugUnitTest`).

============================================================
2. RULES BEFORE
============================================================

Prior to this hardening phase, `firestore.rules` exhibited several critical vulnerabilities and omissions regarding the Phase 03A economy:
1. `isAdmin()` included a fallback check allowing `/users/$(request.auth.uid).data.role in ['admin', 'superadmin', 'owner']`, violating the strict zero-trust boundary where Admin authority must not depend on user documents.
2. Wildcard subcollection rule on `/users/{userId}/{subcollection}/{docId}` permitted standard user writes to ANY subcollection, leaving `/users/{uid}/point_transactions` and `/users/{uid}/task_claims` vulnerable to direct client forgery of point balances and claims.
3. `modifyingSensitiveUserFields()` only protected legacy fields (`isPremium`, `subscriptionTier`, `isPro`, `proExpiresAt`, `proPlan`, `plan`). It lacked protection for the newly introduced Phase 03A canonical subscription fields (`planId`, `durationDays`, `subscriptionSource`, `subscriptionReferenceId`, `subscriptionStartedAt`) and points summary fields (`pointsBalance`, `totalPointsEarned`, `totalPointsSpent`).
4. `/config/features` and `/config/economy` had no explicit rules and were caught by the wildcard `/config/{document=**}` deny rule, preventing legitimate client reads and admin updates.
5. `/reward_tasks/{taskId}`, `/leaderboard/weekly_current`, and `/leaderboard_history/{cycleId}` were completely blocked by the catch-all deny rule.

============================================================
3. RULES AFTER
============================================================

The complete updated rules for `firestore.rules` are active:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // --- Helper Functions ---

    function isAuthenticated() {
      return request.auth != null;
    }

    function isOwner(uid) {
      return isAuthenticated() && request.auth.uid == uid;
    }

    // Official authoritative check against /admins/{uid} collection + Owner bootstrap
    function isAdmin() {
      return isAuthenticated() && (
        (request.auth.token.email != null && request.auth.token.email.matches('(?i)sulopros01@gmail.com')) ||
        (exists(/databases/$(database)/documents/admins/$(request.auth.uid)) &&
         get(/databases/$(database)/documents/admins/$(request.auth.uid)).data.enabled == true)
      );
    }

    // Sensitive administrative fields that standard users are strictly forbidden from modifying
    function modifyingSensitiveUserFields() {
      return request.resource.data.diff(resource.data).affectedKeys().hasAny([
        'role',
        'isPremium',
        'subscriptionTier',
        'planId',
        'durationDays',
        'subscriptionStatus',
        'subscriptionSource',
        'subscriptionReferenceId',
        'subscriptionStartedAt',
        'subscriptionExpiresAt',
        'isPro',
        'proExpiresAt',
        'proPlan',
        'plan',
        'pointsBalance',
        'totalPointsEarned',
        'totalPointsSpent',
        'isActive',
        'isBanned',
        'banReason',
        'banExpiresAt',
        'canWatch',
        'canDownload',
        'canChat',
        'canStory',
        'canP2P',
        'canComment',
        'canUpload',
        'canRequest',
        'watchBan',
        'downloadBan',
        'chatBan',
        'storyBan',
        'p2pBan',
        'deviceLimit',
        'maxDevices',
        'allowedQuality',
        'downloadLimit',
        'offlineDaysOverride',
        'forcedAdsOverride',
        'uid',
        'id',
        'createdAt',
        'admin',
        'isAdmin'
      ]);
    }

    // Enforce safe default constraints when a user registers and provisions their own document
    function hasSafeUserCreationDefaults(userId) {
      let data = request.resource.data;
      return isOwner(userId) &&
        (!('uid' in data) || data.uid == userId) &&
        (!('id' in data) || data.id == userId) &&
        (!('role' in data) || data.role == 'user') &&
        (!('admin' in data) || data.admin == false) &&
        (!('isAdmin' in data) || data.isAdmin == false) &&
        (!('isPremium' in data) || data.isPremium == false) &&
        (!('subscriptionTier' in data) || data.subscriptionTier in ['free', 'FREE']) &&
        (!('planId' in data) || data.planId in ['free', '']) &&
        (!('durationDays' in data) || data.durationDays == null) &&
        (!('subscriptionStatus' in data) || data.subscriptionStatus in ['active', 'ACTIVE', 'free', 'FREE']) &&
        (!('subscriptionSource' in data) || data.subscriptionSource in ['legacy', 'LEGACY', 'free', 'FREE', '']) &&
        (!('subscriptionReferenceId' in data) || data.subscriptionReferenceId == null) &&
        (!('subscriptionStartedAt' in data) || data.subscriptionStartedAt == null) &&
        (!('subscriptionExpiresAt' in data) || data.subscriptionExpiresAt == null) &&
        (!('isPro' in data) || data.isPro == false) &&
        (!('proExpiresAt' in data) || data.proExpiresAt == null) &&
        (!('proPlan' in data) || data.proPlan == null || data.proPlan == '') &&
        (!('plan' in data) || data.plan in ['free', '', null]) &&
        (!('pointsBalance' in data) || data.pointsBalance == 0) &&
        (!('totalPointsEarned' in data) || data.totalPointsEarned == 0) &&
        (!('totalPointsSpent' in data) || data.totalPointsSpent == 0) &&
        (!('isActive' in data) || data.isActive == true) &&
        (!('isBanned' in data) || data.isBanned == false) &&
        (!('banReason' in data) || data.banReason == null || data.banReason == '') &&
        (!('banExpiresAt' in data) || data.banExpiresAt == null) &&
        (!('canWatch' in data) || data.canWatch == true) &&
        (!('canDownload' in data) || data.canDownload == true) &&
        (!('canChat' in data) || data.canChat == true) &&
        (!('canStory' in data) || data.canStory == true) &&
        (!('canP2P' in data) || data.canP2P == true) &&
        (!('canComment' in data) || data.canComment == true) &&
        (!('canUpload' in data) || data.canUpload == false) &&
        (!('canRequest' in data) || data.canRequest == true) &&
        (!('watchBan' in data) || data.watchBan == false) &&
        (!('downloadBan' in data) || data.downloadBan == false) &&
        (!('chatBan' in data) || data.chatBan == false) &&
        (!('storyBan' in data) || data.storyBan == false) &&
        (!('p2pBan' in data) || data.p2pBan == false) &&
        (!('deviceLimit' in data) || data.deviceLimit <= 2) &&
        (!('maxDevices' in data) || data.maxDevices <= 2) &&
        (!('allowedQuality' in data) || data.allowedQuality == null) &&
        (!('downloadLimit' in data) || data.downloadLimit == null) &&
        (!('offlineDaysOverride' in data) || data.offlineDaysOverride == null) &&
        (!('forcedAdsOverride' in data) || data.forcedAdsOverride == null);
    }

    // --- Admins Collection ---
    match /admins/{adminUid} {
      allow read: if isAuthenticated() && (request.auth.uid == adminUid || isAdmin());
      allow write: if isAdmin() || (isAuthenticated() && request.auth.uid == adminUid && request.auth.token.email != null && request.auth.token.email.matches('(?i)sulopros01@gmail.com'));
    }

    // --- Users Collection ---
    match /users/{userId} {
      allow read: if isAdmin() || isOwner(userId);
      allow create: if isAdmin() || hasSafeUserCreationDefaults(userId);
      allow update: if isAdmin() || (isOwner(userId) && !modifyingSensitiveUserFields());
      allow delete: if isAdmin();

      // Immutable Point Transaction Ledger (Phase 03A.1)
      match /point_transactions/{txId} {
        allow read: if isAdmin() || isOwner(userId);
        allow write: if isAdmin();
      }

      // Task Claims (Phase 03A.1)
      match /task_claims/{taskId} {
        allow read: if isAdmin() || isOwner(userId);
        allow write: if isAdmin();
      }

      // General user subcollections (history, bookmarks, devices, favorites, etc.)
      match /{subcollection}/{docId} {
        allow read: if isAdmin() || isOwner(userId);
        allow write: if isAdmin() || (isOwner(userId) && !(subcollection in ['point_transactions', 'task_claims']));
      }
    }

    // --- App Configuration ---
    match /config/app {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // --- Search Order Configuration (Phase EXT-RULES-01) ---
    match /config/search_order {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // --- System Feature Control Flags (Phase 03A.1) ---
    match /config/features {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // --- System Economy Configuration & Redemption Pricing (Phase 03A.1) ---
    match /config/economy {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // Deny all other unspecified /config documents
    match /config/{document=**} {
      allow read, write: if false;
    }

    // --- Reward Tasks Catalog (Phase 03A.1) ---
    match /reward_tasks/{taskId} {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // --- Leaderboard & History (Phase 03A.1) ---
    match /leaderboard/weekly_current {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    match /leaderboard_history/{cycleId} {
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

    // --- Legacy Extensions Management ---
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

    // --- Notifications Management ---
    match /notifications/{notificationId} {
      allow read: if isAuthenticated();
      allow write: if isAdmin();
    }

    // --- Audit Logs ---
    match /auditLogs/{logId} {
      allow read, write: if isAdmin();
    }

    // --- Reports Collection ---
    match /reports/{reportId} {
      allow read: if isAdmin() || (isAuthenticated() && resource.data.userId == request.auth.uid);
      allow create: if isAdmin() || (
        isAuthenticated() &&
        request.resource.data.userId == request.auth.uid &&
        (request.resource.data.status == 'PENDING' || request.resource.data.status == 'pending') &&
        (!('resolvedBy' in request.resource.data) || request.resource.data.resolvedBy == '' || request.resource.data.resolvedBy == null) &&
        (!('resolvedAt' in request.resource.data) || request.resource.data.resolvedAt == null) &&
        (!('resolutionNotes' in request.resource.data) || request.resource.data.resolutionNotes == '' || request.resource.data.resolutionNotes == null)
      );
      allow update, delete: if isAdmin();
    }

    // --- Support Conversations ---
    match /support_conversations/{conversationId} {
      allow read: if isAdmin() || (isAuthenticated() && resource.data.userId == request.auth.uid);
      allow create: if isAdmin() || (isAuthenticated() && request.resource.data.userId == request.auth.uid);
      allow update: if isAdmin() || (
        isAuthenticated() &&
        resource.data.userId == request.auth.uid &&
        request.resource.data.userId == resource.data.userId
      );
      allow delete: if isAdmin();

      match /messages/{messageId} {
        allow read: if isAdmin() || (isAuthenticated() && get(/databases/$(database)/documents/support_conversations/$(conversationId)).data.userId == request.auth.uid);
        allow create: if isAdmin() || (
          isAuthenticated() &&
          get(/databases/$(database)/documents/support_conversations/$(conversationId)).data.userId == request.auth.uid &&
          request.resource.data.senderId == request.auth.uid &&
          (!('senderRole' in request.resource.data) || request.resource.data.senderRole == 'user')
        );
        allow update, delete: if isAdmin();
      }
    }

    // --- Pro Subscription Upgrade Requests ---
    match /pro_requests/{requestId} {
      allow read: if isAdmin() || (isAuthenticated() && resource.data.userId == request.auth.uid);
      allow create: if isAdmin() || (
        isAuthenticated() &&
        request.resource.data.userId == request.auth.uid &&
        (request.resource.data.status == 'PENDING' || request.resource.data.status == 'pending' || !('status' in request.resource.data)) &&
        (!('reviewedBy' in request.resource.data) || request.resource.data.reviewedBy == '' || request.resource.data.reviewedBy == null) &&
        (!('reviewedAt' in request.resource.data) || request.resource.data.reviewedAt == null) &&
        (!('rejectionReason' in request.resource.data) || request.resource.data.rejectionReason == '' || request.resource.data.rejectionReason == null)
      );
      allow update, delete: if isAdmin();
    }

    // --- Social Chat Conversations ---
    match /conversations/{conversationId} {
      allow read: if isAuthenticated() && (
        ('participants' in resource.data && request.auth.uid in resource.data.participants) ||
        resource.data.userId == request.auth.uid
      );
      allow create: if isAuthenticated() && (
        ('participants' in request.resource.data && request.auth.uid in request.resource.data.participants) ||
        request.resource.data.userId == request.auth.uid
      );
      allow update: if isAuthenticated() && (
        ('participants' in resource.data && request.auth.uid in resource.data.participants) ||
        resource.data.userId == request.auth.uid
      );
      allow delete: if isAuthenticated() && (
        resource.data.userId == request.auth.uid ||
        ('ownerId' in resource.data && resource.data.ownerId == request.auth.uid)
      );

      match /messages/{messageId} {
        allow read: if isAuthenticated() && (
          ('participants' in get(/databases/$(database)/documents/conversations/$(conversationId)).data &&
           request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participants) ||
          get(/databases/$(database)/documents/conversations/$(conversationId)).data.userId == request.auth.uid
        );
        allow create: if isAuthenticated() &&
          request.resource.data.senderId == request.auth.uid && (
            ('participants' in get(/databases/$(database)/documents/conversations/$(conversationId)).data &&
             request.auth.uid in get(/databases/$(database)/documents/conversations/$(conversationId)).data.participants) ||
            get(/databases/$(database)/documents/conversations/$(conversationId)).data.userId == request.auth.uid
          );
        allow update, delete: if isAuthenticated() && resource.data.senderId == request.auth.uid;
      }
    }

    // --- User Stories ---
    match /stories/{storyId} {
      allow read: if isAuthenticated();
      allow create: if isAuthenticated() && request.resource.data.userId == request.auth.uid;
      allow update, delete: if isAuthenticated() && resource.data.userId == request.auth.uid;
    }

    // Deny access to any other unspecified collection
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

============================================================
4. MODIFIED FILES
============================================================

1. `/firestore.rules`:
   - Updated `isAdmin()` to remove `users.role` dependency.
   - Expanded `modifyingSensitiveUserFields()` with canonical subscription & points fields.
   - Expanded `hasSafeUserCreationDefaults()` with initial points and canonical subscription defaults.
   - Added `point_transactions/{txId}` and `task_claims/{taskId}` rules under `/users/{userId}`.
   - Added exclusion of `point_transactions` and `task_claims` from user subcollection wildcard writes.
   - Added `/config/features` and `/config/economy` rules.
   - Added `/reward_tasks/{taskId}`, `/leaderboard/weekly_current`, and `/leaderboard_history/{cycleId}` rules.

2. `/app/src/test/java/com/example/CineStreamAdminLogicTest.kt`:
   - Added unit test suite `PHASE SUBSCRIPTION-POINTS-03A.1 — SECURITY RULES & CONTRACT HARDENING TESTS`.
   - Verified sensitive user fields classification, canonical audit log path, canonical config paths, and quality-subscription decoupling.

3. `/firestore-tests/phase03a1-rules.test.js`:
   - Created full 36-case test suite executing against the Firebase Firestore emulator.

============================================================
5. UNMODIFIED FILES
============================================================

- Entire Users App codebase (separate repository/session — 0 files modified).
- Android build configurations: `build.gradle.kts`, `settings.gradle.kts`.
- App identity & secrets: `metadata.json`, `debug.keystore`, `debug.keystore.base64`.
- Core application runtime code: `AdminRepository.kt`, `ProRequestRepository.kt`, `ViewModels.kt`, Compose screens (all remained stable from Phase 03A).

============================================================
6. CANONICAL PATH MATRIX
============================================================

| Firestore Path | Guest Access | Authenticated User Access | Admin Access | Description |
| :--- | :--- | :--- | :--- | :--- |
| `/admins/{adminUid}` | DENIED | READ (own) / WRITE (owner email) | READ / WRITE | Administrative authority whitelist |
| `/users/{userId}` | DENIED | READ (own) / UPDATE (non-sensitive) | READ / WRITE / DELETE | User entitlement & profile document |
| `/users/{userId}/point_transactions/{txId}` | DENIED | READ (own) / WRITE DENIED | READ / WRITE | Immutable points ledger |
| `/users/{userId}/task_claims/{taskId}` | DENIED | READ (own) / WRITE DENIED | READ / WRITE | Server-settled task claims |
| `/users/{userId}/{subcollection}/{docId}` | DENIED | READ (own) / WRITE (own, non-ledger) | READ / WRITE | Bookmarks, history, favorites |
| `/config/app` | DENIED | READ | READ / WRITE | OTA, maintenance, DRM settings |
| `/config/search_order` | DENIED | READ | READ / WRITE | Provider search order |
| `/config/features` | DENIED | READ | READ / WRITE | Feature flags & kill switches |
| `/config/economy` | DENIED | READ | READ / WRITE | Points redemption & ad parameters |
| `/config/{wildcard}` | DENIED | DENIED | DENIED | Legacy/orphaned config protection |
| `/reward_tasks/{taskId}` | DENIED | READ | READ / WRITE | Tasks catalog |
| `/leaderboard/weekly_current` | DENIED | READ | READ / WRITE | Current weekly rankings |
| `/leaderboard_history/{cycleId}` | DENIED | READ | READ / WRITE | Archived weekly cycles |
| `/pro_requests/{requestId}` | DENIED | READ (own) / CREATE (own, PENDING) | READ / WRITE | Pro upgrade requests |
| `/auditLogs/{logId}` | DENIED | DENIED | READ / WRITE | Immutable audit trail |
| `/reports/{reportId}` | DENIED | READ (own) / CREATE (own, PENDING) | READ / WRITE / DELETE | Moderation reports |
| `/support_conversations/{convId}` | DENIED | READ (own) / CREATE / UPDATE | READ / WRITE / DELETE | Support chat threads |
| `/managed_extensions/{extId}` | DENIED | READ | READ / WRITE | Managed scrapers catalog |
| `/{document=**}` (Catch-all) | DENIED | DENIED | DENIED | Zero-trust default close |

============================================================
7. FEATURE CONFIG SECURITY (/config/features)
============================================================

Classification: EMULATOR VERIFIED
Behavior:
- Guest read: DENIED.
- Authenticated user read: ALLOWED.
- Standard user write: DENIED.
- Admin read & write: ALLOWED.
Supported features validated:
`subscriptions`, `points`, `dailyLogin`, `rewardedAds`, `tasks`, `leaderboard`.
Supported states: `ACTIVE`, `COMING_SOON`, `DISABLED`.

============================================================
8. ECONOMY CONFIG SECURITY (/config/economy)
============================================================

Classification: EMULATOR VERIFIED
Behavior:
- Guest read: DENIED.
- Authenticated user read: ALLOWED.
- Standard user write: DENIED.
- Admin read & write: ALLOWED.
Guarantees: Standard clients cannot alter redemption costs (`pro_lite_1d`, `pro_lite_7d`, `pro_lite_10d`, `pro_30d`), daily login rewards ladder, rewarded ad points, or cooldown timers.

============================================================
9. POINTS WALLET SECURITY (/users/{uid})
============================================================

Classification: EMULATOR VERIFIED & UNIT TEST VERIFIED
User summary fields:
`pointsBalance`, `totalPointsEarned`, `totalPointsSpent`.
Guarantees:
- Read: Users can inspect their own point wallet.
- Mutation: Directly modifying any of these fields via client SDK `update()` is classified as sensitive and strictly rejected by `modifyingSensitiveUserFields()`.
- Initial State: On registration, `hasSafeUserCreationDefaults()` rejects any attempt by a user to initialize non-zero points balances.

============================================================
10. POINTS LEDGER SECURITY (/users/{uid}/point_transactions/{txId})
============================================================

Classification: EMULATOR VERIFIED
Behavior:
- Read: Users can only read transactions under their own UID. Cross-user reading is rejected.
- Writes: CREATE, UPDATE, DELETE are completely DENIED to standard users.
- Wildcard Subcollection Isolation: Specifically excluded from `match /{subcollection}/{docId}` writes.
- Immutability: Standard clients cannot fabricate transactions, alter `balanceBefore`, `balanceAfter`, `amount`, or inject fake `actorUid` values. Mutations occur only through administrative operations or trusted server authority.

============================================================
11. TASK SECURITY (/reward_tasks, /users/{uid}/task_claims)
============================================================

Classification: EMULATOR VERIFIED
Behavior:
- Catalog (`/reward_tasks/{taskId}`): Authenticated users have read-only access. Direct client creation, update, or deletion is denied.
- Claims (`/users/{uid}/task_claims/{taskId}`): Users can read their own claim history. Direct client creation or claims writing is strictly denied. Task completion will be verified and settled by trusted server authority.

============================================================
12. LEADERBOARD SECURITY (/leaderboard/weekly_current, /leaderboard_history)
============================================================

Classification: EMULATOR VERIFIED
Behavior:
- Read: Authenticated users can view current rankings and historical archives.
- Write: Denied to all standard clients. Users cannot alter `rank`, `weeklyEarnedPoints`, `reward`, or settlement state. Writes are restricted to admins or scheduled backend settlement functions.

============================================================
13. PRO REQUEST SECURITY (/pro_requests/{requestId})
============================================================

Classification: EMULATOR VERIFIED
Behavior:
- Creation: Standard users can only create requests for their own `request.auth.uid` with initial status `PENDING`.
- Self-Approval Prevention: Users cannot set `reviewedBy`, `reviewedAt`, or status `APPROVED`.
- Updates/Deletions: Strictly restricted to admins.

============================================================
14. SUBSCRIPTION FIELD SECURITY
============================================================

Classification: EMULATOR VERIFIED & UNIT TEST VERIFIED
Protected fields:
`subscriptionTier`, `planId`, `durationDays`, `subscriptionStatus`, `subscriptionSource`, `subscriptionReferenceId`, `subscriptionStartedAt`, `subscriptionExpiresAt`, `isPremium`, `isPro`, `proExpiresAt`, `proPlan`, `plan`.
Guarantees:
- Standard users attempting to write `subscriptionTier = "PRO"` or `isPremium = true` are rejected.
- Cross-user updates are rejected.
- New user account provisioning requires safe defaults (`FREE`, no active expiration).

============================================================
15. QUALITY INDEPENDENCE VERIFICATION
============================================================

Classification: STATICALLY VERIFIED & UNIT TEST VERIFIED
Absolute Rule Verification:
- Video quality is NOT a subscription benefit.
- `allowedQuality` and `downloadLimit` are completely independent technical permissions.
- In `firestore.rules`: `allowedQuality` and `downloadLimit` are protected in `modifyingSensitiveUserFields()` so standard users cannot tamper with them, but they are NEVER coupled to subscription tier.
- In `AdminRepository.kt`: Line 471 & Line 549 explicitly confirm that updating or revoking subscriptions leaves `allowedQuality` and `downloadLimit` untouched.
- Unit Test `testPhase03A1DecouplingQualityFromSubscription` confirms that `allowedQuality` is null/independent regardless of whether a user is `PRO`, `PRO_LITE`, or `FREE`.

============================================================
16. AUDIT LOG PATH RESOLUTION
============================================================

Finding & Resolution:
- Canonical Audit Path: `/auditLogs`
- Code Usage: `FirebaseCollections.AUDIT_LOGS = "auditLogs"` (used by `AdminRepository.kt` lines 49, 289, 1196, 1234).
- Rules Usage: `match /auditLogs/{logId} { allow read, write: if isAdmin(); }`
- Compose Navigation Note: The internal Compose UI route is `"audit_logs"`, which is purely client UI navigation, while the Firestore collection is strictly `/auditLogs`.
- Production Verification: The collection `/audit_logs` does NOT exist in rules or repository code; `/auditLogs` is the single canonical authority path.

============================================================
17. CONFIG GLOBAL STATUS
============================================================

Classification: LEGACY / ORPHANED
Finding:
- Superseded by `/config/app` for application settings, OTA, DRM, and maintenance.
- Rules Status: Explicitly blocked by `match /config/{document=**} { allow read, write: if false; }`.
- Recommendation: No data deletion performed in this phase.

============================================================
18. PRO LITE CUSTOM DURATION FINDING
============================================================

Audit Finding:
- Canonical Plan SKUs: `pro_lite_1d`, `pro_lite_7d`, `pro_lite_10d`, and `pro_30d`.
- Admin UI Support: The Admin UI allows selecting 1d, 7d, 10d, or a custom number of days (e.g. 14 days).
- Classification: Custom duration is an **INTERNAL ADMINISTRATIVE GRANT MECHANISM** with `subscriptionSource = "ADMIN_GRANT"`. It is NOT a user-facing SKU and is not available for purchase or points redemption in the public catalog.
- Architectural Compatibility: Fully valid and compliant with the contract; no silent removal or SKU corruption occurred.

============================================================
19. EMULATOR TESTS
============================================================

Harness: `@firebase/rules-unit-testing` + Node.js 22 built-in test runner against Firebase Firestore Emulator.
Test File: `firestore-tests/phase03a1-rules.test.js`
Total Tests: 36
Passed: 36
Failed: 0
Execution Time: ~9.7s

Summary of Test Results:
- Suite A (Guest): 4/4 PASSED
- Suite B (Authenticated User): 20/20 PASSED
- Suite C (Authoritative Admin): 8/8 PASSED
- Suite D (Wildcard / Unknown Paths): 4/4 PASSED

============================================================
20. ANDROID TESTS
============================================================

Harness: Gradle JVM Unit Tests (`gradle :app:testDebugUnitTest`)
Target Test Suite: `CineStreamAdminLogicTest`
Status: BUILD SUCCESSFUL (33 actionable tasks executed/up-to-date)
Phase 03A.1 Tests Added & Passing:
1. `testPhase03A1SensitiveUserFieldsClassification`
2. `testPhase03A1CanonicalAuditLogPath`
3. `testPhase03A1CanonicalConfigPaths`
4. `testPhase03A1DecouplingQualityFromSubscription`

============================================================
21. BUILD VERIFICATION
============================================================

Tool: `compile_applet`
Status: Succeeded with 0 compilation errors.
APK / Compilation Status: Valid.

============================================================
22. STATIC SECURITY SCAN
============================================================

Regex Scanned:
`(subscriptionTier.*allowedQuality|allowedQuality.*subscriptionTier|isPremium.*allowedQuality|allowedQuality.*isPremium|subscription.*downloadLimit|downloadLimit.*subscription|PRO.*4K|PRO_LITE.*1080)`
Matches Found: 0.
Result: Clean. Zero subscription-to-quality coupling found in codebase.

============================================================
23. PRODUCTION DEPLOYMENT
============================================================

Status: NOT VERIFIED
Reason: Production Firebase credentials are not provisioned in the local development environment (`google-services.json` contains placeholder project ID `"remixed-project-id"`). As mandated by Section 23 of the contract, production deployment is classified as NOT VERIFIED without fabrication.

============================================================
24. REMAINING BACKEND DEPENDENCIES
============================================================

The following operations require future trusted server authority (e.g. Cloud Functions):
1. `claimDailyLogin`: Server verification of daily consecutive login streaks and points credit.
2. `verifyRewardedAd`: Server-side SSV (Server-Side Verification) callback validation from ad networks.
3. `claimTaskReward`: Automated verification of external task conditions.
4. `redeemSubscription`: Atomic client-side or server-side redemption debiting points and activating plans.
5. `weeklyLeaderboardSettlement`: Scheduled cron function to freeze cycles and distribute rewards.

============================================================
25. KNOWN LIMITATIONS
============================================================

1. Firestore Security Rules enforce zero-trust access control, immutability, and boundary validation, but cannot run background cron jobs (e.g. weekly Monday 00:00 UTC cycle reset).
2. Direct client writes to `point_transactions` and `task_claims` are completely denied; until server functions are provisioned, all point ledger additions must occur via Admin App or trusted backend scripts.

============================================================
26. FINAL VERDICT
============================================================

VERDICT: PASS

All conditions for PASS are satisfied:
- Rules compile and execute cleanly in standard Firestore engine.
- 36/36 emulator rules tests passed.
- All subscription fields remain strictly protected against tampering.
- Points wallet and immutable ledger are protected against client forgery.
- Feature control and economy configurations are protected against normal users.
- Zero subscription-quality coupling exists.
- Zero Users App files were modified.
