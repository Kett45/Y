const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const SERVANT_UID = "servant_uid_2";
const NEW_SERVANT_UID = "new_servant_uid_3";
const ADMIN_B_UID = "admin_b_uid_4";
const OUTSIDER_UID = "outsider_uid_99";
const GROUP_ID = "KAM777";
const GROUP_B_ID = "KAM888";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedGroupWithMembers(allowServantUndo = false) {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("groups").doc(GROUP_ID).set({
      inviteCode: GROUP_ID,
      name: "فصل مارمينا",
      adminId: ADMIN_UID,
      adminName: "أمين الفصل",
      memberUids: [ADMIN_UID, SERVANT_UID],
      allowServantUndo,
      enforceWeeklyCardLimit: false,
      maxMonthlyPoints: 0,
      attendanceIsCardOnly: true,
      showLeaderboard: true,
      autoStudentNumber: true,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("1. Unauthenticated user cannot read or list users, groups, or students", async () => {
  await seedGroupWithMembers();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ADMIN_UID).get());
  await assertFails(unauthDb.collection("groups").doc(GROUP_ID).get());
  await assertFails(
    unauthDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .where("groupId", "==", GROUP_ID)
      .get()
  );
});

test("2. User profile PII and personal preferences isolation: owner can read/write own profile, outsider fails", async () => {
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "أبانوب",
      email: "abanoub@example.com",
      groupId: GROUP_ID,
      joinedGroupIds: [GROUP_ID],
      enabledCards: ["ATTENDANCE", "QUIET", "GOLD"],
      collapsedBlockIds: ["blk_1"],
      hiddenBlockIds: [],
      compactViewMode: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  await assertFails(servantDb.collection("users").doc(ADMIN_UID).get());
});

test("3. Shadow field injection on UserProfile is rejected", async () => {
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  await assertFails(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "أبانوب",
      email: "abanoub@example.com",
      groupId: GROUP_ID,
      enabledCards: ["ATTENDANCE"],
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("4. Newly joined servant immediately sees all existing group students and blocks queried by groupId", async () => {
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  // Admin creates group initially with ONLY ADMIN_UID in memberUids
  await assertSucceeds(
    adminDb.collection("groups").doc(GROUP_ID).set({
      inviteCode: GROUP_ID,
      name: "فصل مارمينا",
      adminId: ADMIN_UID,
      adminName: "أمين الفصل",
      memberUids: [ADMIN_UID],
      allowServantUndo: true,
      enforceWeeklyCardLimit: false,
      maxMonthlyPoints: 0,
      attendanceIsCardOnly: true,
      showLeaderboard: true,
      autoStudentNumber: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin adds a student BEFORE any servant joins
  await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .doc("stu_admin_1")
      .set({
        groupId: GROUP_ID,
        name: "كيرلس سمير",
        studentNumber: 1,
        blockId: "",
        sortOrder: 0,
        position: 1000.0,
        avatarInitials: "ك س",
        createdByUid: ADMIN_UID,
        memberUids: [ADMIN_UID],
        createdAt: now,
        updatedAt: now,
      })
  );

  // Brand-new servant joins the group by updating groups/{groupId}.memberUids
  const newServantDb = testEnv.authenticatedContext(NEW_SERVANT_UID).firestore();
  await assertSucceeds(
    newServantDb
      .collection("groups")
      .doc(GROUP_ID)
      .update({
        memberUids: [ADMIN_UID, NEW_SERVANT_UID],
        updatedAt: new Date(),
      })
  );

  // New servant queries students by groupId ONLY -> immediately succeeds and sees Admin's student!
  const snap = await assertSucceeds(
    newServantDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .where("groupId", "==", GROUP_ID)
      .get()
  );
  if (snap.docs.length !== 1) {
    throw new Error("Expected new servant to see 1 student immediately, got " + snap.docs.length);
  }
});

test("5. Servant can create/update students, blocks, and fractional position, but ONLY Admin can delete", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  const blockRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("blocks")
    .doc("block_1");
  await assertSucceeds(
    blockRef.set({
      groupId: GROUP_ID,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      colorHex: "#3B82F6",
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  const studentRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("students")
    .doc("stu_1");
  await assertSucceeds(
    studentRef.set({
      groupId: GROUP_ID,
      name: "مينا جرجس",
      studentNumber: 1,
      blockId: "block_1",
      sortOrder: 0,
      position: 1000.0,
      avatarInitials: "م ج",
      createdByUid: SERVANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Servant can move student to another block and update fractional position (e.g. 1500.5)
  await assertSucceeds(
    studentRef.update({
      blockId: "",
      position: 1500.5,
      updatedAt: new Date(),
    })
  );

  // Admin immediately sees servant's student and block using groupId filter
  const adminStudentsSnap = await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .where("groupId", "==", GROUP_ID)
      .get()
  );
  if (adminStudentsSnap.docs.length !== 1) {
    throw new Error("Admin should see servant's student");
  }

  // Servant CANNOT delete student or block (rejected by server rules!)
  await assertFails(studentRef.delete());
  await assertFails(blockRef.delete());

  // Admin CAN delete student and block
  await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .doc("stu_1")
      .delete()
  );
  await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("blocks")
      .doc("block_1")
      .delete()
  );
});

test("6. Strict Group Isolation: Group B and Outsider cannot read or write Group A data", async () => {
  await seedGroupWithMembers();
  const now = new Date();

  // Seed Group B with Admin B and a student in Group A
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .doc("stu_a")
      .set({
        groupId: GROUP_ID,
        name: "أبانوب عادل",
        studentNumber: 1,
        blockId: "",
        sortOrder: 0,
        position: 1000.0,
        avatarInitials: "أ ع",
        createdByUid: ADMIN_UID,
        createdAt: now,
        updatedAt: now,
      });

    await db.collection("groups").doc(GROUP_B_ID).set({
      inviteCode: GROUP_B_ID,
      name: "فصل البابا كيرلس",
      adminId: ADMIN_B_UID,
      adminName: "أمين ب",
      memberUids: [ADMIN_B_UID],
      allowServantUndo: true,
      enforceWeeklyCardLimit: false,
      maxMonthlyPoints: 0,
      attendanceIsCardOnly: true,
      showLeaderboard: true,
      autoStudentNumber: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  // Admin of Group B tries to read or list Group A's students -> REJECTED BY SERVER
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID).firestore();
  await assertFails(
    adminBDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .doc("stu_a")
      .get()
  );
  await assertFails(
    adminBDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .where("groupId", "==", GROUP_ID)
      .get()
  );

  // Outsider tries to read or list Group A's students -> REJECTED BY SERVER
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    outsiderDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .doc("stu_a")
      .get()
  );
  await assertFails(
    outsiderDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("students")
      .where("groupId", "==", GROUP_ID)
      .get()
  );
});

test("7. CardEvents enforce exact points per cardType and shared group visibility", async () => {
  await seedGroupWithMembers(false);
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  const validEventRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("cardEvents")
    .doc("evt_1");

  // Valid GOLD card = 20 points
  await assertSucceeds(
    validEventRef.set({
      groupId: GROUP_ID,
      studentId: "stu_1",
      studentName: "مينا جرجس",
      cardType: "GOLD",
      points: 20,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      monthKey: "2026-11",
      weekKey: "2026-11-W1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin queries cardEvents by groupId -> sees Servant's card event
  const eventsSnap = await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("cardEvents")
      .where("groupId", "==", GROUP_ID)
      .get()
  );
  if (eventsSnap.docs.length !== 1) {
    throw new Error("Admin should see servant's cardEvent");
  }

  // Invalid forged points for ATTENDANCE (should be 3, not 50)
  const forgedEventRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("cardEvents")
    .doc("evt_forged");
  await assertFails(
    forgedEventRef.set({
      groupId: GROUP_ID,
      studentId: "stu_1",
      studentName: "مينا جرجس",
      cardType: "ATTENDANCE",
      points: 50,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      monthKey: "2026-11",
      weekKey: "2026-11-W1",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cannot mutate points on existing cardEvent
  await assertFails(
    validEventRef.update({
      points: 100,
      updatedAt: new Date(),
    })
  );

  // Servant cannot delete cardEvent when allowServantUndo is false
  await assertFails(validEventRef.delete());

  // Admin can delete cardEvent
  await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("cardEvents")
      .doc("evt_1")
      .delete()
  );
});
