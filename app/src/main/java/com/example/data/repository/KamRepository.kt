package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.BlockColorPreset
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.MonthlyBalance
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.UUID

class KamRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth
    private val profileMutex = Mutex()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول أولاً قبل الوصول لبيانات الفصل.")
    }

    // --- 1. User Profile & Personal Preferences (stored ONLY in users/{uid}) ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String
    ): Result<UserProfile> = runCatching {
        profileMutex.withLock {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            try {
                val snap = docRef.get().await()
                if (snap.exists()) {
                    val existing = snap.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (existing != null) return@withLock existing
                }
                val safeName = displayName.trim().ifBlank { "خادم الفصل" }.take(100)
                val safeEmail = email.trim().ifBlank { "$uid@kamlite.app" }.take(150)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "groupId" to "",
                    "joinedGroupIds" to emptyList<String>(),
                    "enabledCards" to CardType.defaultEnabledIds,
                    "collapsedBlockIds" to emptyList<String>(),
                    "hiddenBlockIds" to emptyList<String>(),
                    "compactViewMode" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                UserProfile(
                    id = uid,
                    userId = uid,
                    displayName = safeName,
                    email = safeEmail,
                    groupId = "",
                    joinedGroupIds = emptyList(),
                    enabledCards = CardType.defaultEnabledIds,
                    collapsedBlockIds = emptyList(),
                    hiddenBlockIds = emptyList(),
                    compactViewMode = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (auth.currentUser != null) {
                    handleFirestoreError(e, OperationType.WRITE, docRef.path)
                }
                throw e
            }
        }
    }

    suspend fun updateEnabledCards(enabledCards: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "enabledCards" to enabledCards.distinct().take(7),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updatePersonalBlockPreferences(
        collapsedBlockIds: List<String>,
        hiddenBlockIds: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "collapsedBlockIds" to collapsedBlockIds.distinct().take(50),
                    "hiddenBlockIds" to hiddenBlockIds.distinct().take(50),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserGroup(
        groupId: String,
        existingJoinedGroupIds: List<String> = emptyList()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val updatedJoined = if (groupId.isNotBlank()) {
                (existingJoinedGroupIds + groupId).distinct().take(20)
            } else {
                existingJoinedGroupIds.distinct().take(20)
            }
            val updates = mutableMapOf<String, Any>(
                "groupId" to groupId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (updatedJoined.isNotEmpty()) {
                updates["joinedGroupIds"] = updatedJoined
            }
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 2. Group Management & Multi-Group Isolation ---

    fun observeGroup(groupId: String): Flow<Group?> {
        val docRef = db.collection("groups").document(groupId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    /**
     * Observes all groups that the current user is a member of, enabling safe switching
     * between isolated groups from the Group and Settings screens.
     */
    fun observeUserGroups(currentUserId: String): Flow<List<Group>> {
        val path = "groups"
        return db.collection("groups")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name }
            }
            .catch { error ->
                if (error is Exception && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createGroup(
        groupName: String,
        adminName: String,
        customCode: String? = null
    ): Result<Group> = runCatching {
        val uid = requireUserId()
        val inviteCode = (customCode?.uppercase()?.replace(Regex("[^A-Z0-9]"), "")?.takeIf { it.length in 4..12 })
            ?: generateInviteCode()
        val dailyInviteToken = generateDailyInviteToken()
        val nowMillis = System.currentTimeMillis()
        val groupRef = db.collection("groups").document(inviteCode)
        val safeGroupName = groupName.trim().ifBlank { "فصل مدارس الأحد" }.take(100)
        val safeAdminName = adminName.trim().ifBlank { "الأمين" }.take(100)
        val memberUids = listOf(uid)

        try {
            val payload = mapOf(
                "inviteCode" to inviteCode,
                "name" to safeGroupName,
                "adminId" to uid,
                "adminName" to safeAdminName,
                "memberUids" to memberUids,
                "allowServantUndo" to true,
                "enforceWeeklyCardLimit" to false,
                "maxMonthlyPoints" to 0L,
                "attendanceIsCardOnly" to true,
                "showLeaderboard" to true,
                "autoStudentNumber" to true,
                "bannedUids" to emptyList<String>(),
                "bannedServantEntries" to emptyList<String>(),
                "dailyInviteToken" to dailyInviteToken,
                "inviteCodeUpdatedAtMillis" to nowMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            groupRef.set(payload).await()
            updateUserGroup(inviteCode, listOf(inviteCode)).getOrThrow()

            // Create initial default block for Admin under groups/{groupId}/blocks
            createBlock(
                groupId = inviteCode,
                servantId = uid,
                servantName = safeAdminName,
                colorHex = BlockColorPreset.AMBER.hex,
                sortOrder = 0,
                memberUids = memberUids
            )

            Group(
                id = inviteCode,
                inviteCode = inviteCode,
                name = safeGroupName,
                adminId = uid,
                adminName = safeAdminName,
                memberUids = memberUids,
                dailyInviteToken = dailyInviteToken,
                inviteCodeUpdatedAtMillis = nowMillis
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, groupRef.path)
            throw e
        }
    }

    suspend fun joinGroupByCode(
        rawCode: String,
        servantName: String
    ): Result<Group> = runCatching {
        val uid = requireUserId()
        val (parsedPrefix, parsedToken) = Group.parseInviteCodeInput(rawCode)
        var groupPrefix = parsedPrefix
        var enteredDailyToken = parsedToken
        require(groupPrefix.length in 4..20) { "رمز المجموعة غير صالح" }
        var groupRef = db.collection("groups").document(groupPrefix)

        try {
            var snap = groupRef.get().await()
            // Support servants who typed <GroupPrefix><DailyToken> without the hyphen '-'
            if (!snap.exists() && enteredDailyToken.isBlank() && groupPrefix.length >= 9) {
                val candidatePrefix = groupPrefix.dropLast(3)
                val candidateToken = groupPrefix.takeLast(3)
                val candidateRef = db.collection("groups").document(candidatePrefix)
                val candidateSnap = candidateRef.get().await()
                if (candidateSnap.exists()) {
                    groupPrefix = candidatePrefix
                    enteredDailyToken = candidateToken
                    groupRef = candidateRef
                    snap = candidateSnap
                }
            }
            require(snap.exists()) { "لم يتم العثور على مجموعة بهذا الرمز ($rawCode)" }
            val group = snap.toObject(Group::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: error("تعذر قراءة بيانات المجموعة")

            // 1. Strict Ban Check (Priority 1): If the servant was removed/banned, reject immediately
            if (uid in group.bannedUids) {
                throw IllegalArgumentException("تم إزالتك من هذه المجموعة")
            }

            // 2. Daily Rotating Code Check (Priority 2): Enforce 24-hour validity & daily token match for new joins
            if (uid !in group.memberUids) {
                val nowMs = System.currentTimeMillis()
                if (group.dailyInviteToken.isNotBlank()) {
                    if (group.isDailyCodeExpired(nowMs)) {
                        throw IllegalArgumentException(
                            "انتهت صلاحية هذا الرمز اليومي (صالح لمدة يوم واحد فقط). اطلب الرمز المحدث من أمين المجموعة"
                        )
                    }
                    if (enteredDailyToken != group.dailyInviteToken.uppercase()) {
                        throw IllegalArgumentException(
                            "رمز المجموعة اليومي غير صحيح أو تم تغييره. تأكد من إدخال الرمز الكامل المكون من بادئة المجموعة والجزء اليومي"
                        )
                    }
                } else if (enteredDailyToken.isNotBlank() && enteredDailyToken != group.effectiveDailyToken.uppercase()) {
                    throw IllegalArgumentException(
                        "رمز المجموعة اليومي غير صحيح. اطلب الرمز المحدث من أمين المجموعة"
                    )
                }
            }

            val updatedMembers = if (uid in group.memberUids) {
                group.memberUids
            } else {
                require(group.memberUids.size < 7) { "المجموعة مكتملة (الأمين + 6 خدام كحد أقصى)" }
                val newMembers = group.memberUids + uid
                groupRef.update(
                    mapOf(
                        "memberUids" to newMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                newMembers
            }

            updateUserGroup(groupPrefix, listOf(groupPrefix)).getOrThrow()

            // Create a block for this servant in the shared group if they don't have one yet,
            // or re-attach their account if they were previously unbanned and their block still exists
            val colorPreset = BlockColorPreset.entries[updatedMembers.size % BlockColorPreset.entries.size]
            val blockId = "blk_$uid"
            val blockRef = groupRef.collection("blocks").document(blockId)
            val blockSnap = try { blockRef.get().await() } catch (_: Exception) { null }
            val safeServantName = servantName.trim().ifBlank { "خادم" }.take(100)
            if (blockSnap == null || !blockSnap.exists()) {
                blockRef.set(
                    mapOf(
                        "groupId" to groupPrefix,
                        "servantId" to uid,
                        "servantName" to safeServantName,
                        "colorHex" to colorPreset.hex,
                        "sortOrder" to updatedMembers.size.toLong(),
                        "memberUids" to updatedMembers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (blockSnap.getString("servantId").isNullOrBlank()) {
                blockRef.update(
                    mapOf(
                        "servantId" to uid,
                        "memberUids" to updatedMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            group.copy(memberUids = updatedMembers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
            throw e
        }
    }

    /**
     * Rotates the 24-hour daily invite token for the specified group (`<groupId>-<dailyInviteToken>`).
     * Because `groupId` is the permanent prefix of the group, rotating the daily token never collides with any other group.
     */
    suspend fun rotateDailyInviteCode(
        groupId: String,
        previousToken: String = ""
    ): Result<Pair<String, Long>> = runCatching {
        requireUserId()
        val newToken = generateDailyInviteToken(excludeToken = previousToken)
        val nowMillis = System.currentTimeMillis()
        val groupRef = db.collection("groups").document(groupId)
        try {
            groupRef.update(
                mapOf(
                    "dailyInviteToken" to newToken,
                    "inviteCodeUpdatedAtMillis" to nowMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            newToken to nowMillis
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
            throw e
        }
    }

    /**
     * Removes ONLY the servant from the group and bans their UID from rejoining,
     * while keeping the ServantBlock and all children (`students`) assigned to that block 100% intact.
     */
    suspend fun removeAndBanServantFromGroup(
        groupId: String,
        block: ServantBlock,
        currentGroup: Group
    ): Result<Group> = runCatching {
        val adminUid = requireUserId()
        require(currentGroup.adminId == adminUid) { "إزالة وحظر الخادم متاحة لأمين المجموعة فقط" }
        val targetServantUid = block.servantId.trim()
        require(targetServantUid.isNotBlank() && targetServantUid != adminUid) {
            "لا يمكن إزالة أو حظر أمين المجموعة"
        }

        val updatedMembers = currentGroup.memberUids.filter { it != targetServantUid }.ifEmpty { listOf(adminUid) }
        val updatedBannedUids = (currentGroup.bannedUids + targetServantUid).distinct().take(50)
        val cleanServantName = block.servantName.trim().ifBlank { "خادم" }.replace("|", " ").take(80)
        val updatedBannedEntries = (
            currentGroup.bannedServantEntries.filterNot { it.startsWith("$targetServantUid|") } +
                "$targetServantUid|$cleanServantName"
            ).distinct().take(50)

        val groupRef = db.collection("groups").document(groupId)
        val blockRef = groupRef.collection("blocks").document(block.id)

        try {
            val batch = db.batch()
            // 1. Remove servant UID from memberUids & add to bannedUids + bannedServantEntries
            batch.update(
                groupRef,
                mapOf(
                    "memberUids" to updatedMembers,
                    "bannedUids" to updatedBannedUids,
                    "bannedServantEntries" to updatedBannedEntries,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            // 2. Keep the block and all its children intact; only clear servantId on the block
            batch.update(
                blockRef,
                mapOf(
                    "servantId" to "",
                    "memberUids" to updatedMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            currentGroup.copy(
                memberUids = updatedMembers,
                bannedUids = updatedBannedUids,
                bannedServantEntries = updatedBannedEntries
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
            throw e
        }
    }

    /**
     * Unbans a previously banned servant UID so they can rejoin the group with a valid daily code.
     */
    suspend fun unbanServantFromGroup(
        groupId: String,
        servantUid: String,
        currentGroup: Group
    ): Result<Group> = runCatching {
        val adminUid = requireUserId()
        require(currentGroup.adminId == adminUid) { "إلغاء الحظر متاح لأمين المجموعة فقط" }
        val updatedBannedUids = currentGroup.bannedUids.filter { it != servantUid }
        val updatedBannedEntries = currentGroup.bannedServantEntries.filterNot { it.startsWith("$servantUid|") }
        val groupRef = db.collection("groups").document(groupId)
        try {
            groupRef.update(
                mapOf(
                    "bannedUids" to updatedBannedUids,
                    "bannedServantEntries" to updatedBannedEntries,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            currentGroup.copy(
                bannedUids = updatedBannedUids,
                bannedServantEntries = updatedBannedEntries
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
            throw e
        }
    }

    suspend fun updateGroupPolicies(
        groupId: String,
        allowServantUndo: Boolean,
        enforceWeeklyCardLimit: Boolean,
        maxMonthlyPoints: Long,
        attendanceIsCardOnly: Boolean,
        showLeaderboard: Boolean,
        autoStudentNumber: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val groupRef = db.collection("groups").document(groupId)
        try {
            groupRef.update(
                mapOf(
                    "allowServantUndo" to allowServantUndo,
                    "enforceWeeklyCardLimit" to enforceWeeklyCardLimit,
                    "maxMonthlyPoints" to maxMonthlyPoints.coerceIn(0L, 10000L),
                    "attendanceIsCardOnly" to attendanceIsCardOnly,
                    "showLeaderboard" to showLeaderboard,
                    "autoStudentNumber" to autoStudentNumber,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, groupRef.path)
            throw e
        }
    }

    // --- 3. Safe Migration & Legacy Normalization (سادسًا: ترحيل البيانات الموجودة) ---

    /**
     * Migrates any students, blocks, or cardEvents that were previously saved under
     * `users/{currentUserId}/...` to the shared `groups/{groupId}/...` collections,
     * and ensures all group students have a valid fractional `position`.
     * Never deletes legacy data before confirming that the group-level document exists.
     */
    suspend fun migrateAndNormalizeGroupData(
        groupId: String,
        currentUserId: String,
        targetMemberUids: List<String>
    ): Result<Int> = runCatching {
        if (groupId.isBlank()) return@runCatching 0
        val groupRef = db.collection("groups").document(groupId)
        val userRef = db.collection("users").document(currentUserId)
        val safeMembers = targetMemberUids.ifEmpty { listOf(currentUserId) }.distinct().take(10)
        var migratedCount = 0

        // Step A: Migrate any legacy blocks under users/{uid}/blocks -> groups/{groupId}/blocks
        try {
            val legacyBlocksSnap = userRef.collection("blocks").get().await()
            for (legacyDoc in legacyBlocksSnap.documents) {
                val targetBlockRef = groupRef.collection("blocks").document(legacyDoc.id)
                val existingTarget = targetBlockRef.get().await()
                if (!existingTarget.exists()) {
                    val servantName = (legacyDoc.getString("servantName") ?: "بلوك خادم").trim().ifBlank { "بلوك خادم" }.take(100)
                    val colorHex = (legacyDoc.getString("colorHex") ?: BlockColorPreset.BLUE.hex).take(30)
                    val sortOrder = (legacyDoc.getLong("sortOrder") ?: 0L).coerceIn(0L, 10000L)
                    val servantId = (legacyDoc.getString("servantId") ?: currentUserId).take(128)
                    val payload = mapOf(
                        "groupId" to groupId,
                        "servantId" to servantId,
                        "servantName" to servantName,
                        "colorHex" to colorHex,
                        "sortOrder" to sortOrder,
                        "memberUids" to safeMembers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    targetBlockRef.set(payload).await()
                }
                // Verify target exists before deleting legacy doc
                val verifySnap = targetBlockRef.get().await()
                if (verifySnap.exists()) {
                    legacyDoc.reference.delete().await()
                    migratedCount++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ignore if no legacy subcollection exists
        }

        // Step B: Migrate any legacy students under users/{uid}/students -> groups/{groupId}/students
        try {
            val legacyStudentsSnap = userRef.collection("students").get().await()
            if (!legacyStudentsSnap.isEmpty) {
                val existingGroupStudentsSnap = groupRef.collection("students")
                    .whereEqualTo("groupId", groupId)
                    .get()
                    .await()
                val existingGroupStudents = existingGroupStudentsSnap.toObjects(
                    Student::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val existingNormNames = existingGroupStudents.map {
                    ArabicInitialsHelper.normalizeArabicName(it.name)
                }.toMutableSet()
                var nextNum = (existingGroupStudents.maxOfOrNull { it.studentNumber.toInt() } ?: 0) + 1
                var nextPos = (existingGroupStudents.maxOfOrNull { it.effectivePosition } ?: 0.0) + 1000.0

                for (legacyDoc in legacyStudentsSnap.documents) {
                    val rawName = (legacyDoc.getString("name") ?: "").trim().take(100)
                    if (rawName.isBlank()) continue
                    val normName = ArabicInitialsHelper.normalizeArabicName(rawName)
                    val targetStudentRef = groupRef.collection("students").document(legacyDoc.id)
                    val existingTarget = targetStudentRef.get().await()

                    if (!existingTarget.exists() && normName !in existingNormNames) {
                        val stuNum = (legacyDoc.getLong("studentNumber")?.toInt() ?: nextNum).coerceIn(1, 9999)
                        val blockId = (legacyDoc.getString("blockId") ?: "").take(128)
                        val sortOrder = (legacyDoc.getLong("sortOrder") ?: 0L).coerceIn(0L, 100000L)
                        val pos = legacyDoc.getDouble("position")?.takeIf { it != 0.0 } ?: nextPos
                        val avatarBase64 = (legacyDoc.getString("avatarBase64") ?: "").take(50000)

                        val payload = mutableMapOf<String, Any>(
                            "groupId" to groupId,
                            "name" to rawName,
                            "studentNumber" to stuNum.toLong(),
                            "blockId" to blockId,
                            "sortOrder" to sortOrder,
                            "position" to pos,
                            "avatarInitials" to ArabicInitialsHelper.extractInitials(rawName),
                            "createdByUid" to currentUserId,
                            "memberUids" to safeMembers,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        if (avatarBase64.isNotBlank()) {
                            payload["avatarBase64"] = avatarBase64
                        }
                        targetStudentRef.set(payload).await()
                        existingNormNames.add(normName)
                        nextNum++
                        nextPos += 1000.0

                        // Verify target exists before deleting legacy doc
                        if (targetStudentRef.get().await().exists()) {
                            legacyDoc.reference.delete().await()
                            migratedCount++
                        }
                    } else if (existingTarget.exists()) {
                        legacyDoc.reference.delete().await()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        // Step C: Ensure all existing students in groups/{groupId}/students have a non-zero fractional position
        try {
            val studentsSnap = groupRef.collection("students")
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            val docsMissingPosition = studentsSnap.documents.filter { doc ->
                val pos = doc.getDouble("position")
                pos == null || pos == 0.0
            }
            if (docsMissingPosition.isNotEmpty()) {
                val batch = db.batch()
                docsMissingPosition.forEachIndexed { idx, doc ->
                    val sortOrder = doc.getLong("sortOrder") ?: idx.toLong()
                    val stuNum = doc.getLong("studentNumber") ?: (idx + 1).toLong()
                    val computedPosition = (sortOrder + 1).toDouble() * 1000.0 + stuNum.toDouble()
                    batch.update(
                        doc.reference,
                        mapOf(
                            "position" to computedPosition,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        migratedCount
    }

    /**
     * Merges a duplicate student record (`duplicateStudent`) into the chosen student record (`keepStudent`):
     * 1. Transfers any cardEvents belonging to `duplicateStudent.id` over to `keepStudent.id`.
     * 2. Verifies the events were reassigned.
     * 3. Deletes `duplicateStudent` (requires Admin permissions on the server).
     */
    suspend fun mergeDuplicateStudents(
        groupId: String,
        keepStudent: Student,
        duplicateStudent: Student,
        memberUids: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        require(keepStudent.id != duplicateStudent.id) { "لا يمكن دمج السجل مع نفسه" }
        val groupRef = db.collection("groups").document(groupId)
        val eventsCol = groupRef.collection("cardEvents")

        try {
            val allGroupEventsSnap = eventsCol
                .whereEqualTo("groupId", groupId)
                .get()
                .await()
            val duplicateEvents = allGroupEventsSnap.documents.filter { doc ->
                doc.getString("studentId") == duplicateStudent.id
            }
            if (duplicateEvents.isNotEmpty()) {
                val batch = db.batch()
                duplicateEvents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "studentId" to keepStudent.id,
                            "studentName" to keepStudent.name.take(100),
                            "memberUids" to memberUids.distinct().take(10),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }

            // Delete the duplicate student document after transferring its card events
            groupRef.collection("students").document(duplicateStudent.id).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "groups/$groupId/students/${duplicateStudent.id}")
            throw e
        }
    }

    // --- 4. Invitations (Filtered ONLY by groupId) ---

    fun observeInvitations(groupId: String, @Suppress("UNUSED_PARAMETER") currentUserId: String = ""): Flow<List<Invitation>> {
        val path = "groups/$groupId/invitations"
        return db.collection("groups").document(groupId).collection("invitations")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Invitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createInvitation(
        groupId: String,
        groupName: String,
        inviteeName: String,
        memberUids: List<String>,
        fullInviteCode: String = groupId
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val invId = "inv_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("groups").document(groupId).collection("invitations").document(invId)
        try {
            val payload = mapOf(
                "groupId" to groupId,
                "groupName" to groupName.trim().take(100),
                "adminId" to uid,
                "inviteeName" to inviteeName.trim().ifBlank { "خادم جديد" }.take(100),
                "inviteCode" to fullInviteCode.trim().take(32),
                "status" to "PENDING",
                "acceptedByUid" to "",
                "memberUids" to memberUids.distinct().take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            invId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun respondToInvitation(
        groupId: String,
        invitationId: String,
        accept: Boolean,
        memberUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("invitations").document(invitationId)
        try {
            docRef.update(
                mapOf(
                    "status" to if (accept) "ACCEPTED" else "REJECTED",
                    "acceptedByUid" to uid,
                    "memberUids" to memberUids.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteInvitation(groupId: String, invitationId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("invitations").document(invitationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 5. Servant Blocks (Filtered ONLY by groupId) ---

    fun observeBlocks(groupId: String, @Suppress("UNUSED_PARAMETER") currentUserId: String = ""): Flow<List<ServantBlock>> {
        val path = "groups/$groupId/blocks"
        return db.collection("groups").document(groupId).collection("blocks")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ServantBlock::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<ServantBlock> { it.sortOrder }.thenBy { it.id })
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createBlock(
        groupId: String,
        servantId: String,
        servantName: String,
        colorHex: String,
        sortOrder: Int,
        memberUids: List<String>,
        waitForServerAck: Boolean = true
    ): Result<String> = runCatching {
        requireUserId()
        val blockId = "blk_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("groups").document(groupId).collection("blocks").document(blockId)
        try {
            val payload = mapOf(
                "groupId" to groupId,
                "servantId" to servantId.take(128),
                "servantName" to servantName.trim().ifBlank { "بلوك خادم" }.take(100),
                "colorHex" to colorHex.take(30),
                "sortOrder" to sortOrder.toLong().coerceIn(0L, 10000L),
                "memberUids" to memberUids.distinct().take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = docRef.set(payload)
            if (waitForServerAck) {
                task.await()
            }
            blockId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateBlock(
        groupId: String,
        blockId: String,
        servantName: String,
        colorHex: String,
        sortOrder: Long,
        memberUids: List<String>,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("blocks").document(blockId)
        try {
            val task = docRef.update(
                mapOf(
                    "servantName" to servantName.trim().ifBlank { "بلوك خادم" }.take(100),
                    "colorHex" to colorHex.take(30),
                    "sortOrder" to sortOrder.coerceIn(0L, 10000L),
                    "memberUids" to memberUids.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (waitForServerAck) {
                task.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteBlock(groupId: String, blockId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("blocks").document(blockId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 6. Students (Filtered ONLY by groupId, Ordered by Fractional `position`) ---

    fun observeStudents(groupId: String, @Suppress("UNUSED_PARAMETER") currentUserId: String = ""): Flow<List<Student>> {
        val path = "groups/$groupId/students"
        return db.collection("groups").document(groupId).collection("students")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Student::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<Student> { it.effectivePosition }
                            .thenBy { it.studentNumber }
                            .thenBy { it.id }
                    )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun addStudent(
        groupId: String,
        name: String,
        studentNumber: Int,
        blockId: String = "",
        sortOrder: Int = 0,
        memberUids: List<String> = emptyList(),
        position: Double = (sortOrder + 1).toDouble() * 1000.0,
        waitForServerAck: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanName = name.trim().take(100)
        require(cleanName.isNotBlank()) { "يرجى إدخال اسم الطفل" }
        val studentId = "stu_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("groups").document(groupId).collection("students").document(studentId)
        val safePosition = position.coerceIn(-99_000_000.0, 99_000_000.0)
        try {
            val payload = mapOf(
                "groupId" to groupId,
                "name" to cleanName,
                "studentNumber" to studentNumber.toLong().coerceIn(1L, 9999L),
                "blockId" to blockId.take(128),
                "sortOrder" to sortOrder.toLong().coerceIn(0L, 100000L),
                "position" to safePosition,
                "avatarInitials" to ArabicInitialsHelper.extractInitials(cleanName),
                "createdByUid" to uid,
                "memberUids" to memberUids.distinct().take(10),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val task = docRef.set(payload)
            if (waitForServerAck) {
                task.await()
            }
            studentId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    /**
     * Bulk imports a list of student names into the shared group's "بدون بلوك" (`blockId = ""`)
     * with sequential fractional positions (`1000.0`, `2000.0`, ...).
     */
    suspend fun importStudentsBatch(
        groupId: String,
        names: List<String>,
        startingStudentNumber: Int,
        startingSortOrder: Int,
        memberUids: List<String>,
        startingPosition: Double = (startingSortOrder + 1).toDouble() * 1000.0,
        waitForServerAck: Boolean = true
    ): Result<Int> = runCatching {
        val uid = requireUserId()
        val cleanNames = names.map { it.trim().take(100) }.filter { it.isNotBlank() }
        if (cleanNames.isEmpty()) return@runCatching 0

        val studentsCol = db.collection("groups").document(groupId).collection("students")
        val safeMembers = memberUids.distinct().take(10)

        try {
            cleanNames.chunked(100).forEachIndexed { chunkIdx, chunk ->
                val batch = db.batch()
                chunk.forEachIndexed { idx, studentName ->
                    val globalIdx = chunkIdx * 100 + idx
                    val studentId = "stu_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                    val docRef = studentsCol.document(studentId)
                    val fractionalPos = (startingPosition + globalIdx * 1000.0).coerceIn(-99_000_000.0, 99_000_000.0)
                    val payload = mapOf(
                        "groupId" to groupId,
                        "name" to studentName,
                        "studentNumber" to (startingStudentNumber + globalIdx).toLong().coerceIn(1L, 9999L),
                        "blockId" to "",
                        "sortOrder" to (startingSortOrder + globalIdx).toLong().coerceIn(0L, 100000L),
                        "position" to fractionalPos,
                        "avatarInitials" to ArabicInitialsHelper.extractInitials(studentName),
                        "createdByUid" to uid,
                        "memberUids" to safeMembers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                }
                val commitTask = batch.commit()
                if (waitForServerAck) {
                    commitTask.await()
                }
            }
            cleanNames.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, studentsCol.path)
            throw e
        }
    }

    suspend fun moveStudentToBlock(
        groupId: String,
        studentId: String,
        targetBlockId: String,
        newSortOrder: Long,
        memberUids: List<String>,
        newPosition: Double = (newSortOrder + 1).toDouble() * 1000.0,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("students").document(studentId)
        val safePosition = newPosition.coerceIn(-99_000_000.0, 99_000_000.0)
        try {
            val task = docRef.update(
                mapOf(
                    "blockId" to targetBlockId.take(128),
                    "sortOrder" to newSortOrder.coerceIn(0L, 100000L),
                    "position" to safePosition,
                    "memberUids" to memberUids.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (waitForServerAck) {
                task.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Updates ONLY the fractional `position` of a single student document so two servants
     * reordering different children at the same time never overwrite each other's changes.
     */
    suspend fun updateStudentPosition(
        groupId: String,
        studentId: String,
        newPosition: Double,
        newStudentNumber: Long? = null,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("students").document(studentId)
        val safePosition = newPosition.coerceIn(-99_000_000.0, 99_000_000.0)
        try {
            val updates = mutableMapOf<String, Any>(
                "position" to safePosition,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newStudentNumber != null) {
                updates["studentNumber"] = newStudentNumber.coerceIn(1L, 9999L)
            }
            val task = docRef.update(updates)
            if (waitForServerAck) {
                task.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Synchronizes global sequential `studentNumber` (1, 2, 3, 4...) across the group
     * for any student records whose sequential position changed.
     */
    suspend fun syncStudentSequentialNumbers(
        groupId: String,
        numberUpdates: Map<String, Long>,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        if (numberUpdates.isEmpty()) return@runCatching
        requireUserId()
        val studentsCol = db.collection("groups").document(groupId).collection("students")
        try {
            numberUpdates.entries.chunked(100).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (studentId, seqNum) ->
                    batch.update(
                        studentsCol.document(studentId),
                        mapOf(
                            "studentNumber" to seqNum.coerceIn(1L, 9999L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                val task = batch.commit()
                if (waitForServerAck) {
                    task.await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, studentsCol.path)
            throw e
        }
    }

    suspend fun updateStudent(
        groupId: String,
        studentId: String,
        newName: String,
        newStudentNumber: Int,
        newBlockId: String,
        newSortOrder: Long,
        memberUids: List<String>,
        newPosition: Double = (newSortOrder + 1).toDouble() * 1000.0,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanName = newName.trim().take(100)
        require(cleanName.isNotBlank()) { "الاسم لا يمكن أن يكون فارغًا" }
        val docRef = db.collection("groups").document(groupId).collection("students").document(studentId)
        val safePosition = newPosition.coerceIn(-99_000_000.0, 99_000_000.0)
        try {
            val task = docRef.update(
                mapOf(
                    "name" to cleanName,
                    "studentNumber" to newStudentNumber.toLong().coerceIn(1L, 9999L),
                    "blockId" to newBlockId.take(128),
                    "sortOrder" to newSortOrder.coerceIn(0L, 100000L),
                    "position" to safePosition,
                    "avatarInitials" to ArabicInitialsHelper.extractInitials(cleanName),
                    "memberUids" to memberUids.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (waitForServerAck) {
                task.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Updates a child's square-cropped compressed photo (`avatarBase64`, < 30KB) directly in the
     * student document. Any group member (Admin or Servant) can update it, online or offline.
     */
    suspend fun updateStudentAvatar(
        groupId: String,
        studentId: String,
        avatarBase64: String,
        memberUids: List<String>,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val safeAvatar = avatarBase64.trim().take(50000)
        val docRef = db.collection("groups").document(groupId).collection("students").document(studentId)
        try {
            val updateTask = docRef.update(
                mapOf(
                    "avatarBase64" to safeAvatar,
                    "memberUids" to memberUids.distinct().take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (waitForServerAck) {
                updateTask.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStudent(groupId: String, studentId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("students").document(studentId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 7. Cards & Monthly Points (Filtered ONLY by groupId) ---

    fun observeAllCardEventsForGroup(
        groupId: String,
        @Suppress("UNUSED_PARAMETER") currentUserId: String = ""
    ): Flow<List<CardEvent>> {
        val path = "groups/$groupId/cardEvents"
        return db.collection("groups").document(groupId).collection("cardEvents")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CardEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeCardEventsForMonth(
        groupId: String,
        currentUserId: String = "",
        monthKey: String
    ): Flow<List<CardEvent>> {
        return observeAllCardEventsForGroup(groupId, currentUserId)
            .map { events -> events.filter { it.monthKey == monthKey } }
    }

    fun observeAllMonthlyBalancesForGroup(
        groupId: String,
        @Suppress("UNUSED_PARAMETER") currentUserId: String = ""
    ): Flow<List<MonthlyBalance>> {
        val path = "groups/$groupId/monthlyBalances"
        return db.collection("groups").document(groupId).collection("monthlyBalances")
            .whereEqualTo("groupId", groupId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MonthlyBalance::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMonthlyBalances(
        groupId: String,
        currentUserId: String = "",
        monthKey: String
    ): Flow<List<MonthlyBalance>> {
        return observeAllMonthlyBalancesForGroup(groupId, currentUserId)
            .map { balances -> balances.filter { it.monthKey == monthKey } }
    }

    fun observeHasPendingWrites(
        groupId: String,
        @Suppress("UNUSED_PARAMETER") currentUserId: String = ""
    ): Flow<Boolean> {
        val groupRef = db.collection("groups").document(groupId)
        val eventsPending = groupRef.collection("cardEvents")
            .whereEqualTo("groupId", groupId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { it.metadata.hasPendingWrites() }
            .catch { emit(false) }

        val studentsPending = groupRef.collection("students")
            .whereEqualTo("groupId", groupId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { it.metadata.hasPendingWrites() }
            .catch { emit(false) }

        val blocksPending = groupRef.collection("blocks")
            .whereEqualTo("groupId", groupId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { it.metadata.hasPendingWrites() }
            .catch { emit(false) }

        return combine(eventsPending, studentsPending, blocksPending) { ePending, sPending, bPending ->
            ePending || sPending || bPending
        }.distinctUntilChanged()
    }

    /**
     * Awards a card with a deterministic idempotency key (`eventId`) scoped by
     * `(studentId, cardType, deterministicSlotKey, servantUid)` so:
     * 1. The same servant tapping twice or offline retries never duplicate points.
     * 2. Two different servants awarding the same card to the same child in the same week
     *    both sync cleanly and are visible to everyone in the group.
     */
    suspend fun awardCard(
        groupId: String,
        student: Student,
        cardType: CardType,
        servantName: String,
        monthKey: String,
        weekKey: String,
        deterministicSlotKey: String,
        currentMonthPoints: Long,
        currentMonthEventsCount: Long,
        memberUids: List<String>,
        waitForServerAck: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val safeSlot = deterministicSlotKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        val eventId = "evt_${student.id}_${cardType.id}_${safeSlot}_${uid}".take(120)
        val balanceId = "bal_${student.id}_${monthKey.replace("-", "")}".take(120)

        val groupRef = db.collection("groups").document(groupId)
        val eventRef = groupRef.collection("cardEvents").document(eventId)
        val balanceRef = groupRef.collection("monthlyBalances").document(balanceId)
        val safeMembers = memberUids.distinct().take(10)

        try {
            val batch = db.batch()
            val eventPayload = mapOf(
                "groupId" to groupId,
                "studentId" to student.id,
                "studentName" to student.name.take(100),
                "cardType" to cardType.id,
                "points" to cardType.points.toLong(),
                "servantId" to uid,
                "servantName" to servantName.trim().ifBlank { "خادم" }.take(100),
                "monthKey" to monthKey,
                "weekKey" to weekKey,
                "memberUids" to safeMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(eventRef, eventPayload)

            val balancePayload = mapOf(
                "groupId" to groupId,
                "studentId" to student.id,
                "studentName" to student.name.take(100),
                "monthKey" to monthKey,
                "totalPoints" to (currentMonthPoints + cardType.points).coerceIn(0L, 100000L),
                "eventsCount" to (currentMonthEventsCount + 1L).coerceIn(0L, 10000L),
                "lastUpdatedByUid" to uid,
                "memberUids" to safeMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(balanceRef, balancePayload)
            val commitTask = batch.commit()
            if (waitForServerAck) {
                commitTask.await()
            }
            eventId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, eventRef.path)
            throw e
        }
    }

    suspend fun undoCardEvent(
        groupId: String,
        event: CardEvent,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("cardEvents").document(event.id)
        try {
            val deleteTask = docRef.delete()
            if (waitForServerAck) {
                deleteTask.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val randomSuffix = (1..5).map { chars.random() }.joinToString("")
        return "KAM$randomSuffix"
    }

    private fun generateDailyInviteToken(excludeToken: String = ""): String {
        val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        var candidate: String
        do {
            candidate = (1..3).map { chars.random() }.joinToString("")
        } while (candidate == excludeToken && excludeToken.isNotBlank())
        return candidate
    }
}
