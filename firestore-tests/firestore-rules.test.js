const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const fs = require('fs');
const path = require('path');

const PROJECT_ID = 'cinestream-admin-test';
const RULES_PATH = path.resolve(__dirname, '../firestore.rules');

describe('CineStream Admin Phase C7: Firestore Security Rules Live Verification', function () {
  this.timeout(30000);

  let testEnv;

  before(async function () {
    const rules = fs.readFileSync(RULES_PATH, 'utf8');
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host: '127.0.0.1',
        port: 8085,
        rules: rules
      }
    });
  });

  beforeEach(async function () {
    await testEnv.clearFirestore();

    // Seed canonical authority data using withSecurityRulesDisabled
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();

      // CASE A: Enabled Admin
      await db.doc('admins/adminA').set({
        enabled: true,
        email: 'adminA@cinestream.com',
        role: 'superadmin'
      });

      // CASE B: Authenticated Non-Admin (enabled = false)
      await db.doc('admins/userA').set({
        enabled: false,
        email: 'userA@cinestream.com'
      });

      // User Profiles in /users
      // userA (normal user)
      await db.doc('users/userA').set({
        uid: 'userA',
        id: 'userA',
        displayName: 'User Alice',
        role: 'user',
        isPremium: false,
        isPro: false,
        plan: 'free',
        canWatch: true,
        canDownload: true,
        canChat: true,
        isBanned: false
      });

      // userB (normal user, no doc in /admins)
      await db.doc('users/userB').set({
        uid: 'userB',
        id: 'userB',
        displayName: 'User Bob',
        role: 'user',
        isPremium: false,
        isPro: false,
        plan: 'free',
        canWatch: true,
        canDownload: true,
        canChat: true,
        isBanned: false
      });

      // CASE D: users.role = "admin" but NO document in /admins
      await db.doc('users/userC').set({
        uid: 'userC',
        id: 'userC',
        displayName: 'Attacker Charlie',
        role: 'admin', // Fake metadata role
        isPremium: false,
        isPro: false
      });

      // Support conversation belonging to userA
      await db.doc('support_conversations/convA').set({
        conversationId: 'convA',
        userId: 'userA',
        subject: 'Streaming issue',
        status: 'OPEN'
      });

      // Pro request belonging to userA
      await db.doc('pro_requests/reqA').set({
        requestId: 'reqA',
        userId: 'userA',
        status: 'PENDING',
        requestedTier: 'pro_annual'
      });

      // Managed Extension in catalog
      await db.doc('managed_extensions/ext1').set({
        id: 'ext1',
        name: 'Subtitle Provider',
        version: '1.0.0',
        status: 'PUBLISHED'
      });

      // Audit Log
      await db.doc('auditLogs/log1').set({
        logId: 'log1',
        action: 'USER_CREATED',
        timestamp: 1000
      });

      // Config docs
      await db.doc('config/global').set({
        maintenance: false,
        minAppVersion: '1.0.0'
      });

      await db.doc('config/app').set({
        maintenance: false,
        latestVersionCode: 1,
        latestVersionName: '1.0.0'
      });

      await db.doc('config/search_order').set({
        movie: ['qfilm', 'egydead'],
        tv: ['egydead', 'qfilm'],
        series: ['egydead', 'qfilm'],
        anime: ['witanime', 'anime4up'],
        updatedAt: 1759160000000,
        updatedBy: 'admin@cinestream.com'
      });
    });
  });

  after(async function () {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  // ==========================================
  // SECTION 4: ADMIN AUTHORITY CONTRACT
  // ==========================================
  describe('4. Admin Authority Contract Forensic Check', function () {
    it('CASE A: Enabled Admin (/admins/adminA.enabled == true) ALLOWED for admin operations', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      // Admin should be able to read audit logs (admin-only)
      await assertSucceeds(adminDb.doc('auditLogs/log1').get());
      // Admin should be able to write to /admins
      await assertSucceeds(adminDb.doc('admins/adminNew').set({ enabled: true }));
    });

    it('CASE B: Authenticated Non-Admin (/admins/userA.enabled == false) DENIED for admin operations', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      // Non-admin cannot read audit logs
      await assertFails(userDb.doc('auditLogs/log1').get());
      // Non-admin cannot write to /admins
      await assertFails(userDb.doc('admins/userA').set({ enabled: true }));
    });

    it('CASE C: Missing Admin Document (/admins/userB does not exist) DENIED for admin operations', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      // No admin doc exists -> admin-only collections must be denied
      await assertFails(userDb.doc('auditLogs/log1').get());
      await assertFails(userDb.doc('managed_extensions/ext1').update({ name: 'Tampered' }));
    });

    it('CASE D (MANDATORY): users.role = "admin" but /admins doc missing MUST BE DENIED', async function () {
      const userCDb = testEnv.authenticatedContext('userC').firestore();
      // Even though userC has role: "admin" in /users/userC, userC is NOT an admin in /admins!
      await assertFails(userCDb.doc('auditLogs/log1').get());
      await assertFails(userCDb.doc('admins/userC').set({ enabled: true }));
      await assertFails(userCDb.doc('managed_extensions/extNew').set({ name: 'Hacked Ext' }));
    });
  });

  // ==========================================
  // SECTION 5: USERS COLLECTION SECURITY TESTS
  // ==========================================
  describe('5. Users Collection Security (/users/{uid})', function () {
    it('OWNER READ: User can read own profile', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('users/userA').get());
    });

    it('OTHER USER READ: User A CANNOT read User B profile', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userB').get());
    });

    it('ADMIN READ: Admin can read any user profile', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('users/userA').get());
      await assertSucceeds(adminDb.doc('users/userB').get());
    });

    it('OWNER SAFE UPDATE: User can update non-sensitive profile fields (displayName, etc.)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('users/userA').update({
        displayName: 'Alice Updated'
      }));
    });

    it('PROTECTED FIELDS UPDATE: User CANNOT update sensitive field (role)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({
        role: 'admin'
      }));
    });

    it('PROTECTED FIELDS UPDATE: User CANNOT update sensitive field (isPremium)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({
        isPremium: true
      }));
    });

    it('PROTECTED FIELDS UPDATE: User CANNOT update sensitive field (isPro)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({
        isPro: true
      }));
    });

    it('PROTECTED FIELDS UPDATE: User CANNOT update sensitive field (canWatch)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({
        canWatch: false
      }));
    });

    it('PROTECTED FIELDS UPDATE: User CANNOT update sensitive field (isBanned)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({
        isBanned: true
      }));
    });

    it('PROTECTED FIELDS UPDATE: User CANNOT update sensitive field (deviceLimit)', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({
        deviceLimit: 10
      }));
    });

    it('ADMIN UPDATE: Admin can update any user field including sensitive fields', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('users/userA').update({
        isBanned: true,
        banReason: 'Terms violation',
        isPremium: true,
        canWatch: false
      }));
    });

    it('USER DELETE: Standard user CANNOT delete profile', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').delete());
    });

    it('ADMIN DELETE: Admin can delete user profile', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('users/userB').delete());
    });

    it('USER REGISTRATION / CREATION: User can create own profile with safe defaults', async function () {
      const newUserId = 'newUserRegistered';
      const userDb = testEnv.authenticatedContext(newUserId).firestore();
      await assertSucceeds(userDb.doc(`users/${newUserId}`).set({
        uid: newUserId,
        id: newUserId,
        displayName: 'New Member',
        role: 'user',
        isPremium: false,
        isPro: false,
        plan: 'free',
        subscriptionTier: 'free',
        subscriptionStatus: 'free',
        canWatch: true,
        canDownload: true,
        canChat: true,
        canStory: true,
        canP2P: true,
        canComment: true,
        canUpload: false,
        canRequest: true,
        isBanned: false,
        deviceLimit: 2
      }));
    });

    it('USER REGISTRATION: User CANNOT create profile for another user ID', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/attackerForgedId').set({
        uid: 'attackerForgedId',
        role: 'user',
        isPremium: false
      }));
    });

    it('USER REGISTRATION: User CANNOT forge role="admin" during registration', async function () {
      const newUserId = 'userAttemptingAdmin';
      const userDb = testEnv.authenticatedContext(newUserId).firestore();
      await assertFails(userDb.doc(`users/${newUserId}`).set({
        uid: newUserId,
        role: 'admin',
        isPremium: false
      }));
    });

    it('USER REGISTRATION: User CANNOT forge isPremium=true during registration', async function () {
      const newUserId = 'userAttemptingPremium';
      const userDb = testEnv.authenticatedContext(newUserId).firestore();
      await assertFails(userDb.doc(`users/${newUserId}`).set({
        uid: newUserId,
        role: 'user',
        isPremium: true
      }));
    });

    it('USER REGISTRATION: User CANNOT forge deviceLimit > 2 during registration', async function () {
      const newUserId = 'userAttemptingHighLimit';
      const userDb = testEnv.authenticatedContext(newUserId).firestore();
      await assertFails(userDb.doc(`users/${newUserId}`).set({
        uid: newUserId,
        role: 'user',
        isPremium: false,
        deviceLimit: 5
      }));
    });
  });

  // ==========================================
  // SECTION: ADMINS COLLECTION SECURITY
  // ==========================================
  describe('Admins Collection Security (/admins/{adminUid})', function () {
    it('ADMIN READ: Admin can read any admin document', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('admins/userA').get());
    });

    it('USER READ OWN: User can read own admin document to check status', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('admins/userA').get());
    });

    it('USER READ OTHER: User CANNOT read another user admin document', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertFails(userDb.doc('admins/adminA').get());
    });

    it('USER WRITE: Standard user CANNOT write to /admins', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('admins/userA').update({ enabled: true }));
      await assertFails(userDb.doc('admins/userB').set({ enabled: true }));
    });

    it('ADMIN WRITE: Admin can write/grant admin authority in /admins', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('admins/userB').set({
        enabled: true,
        email: 'userB@cinestream.com'
      }));
    });
  });

  // ==========================================
  // SECTION 6: SUPPORT CONVERSATIONS SECURITY
  // ==========================================
  describe('6. Support Conversations Security (/support_conversations)', function () {
    it('OWNER READ: User can read own support conversation', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('support_conversations/convA').get());
    });

    it('OTHER USER READ: User B CANNOT read User A support conversation', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertFails(userDb.doc('support_conversations/convA').get());
    });

    it('ADMIN READ: Admin can read any support conversation', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('support_conversations/convA').get());
    });

    it('OWNER CREATE: User can create conversation with own userId', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('support_conversations/convA2').set({
        conversationId: 'convA2',
        userId: 'userA',
        subject: 'Billing question',
        status: 'OPEN'
      }));
    });

    it('USER CREATE FOR ANOTHER: User A CANNOT create conversation with userId of User B', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('support_conversations/convForged').set({
        conversationId: 'convForged',
        userId: 'userB',
        subject: 'Fake ticket'
      }));
    });

    it('MESSAGES: Owner can send message with senderId=ownUid and senderRole=user', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('support_conversations/convA/messages/msg1').set({
        messageId: 'msg1',
        senderId: 'userA',
        senderRole: 'user',
        text: 'Hello support team',
        timestamp: Date.now()
      }));
    });

    it('MESSAGES: User CANNOT forge senderRole=admin in support message', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('support_conversations/convA/messages/msgForged').set({
        messageId: 'msgForged',
        senderId: 'userA',
        senderRole: 'admin',
        text: 'I am the admin now'
      }));
    });

    it('MESSAGES: User B CANNOT post message in User A conversation', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertFails(userDb.doc('support_conversations/convA/messages/msgSpam').set({
        messageId: 'msgSpam',
        senderId: 'userB',
        senderRole: 'user',
        text: 'Intruder message'
      }));
    });

    it('MESSAGES: Admin can send support message in any conversation', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('support_conversations/convA/messages/msgReply').set({
        messageId: 'msgReply',
        senderId: 'adminA',
        senderRole: 'admin',
        text: 'Hello, how can I help you?',
        timestamp: Date.now()
      }));
    });

    it('USER DELETE: User CANNOT delete support conversation or message', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('support_conversations/convA').delete());
    });

    it('ADMIN DELETE: Admin can delete support conversation', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('support_conversations/convA').delete());
    });
  });

  // ==========================================
  // SECTION 7: PRO REQUESTS SECURITY
  // ==========================================
  describe('7. Pro Requests Security (/pro_requests)', function () {
    it('OWNER READ: User can read own pro request', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('pro_requests/reqA').get());
    });

    it('OTHER USER READ: User B CANNOT read User A pro request', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertFails(userDb.doc('pro_requests/reqA').get());
    });

    it('ADMIN READ: Admin can read any pro request', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('pro_requests/reqA').get());
    });

    it('OWNER CREATE: User can submit pro request with status PENDING', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertSucceeds(userDb.doc('pro_requests/reqB').set({
        requestId: 'reqB',
        userId: 'userB',
        status: 'PENDING',
        requestedTier: 'pro_monthly'
      }));
    });

    it('USER FORGE: User CANNOT create pro request with status APPROVED', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertFails(userDb.doc('pro_requests/reqBFake').set({
        requestId: 'reqBFake',
        userId: 'userB',
        status: 'APPROVED',
        requestedTier: 'pro_monthly'
      }));
    });

    it('USER FORGE: User CANNOT create pro request with reviewedBy populated', async function () {
      const userDb = testEnv.authenticatedContext('userB').firestore();
      await assertFails(userDb.doc('pro_requests/reqBReviewForged').set({
        requestId: 'reqBReviewForged',
        userId: 'userB',
        status: 'PENDING',
        reviewedBy: 'adminA'
      }));
    });

    it('USER UPDATE/DELETE: User CANNOT update or approve own request', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('pro_requests/reqA').update({
        status: 'APPROVED'
      }));
      await assertFails(userDb.doc('pro_requests/reqA').delete());
    });

    it('ADMIN UPDATE: Admin can approve or reject pro requests', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('pro_requests/reqA').update({
        status: 'APPROVED',
        reviewedBy: 'adminA',
        reviewedAt: Date.now()
      }));
    });

    it('ADMIN DELETE: Admin can delete pro request', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('pro_requests/reqA').delete());
    });
  });

  // ==========================================
  // SECTION 8: MANAGED EXTENSIONS SECURITY
  // ==========================================
  describe('8. Managed Extensions Security (/managed_extensions)', function () {
    it('USER READ: Authenticated user can read extension catalog', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('managed_extensions/ext1').get());
    });

    it('UNAUTHENTICATED READ: Unauthenticated user CANNOT read catalog', async function () {
      const unauthDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(unauthDb.doc('managed_extensions/ext1').get());
    });

    it('USER WRITE: Standard user CANNOT modify extension catalog', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('managed_extensions/ext1').update({
        name: 'Hacked Name'
      }));
      await assertFails(userDb.doc('managed_extensions/extNew').set({
        name: 'Unauthorized Ext'
      }));
      await assertFails(userDb.doc('managed_extensions/ext1').delete());
    });

    it('ADMIN WRITE: Admin can create, update, and delete managed extensions', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('managed_extensions/ext2').set({
        id: 'ext2',
        name: '4K Player Addon',
        version: '1.2.0',
        status: 'ACTIVE'
      }));
      await assertSucceeds(adminDb.doc('managed_extensions/ext2').update({
        version: '1.2.1'
      }));
      await assertSucceeds(adminDb.doc('managed_extensions/ext2').delete());
    });
  });

  // ==========================================
  // SECTION 9: AUDIT LOGS SECURITY
  // ==========================================
  describe('9. Audit Logs Security (/auditLogs)', function () {
    it('USER READ: Standard user CANNOT read audit logs', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('auditLogs/log1').get());
    });

    it('USER WRITE: Standard user CANNOT write to audit logs', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('auditLogs/logFake').set({
        action: 'FAKE_LOG'
      }));
    });

    it('ADMIN READ: Admin can read audit logs', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('auditLogs/log1').get());
    });

    it('ADMIN WRITE: Admin can append audit log', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('auditLogs/log2').set({
        logId: 'log2',
        action: 'EXTENSION_PUBLISHED',
        adminUid: 'adminA',
        timestamp: Date.now()
      }));
    });
  });

  // ==========================================
  // SECTION 10: CATCH-ALL DENY & SYSTEM CONFIG
  // ==========================================
  describe('10. App Config and Catch-All Deny', function () {
    it('CONFIG/APP READ: Authenticated user can read app configuration', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('config/app').get());
    });

    it('CONFIG/APP WRITE: Standard user CANNOT modify app configuration', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/app').update({
        maintenance: true
      }));
    });

    it('CONFIG/APP WRITE: Admin can modify app configuration', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/app').update({
        maintenance: true
      }));
    });

    it('CATCH-ALL: Standard user cannot access non-canonical /analytics', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('analytics/stats').get());
      await assertFails(userDb.doc('analytics/stats').set({ count: 1 }));
    });

    it('CATCH-ALL: Admin cannot access non-canonical /analytics (strict catch-all deny: false)', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertFails(adminDb.doc('analytics/stats').get());
      await assertFails(adminDb.doc('analytics/stats').set({ count: 1 }));
    });

    it('CATCH-ALL: Standard user cannot access non-canonical /admin_users', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('admin_users/123').get());
    });

    it('CATCH-ALL: Standard user cannot access non-canonical /dashboard_stats', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('dashboard_stats/overview').get());
    });
  });

  // =========================================================================
  // SECTION 11: PHASE EXT-RULES-01: SEARCH ORDER SECURITY & WILDCARD REGRESSION
  // =========================================================================
  describe('11. Search Order Security & Wildcard Regression (/config/search_order)', function () {
    // TEST A — UNAUTHENTICATED READ
    it('TEST A: Unauthenticated user CANNOT read /config/search_order', async function () {
      const unauthDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(unauthDb.doc('config/search_order').get());
    });

    // TEST B — AUTHENTICATED USER READ
    it('TEST B: Authenticated normal user CAN read /config/search_order', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('config/search_order').get());
    });

    // TEST C — AUTHENTICATED USER CREATE
    it('TEST C: Normal authenticated user CANNOT create /config/search_order', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/search_order').set({
        movie: ['hacked_scraper']
      }));
    });

    // TEST D — AUTHENTICATED USER UPDATE
    it('TEST D: Normal authenticated user CANNOT update /config/search_order', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/search_order').update({
        movie: ['hacked_scraper']
      }));
    });

    // TEST E — AUTHENTICATED USER DELETE
    it('TEST E: Normal authenticated user CANNOT delete /config/search_order', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/search_order').delete());
    });

    // TEST F — ADMIN READ
    it('TEST F: Admin CAN read /config/search_order', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/search_order').get());
    });

    // TEST G — ADMIN CREATE/WRITE
    it('TEST G: Admin CAN write/set /config/search_order', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/search_order').set({
        movie: ['qfilm', 'egydead'],
        tv: ['egydead', 'qfilm'],
        series: ['egydead', 'qfilm'],
        anime: ['witanime', 'anime4up'],
        updatedAt: Date.now(),
        updatedBy: 'adminA@cinestream.com'
      }));
    });

    // TEST H — ADMIN UPDATE
    it('TEST H: Admin CAN update /config/search_order', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/search_order').update({
        movie: ['egydead', 'qfilm']
      }));
    });

    // TEST I — ADMIN DELETE
    it('TEST I: Admin CAN delete /config/search_order', async function () {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/search_order').delete());
    });

    // WILDCARD REGRESSION TESTS (Section 9)
    it('WILDCARD: Normal authenticated user CANNOT read /config/random', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/random').get());
    });

    it('WILDCARD: Unauthenticated user CANNOT read /config/random', async function () {
      const unauthDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(unauthDb.doc('config/random').get());
    });

    it('WILDCARD: Normal authenticated user CANNOT read /config/global', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/global').get());
    });

    it('WILDCARD: Unauthenticated user CANNOT read /config/global', async function () {
      const unauthDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(unauthDb.doc('config/global').get());
    });

    it('WILDCARD: Normal authenticated user CANNOT read /config/unknown', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/unknown').get());
    });

    it('WILDCARD: Unauthenticated user CANNOT read /config/unknown', async function () {
      const unauthDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(unauthDb.doc('config/unknown').get());
    });

    it('WILDCARD: Normal authenticated user CANNOT read /config/test_document', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/test_document').get());
    });

    it('WILDCARD: Unauthenticated user CANNOT read /config/test_document', async function () {
      const unauthDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(unauthDb.doc('config/test_document').get());
    });

    it('WILDCARD: Normal authenticated user CANNOT write /config/random', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/random').set({ val: 1 }));
    });

    it('WILDCARD: Normal authenticated user CANNOT write /config/global', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/global').set({ val: 1 }));
    });

    it('WILDCARD: Normal authenticated user CANNOT write /config/unknown', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/unknown').set({ val: 1 }));
    });

    it('WILDCARD: Normal authenticated user CANNOT write /config/test_document', async function () {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/test_document').set({ val: 1 }));
    });
  });
});
