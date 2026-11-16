package com.example.data.repository

import android.util.Log
import com.example.data.model.ActiveChatRoom
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.DmChatMeta
import com.example.data.model.GroupChatMeta
import com.example.data.model.MAX_SERVANTS_PER_GROUP
import com.example.data.model.PreparedChatMediaPayload
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Spark-tier optimized Firestore repository for in-app group & private messaging:
 * - Group chat metadata: `groups/{groupId}/chatMeta/main` (1 document listener)
 * - Private chats metadata: `dms/{chatId}` queried by `participants array-contains currentUid`
 * - Messages: `groups/{groupId}/messages/{id}` & `dms/{chatId}/messages/{id}`
 *   Loaded ONLY for the currently opened conversation, bounded to the latest 30 messages (`limit(30)`),
 *   with older messages fetched on demand via cursor pagination (`startAfter`).
 * - Offline-first writes: all write operations queue locally and sync automatically when online.
 */
class ChatRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "ChatRepository"
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: throw IllegalStateException("المستخدم غير مسجل الدخول")
    }

    /**
     * Observes the single `groups/{groupId}/chatMeta/main` document for the active group.
     * Zero listener on the messages collection when the chat room is closed.
     */
    fun observeGroupChatMeta(groupId: String): Flow<GroupChatMeta?> {
        if (groupId.isBlank()) return flow { emit(null) }
        val docRef = db.collection("groups").document(groupId)
            .collection("chatMeta").document("main")
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        GroupChatMeta::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.copy(id = snapshot.id, groupId = groupId)
                } else {
                    null
                }
            }
            .catch { err ->
                Log.w(TAG, "observeGroupChatMeta($groupId) error: ${err.message}")
                emit(null)
            }
    }

    /**
     * Observes all private DM chat metadata documents (`dms/{chatId}`) where `participants` contains `currentUid`.
     */
    fun observeUserDmMetas(currentUid: String, activeGroupId: String): Flow<List<DmChatMeta>> {
        if (currentUid.isBlank()) return flow { emit(emptyList()) }
        return flow {
            val metasById = LinkedHashMap<String, DmChatMeta>()
            db.collection("dms")
                .whereArrayContains("participants", currentUid)
                .snapshots()
                .collect { snapshot ->
                    for (change in snapshot.documentChanges) {
                        val docId = change.document.id
                        when (change.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                val parsed = change.document.toObject(
                                    DmChatMeta::class.java,
                                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                ).copy(id = docId, chatId = docId)
                                metasById[docId] = parsed
                            }
                            DocumentChange.Type.REMOVED -> {
                                metasById.remove(docId)
                            }
                        }
                    }
                    val filtered = metasById.values
                        .filter { activeGroupId.isBlank() || it.groupId.isBlank() || it.groupId == activeGroupId }
                        .sortedByDescending { it.lastMessageAt?.toDate()?.time ?: it.updatedAt?.toDate()?.time ?: 0L }
                    emit(filtered)
                }
        }.catch { err ->
            Log.w(TAG, "observeUserDmMetas($currentUid) error: ${err.message}")
            emit(emptyList())
        }
    }

    /**
     * Observes ONLY the latest [limit] (default 30) messages for the currently open chat room.
     * Detached immediately when the user leaves the conversation screen.
     */
    fun observeLatestRoomMessages(
        room: ActiveChatRoom,
        limit: Int = ChatMessage.PAGE_SIZE
    ): Flow<List<ChatMessage>> {
        val colRef = when (room) {
            is ActiveChatRoom.GroupRoom -> db.collection("groups").document(room.groupId).collection("messages")
            is ActiveChatRoom.DirectRoom -> db.collection("dms").document(room.chatId).collection("messages")
        }
        return colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val parsed = doc.toObject(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    parsed.copy(
                        id = doc.id,
                        isPendingOffline = doc.metadata.hasPendingWrites()
                    )
                }.sortedBy { it.effectiveTimeMillis }
            }
            .catch { err ->
                Log.w(TAG, "observeLatestRoomMessages(${room.roomKey}) error: ${err.message}")
                emit(emptyList())
            }
    }

    /**
     * One-shot query for older messages (30 at a time) before [oldestCreatedAt] when the user scrolls up.
     */
    suspend fun fetchOlderMessages(
        room: ActiveChatRoom,
        oldestCreatedAt: Timestamp,
        limit: Int = ChatMessage.PAGE_SIZE
    ): Result<List<ChatMessage>> = runCatching {
        val colRef = when (room) {
            is ActiveChatRoom.GroupRoom -> db.collection("groups").document(room.groupId).collection("messages")
            is ActiveChatRoom.DirectRoom -> db.collection("dms").document(room.chatId).collection("messages")
        }
        val snapshot = colRef
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(oldestCreatedAt)
            .limit(limit.toLong())
            .get()
            .await()

        snapshot.documents.mapNotNull { doc ->
            val parsed = doc.toObject(
                ChatMessage::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return@mapNotNull null
            parsed.copy(
                id = doc.id,
                isPendingOffline = doc.metadata.hasPendingWrites()
            )
        }.sortedBy { it.effectiveTimeMillis }
    }

    /**
     * Sends a message (`TEXT`, `IMAGE`, `AUDIO`, or `SYSTEM`) to the group room (`groups/{groupId}/messages/{messageId}`)
     * and atomically updates `groups/{groupId}/chatMeta/main` with `FieldValue.increment(1)`.
     * Works offline and syncs automatically when connectivity returns.
     */
    suspend fun sendGroupMessage(
        groupId: String,
        senderName: String,
        text: String,
        memberUids: List<String>,
        type: ChatMessageType = ChatMessageType.TEXT,
        mediaPayload: PreparedChatMediaPayload? = null,
        waitForServerAck: Boolean = false
    ): Result<ChatMessage> = runCatching {
        val uid = requireUserId()
        require(groupId.isNotBlank()) { "المجموعة غير محددة" }
        val finalType = mediaPayload?.type ?: type
        val maxLen = if (finalType == ChatMessageType.SYSTEM) {
            ChatMessage.MAX_SYSTEM_TEXT_LENGTH
        } else {
            ChatMessage.MAX_TEXT_LENGTH
        }
        val cleanText = text.trim().take(maxLen).ifBlank {
            mediaPayload?.textLabel.orEmpty()
        }
        require(cleanText.isNotBlank()) { "نص الرسالة فارغ" }

        val previewText = if (cleanText.startsWith(ChatMessage.ATTENDANCE_REPORT_V2_PREFIX)) {
            cleanText
                .removePrefix(ChatMessage.ATTENDANCE_REPORT_V2_PREFIX)
                .trim()
                .lineSequence()
                .firstOrNull()
                ?.trim()
                ?.take(200)
                ?.ifBlank { "📋 تقرير تحضير الفصل" }
                ?: "📋 تقرير تحضير الفصل"
        } else {
            cleanText.take(200)
        }

        val safeSenderName = senderName.trim().ifBlank { "خادم" }.take(100)
        val safeMembers = (memberUids + uid).filter { it.isNotBlank() }.distinct().take(MAX_SERVANTS_PER_GROUP)
        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val groupRef = db.collection("groups").document(groupId)
        val msgRef = groupRef.collection("messages").document(msgId)
        val metaRef = groupRef.collection("chatMeta").document("main")

        try {
            val batch = db.batch()
            val msgPayload = HashMap<String, Any>().apply {
                put("chatId", "group_$groupId")
                put("groupId", groupId)
                put("senderUid", uid)
                put("senderName", safeSenderName)
                put("type", finalType.id)
                put("text", cleanText)
                put("isDeleted", false)
                put("memberUids", safeMembers)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
                if (mediaPayload != null) {
                    put("mediaBase64", mediaPayload.mediaBase64)
                    put("mediaUrl", mediaPayload.mediaUrl)
                    put("mediaMimeType", mediaPayload.mediaMimeType)
                    put("imageWidth", mediaPayload.imageWidth)
                    put("imageHeight", mediaPayload.imageHeight)
                    put("audioDurationSec", mediaPayload.audioDurationSec)
                }
            }
            batch.set(msgRef, msgPayload)

            val metaPayload = mapOf(
                "groupId" to groupId,
                "lastMessageId" to msgId,
                "lastMessageText" to previewText,
                "lastMessageType" to finalType.id,
                "lastMessageSenderUid" to uid,
                "lastMessageSenderName" to safeSenderName,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "totalMessagesCount" to FieldValue.increment(1L),
                "memberUids" to safeMembers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(metaRef, metaPayload, SetOptions.merge())

            val task = batch.commit()
            if (waitForServerAck) {
                task.await()
            }

            val now = Timestamp.now()
            ChatMessage(
                id = msgId,
                chatId = "group_$groupId",
                groupId = groupId,
                senderUid = uid,
                senderName = safeSenderName,
                type = finalType.id,
                text = cleanText,
                isDeleted = false,
                mediaBase64 = mediaPayload?.mediaBase64.orEmpty(),
                mediaUrl = mediaPayload?.mediaUrl.orEmpty(),
                mediaMimeType = mediaPayload?.mediaMimeType.orEmpty(),
                imageWidth = mediaPayload?.imageWidth ?: 0,
                imageHeight = mediaPayload?.imageHeight ?: 0,
                audioDurationSec = mediaPayload?.audioDurationSec ?: 0,
                memberUids = safeMembers,
                createdAt = now,
                updatedAt = now,
                isPendingOffline = true,
                clientCreatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Sends a message (`TEXT`, `IMAGE`, `AUDIO`, or `SYSTEM`) to a private DM room (`dms/{chatId}/messages/{messageId}`)
     * where `chatId = dm_{uidA}_{uidB}` sorted, and atomically updates `dms/{chatId}` with
     * `participants = [uidA, uidB]` and `FieldValue.increment(1)`.
     */
    suspend fun sendDirectMessage(
        groupId: String,
        peerUid: String,
        senderName: String,
        peerName: String,
        text: String,
        type: ChatMessageType = ChatMessageType.TEXT,
        mediaPayload: PreparedChatMediaPayload? = null,
        waitForServerAck: Boolean = false
    ): Result<ChatMessage> = runCatching {
        val uid = requireUserId()
        require(peerUid.isNotBlank() && peerUid != uid) { "الخادم الآخر غير محدد" }
        val finalType = mediaPayload?.type ?: type
        val cleanText = text.trim().take(ChatMessage.MAX_TEXT_LENGTH).ifBlank {
            mediaPayload?.textLabel.orEmpty()
        }
        require(cleanText.isNotBlank()) { "نص الرسالة فارغ" }

        val sortedParticipants = listOf(uid, peerUid.trim()).sorted()
        val chatId = DmChatMeta.buildDmChatId(uid, peerUid)
        val safeSenderName = senderName.trim().ifBlank { "خادم" }.take(100)
        val safePeerName = peerName.trim().ifBlank { "خادم" }.take(100)
        val msgId = "msg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(8)}"

        val dmRef = db.collection("dms").document(chatId)
        val msgRef = dmRef.collection("messages").document(msgId)

        try {
            val batch = db.batch()
            val msgPayload = HashMap<String, Any>().apply {
                put("chatId", chatId)
                put("groupId", groupId)
                put("senderUid", uid)
                put("senderName", safeSenderName)
                put("type", finalType.id)
                put("text", cleanText)
                put("isDeleted", false)
                put("participants", sortedParticipants)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
                if (mediaPayload != null) {
                    put("mediaBase64", mediaPayload.mediaBase64)
                    put("mediaUrl", mediaPayload.mediaUrl)
                    put("mediaMimeType", mediaPayload.mediaMimeType)
                    put("imageWidth", mediaPayload.imageWidth)
                    put("imageHeight", mediaPayload.imageHeight)
                    put("audioDurationSec", mediaPayload.audioDurationSec)
                }
            }
            batch.set(msgRef, msgPayload)

            val metaPayload = mapOf(
                "chatId" to chatId,
                "groupId" to groupId,
                "participants" to sortedParticipants,
                "participantNames" to mapOf(
                    uid to safeSenderName,
                    peerUid.trim() to safePeerName
                ),
                "lastMessageId" to msgId,
                "lastMessageText" to cleanText.take(200),
                "lastMessageType" to finalType.id,
                "lastMessageSenderUid" to uid,
                "lastMessageSenderName" to safeSenderName,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "totalMessagesCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(dmRef, metaPayload, SetOptions.merge())

            val task = batch.commit()
            if (waitForServerAck) {
                task.await()
            }

            val now = Timestamp.now()
            ChatMessage(
                id = msgId,
                chatId = chatId,
                groupId = groupId,
                senderUid = uid,
                senderName = safeSenderName,
                type = finalType.id,
                text = cleanText,
                isDeleted = false,
                mediaBase64 = mediaPayload?.mediaBase64.orEmpty(),
                mediaUrl = mediaPayload?.mediaUrl.orEmpty(),
                mediaMimeType = mediaPayload?.mediaMimeType.orEmpty(),
                imageWidth = mediaPayload?.imageWidth ?: 0,
                imageHeight = mediaPayload?.imageHeight ?: 0,
                audioDurationSec = mediaPayload?.audioDurationSec ?: 0,
                participants = sortedParticipants,
                createdAt = now,
                updatedAt = now,
                isPendingOffline = true,
                clientCreatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Soft-deletes a message written by the current user (`senderUid == currentUid`),
     * turning its text into `"تم حذف الرسالة"`, `isDeleted = true`, and clearing `mediaBase64`.
     */
    suspend fun deleteOwnMessage(
        room: ActiveChatRoom,
        message: ChatMessage,
        isLatestRoomMessage: Boolean = false,
        waitForServerAck: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(message.senderUid == uid) { "يمكنك حذف رسائلك فقط" }
        require(message.id.isNotBlank()) { "معرف الرسالة غير صالح" }

        val batch = db.batch()
        when (room) {
            is ActiveChatRoom.GroupRoom -> {
                val groupRef = db.collection("groups").document(room.groupId)
                val msgRef = groupRef.collection("messages").document(message.id)
                batch.update(
                    msgRef,
                    mapOf(
                        "isDeleted" to true,
                        "text" to ChatMessage.DELETED_MESSAGE_PLACEHOLDER,
                        "mediaBase64" to "",
                        "mediaUrl" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (isLatestRoomMessage) {
                    val metaRef = groupRef.collection("chatMeta").document("main")
                    batch.set(
                        metaRef,
                        mapOf(
                            "lastMessageText" to ChatMessage.DELETED_MESSAGE_PLACEHOLDER,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
            }
            is ActiveChatRoom.DirectRoom -> {
                val dmRef = db.collection("dms").document(room.chatId)
                val msgRef = dmRef.collection("messages").document(message.id)
                batch.update(
                    msgRef,
                    mapOf(
                        "isDeleted" to true,
                        "text" to ChatMessage.DELETED_MESSAGE_PLACEHOLDER,
                        "mediaBase64" to "",
                        "mediaUrl" to "",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (isLatestRoomMessage) {
                    batch.set(
                        dmRef,
                        mapOf(
                            "lastMessageText" to ChatMessage.DELETED_MESSAGE_PLACEHOLDER,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
            }
        }

        val task = batch.commit()
        if (waitForServerAck) {
            task.await()
        }
    }
}
