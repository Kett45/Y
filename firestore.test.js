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

test("5. Admin creates blocks, Servant cannot create/delete blocks, Servant can manage students, and ONLY Admin can delete", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();

  const servantAttemptBlockRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("blocks")
    .doc("block_servant_denied");
  await assertFails(
    servantAttemptBlockRef.set({
      groupId: GROUP_ID,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      colorHex: "#3B82F6",
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  const blockRef = adminDb
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
  await assertFails(
    servantDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("blocks")
      .doc("block_1")
      .delete()
  );

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

test("8. Decoupled student avatars collection (groups/{groupId}/avatars/{studentId}) allows group members and rejects outsiders", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  const studentRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("students")
    .doc("stu_avatar_1");

  await assertSucceeds(
    studentRef.set({
      groupId: GROUP_ID,
      name: "مينا سمير",
      studentNumber: 1,
      blockId: "",
      sortOrder: 0,
      position: 1000.0,
      avatarInitials: "م س",
      avatarBase64: "",
      hasAvatar: true,
      avatarHash: "hash_abc123",
      createdByUid: SERVANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  const avatarRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("avatars")
    .doc("stu_avatar_1");

  await assertSucceeds(
    avatarRef.set({
      groupId: GROUP_ID,
      studentId: "stu_avatar_1",
      avatarBase64: "F3|0|0.0|0.0|1.0|/9j/4AAQSkZJRgABAQAAAQABAAD/",
      avatarHash: "hash_abc123",
      updatedByUid: SERVANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Outsider cannot read decoupled student avatar
  await assertFails(
    outsiderDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("avatars")
      .doc("stu_avatar_1")
      .get()
  );
});

test("9. Group Admin can toggle enforceWeeklyCardLimit and update policies (even on legacy group doc), while Servant cannot", async () => {
  await seedGroupWithMembers(true);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();

  // Admin enables enforceWeeklyCardLimit and sets maxMonthlyPoints = 10000
  await assertSucceeds(
    adminDb.collection("groups").doc(GROUP_ID).update({
      enforceWeeklyCardLimit: true,
      maxMonthlyPoints: 10000,
      updatedAt: new Date(),
    })
  );

  // Servant cannot mutate enforceWeeklyCardLimit
  await assertFails(
    servantDb.collection("groups").doc(GROUP_ID).update({
      enforceWeeklyCardLimit: false,
      updatedAt: new Date(),
    })
  );

  // Legacy group document without createdAt field can still be updated by Admin
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("groups").doc("KAMLEGACY").set({
      inviteCode: "KAMLEGACY",
      name: "فصل قديم",
      adminId: ADMIN_UID,
      adminName: "أمين الفصل",
      memberUids: [ADMIN_UID, SERVANT_UID],
      allowServantUndo: true,
      enforceWeeklyCardLimit: false,
      maxMonthlyPoints: 0,
      attendanceIsCardOnly: true,
      showLeaderboard: true,
      autoStudentNumber: true,
      updatedAt: new Date(),
    });
  });

  await assertSucceeds(
    adminDb.collection("groups").doc("KAMLEGACY").update({
      enforceWeeklyCardLimit: true,
      updatedAt: new Date(),
    })
  );
});

test("10. ServantMemberProfile (groups/{groupId}/members/{memberUid}) allows group members to read, owner to write with phoneVerified=false, and rejects cross-servant writes or outsider reads", async () => {
  await seedGroupWithMembers(true);
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  const servantMemberRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("members")
    .doc(SERVANT_UID);

  // Servant creates own member profile with phone, primaryPhone, secondaryPhone, showPhone, phoneHidden, and phoneVerified = false
  await assertSucceeds(
    servantMemberRef.set({
      uid: SERVANT_UID,
      groupId: GROUP_ID,
      displayName: "مايكل",
      phone: "+201012345678",
      primaryPhone: "+201012345678",
      secondaryPhone: "+201298765432",
      phoneVerified: false,
      showPhone: true,
      phoneHidden: false,
      hasAvatar: true,
      avatarHash: "hash_srv_1",
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Admin (fellow group member) can read and list members
  await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("members")
      .doc(SERVANT_UID)
      .get()
  );

  // Servant cannot spoof phoneVerified = true
  await assertFails(
    servantMemberRef.update({
      phoneVerified: true,
      updatedAt: new Date(),
    })
  );

  // Servant cannot overwrite Admin's member profile
  await assertFails(
    servantDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("members")
      .doc(ADMIN_UID)
      .set({
        uid: ADMIN_UID,
        groupId: GROUP_ID,
        displayName: "مزيف",
        phone: "+201000000000",
        primaryPhone: "+201000000000",
        secondaryPhone: "",
        phoneVerified: false,
        showPhone: true,
        phoneHidden: false,
        createdAt: now,
        updatedAt: now,
      })
  );

  // Outsider cannot read member profiles
  await assertFails(
    outsiderDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("members")
      .doc(SERVANT_UID)
      .get()
  );
});

test("11. EmailDirectory allows exact get by signed-in user, owner write only, and blocks list scraping", async () => {
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const entryRef = servantDb.collection("emailDirectory").doc("em_test_hash_123");

  await assertSucceeds(
    entryRef.set({
      email: "servant@gmail.com",
      uid: SERVANT_UID,
      displayName: "مايكل",
      updatedAt: now,
    })
  );

  await assertSucceeds(adminDb.collection("emailDirectory").doc("em_test_hash_123").get());
  await assertFails(adminDb.collection("emailDirectory").get());
  await assertFails(
    adminDb.collection("emailDirectory").doc("em_test_hash_123").set({
      email: "servant@gmail.com",
      uid: SERVANT_UID,
      displayName: "انتحال",
      updatedAt: new Date(),
    })
  );
});

test("12. ChildNotes supports group member reads/creates, author deletes, and rejects outsider reads", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  const noteRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("childNotes")
    .doc("note_1");

  await assertSucceeds(
    noteRef.set({
      groupId: GROUP_ID,
      studentId: "stu_1",
      studentName: "مينا جرجس",
      type: "MEMORIZATION",
      text: "حفظ مزمور ٢٣ كامل",
      status: "VISITED",
      authorUid: SERVANT_UID,
      authorName: "مايكل",
      clientCreatedAtMillis: 1730000000000,
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("childNotes")
      .where("groupId", "==", GROUP_ID)
      .get()
  );

  await assertFails(
    outsiderDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("childNotes")
      .doc("note_1")
      .get()
  );

  await assertSucceeds(noteRef.delete());
});

test("13. High-resolution student avatars (<=360KB) and chat images (<=460KB, <=1600px) are allowed while oversized payloads are rejected", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();

  const hdAvatarPayload = "A".repeat(180000);
  const avatarRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("avatars")
    .doc("stu_hd_1");

  await assertSucceeds(
    avatarRef.set({
      groupId: GROUP_ID,
      studentId: "stu_hd_1",
      avatarBase64: hdAvatarPayload,
      avatarHash: "hd_hash_1",
      updatedByUid: SERVANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    avatarRef.set({
      groupId: GROUP_ID,
      studentId: "stu_hd_1",
      avatarBase64: "A".repeat(370000),
      avatarHash: "hd_hash_too_big",
      updatedByUid: SERVANT_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  const msgCol = servantDb.collection("groups").doc(GROUP_ID).collection("messages");
  await assertSucceeds(
    msgCol.doc("msg_hd_img_1").set({
      groupId: GROUP_ID,
      senderUid: SERVANT_UID,
      senderName: "مايكل",
      type: "IMAGE",
      text: "صورة عالية الجودة",
      mediaBase64: "B".repeat(250000),
      mediaMimeType: "image/jpeg",
      imageWidth: 1440,
      imageHeight: 1080,
      isDeleted: false,
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    msgCol.doc("msg_hd_img_too_big").set({
      groupId: GROUP_ID,
      senderUid: SERVANT_UID,
      senderName: "مايكل",
      type: "IMAGE",
      text: "صورة أكبر من الحد",
      mediaBase64: "B".repeat(470000),
      mediaMimeType: "image/jpeg",
      imageWidth: 1440,
      imageHeight: 1080,
      isDeleted: false,
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("14. Primary Admin can appoint Assistant Admin with full admin permissions while protecting primary admin", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const groupRef = adminDb.collection("groups").doc(GROUP_ID);
  const servantGroupRef = servantDb.collection("groups").doc(GROUP_ID);

  // Regular servant cannot self-promote to assistantAdminIds
  await assertFails(
    servantGroupRef.update({
      assistantAdminIds: [SERVANT_UID],
      updatedAt: now,
    })
  );

  // Regular servant cannot update group policies before appointment
  await assertFails(
    servantGroupRef.update({
      allowServantUndo: true,
      updatedAt: now,
    })
  );

  // Primary admin appoints SERVANT_UID as Assistant Admin
  await assertSucceeds(
    groupRef.update({
      assistantAdminIds: [SERVANT_UID],
      updatedAt: now,
    })
  );

  // Assistant Admin now has full admin permissions:
  // 1) Update group policies
  await assertSucceeds(
    servantGroupRef.update({
      allowServantUndo: true,
      maxMonthlyPoints: 200,
      updatedAt: now,
    })
  );

  // 2) Create and delete a block
  const assistantBlockRef = servantGroupRef.collection("blocks").doc("blk_by_assistant");
  await assertSucceeds(
    assistantBlockRef.set({
      groupId: GROUP_ID,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      colorHex: "#6D28D9",
      sortOrder: 5,
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(assistantBlockRef.delete());

  // 3) Create and delete an invitation
  const assistantInvRef = servantGroupRef.collection("invitations").doc("inv_by_assistant");
  await assertSucceeds(
    assistantInvRef.set({
      groupId: GROUP_ID,
      groupName: "أولى ابتدائي",
      adminId: SERVANT_UID,
      inviteCode: GROUP_ID,
      inviteeName: "خادم جديد",
      status: "PENDING",
      acceptedByUid: "",
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(assistantInvRef.delete());

  // 4) Assistant Admin CANNOT modify assistantAdminIds or ban the Primary Admin
  await assertFails(
    servantGroupRef.update({
      assistantAdminIds: [],
      updatedAt: now,
    })
  );
  await assertFails(
    servantGroupRef.update({
      bannedUids: [ADMIN_UID],
      updatedAt: now,
    })
  );

  // 5) Primary Admin can revoke Assistant Admin status
  await assertSucceeds(
    groupRef.update({
      assistantAdminIds: [],
      updatedAt: now,
    })
  );

  // Once revoked, servant can no longer update group policies
  await assertFails(
    servantGroupRef.update({
      allowServantUndo: false,
      updatedAt: now,
    })
  );
});

test("17. Custom Elder Card (كارت القمداء): Admin/Assistant can configure, regular servant denied, CUSTOM_ELDER events validated (0..100 pts), and UserProfile supports 8 enabled cards", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();
  const adminGroupRef = adminDb.collection("groups").doc(GROUP_ID);
  const servantGroupRef = servantDb.collection("groups").doc(GROUP_ID);

  // Regular servant cannot configure customCard* fields
  await assertFails(
    servantGroupRef.update({
      customCardEnabled: true,
      customCardName: "كارت التميز",
      customCardPoints: 25,
      customCardIcon: "👑",
      customCardColorKey: "GOLD",
      customCardFrequency: "MONTHLY",
      updatedAt: now,
    })
  );

  // Admin can configure valid customCard* fields
  await assertSucceeds(
    adminGroupRef.update({
      customCardEnabled: true,
      customCardName: "كارت القمداء",
      customCardPoints: 25,
      customCardIcon: "👑",
      customCardColorKey: "GOLD",
      customCardFrequency: "ONCE_EVER",
      updatedAt: now,
    })
  );

  // Invalid frequency is rejected even for Admin
  await assertFails(
    adminGroupRef.update({
      customCardFrequency: "DAILY",
      updatedAt: now,
    })
  );

  // Invalid points (> 100) is rejected
  await assertFails(
    adminGroupRef.update({
      customCardPoints: 150,
      updatedAt: now,
    })
  );

  // Group member can create CUSTOM_ELDER cardEvent with points in 0..100
  await assertSucceeds(
    servantGroupRef.collection("cardEvents").doc("ev_custom_1").set({
      groupId: GROUP_ID,
      studentId: "stu_1",
      studentName: "مارك",
      cardType: "CUSTOM_ELDER",
      points: 25,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      monthKey: "2026-11",
      weekKey: "2026-11-W3",
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // CUSTOM_ELDER with points > 100 is rejected
  await assertFails(
    servantGroupRef.collection("cardEvents").doc("ev_custom_bad").set({
      groupId: GROUP_ID,
      studentId: "stu_1",
      studentName: "مارك",
      cardType: "CUSTOM_ELDER",
      points: 101,
      servantId: SERVANT_UID,
      servantName: "مايكل",
      monthKey: "2026-11",
      weekKey: "2026-11-W3",
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // UserProfile accepts 8 enabled cards including CUSTOM_ELDER
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      displayName: "أمين الفصل",
      email: "admin@example.com",
      groupId: GROUP_ID,
      enabledCards: [
        "ATTENDANCE",
        "QUIET",
        "PARTICIPATION",
        "PRAISE",
        "GOLD",
        "MEMORIZATION",
        "CONFESSION",
        "CUSTOM_ELDER",
      ],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("16. Live SYSTEM report update: creator can live-update own SYSTEM report, others & TEXT edits blocked", async () => {
  await seedGroupWithMembers();
  const now = new Date();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const servantDb = testEnv.authenticatedContext(SERVANT_UID).firestore();

  const sysMsgRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("messages")
    .doc("sys_vis_1");

  await assertSucceeds(
    sysMsgRef.set({
      chatId: `group_${GROUP_ID}`,
      groupId: GROUP_ID,
      senderUid: SERVANT_UID,
      senderName: "مايكل",
      type: "SYSTEM",
      text: "[VISITATION_V1]\nS|مايكل\nV|stu_1|1|مارك",
      isDeleted: false,
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Creator CAN update text & updatedAt on their own SYSTEM report
  await assertSucceeds(
    sysMsgRef.update({
      text: "[VISITATION_V1]\nS|مايكل\nV|stu_1|1|مارك\nF|stu_2|2|مينا|يحتاج متابعة",
      updatedAt: new Date(),
    })
  );

  // Another user (even Admin) CANNOT alter the first servant's SYSTEM report text
  await assertFails(
    adminDb
      .collection("groups")
      .doc(GROUP_ID)
      .collection("messages")
      .doc("sys_vis_1")
      .update({
        text: "[VISITATION_V1]\nS|تزوير",
        updatedAt: new Date(),
      })
  );

  // Creator CANNOT mutate regular TEXT messages in place
  const textMsgRef = servantDb
    .collection("groups")
    .doc(GROUP_ID)
    .collection("messages")
    .doc("txt_msg_1");
  await assertSucceeds(
    textMsgRef.set({
      chatId: `group_${GROUP_ID}`,
      groupId: GROUP_ID,
      senderUid: SERVANT_UID,
      senderName: "مايكل",
      type: "TEXT",
      text: "سلام للجميع",
      isDeleted: false,
      memberUids: [ADMIN_UID, SERVANT_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    textMsgRef.update({
      text: "نص معدل",
      updatedAt: new Date(),
    })
  );
});







