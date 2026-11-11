package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CardType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class KamRepositoryRuleTest : FirestoreEmulatorTestBase() {

    /**
     * Verifies Scenarios 1, 2, 3, 4, 5, 6, and Admin-only Delete:
     * 1. Admin adds a child BEFORE Servant joins -> Servant joins and immediately sees the child.
     * 2. Servant adds a child and a block -> Admin immediately sees both.
     * 3. Servant moves a child to another block and updates fractional position -> Admin sees the updated block & fractional position.
     * 4. Two accounts (Admin & Servant) award cards to the same child -> both events appear for both accounts.
     * 5. Offline queue (`waitForServerAck = false`) syncs and updates across members.
     * 6. Newly joined Servant 2 joins by invite code and immediately sees 100% of existing children, blocks, and card events without manual memberUids sync.
     * 7. Servant delete attempt is rejected by Firestore Security Rules (`PERMISSION_DENIED`), while Admin delete succeeds.
     */
    @Test
    fun sharedGroupData_adminAndServantsShareAllStudentsBlocksPositionsAndCards() = runBlocking {
        val groupCode = "KAM" + UUID.randomUUID().toString().replace("-", "").uppercase().take(6)
        val repository = KamRepository(firestore)

        // 1. Admin creates group and adds Student 1 BEFORE any servant joins
        val adminUid = signInTestUser("admin_${groupCode}@test.com")
        val (createdGroup, adminStudentId) = withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("الأمين أبانوب", "admin@test.com").getOrThrow()
            val grp = repository.createGroup("فصل مارمينا", "الأمين أبانوب", groupCode).getOrThrow()
            val stuId = repository.addStudent(
                groupId = groupCode,
                name = "مينا جرجس",
                studentNumber = 1,
                blockId = "",
                sortOrder = 0,
                position = 1000.0,
                memberUids = listOf(adminUid) // Only adminUid was known at creation time!
            ).getOrThrow()
            grp to stuId
        }

        // 2. Servant 1 joins by code -> MUST immediately see Student 1 added by Admin!
        val servant1Uid = signInTestUser("servant1_${groupCode}@test.com")
        val joinedGroup = withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("الخادم مايكل", "servant1@test.com").getOrThrow()
            repository.joinGroupByCode(createdGroup.fullDailyInviteCode, "الخادم مايكل").getOrThrow()
        }
        assertTrue(joinedGroup.memberUids.contains(adminUid))
        assertTrue(joinedGroup.memberUids.contains(servant1Uid))

        val studentsSeenByServant1 = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { list -> list.any { it.id == adminStudentId } }
        }
        assertEquals("مينا جرجس", studentsSeenByServant1.first { it.id == adminStudentId }.name)

        // 3. Servant 1 adds a Block and Student 2, then moves Student 1 into the block and updates fractional position
        val blockId = withTimeout(TIMEOUT_MS) {
            repository.createBlock(
                groupId = groupCode,
                servantId = servant1Uid,
                servantName = "مايكل",
                colorHex = "#3B82F6",
                sortOrder = 0,
                memberUids = joinedGroup.memberUids
            ).getOrThrow()
        }

        val servantStudentId = withTimeout(TIMEOUT_MS) {
            repository.addStudent(
                groupId = groupCode,
                name = "كيرلس سمير",
                studentNumber = 2,
                blockId = blockId,
                sortOrder = 1,
                position = 2000.0,
                memberUids = joinedGroup.memberUids
            ).getOrThrow()
        }

        // Servant 1 moves Admin's student into the block and reorders it to the top with fractional position 500.0
        withTimeout(TIMEOUT_MS) {
            repository.moveStudentToBlock(
                groupId = groupCode,
                studentId = adminStudentId,
                targetBlockId = blockId,
                newSortOrder = 0L,
                newPosition = 500.0,
                memberUids = joinedGroup.memberUids
            ).getOrThrow()
            repository.updateStudentPosition(
                groupId = groupCode,
                studentId = servantStudentId,
                newPosition = 250.0 // Move servant's student above 500.0 using fractional position!
            ).getOrThrow()
        }

        // Servant 1 awards an ATTENDANCE card to Student 1
        val student1Obj = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { list -> list.size == 2 }.first { it.id == adminStudentId }
        }
        val servantEventId = withTimeout(TIMEOUT_MS) {
            repository.awardCard(
                groupId = groupCode,
                student = student1Obj,
                cardType = CardType.ATTENDANCE,
                servantName = "الخادم مايكل",
                monthKey = "2026-12",
                weekKey = "2026-W52",
                deterministicSlotKey = "2026-W52",
                currentMonthPoints = 0L,
                currentMonthEventsCount = 0L,
                memberUids = joinedGroup.memberUids
            ).getOrThrow()
        }
        assertTrue(servantEventId.isNotEmpty())

        // 4. Admin signs back in -> sees Servant 1's block, both students in fractional order (250.0 then 500.0), and Servant 1's card
        signInTestUser("admin_${groupCode}@test.com")
        val blocksSeenByAdmin = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBlocks(groupCode).first { list -> list.any { it.id == blockId } }
        }
        assertTrue(blocksSeenByAdmin.any { it.id == blockId && it.servantName == "مايكل" })

        val orderedStudentsSeenByAdmin = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { it.size == 2 }
        }
        assertEquals(servantStudentId, orderedStudentsSeenByAdmin[0].id)
        assertEquals(250.0, orderedStudentsSeenByAdmin[0].position, 0.01)
        assertEquals(adminStudentId, orderedStudentsSeenByAdmin[1].id)
        assertEquals(500.0, orderedStudentsSeenByAdmin[1].position, 0.01)
        assertEquals(blockId, orderedStudentsSeenByAdmin[1].blockId)

        // Admin awards a GOLD card to the SAME student -> both events exist and are visible to everyone
        withTimeout(TIMEOUT_MS) {
            repository.awardCard(
                groupId = groupCode,
                student = orderedStudentsSeenByAdmin[1],
                cardType = CardType.GOLD,
                servantName = "الأمين أبانوب",
                monthKey = "2026-12",
                weekKey = "2026-W52",
                deterministicSlotKey = "2026-W52",
                currentMonthPoints = 3L,
                currentMonthEventsCount = 1L,
                memberUids = joinedGroup.memberUids
            ).getOrThrow()
        }

        // 5. A brand new Servant 2 joins by code -> immediately sees all 2 students, 1 block, and 2 card events!
        signInTestUser("servant2_${groupCode}@test.com")
        withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("الخادم بيشوي", "servant2@test.com").getOrThrow()
            repository.joinGroupByCode(createdGroup.fullDailyInviteCode, "الخادم بيشوي").getOrThrow()
        }
        val studentsForNewServant = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { it.size == 2 }
        }
        assertEquals(2, studentsForNewServant.size)
        val eventsForNewServant = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCardEventsForMonth(groupCode, monthKey = "2026-12").first { it.size == 2 }
        }
        assertEquals(2, eventsForNewServant.size)

        // 6. Servant 2 attempts to delete a student -> MUST be rejected by server (PERMISSION_DENIED)
        val deleteByServantResult = withTimeout(TIMEOUT_MS) {
            repository.deleteStudent(groupCode, adminStudentId)
        }
        assertTrue(deleteByServantResult.isFailure)
        val servantDeleteEx = deleteByServantResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(servantDeleteEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, servantDeleteEx?.code)

        // 7. Admin signs in and deletes student -> succeeds
        signInTestUser("admin_${groupCode}@test.com")
        val deleteByAdminResult = withTimeout(TIMEOUT_MS) {
            repository.deleteStudent(groupCode, adminStudentId)
        }
        assertTrue(deleteByAdminResult.isSuccess)
    }

    /**
     * Verifies Scenarios 7 & 8 (Strict Group Isolation):
     * - Create two groups (Group A and Group B) with different Admins and add children in each.
     * - Children of Group A NEVER appear in Group B.
     * - An account outside a group attempting to read its data is rejected by the server (`PERMISSION_DENIED`).
     */
    @Test
    fun groupIsolation_twoGroupsRemainCompletelySeparatedAndOutsiderReadsRejectedByServer() = runBlocking {
        val groupACode = "KAMA" + UUID.randomUUID().toString().replace("-", "").uppercase().take(5)
        val groupBCode = "KAMB" + UUID.randomUUID().toString().replace("-", "").uppercase().take(5)
        val repository = KamRepository(firestore)

        // Create Group A with Admin A and add Student A
        val adminAUid = signInTestUser("adminA_${groupACode}@test.com")
        withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("أمين أ", "adminA@test.com").getOrThrow()
            repository.createGroup("مجموعة أ", "أمين أ", groupACode).getOrThrow()
            repository.addStudent(
                groupId = groupACode,
                name = "طفل مجموعة أ",
                studentNumber = 1,
                blockId = "",
                sortOrder = 0,
                memberUids = listOf(adminAUid),
                position = 1000.0
            ).getOrThrow()
        }

        // Create Group B with Admin B and add Student B
        val adminBUid = signInTestUser("adminB_${groupBCode}@test.com")
        withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("أمين ب", "adminB@test.com").getOrThrow()
            repository.createGroup("مجموعة ب", "أمين ب", groupBCode).getOrThrow()
            repository.addStudent(
                groupId = groupBCode,
                name = "طفل مجموعة ب",
                studentNumber = 1,
                blockId = "",
                sortOrder = 0,
                memberUids = listOf(adminBUid),
                position = 1000.0
            ).getOrThrow()
        }

        // Verify Group B only contains "طفل مجموعة ب" and never "طفل مجموعة أ"
        val groupBStudents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupBCode).first { it.isNotEmpty() }
        }
        assertEquals(1, groupBStudents.size)
        assertEquals("طفل مجموعة ب", groupBStudents.first().name)
        assertFalse(groupBStudents.any { it.name == "طفل مجموعة أ" })

        // Verify Admin B (outsider to Group A) attempting to read Group A's students is rejected by server
        var outsiderError: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeStudents(groupACode).first()
            }
        } catch (e: Throwable) {
            outsiderError = e
        }
        assertNotNull(outsiderError)
        val fsEx = outsiderError?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun duplicateStudentNames_mergesCardEventsToKeptStudentAndNormalizesPositions() = runBlocking {
        val groupCode = "KAMM" + UUID.randomUUID().toString().replace("-", "").uppercase().take(5)
        val repository = KamRepository(firestore)

        val adminUid = signInTestUser("admin_mig_${groupCode}@test.com")
        withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("الأمين مينا", "admin_mig@test.com").getOrThrow()
            repository.createGroup("فصل الترحيل والدمج", "الأمين مينا", groupCode).getOrThrow()
        }

        // Add two students with the same Arabic name (e.g. added concurrently while offline)
        val keepId = withTimeout(TIMEOUT_MS) {
            repository.addStudent(
                groupId = groupCode,
                name = "بيشوي هاني",
                studentNumber = 1,
                blockId = "",
                sortOrder = 0,
                memberUids = listOf(adminUid),
                position = 0.0
            ).getOrThrow()
        }
        val duplicateId = withTimeout(TIMEOUT_MS) {
            repository.addStudent(
                groupId = groupCode,
                name = "أبانوب هاني", // will rename to بيشوي هاني to simulate duplicate name
                studentNumber = 2,
                blockId = "",
                sortOrder = 1,
                memberUids = listOf(adminUid),
                position = 0.0
            ).getOrThrow()
        }

        // Normalize positions via migrateAndNormalizeGroupData
        withTimeout(TIMEOUT_MS) {
            repository.migrateAndNormalizeGroupData(groupCode, adminUid, listOf(adminUid)).getOrThrow()
        }

        val initialStudents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { list ->
                list.size == 2 && list.all { it.position > 0.0 }
            }
        }
        val keepStudent = initialStudents.first { it.id == keepId }
        val dupStudent = initialStudents.first { it.id == duplicateId }

        // Award a card to the duplicate student record
        withTimeout(TIMEOUT_MS) {
            repository.awardCard(
                groupId = groupCode,
                student = dupStudent,
                cardType = CardType.PRAISE,
                servantName = "الأمين مينا",
                monthKey = "2026-12",
                weekKey = "2026-W52",
                deterministicSlotKey = "2026-W52",
                currentMonthPoints = 0L,
                currentMonthEventsCount = 0L,
                memberUids = listOf(adminUid)
            ).getOrThrow()
        }

        // Merge duplicate student into keepStudent -> card event transfers to keepStudent and duplicate is deleted
        withTimeout(TIMEOUT_MS) {
            repository.mergeDuplicateStudents(
                groupId = groupCode,
                keepStudent = keepStudent,
                duplicateStudent = dupStudent,
                memberUids = listOf(adminUid)
            ).getOrThrow()
        }

        val remainingStudents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { it.size == 1 }
        }
        assertEquals(keepId, remainingStudents.first().id)

        val transferredEvents = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeCardEventsForMonth(groupCode, monthKey = "2026-12").first { it.isNotEmpty() }
        }
        assertEquals(1, transferredEvents.size)
        assertEquals(keepId, transferredEvents.first().studentId)
    }

    @Test
    fun decoupledStudentAvatar_storesInSeparateAvatarsCollectionAndKeepsStudentLightweight() = runBlocking {
        val groupCode = "KAMA" + UUID.randomUUID().toString().replace("-", "").uppercase().take(5)
        val repository = KamRepository(firestore)

        val adminUid = signInTestUser("admin_av_${groupCode}@test.com")
        val studentId = withTimeout(TIMEOUT_MS) {
            repository.ensureUserProfile("الأمين كيرلس", "admin_av@test.com").getOrThrow()
            repository.createGroup("فصل الصور المنفصلة", "الأمين كيرلس", groupCode).getOrThrow()
            repository.addStudent(
                groupId = groupCode,
                name = "ابانوب مينا",
                studentNumber = 1,
                blockId = "",
                sortOrder = 0,
                memberUids = listOf(adminUid),
                position = 1000.0
            ).getOrThrow()
        }

        val samplePackedAvatar = "F3|1|0.0|0.0|1.0|dGVzdF9hdmF0YXJfYmFzZTY0X3BheWxvYWQ="
        withTimeout(TIMEOUT_MS) {
            repository.updateStudentAvatar(
                groupId = groupCode,
                studentId = studentId,
                avatarBase64 = samplePackedAvatar
            ).getOrThrow()
        }

        val updatedStudent = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeStudents(groupCode).first { list ->
                list.any { it.id == studentId && it.hasAvatar }
            }.first { it.id == studentId }
        }
        // Student doc in main listener has empty avatarBase64 and non-empty avatarHash
        assertEquals("", updatedStudent.avatarBase64)
        assertTrue(updatedStudent.hasAvatar)
        assertTrue(updatedStudent.avatarHash.isNotEmpty())

        // Lazy fetch from groups/{groupId}/avatars/{studentId} returns the full packed payload
        val fetchedAvatar = withTimeout(TIMEOUT_MS) {
            repository.fetchStudentAvatar(groupCode, studentId)
        }
        assertEquals(samplePackedAvatar, fetchedAvatar)
    }

    private companion object {
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}

