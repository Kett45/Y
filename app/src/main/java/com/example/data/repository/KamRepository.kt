package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.BlockColorPreset
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ChildNote
import com.example.data.model.ChildNoteType
import com.example.data.model.EmailDirectoryEntry
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.MAX_SERVANTS_PER_GROUP
import com.example.data.model.MonthlyBalance
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.model.Student
import com.example.data.model.UserProfile
import com.example.data.model.VisitationStatus
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.cache.LocalClassroomCache
import com.example.ui.components.StudentAvatarImageHelper
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
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
    private val pendingWritesBySource = MutableStateFlow( Triple(false, false, false) )
    private val hasPendingWritesFlow = pendingWritesBySource.map { (b, s, e) -> b || s || e }

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
                    if (existing != null) {
                        val effectiveEmail = existing.email.ifBlank { email }
                        val effectiveName = existing.displayName.ifBlank { displayName }
                        runCatching { upsertEmailDirectoryEntry(uid, effectiveEmail, effectiveName) }
                        return@withLock existing
                    }
                }
                val safeName = displayName.trim().ifBlank { "خادم الفصل" }.take(80)
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
                    "themeId" to "DARK",
                    "uiScaleMode" to "AUTO",
                    "uiScaleFactor" to 0.85,
                    "showFloatingAddButton" to true,
                    "servantAliases" to emptyMap<String, String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                runCatching { upsertEmailDirectoryEntry(uid, safeEmail, safeName) }
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
                    compactViewMode = false,
                    themeId = "DARK",
                    uiScaleMode = "AUTO",
                    uiScaleFactor = 0.85,
                    showFloatingAddButton = true,
                    servantAliases = emptyMap()
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

    suspend fun upsertEmailDirectoryEntry(
        uid: String,
        rawEmail: String,
        displayName: String
    ) {
        val normEmail = EmailDirectoryEntry.normalizeEmail(rawEmail).take(150)
        val docId = EmailDirectoryEntry.buildDocId(normEmail)
        if (uid.isBlank() || docId.isBlank() || normEmail.isBlank()) return
        val safeName = displayName.trim().ifBlank { "خادم الفصل" }.take(80)
        db.collection("emailDirectory").document(docId)
            .set(
                mapOf(
                    "email" to normEmail,
                    "uid" to uid,
                    "displayName" to safeName,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun lookupUserByEmail(rawEmail: String): Result<EmailDirectoryEntry?> = runCatching {
        requireUserId()
        val normEmail = EmailDirectoryEntry.normalizeEmail(rawEmail)
        val docId = EmailDirectoryEntry.buildDocId(normEmail)
        if (docId.isBlank() || !normEmail.contains("@")) {
            throw IllegalArgumentException("يرجى إدخال بريد إلكتروني صحيح")
        }
        val snap = db.collection("emailDirectory").document(docId).get().await()
        if (!snap.exists()) {
            null
        } else {
            snap.toObject(EmailDirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.copy(id = snap.id)
        }
    }

    suspend fun updateOwnDisplayName(
        newName: String,
        email: String,
        memberGroups: List<Group>,
        ownBlocks: List<ServantBlock>,
        waitForServerAck: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanName = newName.trim().take(80)
        require(cleanName.isNotBlank()) { "الاسم لا يمكن أن يكون فارغًا" }

        val userRef = db.collection("users").document(uid)
        safeUpsertUserProfileFields(
            uid = uid,
            docRef = userRef,
            fields = mapOf("displayName" to cleanName)
        )
        runCatching {
            if (email.isNotBlank()) {
                upsertEmailDirectoryEntry(uid, email, cleanName)
            }
        }

        val batch = db.batch()
        memberGroups.distinctBy { it.inviteCode }.forEach { grp ->
            if (grp.inviteCode.isNotBlank()) {
                val memberRef = db.collection("groups").document(grp.inviteCode)
                    .collection("members").document(uid)
                batch.set(
                    memberRef,
                    mapOf(
                        "uid" to uid,
                        "groupId" to grp.inviteCode,
                        "displayName" to cleanName,
                        "phoneVerified" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                if (grp.adminId == uid) {
                    batch.update(
                        db.collection("groups").document(grp.inviteCode),
                        mapOf(
                            "adminName" to cleanName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
        }
        ownBlocks.filter { it.servantId == uid && it.groupId.isNotBlank() && it.id.isNotBlank() }
            .forEach { blk ->
                batch.update(
                    db.collection("groups").document(blk.groupId)
                        .collection("blocks").document(blk.id),
                    mapOf(
                        "servantName" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        val task = batch.commit()
        if (waitForServerAck) {
            task.await()
        }
        cleanName
    }

    suspend fun updatePersonalServantAliases(
        aliases: Map<String, String>,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val cleaned = LinkedHashMap<String, String>()
        aliases.entries
            .filter { it.key.isNotBlank() && it.value.trim().isNotBlank() }
            .take(100)
            .forEach { (k, v) ->
                cleaned[k.trim().take(64)] = v.trim().take(80)
            }
        safeUpsertUserProfileFields(
            uid = uid,
            docRef = docRef,
            fields = mapOf("servantAliases" to cleaned)
        )
        if (!waitForServerAck) Unit
    }

    suspend fun updateGroupAvatar(
        groupId: String,
        avatarBase64: String,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        requireUserId()
        require(groupId.isNotBlank()) { "المجموعة غير محددة" }
        val safeAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
        val avatarHash = if (safeAvatar.isNotBlank()) Student.computeAvatarHash(safeAvatar) else ""
        StudentAvatarImageHelper.writeLocalAlbumRaw(null, "group_$groupId", safeAvatar)
        val docRef = db.collection("groups").document(groupId)
        val task = docRef.update(
            mapOf(
                "avatarBase64" to safeAvatar,
                "avatarHash" to avatarHash,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (waitForServerAck) {
            task.await()
        }
    }

    private suspend fun safeUpsertUserProfileFields(
        uid: String,
        docRef: DocumentReference,
        fields: Map<String, Any>
    ) {
        val updatePayload = fields + ("updatedAt" to FieldValue.serverTimestamp())
        try {
            docRef.update(updatePayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Fallback for legacy or not-yet-created profile document: ensure all required blueprint fields exist
            val existingSnap = runCatching { docRef.get().await() }.getOrNull()
            val currentUser = auth.currentUser
            val safeName = (existingSnap?.getString("displayName") ?: currentUser?.displayName ?: "خادم الفصل")
                .trim().ifBlank { "خادم الفصل" }.take(80)
            val safeEmail = (existingSnap?.getString("email") ?: currentUser?.email ?: "$uid@kamlite.app")
                .trim().take(150)
            val safeGroupId = (existingSnap?.getString("groupId") ?: "").take(40)
            @Suppress("UNCHECKED_CAST")
            val safeEnabledCards = (existingSnap?.get("enabledCards") as? List<String>)
                ?.distinct()?.take(7) ?: CardType.defaultEnabledIds
            val createdAtVal: Any = existingSnap?.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()

            val fullMergePayload = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to safeName,
                "email" to safeEmail,
                "groupId" to safeGroupId,
                "enabledCards" to safeEnabledCards,
                "createdAt" to createdAtVal,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            fullMergePayload.putAll(fields)
            docRef.set(fullMergePayload, SetOptions.merge()).await()
        }
    }

    suspend fun updateEnabledCards(enabledCards: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            safeUpsertUserProfileFields(
                uid = uid,
                docRef = docRef,
                fields = mapOf("enabledCards" to enabledCards.distinct().take(7))
            )
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
            safeUpsertUserProfileFields(
                uid = uid,
                docRef = docRef,
                fields = mapOf(
                    "collapsedBlockIds" to collapsedBlockIds.distinct().take(50),
                    "hiddenBlockIds" to hiddenBlockIds.distinct().take(50)
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserAppearanceAndScalePreferences(
        themeId: String,
        uiScaleMode: String,
        uiScaleFactor: Double,
        showFloatingAddButton: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            safeUpsertUserProfileFields(
                uid = uid,
                docRef = docRef,
                fields = mapOf(
                    "themeId" to themeId.trim().ifBlank { "DARK" }.take(40),
                    "uiScaleMode" to uiScaleMode.trim().ifBlank { "AUTO" }.take(20),
                    "uiScaleFactor" to uiScaleFactor.coerceIn(0.70, 1.30),
                    "showFloatingAddButton" to showFloatingAddButton
                )
            )
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
                "groupId" to groupId
            )
            if (updatedJoined.isNotEmpty()) {
                updates["joinedGroupIds"] = updatedJoined
            }
            safeUpsertUserProfileFields(uid = uid, docRef = docRef, fields = updates)
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
            runCatching {
                upsertOwnMemberProfile(
                    groupId = inviteCode,
                    displayName = safeAdminName,
                    memberUids = memberUids,
                    waitForServerAck = true
                )
            }

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
                require(group.memberUids.size < MAX_SERVANTS_PER_GROUP) {
                    "المجموعة مكتملة ($MAX_SERVANTS_PER_GROUP خادم كحد أقصى)"
                }
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

            val safeServantName = servantName.trim().ifBlank { "خادم" }.take(80)
            // Note: Creating/assigning blocks is strictly restricted to Group Admin per Firestore Rules.
            runCatching {
                upsertOwnMemberProfile(
                    groupId = groupPrefix,
                    displayName = safeServantName,
                    memberUids = updatedMembers,
                    waitForServerAck = true
                )
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
        require(currentGroup.isUserAdmin(adminUid)) { "إزالة وحظر الخادم متاحة لأمين المجموعة أو مساعده فقط" }
        val targetServantUid = block.servantId.trim()
        require(targetServantUid.isNotBlank() && targetServantUid != currentGroup.adminId && targetServantUid != adminUid) {
            "لا يمكن إزالة أو حظر أمين المجموعة"
        }
        if (!currentGroup.isPrimaryAdmin(adminUid)) {
            require(!currentGroup.isAssistantAdmin(targetServantUid)) {
                "لا يمكن لمساعد الأمين إزالة مساعد أمين آخر"
            }
        }

        val updatedMembers = currentGroup.memberUids.filter { it != targetServantUid }.ifEmpty { listOf(currentGroup.adminId.ifBlank { adminUid }) }
        val updatedAssistants = currentGroup.assistantAdminIds.filter { it != targetServantUid }
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
            val groupUpdates = mutableMapOf<String, Any>(
                "memberUids" to updatedMembers,
                "bannedUids" to updatedBannedUids,
                "bannedServantEntries" to updatedBannedEntries,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (currentGroup.isPrimaryAdmin(adminUid) && updatedAssistants != currentGroup.assistantAdminIds) {
                groupUpdates["assistantAdminIds"] = updatedAssistants
            }
            batch.update(groupRef, groupUpdates)
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
                assistantAdminIds = if (currentGroup.isPrimaryAdmin(adminUid)) updatedAssistants else currentGroup.assistantAdminIds,
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
        require(currentGroup.isUserAdmin(adminUid)) { "إلغاء الحظر متاح لأمين المجموعة أو مساعده فقط" }
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

    /**
     * Allows the primary Group Admin (`adminId`) to appoint or remove an Assistant Admin
     * (`assistantAdminIds`) who inherits full admin permissions across the group.
     */
    suspend fun setAssistantAdminStatus(
        groupId: String,
        targetUid: String,
        makeAssistant: Boolean,
        currentGroup: Group
    ): Result<Group> = runCatching {
        val actorUid = requireUserId()
        require(currentGroup.isPrimaryAdmin(actorUid)) {
            "تعيين أو إلغاء مساعد الأمين متاح لأمين المجموعة الأساسي فقط"
        }
        val cleanTarget = targetUid.trim()
        require(cleanTarget.isNotBlank() && cleanTarget != currentGroup.adminId) {
            "لا يمكن تغيير صلاحية أمين المجموعة الأساسي"
        }
        require(cleanTarget in currentGroup.memberUids) {
            "هذا الخادم ليس عضواً حالياً في المجموعة"
        }

        val updatedAssistants = if (makeAssistant) {
            (currentGroup.assistantAdminIds + cleanTarget).distinct().take(20)
        } else {
            currentGroup.assistantAdminIds.filter { it != cleanTarget }
        }
        val groupRef = db.collection("groups").document(groupId)
        try {
            groupRef.update(
                mapOf(
                    "assistantAdminIds" to updatedAssistants,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            currentGroup.copy(assistantAdminIds = updatedAssistants)
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
        val safeMembers = targetMemberUids.ifEmpty { listOf(currentUserId) }.distinct().take(MAX_SERVANTS_PER_GROUP)
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
                        val avatarBase64 = (legacyDoc.getString("avatarBase64") ?: "").take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)

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
        // and migrate any inline avatarBase64 into groups/{groupId}/avatars/{studentId}
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

            // Step D: Automatically migrate inline avatarBase64 strings from students/{id} to avatars/{id}
            val docsWithInlineAvatar = studentsSnap.documents.filter { doc ->
                !doc.getString("avatarBase64").isNullOrBlank()
            }
            if (docsWithInlineAvatar.isNotEmpty()) {
                docsWithInlineAvatar.chunked(20).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc ->
                        val rawAvatar = doc.getString("avatarBase64").orEmpty().trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
                        if (rawAvatar.isNotBlank()) {
                            val hash = Student.computeAvatarHash(rawAvatar)
                            val avatarRef = groupRef.collection("avatars").document(doc.id)
                            batch.set(
                                avatarRef,
                                mapOf(
                                    "groupId" to groupId,
                                    "studentId" to doc.id,
                                    "avatarBase64" to rawAvatar,
                                    "avatarHash" to hash,
                                    "updatedByUid" to currentUserId,
                                    "memberUids" to safeMembers,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                            batch.update(
                                doc.reference,
                                mapOf(
                                    "avatarBase64" to "",
                                    "hasAvatar" to true,
                                    "avatarHash" to hash,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                            migratedCount++
                        }
                    }
                    batch.commit().await()
                }
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
                            "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
                "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
                    "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
        return flow {
            val blocksById = LinkedHashMap<String, ServantBlock>()
            var hasEmittedInitial = false
            db.collection("groups").document(groupId).collection("blocks")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .collect { snapshot ->
                    val cur = pendingWritesBySource.value
                    pendingWritesBySource.value = cur.copy(first = snapshot.metadata.hasPendingWrites())
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    ServantBlock::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                                blocksById[docId] = parsed
                            }
                            DocumentChange.Type.REMOVED -> {
                                blocksById.remove(docId)
                            }
                        }
                    }
                    emit(
                        blocksById.values.sortedWith(
                            compareBy<ServantBlock> { it.sortOrder }.thenBy { it.id }
                        )
                    )
                }
        }.catch { error ->
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
                "servantId" to servantId.take(64),
                "servantName" to servantName.trim().ifBlank { "بلوك خادم" }.take(80),
                "colorHex" to colorHex.take(24),
                "sortOrder" to sortOrder.toLong().coerceIn(0L, 500L),
                "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
        servantId: String? = null,
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups").document(groupId).collection("blocks").document(blockId)
        try {
            val payload = mutableMapOf<String, Any>(
                "servantName" to servantName.trim().ifBlank { "بلوك خادم" }.take(80),
                "colorHex" to colorHex.take(24),
                "sortOrder" to sortOrder.coerceIn(0L, 500L),
                "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (servantId != null) {
                payload["servantId"] = servantId.take(64)
            }
            val task = docRef.update(payload)
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

    suspend fun updateBlockCustomAvatar(
        groupId: String,
        blockId: String,
        avatarBase64: String,
        memberUids: List<String> = emptyList(),
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(groupId.isNotBlank() && blockId.isNotBlank()) { "البلوك غير محدد" }
        val avatarDocId = "servant_block_$blockId".take(80)
        val safeAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
        val hasAvatar = safeAvatar.isNotBlank()
        val avatarHash = if (hasAvatar) Student.computeAvatarHash(safeAvatar) else ""
        val safeMembers = memberUids.ifEmpty { listOf(uid) }.distinct().take(MAX_SERVANTS_PER_GROUP)

        StudentAvatarImageHelper.writeLocalAlbumRaw(null, avatarDocId, safeAvatar)
        val avatarRef = db.collection("groups").document(groupId)
            .collection("avatars").document(avatarDocId)
        try {
            val task = if (hasAvatar) {
                avatarRef.set(
                    mapOf(
                        "groupId" to groupId,
                        "studentId" to avatarDocId,
                        "avatarBase64" to safeAvatar,
                        "avatarHash" to avatarHash,
                        "updatedByUid" to uid,
                        "memberUids" to safeMembers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                avatarRef.delete()
            }
            if (waitForServerAck) {
                task.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, avatarRef.path)
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

    suspend fun deleteBlockKeepingChildren(
        groupId: String,
        blockId: String,
        studentIdsInBlock: List<String>,
        memberUids: List<String>,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        requireUserId()
        val groupRef = db.collection("groups").document(groupId)
        val studentsCol = groupRef.collection("students")
        val safeMembers = memberUids.distinct().take(MAX_SERVANTS_PER_GROUP)

        try {
            if (studentIdsInBlock.isEmpty()) {
                groupRef.collection("blocks").document(blockId).delete().let { t ->
                    if (waitForServerAck) t.await()
                }
                return@runCatching
            }
            val chunks = studentIdsInBlock.distinct().chunked(120)
            chunks.forEachIndexed { idx, chunk ->
                val batch = db.batch()
                chunk.forEach { stuId ->
                    batch.update(
                        studentsCol.document(stuId),
                        mapOf(
                            "blockId" to "",
                            "memberUids" to safeMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (idx == chunks.lastIndex) {
                    batch.delete(groupRef.collection("blocks").document(blockId))
                }
                val task = batch.commit()
                if (waitForServerAck) {
                    task.await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "groups/$groupId/blocks/$blockId")
            throw e
        }
    }

    suspend fun deleteBlockWithChildren(
        groupId: String,
        blockId: String,
        studentIdsInBlock: List<String>,
        noteIdsToDelete: List<String>,
        balanceIdsToDelete: List<String>,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        requireUserId()
        val groupRef = db.collection("groups").document(groupId)
        val docRefsToDelete = mutableListOf<DocumentReference>()
        studentIdsInBlock.distinct().forEach { stuId ->
            docRefsToDelete.add(groupRef.collection("students").document(stuId))
            docRefsToDelete.add(groupRef.collection("avatars").document(stuId))
        }
        noteIdsToDelete.distinct().forEach { nid ->
            docRefsToDelete.add(groupRef.collection("childNotes").document(nid))
        }
        balanceIdsToDelete.distinct().forEach { bid ->
            docRefsToDelete.add(groupRef.collection("monthlyBalances").document(bid))
        }
        docRefsToDelete.add(groupRef.collection("blocks").document(blockId))

        try {
            docRefsToDelete.chunked(120).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                val task = batch.commit()
                if (waitForServerAck) {
                    task.await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "groups/$groupId/blocks/$blockId")
            throw e
        }
    }

    // --- 5B. Group Member Public Profiles (groups/{groupId}/members/{memberUid}) ---

    fun observeGroupMembers(groupId: String): Flow<List<ServantMemberProfile>> {
        val path = "groups/$groupId/members"
        return flow {
            val membersByUid = LinkedHashMap<String, ServantMemberProfile>()
            var hasEmittedInitial = false
            db.collection("groups").document(groupId).collection("members")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .collect { snapshot ->
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    ServantMemberProfile::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                ).let { raw ->
                                    val effectiveUid = raw.uid.ifBlank { docId }
                                    raw.copy(id = docId, uid = effectiveUid)
                                }
                                if (parsed.effectiveAvatarBase64.isNotBlank()) {
                                    StudentAvatarImageHelper.writeLocalAlbumRaw(
                                        null,
                                        parsed.localAvatarKey,
                                        parsed.effectiveAvatarBase64
                                    )
                                }
                                membersByUid[docId] = parsed
                            }
                            DocumentChange.Type.REMOVED -> {
                                membersByUid.remove(docId)
                            }
                        }
                    }
                    emit(membersByUid.values.sortedBy { it.effectiveUid })
                }
        }.catch { error ->
            if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
            throw error
        }
    }

    suspend fun fetchServantAvatar(groupId: String, servantUid: String): String? {
        if (groupId.isBlank() || servantUid.isBlank()) return null
        return try {
            val doc = db.collection("groups").document(groupId)
                .collection("members").document(servantUid)
                .get()
                .await()
            val raw = doc.getString("avatarBase64")?.trim().orEmpty()
                .ifBlank { doc.getString("avatar")?.trim().orEmpty() }
            if (raw.isNotBlank()) {
                StudentAvatarImageHelper.writeLocalAlbumRaw(null, "servant_$servantUid", raw)
                raw
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun upsertOwnMemberProfile(
        groupId: String,
        displayName: String,
        primaryPhone: String? = null,
        secondaryPhone: String? = null,
        phoneHidden: Boolean? = null,
        avatarBase64: String? = null,
        memberUids: List<String> = emptyList(),
        existingProfile: ServantMemberProfile? = null,
        touchLastSeen: Boolean = false,
        waitForServerAck: Boolean = true
    ): Result<ServantMemberProfile> = runCatching {
        val uid = requireUserId()
        require(groupId.isNotBlank()) { "المجموعة غير محددة" }
        val docRef = db.collection("groups").document(groupId).collection("members").document(uid)
        try {
            val snap = if (existingProfile == null) {
                try { docRef.get().await() } catch (_: Exception) { null }
            } else {
                null
            }
            val current = existingProfile ?: snap?.toObject(
                ServantMemberProfile::class.java
            )
            val safeName = displayName.trim().ifBlank {
                current?.displayName?.ifBlank { "خادم" } ?: "خادم"
            }.take(100)
            val finalPrimary = (primaryPhone ?: current?.effectivePrimaryPhone.orEmpty()).trim().take(24)
            val finalSecondary = (secondaryPhone ?: current?.secondaryPhone.orEmpty()).trim().take(24)
            val finalHidden = false
            val finalShowPhone = true

            val finalAvatarBase64: String
            val finalHasAvatar: Boolean
            val finalAvatarHash: String
            when {
                avatarBase64 == null -> {
                    val existingRaw = current?.effectiveAvatarBase64?.trim().orEmpty()
                        .ifBlank { StudentAvatarImageHelper.readLocalAlbumRaw(null, "servant_$uid") }
                        .take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
                    finalAvatarBase64 = existingRaw
                    finalHasAvatar = (current?.effectiveHasAvatar == true) || existingRaw.isNotBlank()
                    finalAvatarHash = current?.avatarHash?.ifBlank {
                        if (existingRaw.isNotBlank()) Student.computeAvatarHash(existingRaw) else ""
                    } ?: if (existingRaw.isNotBlank()) Student.computeAvatarHash(existingRaw) else ""
                }
                avatarBase64.isBlank() -> {
                    finalAvatarBase64 = ""
                    finalHasAvatar = false
                    finalAvatarHash = ""
                    StudentAvatarImageHelper.writeLocalAlbumRaw(null, "servant_$uid", "")
                }
                else -> {
                    val cleanAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
                    finalAvatarBase64 = cleanAvatar
                    finalHasAvatar = true
                    finalAvatarHash = Student.computeAvatarHash(cleanAvatar)
                    StudentAvatarImageHelper.writeLocalAlbumRaw(null, "servant_$uid", cleanAvatar)
                }
            }

            val safeMembers = memberUids.ifEmpty {
                current?.memberUids.orEmpty().ifEmpty { listOf(uid) }
            }.distinct().take(MAX_SERVANTS_PER_GROUP)
            val finalEmail = (current?.email?.ifBlank { auth.currentUser?.email.orEmpty() }
                ?: auth.currentUser?.email.orEmpty()).trim().take(150)

            val payload = HashMap<String, Any>().apply {
                put("uid", uid)
                put("groupId", groupId)
                put("displayName", safeName)
                put("email", finalEmail)
                put("phone", finalPrimary)
                put("primaryPhone", finalPrimary)
                put("secondaryPhone", finalSecondary)
                put("phoneVerified", false)
                put("showPhone", finalShowPhone)
                put("phoneHidden", finalHidden)
                put("hasAvatar", finalHasAvatar)
                put("avatarHash", finalAvatarHash)
                put("avatarBase64", finalAvatarBase64)
                put("memberUids", safeMembers)
                put("lastSeenAt", if (touchLastSeen) FieldValue.serverTimestamp() else (current?.lastSeenAt ?: FieldValue.serverTimestamp()))
                put("createdAt", current?.createdAt ?: FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            val task = docRef.set(payload)
            if (waitForServerAck) {
                task.await()
            }

            val nowTs = Timestamp.now()
            ServantMemberProfile(
                id = uid,
                uid = uid,
                groupId = groupId,
                displayName = safeName,
                email = finalEmail,
                phone = finalPrimary,
                primaryPhone = finalPrimary,
                secondaryPhone = finalSecondary,
                phoneVerified = false,
                showPhone = finalShowPhone,
                phoneHidden = finalHidden,
                hasAvatar = finalHasAvatar,
                avatarHash = finalAvatarHash,
                avatarBase64 = finalAvatarBase64,
                memberUids = safeMembers,
                lastSeenAt = if (touchLastSeen) nowTs else (current?.lastSeenAt ?: nowTs),
                createdAt = current?.createdAt,
                updatedAt = nowTs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- 6. Students (Filtered ONLY by groupId, Ordered by Fractional `position`) ---

    fun observeStudents(groupId: String, @Suppress("UNUSED_PARAMETER") currentUserId: String = ""): Flow<List<Student>> {
        val path = "groups/$groupId/students"
        return flow {
            val studentsById = LinkedHashMap<String, Student>()
            var hasEmittedInitial = false
            db.collection("groups").document(groupId).collection("students")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .collect { snapshot ->
                    val cur = pendingWritesBySource.value
                    pendingWritesBySource.value = cur.copy(second = snapshot.metadata.hasPendingWrites())
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    Student::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                                studentsById[docId] = parsed
                            }
                            DocumentChange.Type.REMOVED -> {
                                studentsById.remove(docId)
                            }
                        }
                    }
                    emit(
                        studentsById.values.sortedWith(
                            compareBy<Student> { it.effectivePosition }
                                .thenBy { it.studentNumber }
                                .thenBy { it.id }
                        )
                    )
                }
        }.catch { error ->
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
                "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
        val safeMembers = memberUids.distinct().take(MAX_SERVANTS_PER_GROUP)

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
                    "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
                    "memberUids" to memberUids.distinct().take(MAX_SERVANTS_PER_GROUP),
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
     * Stores the child's packed photo (`avatarBase64`, < 45KB) in the dedicated
     * `groups/{groupId}/avatars/{studentId}` collection so it is loaded lazily on demand,
     * while updating `groups/{groupId}/students/{studentId}` with lightweight metadata
     * (`avatarBase64 = ""`, `hasAvatar`, `avatarHash`) so the main student list stays fast and tiny.
     */
    suspend fun updateStudentAvatar(
        groupId: String,
        studentId: String,
        avatarBase64: String,
        memberUids: List<String> = emptyList(),
        waitForServerAck: Boolean = true
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val safeAvatar = avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
        val hasAvatar = safeAvatar.isNotBlank()
        val avatarHash = Student.computeAvatarHash(safeAvatar)
        val safeMembers = memberUids.ifEmpty { listOf(uid) }.distinct().take(MAX_SERVANTS_PER_GROUP)

        val groupRef = db.collection("groups").document(groupId)
        val studentRef = groupRef.collection("students").document(studentId)
        val avatarRef = groupRef.collection("avatars").document(studentId)
        try {
            val batch = db.batch()
            if (hasAvatar) {
                batch.set(
                    avatarRef,
                    mapOf(
                        "groupId" to groupId,
                        "studentId" to studentId,
                        "avatarBase64" to safeAvatar,
                        "avatarHash" to avatarHash,
                        "updatedByUid" to uid,
                        "memberUids" to safeMembers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.delete(avatarRef)
            }
            batch.update(
                studentRef,
                mapOf(
                    "avatarBase64" to "",
                    "hasAvatar" to hasAvatar,
                    "avatarHash" to avatarHash,
                    "memberUids" to safeMembers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val commitTask = batch.commit()
            if (waitForServerAck) {
                commitTask.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, studentRef.path)
            throw e
        }
    }

    /**
     * Lazily fetches a single student's avatar document (`groups/{groupId}/avatars/{studentId}`)
     * on demand when the student becomes visible on screen.
     */
    suspend fun fetchStudentAvatar(groupId: String, studentId: String): String? {
        if (groupId.isBlank() || studentId.isBlank()) return null
        val groupRef = db.collection("groups").document(groupId)
        return try {
            val avatarDoc = groupRef.collection("avatars").document(studentId).get().await()
            val fromAvatarCol = avatarDoc.getString("avatarBase64")?.trim().orEmpty()
            if (fromAvatarCol.isNotBlank()) {
                fromAvatarCol
            } else {
                // Fallback for any unmigrated legacy student document
                val stuDoc = groupRef.collection("students").document(studentId).get().await()
                stuDoc.getString("avatarBase64")?.trim()?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Background helper to migrate any inline `Student.avatarBase64` strings into
     * `groups/{groupId}/avatars/{studentId}` and clear them from `groups/{groupId}/students/{studentId}`.
     */
    suspend fun migrateInlineStudentAvatars(
        groupId: String,
        studentsWithInlineAvatars: List<Student>,
        memberUids: List<String>
    ): Result<Int> = runCatching {
        if (groupId.isBlank() || studentsWithInlineAvatars.isEmpty()) return@runCatching 0
        val uid = auth.currentUser?.uid ?: return@runCatching 0
        val groupRef = db.collection("groups").document(groupId)
        val safeMembers = memberUids.ifEmpty { listOf(uid) }.distinct().take(MAX_SERVANTS_PER_GROUP)
        var migrated = 0
        studentsWithInlineAvatars.chunked(20).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { stu ->
                val rawAvatar = stu.avatarBase64.trim().take(StudentAvatarImageHelper.MAX_FIRESTORE_AVATAR_CHARS)
                if (rawAvatar.isNotBlank()) {
                    val hash = Student.computeAvatarHash(rawAvatar)
                    val avatarRef = groupRef.collection("avatars").document(stu.id)
                    val stuRef = groupRef.collection("students").document(stu.id)
                    batch.set(
                        avatarRef,
                        mapOf(
                            "groupId" to groupId,
                            "studentId" to stu.id,
                            "avatarBase64" to rawAvatar,
                            "avatarHash" to hash,
                            "updatedByUid" to uid,
                            "memberUids" to safeMembers,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    batch.update(
                        stuRef,
                        mapOf(
                            "avatarBase64" to "",
                            "hasAvatar" to true,
                            "avatarHash" to hash,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    migrated++
                }
            }
            batch.commit().await()
        }
        migrated
    }

    suspend fun deleteStudent(groupId: String, studentId: String): Result<Unit> = runCatching {
        requireUserId()
        val groupRef = db.collection("groups").document(groupId)
        val docRef = groupRef.collection("students").document(studentId)
        val avatarRef = groupRef.collection("avatars").document(studentId)
        try {
            val batch = db.batch()
            batch.delete(docRef)
            batch.delete(avatarRef)
            batch.commit().await()
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
        return flow {
            val eventsById = LinkedHashMap<String, CardEvent>()
            var hasEmittedInitial = false
            db.collection("groups").document(groupId).collection("cardEvents")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .collect { snapshot ->
                    val cur = pendingWritesBySource.value
                    pendingWritesBySource.value = cur.copy(third = snapshot.metadata.hasPendingWrites())
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    CardEvent::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                                eventsById[docId] = parsed
                            }
                            DocumentChange.Type.REMOVED -> {
                                eventsById.remove(docId)
                            }
                        }
                    }
                    emit(
                        eventsById.values.sortedByDescending { it.createdAt?.seconds ?: 0L }
                    )
                }
        }.catch { error ->
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

    /**
     * One-shot `get()` query for `cardEvents` within `[startMillis, endMillis]` by `createdAt`
     * when the servant selects a custom date range outside the current month.
     * - Does NOT attach a live listener.
     * - Queries `groups/{groupId}/cardEvents` directly by `createdAt` range so it uses Firestore's
     *   automatic single-field index on `createdAt` without requiring a manual composite index.
     * - Stores the result in [LocalClassroomCache] so repeated selections of historical periods
     *   return in 0ms without re-querying Firestore.
     */
    suspend fun fetchCardEventsForDateRange(
        groupId: String,
        startMillis: Long,
        endMillis: Long
    ): Result<List<CardEvent>> = runCatching {
        if (groupId.isBlank()) return@runCatching emptyList()
        LocalClassroomCache.getCachedHistoricalRangeEvents(groupId, startMillis, endMillis)?.let { cached ->
            return@runCatching cached
        }
        val path = "groups/$groupId/cardEvents"
        val startTimestamp = Timestamp(Date(startMillis))
        val endTimestamp = Timestamp(Date(endMillis))
        try {
            val snapshot = db.collection("groups").document(groupId).collection("cardEvents")
                .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
                .whereLessThanOrEqualTo("createdAt", endTimestamp)
                .get()
                .await()
            val events = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CardEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.sortedByDescending { it.effectiveTimestampMillis() }
            LocalClassroomCache.saveCachedHistoricalRangeEvents(groupId, startMillis, endMillis, events)
            events
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    fun observeAllMonthlyBalancesForGroup(
        groupId: String,
        @Suppress("UNUSED_PARAMETER") currentUserId: String = ""
    ): Flow<List<MonthlyBalance>> {
        val path = "groups/$groupId/monthlyBalances"
        return flow {
            val balancesById = LinkedHashMap<String, MonthlyBalance>()
            var hasEmittedInitial = false
            db.collection("groups").document(groupId).collection("monthlyBalances")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .collect { snapshot ->
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    MonthlyBalance::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                                balancesById[docId] = parsed
                            }
                            DocumentChange.Type.REMOVED -> {
                                balancesById.remove(docId)
                            }
                        }
                    }
                    emit(balancesById.values.toList())
                }
        }.catch { error ->
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
        @Suppress("UNUSED_PARAMETER") groupId: String,
        @Suppress("UNUSED_PARAMETER") currentUserId: String = ""
    ): Flow<Boolean> {
        return hasPendingWritesFlow
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
        val safeMembers = memberUids.distinct().take(MAX_SERVANTS_PER_GROUP)

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

    // --- Smart Swipe Attendance (groups/{groupId}/attendance/{weekKey}_{studentId}) ---

    fun observeWeeklyAttendance(
        groupId: String,
        weekKey: String
    ): Flow<List<AttendanceRecord>> {
        if (groupId.isBlank() || weekKey.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        val path = "groups/$groupId/attendance"
        return flow {
            val recordsById = LinkedHashMap<String, AttendanceRecord>()
            var hasEmittedInitial = false
            db.collection("groups")
                .document(groupId)
                .collection("attendance")
                .whereEqualTo("weekKey", weekKey)
                .snapshots()
                .collect { snapshot ->
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    AttendanceRecord::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                                recordsById[docId] = parsed.copy(id = docId)
                            }
                            DocumentChange.Type.REMOVED -> {
                                recordsById.remove(docId)
                            }
                        }
                    }
                    emit(recordsById.values.toList())
                }
        }.catch { error ->
            if (error is Exception && auth.currentUser != null) {
                handleFirestoreError(error, OperationType.LIST, path)
            }
            emit(emptyList())
        }
    }

    suspend fun upsertAttendanceRecord(
        groupId: String,
        student: Student,
        weekKey: String,
        status: com.example.data.model.AttendanceStatus,
        servantName: String,
        note: String = "",
        memberUids: List<String>,
        existingRecord: AttendanceRecord? = null,
        waitForServerAck: Boolean = false
    ): Result<AttendanceRecord> = runCatching {
        val uid = requireUserId()
        val safeGroupId = groupId.trim()
        val safeWeekKey = weekKey.trim()
        require(safeGroupId.isNotBlank() && safeWeekKey.isNotBlank() && student.id.isNotBlank()) {
            "بيانات التحضير غير مكتملة"
        }
        val docId = AttendanceRecord.buildDocId(safeWeekKey, student.id)
        val docRef = db.collection("groups")
            .document(safeGroupId)
            .collection("attendance")
            .document(docId)

        val safeServantName = servantName.trim().ifBlank { "خادم" }.take(80)
        val safeNote = note.trim().take(AttendanceRecord.MAX_NOTE_LENGTH)
        val safeMembers = (memberUids + uid).filter { it.isNotBlank() }.distinct().take(MAX_SERVANTS_PER_GROUP)
        val createdAtVal: Any = existingRecord?.createdAt ?: FieldValue.serverTimestamp()

        val payload = mapOf(
            "groupId" to safeGroupId,
            "studentId" to student.id,
            "studentName" to student.name.trim().take(100),
            "status" to status.id,
            "weekKey" to safeWeekKey,
            "servantId" to uid,
            "servantName" to safeServantName,
            "note" to safeNote,
            "memberUids" to safeMembers,
            "createdAt" to createdAtVal,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val writeTask = docRef.set(payload)
            if (waitForServerAck) {
                writeTask.await()
            }
            val nowTs = Timestamp.now()
            AttendanceRecord(
                id = docId,
                groupId = safeGroupId,
                studentId = student.id,
                studentName = student.name.trim().take(100),
                status = status.id,
                weekKey = safeWeekKey,
                servantId = uid,
                servantName = safeServantName,
                note = safeNote,
                memberUids = safeMembers,
                createdAt = existingRecord?.createdAt ?: nowTs,
                updatedAt = nowTs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAttendanceRecord(
        groupId: String,
        docId: String,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups")
            .document(groupId.trim())
            .collection("attendance")
            .document(docId.trim())
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

    // --- 8. Shared Child Notes (Comments, Memorization Progress, Visitation Follow-ups) ---

    fun observeChildNotes(groupId: String): Flow<List<ChildNote>> {
        if (groupId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val path = "groups/$groupId/childNotes"
        return flow {
            val notesById = LinkedHashMap<String, ChildNote>()
            var hasEmittedInitial = false
            db.collection("groups")
                .document(groupId)
                .collection("childNotes")
                .whereEqualTo("groupId", groupId)
                .snapshots()
                .collect { snapshot ->
                    if (hasEmittedInitial && snapshot.documentChanges.isEmpty()) {
                        return@collect
                    }
                    hasEmittedInitial = true
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    ChildNote::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                )
                                notesById[docId] = parsed.copy(id = docId)
                            }
                            DocumentChange.Type.REMOVED -> {
                                notesById.remove(docId)
                            }
                        }
                    }
                    emit(notesById.values.sortedByDescending { it.effectiveTimeMillis })
                }
        }.catch { error ->
            if (error is Exception && auth.currentUser != null) {
                handleFirestoreError(error, OperationType.LIST, path)
            }
            emit(emptyList())
        }
    }

    suspend fun addChildNote(
        groupId: String,
        student: Student,
        type: ChildNoteType,
        text: String,
        status: String = "",
        authorName: String,
        memberUids: List<String>,
        waitForServerAck: Boolean = false
    ): Result<ChildNote> = runCatching {
        val uid = requireUserId()
        val safeGroupId = groupId.trim()
        val cleanText = text.trim().take(ChildNote.MAX_TEXT_LENGTH)
        require(safeGroupId.isNotBlank() && student.id.isNotBlank() && cleanText.isNotBlank()) {
            "بيانات الملاحظة غير مكتملة"
        }
        val safeAuthor = authorName.trim().ifBlank { "خادم" }.take(80)
        val safeStatus = status.trim().take(30)
        val safeMembers = (memberUids + uid).filter { it.isNotBlank() }.distinct().take(MAX_SERVANTS_PER_GROUP)
        val nowMs = System.currentTimeMillis()
        val noteId = "note_${nowMs}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val groupRef = db.collection("groups").document(safeGroupId)
        val noteRef = groupRef.collection("childNotes").document(noteId)
        val batch = db.batch()
        batch.set(
            noteRef,
            mapOf(
                "groupId" to safeGroupId,
                "studentId" to student.id,
                "studentName" to student.name.trim().take(100),
                "type" to type.id,
                "text" to cleanText,
                "status" to safeStatus,
                "authorUid" to uid,
                "authorName" to safeAuthor,
                "clientCreatedAtMillis" to nowMs,
                "memberUids" to safeMembers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        if (type == ChildNoteType.MEMORIZATION) {
            batch.update(
                groupRef.collection("students").document(student.id),
                mapOf(
                    "lastMemorizedText" to cleanText,
                    "lastMemorizedBy" to safeAuthor,
                    "lastMemorizedAtMillis" to nowMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        val task = batch.commit()
        if (waitForServerAck) {
            task.await()
        }
        ChildNote(
            id = noteId,
            groupId = safeGroupId,
            studentId = student.id,
            studentName = student.name.trim().take(100),
            type = type.id,
            text = cleanText,
            status = safeStatus,
            authorUid = uid,
            authorName = safeAuthor,
            clientCreatedAtMillis = nowMs,
            memberUids = safeMembers,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    }

    suspend fun deleteChildNote(
        groupId: String,
        noteId: String,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("groups")
            .document(groupId.trim())
            .collection("childNotes")
            .document(noteId.trim())
        val task = docRef.delete()
        if (waitForServerAck) {
            task.await()
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

