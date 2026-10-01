const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = require('@firebase/rules-unit-testing');
const { test, describe, before, beforeEach, after } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');

const PROJECT_ID = 'cinestream-admin-test';
const RULES_PATH = path.resolve(__dirname, '../firestore.rules');

describe('PHASE SUBSCRIPTION-POINTS-03A.1 — Firestore Security Rules Verification', () => {
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

    // Seed canonical authority data
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();

      // Canonical Admin: /admins/adminA with enabled: true
      await db.doc('admins/adminA').set({
        enabled: true,
        email: 'adminA@cinestream.com',
        role: 'superadmin'
      });

      // Regular User: /users/userA
      await db.doc('users/userA').set({
        uid: 'userA',
        id: 'userA',
        displayName: 'User Alice',
        role: 'user',
        isPremium: false,
        subscriptionTier: 'FREE',
        planId: 'free',
        subscriptionStatus: 'ACTIVE',
        pointsBalance: 150,
        totalPointsEarned: 200,
        totalPointsSpent: 50,
        canWatch: true,
        canDownload: true
      });

      // Regular User: /users/userB
      await db.doc('users/userB').set({
        uid: 'userB',
        id: 'userB',
        displayName: 'User Bob',
        role: 'user',
        isPremium: false,
        subscriptionTier: 'FREE',
        planId: 'free',
        pointsBalance: 0
      });

      // Seed /config/features
      await db.doc('config/features').set({
        subscriptions: { status: 'ACTIVE' },
        points: { status: 'ACTIVE' }
      });

      // Seed /config/economy
      await db.doc('config/economy').set({
        redemptionCosts: { pro_lite_1d: 50, pro_30d: 1000 },
        rewardedAdPoints: 15
      });

      // Seed /reward_tasks/task1
      await db.doc('reward_tasks/task1').set({
        taskId: 'task1',
        title: 'Daily Watch Task',
        rewardPoints: 50
      });

      // Seed /leaderboard/weekly_current
      await db.doc('leaderboard/weekly_current').set({
        cycle: 'WEEKLY',
        topUsers: []
      });

      // Seed /users/userA/point_transactions/tx1
      await db.doc('users/userA/point_transactions/tx1').set({
        txId: 'tx1',
        userId: 'userA',
        amount: 100,
        balanceAfter: 100
      });

      // Seed /users/userA/task_claims/claim1
      await db.doc('users/userA/task_claims/claim1').set({
        taskId: 'task1',
        claimedAt: 123456789
      });
    });
  });

  after(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  // ============================================================
  // A. GUEST (UNAUTHENTICATED) TESTS
  // ============================================================
  describe('A. Guest (Unauthenticated)', () => {
    test('guest read /config/features -> DENIED', async () => {
      const guestDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(guestDb.doc('config/features').get());
    });

    test('guest read /config/economy -> DENIED', async () => {
      const guestDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(guestDb.doc('config/economy').get());
    });

    test('guest read /reward_tasks/task1 -> DENIED', async () => {
      const guestDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(guestDb.doc('reward_tasks/task1').get());
    });

    test('guest read /leaderboard/weekly_current -> DENIED', async () => {
      const guestDb = testEnv.unauthenticatedContext().firestore();
      await assertFails(guestDb.doc('leaderboard/weekly_current').get());
    });
  });

  // ============================================================
  // B. AUTHENTICATED STANDARD USER TESTS
  // ============================================================
  describe('B. Authenticated Standard User (userA)', () => {
    test('userA read /config/features -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('config/features').get());
    });

    test('userA write /config/features -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/features').set({ subscriptions: { status: 'DISABLED' } }));
    });

    test('userA read /config/economy -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('config/economy').get());
    });

    test('userA write /config/economy -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/economy').set({ rewardedAdPoints: 1000 }));
    });

    test('userA read /reward_tasks/task1 -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('reward_tasks/task1').get());
    });

    test('userA write /reward_tasks/task1 -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('reward_tasks/task1').set({ title: 'Hacked Task' }));
    });

    test('userA read /leaderboard/weekly_current -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('leaderboard/weekly_current').get());
    });

    test('userA write /leaderboard/weekly_current -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('leaderboard/weekly_current').set({ topUsers: ['userA'] }));
    });

    test('userA read own point ledger -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('users/userA/point_transactions/tx1').get());
    });

    test('userA write own point ledger -> DENIED (Immutable Ledger)', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA/point_transactions/tx_fake').set({
        txId: 'tx_fake',
        amount: 9999,
        balanceAfter: 10000
      }));
    });

    test('userA read other user point ledger (userB) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userB/point_transactions/tx1').get());
    });

    test('userA read own task claims -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('users/userA/task_claims/claim1').get());
    });

    test('userA write own task claims -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA/task_claims/claim2').set({
        taskId: 'task2',
        claimedAt: 999999
      }));
    });

    test('userA own subscriptionTier modification -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ subscriptionTier: 'PRO' }));
    });

    test('userA own planId modification -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ planId: 'pro_30d' }));
    });

    test('userA own isPremium modification -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ isPremium: true }));
    });

    test('userA other user subscription modification (userB) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userB').update({ subscriptionTier: 'PRO' }));
    });

    test('userA own pointsBalance modification -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userA').update({ pointsBalance: 999999 }));
    });

    test('userA other user pointsBalance modification (userB) -> DENIED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('users/userB').update({ pointsBalance: 100 }));
    });

    test('userA profile update (non-sensitive displayName) -> ALLOWED', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertSucceeds(userDb.doc('users/userA').update({ displayName: 'Alice In Wonderland' }));
    });
  });

  // ============================================================
  // C. AUTHORITATIVE ADMIN TESTS
  // ============================================================
  describe('C. Authoritative Admin (adminA)', () => {
    test('adminA read /config/features -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/features').get());
    });

    test('adminA write /config/features -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/features').set({
        subscriptions: { status: 'COMING_SOON' },
        updatedAt: Date.now()
      }));
    });

    test('adminA read /config/economy -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/economy').get());
    });

    test('adminA write /config/economy -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('config/economy').set({
        redemptionCosts: { pro_lite_1d: 60, pro_30d: 1100 },
        updatedAt: Date.now()
      }));
    });

    test('adminA create & manage /reward_tasks -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('reward_tasks/task2').set({
        taskId: 'task2',
        title: 'New Video Task',
        rewardPoints: 100
      }));
      await assertSucceeds(adminDb.doc('reward_tasks/task2').delete());
    });

    test('adminA write /leaderboard/weekly_current -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('leaderboard/weekly_current').set({
        cycle: 'WEEKLY',
        topUsers: [{ uid: 'userA', points: 500 }]
      }));
    });

    test('adminA point ledger write -> ALLOWED', async () => {
      const adminDb = testEnv.authenticatedContext('adminA').firestore();
      await assertSucceeds(adminDb.doc('users/userA/point_transactions/tx_admin').set({
        txId: 'tx_admin',
        userId: 'userA',
        amount: 50,
        balanceAfter: 200,
        type: 'ADMIN_GRANT'
      }));
    });

    test('adminA subscription management on userA -> ALLOWED', async () => {
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

  // ============================================================
  // D. WILDCARD & CONFIG PATH CONSTRAINTS
  // ============================================================
  describe('D. Wildcard & Unknown Config Paths', () => {
    test('/config/global -> DENIED for standard user', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/global').get());
    });

    test('/config/random -> DENIED for standard user', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/random').get());
    });

    test('/config/unknown -> DENIED for standard user', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('config/unknown').get());
    });

    test('/unknown_collection/doc1 -> DENIED for standard user', async () => {
      const userDb = testEnv.authenticatedContext('userA').firestore();
      await assertFails(userDb.doc('unknown_collection/doc1').get());
    });
  });
});
