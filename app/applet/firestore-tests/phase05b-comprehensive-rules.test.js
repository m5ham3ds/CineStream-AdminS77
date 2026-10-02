const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const { test, describe, before, beforeEach, after } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const PROJECT_ID = 'cinestream-admin-phase05b-test';
const RULES_PATH = path.resolve(__dirname, '../firestore.rules');

describe('PHASE 05B — Final Firestore Rules 32-Case Security Test Matrix', () => {
  let testEnv;

  before(async () => {
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

  beforeEach(async () => {
    await testEnv.clearFirestore();

    // Seed test state with security rules disabled
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();

      // CASE 1: Owner bootstrap doc
      await db.doc('admins/ownerUid').set({
        enabled: true,
        email: 'sulopros01@gmail.com',
        role: 'superadmin'
      });

      // CASE 2: Enabled Admin
      await db.doc('admins/adminA').set({
        enabled: true,
        email: 'adminA@cinestream.com',
        role: 'admin'
      });

      // CASE 3: Disabled Admin
      await db.doc('admins/disabledAdmin').set({
        enabled: false,
        email: 'disabled@cinestream.com',
        role: 'admin'
      });

      // CASE 4 & 5: Regular User A
      await db.doc('users/userA').set({
        uid: 'userA',
        id: 'userA',
        displayName: 'User Alice',
        role: 'user',
        isPremium: false,
        isPro: false,
        subscriptionTier: 'FREE',
        planId: 'free',
        subscriptionStatus: 'ACTIVE',
        pointsBalance: 150,
        totalPointsEarned: 200,
        totalPointsSpent: 50,
        canWatch: true,
        canDownload: true,
        isBanned: false
      });

      // User B
      await db.doc('users/userB').set({
        uid: 'userB',
        id: 'userB',
        displayName: 'User Bob',
        role: 'user',
        isPremium: false,
        isPro: false,
        subscriptionTier: 'FREE',
        pointsBalance: 0
      });

      // Config docs
      await db.doc('config/features').set({
        subscriptions: { state: 'ACTIVE' },
        points: { state: 'ACTIVE' }
      });

      await db.doc('config/economy').set({
        rewardedAdPoints: 15,
        rewardedAdDailyCap: 5
      });

      await db.doc('reward_tasks/task1').set({
        taskId: 'task1',
        title: 'Watch Trailer',
        rewardPoints: 50
      });

      await db.doc('notifications/notif1').set({
        notificationId: 'notif1',
        title: 'System Notice',
        message: 'Welcome'
      });

      await db.doc('app_updates/update1').set({
        updateId: 'update1',
        versionCode: 10,
        versionName: '1.0.0'
      });

      // User A existing transaction
      await db.doc('users/userA/point_transactions/tx1').set({
        txId: 'tx1',
        amount: 50,
        balanceAfter: 150,
        type: 'DAILY_LOGIN'
      });
    });
  });

  after(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  // ==========================================
  // GROUP 1: ADMIN AUTHORITY (CASES 1-4)
  // ==========================================
  describe('ADMIN AUTHORITY (Cases 1-4)', () => {
    test('CASE 1: Owner email (sulopros01@gmail.com) -> Admin = TRUE', async () => {
      const ownerDb = testEnv.authenticatedContext('ownerUid', { email: 'sulopros01@gmail.com' }).firestore();
      await assertSucceeds(ownerDb.doc('auditLogs/ownerLog').set({ action: 'TEST_OWNER', timestamp: Date.now() }));
    });

    test('CASE 2: Enabled Admin (/admins/adminA.enabled == true) -> Admin = TRUE', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('auditLogs/adminLog').set({ action: 'TEST_ADMIN', timestamp: Date.now() }));
    });

    test('CASE 3: Disabled Admin (/admins/disabledAdmin.enabled == false) -> Admin = FALSE', async () => {
      const disabledDb = testEnv.authenticatedContext('disabledAdmin').firestore();
      await assertFails(disabledDb.doc('auditLogs/failLog').set({ action: 'ATTEMPT' }));
    });

    test('CASE 4: Normal user without /admins doc -> Admin = FALSE', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('auditLogs/failLog').set({ action: 'ATTEMPT' }));
    });
  });

  // ==========================================
  // GROUP 2: USERS SECURITY (CASES 5-8)
  // ==========================================
  describe('USERS SECURITY (Cases 5-8)', () => {
    test('CASE 5: Own user document read -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('users/userA').get());
    });

    test('CASE 6: Another user document read (userA -> userB) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userB').get());
    });

    test('CASE 7: Protected field mutation (canWatch, deviceLimit) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ canWatch: false }));
      await assertFails(userDb.doc('users/userA').update({ deviceLimit: 10 }));
    });

    test('CASE 8: Role escalation attempt (role: admin, isAdmin: true) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ role: 'admin' }));
      await assertFails(userDb.doc('users/userA').update({ isAdmin: true }));
      await assertFails(userDb.doc('users/userA').update({ admin: true }));
    });
  });

  // ==========================================
  // GROUP 3: POINTS & LEDGER (CASES 9-13)
  // ==========================================
  describe('POINTS & LEDGER (Cases 9-13)', () => {
    test('CASE 9: Authorized economy operation (Admin/Settlement write) -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('users/userA').update({ pointsBalance: 200 }));
      await assertSucceeds(adminDb.doc('users/userA/point_transactions/tx_new').set({
        txId: 'tx_new',
        amount: 50,
        type: 'ADMIN_GRANT',
        timestamp: Date.now()
      }));
    });

    test('CASE 10: Unauthorized balance mutation (User directly modifying pointsBalance) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ pointsBalance: 999999 }));
      await assertFails(userDb.doc('users/userA').update({ totalPointsEarned: 999999 }));
      await assertFails(userDb.doc('users/userA').update({ totalPointsSpent: 0 }));
    });

    test('CASE 11: Unauthorized ledger update (User modifying existing tx1) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA/point_transactions/tx1').update({ amount: 10000 }));
    });

    test('CASE 12: Unauthorized ledger delete (User deleting existing tx1) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA/point_transactions/tx1').delete());
    });

    test('CASE 13: Unauthorized admin transaction forgery -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA/point_transactions/tx_forged').set({
        txId: 'tx_forged',
        amount: 1000,
        type: 'ADMIN_GRANT'
      }));
    });
  });

  // ==========================================
  // GROUP 4: REWARD TASKS (CASES 14-16)
  // ==========================================
  describe('REWARD TASKS (Cases 14-16)', () => {
    test('CASE 14: User task read -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('reward_tasks/task1').get());
    });

    test('CASE 15: Admin task write (create, update, delete) -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('reward_tasks/task2').set({ taskId: 'task2', rewardPoints: 25 }));
      await assertSucceeds(adminDb.doc('reward_tasks/task2').update({ rewardPoints: 30 }));
      await assertSucceeds(adminDb.doc('reward_tasks/task2').delete());
    });

    test('CASE 16: Unauthorized task write by user -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('reward_tasks/task_hack').set({ taskId: 'hack', rewardPoints: 9999 }));
      await assertFails(userDb.doc('reward_tasks/task1').delete());
    });
  });

  // ==========================================
  // GROUP 5: CONFIG MANAGEMENT (CASES 17-20)
  // ==========================================
  describe('CONFIG MANAGEMENT (Cases 17-20)', () => {
    test('CASE 17: User config read (/config/features, /config/economy) -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('config/features').get());
      await assertSucceeds(userDb.doc('config/economy').get());
    });

    test('CASE 18: Admin config write -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/features').update({ 'points.state': 'COMING_SOON' }));
      await assertSucceeds(adminDb.doc('config/economy').update({ rewardedAdPoints: 20 }));
    });

    test('CASE 19: User config write -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/features').update({ 'points.state': 'DISABLED' }));
      await assertFails(userDb.doc('config/economy').update({ rewardedAdPoints: 1000 }));
    });

    test('CASE 20: Unknown config path write (/config/unknown, /config/random) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertFails(userDb.doc('config/unknown').set({ value: 1 }));
      await assertFails(adminDb.doc('config/unknown').set({ value: 1 }));
      await assertFails(userDb.doc('config/random').set({ value: 1 }));
      await assertFails(adminDb.doc('config/random').set({ value: 1 }));
    });
  });

  // ==========================================
  // GROUP 6: SUBSCRIPTION (CASES 21-22)
  // ==========================================
  describe('SUBSCRIPTION (Cases 21-22)', () => {
    test('CASE 21: User unauthorized subscription mutation (subscriptionTier, planId, isPremium) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ subscriptionTier: 'PRO' }));
      await assertFails(userDb.doc('users/userA').update({ planId: 'pro_30d' }));
      await assertFails(userDb.doc('users/userA').update({ isPremium: true }));
      await assertFails(userDb.doc('users/userA').update({ isPro: true }));
    });

    test('CASE 22: Admin allowed subscription management -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('users/userA').update({
        subscriptionTier: 'PRO',
        planId: 'pro_30d',
        subscriptionStatus: 'ACTIVE',
        subscriptionExpiresAt: Date.now() + 30 * 86400000,
        isPremium: true
      }));
    });
  });

  // ==========================================
  // GROUP 7: NOTIFICATIONS (CASES 23-25)
  // ==========================================
  describe('NOTIFICATIONS (Cases 23-25)', () => {
    test('CASE 23: Admin write notification -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('notifications/notif_new').set({
        title: 'New Announcement',
        message: 'Hello users',
        createdAt: Date.now()
      }));
    });

    test('CASE 24: User allowed read notification -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('notifications/notif1').get());
    });

    test('CASE 25: User write notification -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('notifications/spam').set({ title: 'Spam' }));
    });
  });

  // ==========================================
  // GROUP 8: APP UPDATES (CASES 26-28)
  // ==========================================
  describe('APP UPDATES (Cases 26-28)', () => {
    test('CASE 26: Admin write app update -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('app_updates/update_v2').set({
        versionCode: 11,
        versionName: '1.1.0'
      }));
    });

    test('CASE 27: User read app update -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('app_updates/update1').get());
    });

    test('CASE 28: User write app update -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('app_updates/tamper').set({ versionCode: 999 }));
    });
  });

  // ==========================================
  // GROUP 9: LEGACY PATHS (CASES 29-30)
  // ==========================================
  describe('LEGACY PATHS (Cases 29-30)', () => {
    test('CASE 29: /config/global blocked -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertFails(userDb.doc('config/global').get());
      await assertFails(userDb.doc('config/global').set({ active: true }));
      await assertFails(adminDb.doc('config/global').set({ active: true }));
    });

    test('CASE 30: /audit_logs (non-canonical underscore) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertFails(userDb.doc('audit_logs/log1').get());
      await assertFails(userDb.doc('audit_logs/log1').set({ message: 'Fake' }));
      await assertFails(adminDb.doc('audit_logs/log1').set({ message: 'Fake' }));
    });
  });

  // ==========================================
  // GROUP 10: UNKNOWN COLLECTIONS & WILDCARDS (CASES 31-32)
  // ==========================================
  describe('UNKNOWN COLLECTIONS & WILDCARDS (Cases 31-32)', () => {
    test('CASE 31: Unknown collection write denied (/unknown_collection/doc) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertFails(userDb.doc('unknown_collection/doc1').set({ data: 123 }));
      await assertFails(adminDb.doc('unknown_collection/doc1').set({ data: 123 }));
    });

    test('CASE 32: Unknown document mutation denied (/arbitrary_path/item) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      const guestDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(userDb.doc('arbitrary_path/item1').update({ data: 456 }));
      await assertFails(guestDb.doc('arbitrary_path/item1').get());
    });
  });
});
