package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cache.LocalClassroomCache
import com.example.data.media.ChatMediaStorageProvider
import com.example.data.media.InlineFirestoreChatMediaStorageProvider
import com.example.data.model.ActiveChatRoom
import com.example.data.model.ChatFormatHelper
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.DmChatMeta
import com.example.data.model.Group
import com.example.data.model.GroupChatMeta
import com.example.data.model.NotificationCategory
import com.example.data.model.PreparedChatMediaPayload
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.repository.ChatRepository
import com.example.data.repository.KamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class GroupFellowServantItem(
    val uid: String,
    val displayName: String,
    val memberProfile: ServantMemberProfile?
)

@Immutable
data class ChatUiState(
    val activeGroupId: String = "",
    val activeGroupName: String = "",
    val activeGroupAvatarBase64: String = "",
    val activeGroupAvatarHash: String = "",
    val groupMemberUids: List<String> = emptyList(),
    val fellowServants: List<GroupFellowServantItem> = emptyList(),
    val servantAliases: Map<String, String> = emptyMap(),
    val groupChatMeta: GroupChatMeta? = null,
    val groupUnreadCount: Int = 0,
    val dmConversations: List<DmChatMeta> = emptyList(),
    val dmUnreadCounts: Map<String, Int> = emptyMap(),
    val totalUnreadCount: Int = 0,
    val activeRoom: ActiveChatRoom? = null,
    val activeRoomMessages: List<ChatMessage> = emptyList(),
    val isLoadingOlderMessages: Boolean = false,
    val canLoadOlderMessages: Boolean = false,
    val isSendingMedia: Boolean = false,
    val mediaErrorMessage: String? = null,
    val isLookingUpEmail: Boolean = false,
    val emailLookupError: String? = null,
    val forwardStatusMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val chatRepository: ChatRepository,
    private val kamRepository: KamRepository,
    val mediaStorage: ChatMediaStorageProvider = InlineFirestoreChatMediaStorageProvider
) : ViewModel() {

    private data class ClassroomChatContext(
        val groupId: String = "",
        val groupName: String = "",
        val groupAvatarBase64: String = "",
        val groupAvatarHash: String = "",
        val adminId: String = "",
        val adminName: String = "",
        val memberUids: List<String> = emptyList(),
        val memberProfiles: List<ServantMemberProfile> = emptyList(),
        val blocks: List<ServantBlock> = emptyList(),
        val ownDisplayName: String = "",
        val servantAliases: Map<String, String> = emptyMap()
    )

    private val _classroomContext = MutableStateFlow(
        ClassroomChatContext(ownDisplayName = initialDisplayName.ifBlank { "خادم" })
    )
    private val _activeRoom = MutableStateFlow<ActiveChatRoom?>(null)
    private val _localReadCounts = MutableStateFlow(
        LocalClassroomCache.getMemoryChatReadCounts(currentUserId)
    )
    private val _olderMessagesByRoom = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val _optimisticMessagesByRoom = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val _optimisticDeletedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoadingOlder = MutableStateFlow(false)
    private val _exhaustedOlderRooms = MutableStateFlow<Set<String>>(emptySet())
    private val _isSendingMedia = MutableStateFlow(false)
    private val _mediaErrorMessage = MutableStateFlow<String?>(null)
    private val _isLookingUpEmail = MutableStateFlow(false)
    private val _emailLookupError = MutableStateFlow<String?>(null)
    private val _forwardStatusMessage = MutableStateFlow<String?>(null)

    private val lastSeenWriteMillis = AtomicLong(0L)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = LocalClassroomCache.loadChatReadCountsAsync(currentUserId)
            _localReadCounts.value = loaded
        }
    }

    private val activeGroupIdFlow: Flow<String> = _classroomContext
        .map { it.groupId }
        .distinctUntilChanged()

    private val groupChatMetaFlow: Flow<GroupChatMeta?> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (groupId.isBlank()) {
            flowOf(null)
        } else {
            chatRepository.observeGroupChatMeta(groupId)
        }
    }

    private val dmMetasFlow: Flow<List<DmChatMeta>> = activeGroupIdFlow.flatMapLatest { groupId ->
        if (currentUserId.isBlank()) {
            flowOf(emptyList())
        } else {
            chatRepository.observeUserDmMetas(currentUserId, groupId)
        }
    }

    private val latestRoomMessagesFlow: Flow<List<ChatMessage>> = _activeRoom.flatMapLatest { room ->
        if (room == null) {
            flowOf(emptyList())
        } else {
            chatRepository.observeLatestRoomMessages(room, limit = ChatMessage.PAGE_SIZE)
        }
    }

    private val lookupStatusFlow = combine(
        _isLookingUpEmail,
        _emailLookupError,
        _forwardStatusMessage
    ) { lookingUp, lookupErr, fwdMsg ->
        Triple(lookingUp, lookupErr, fwdMsg)
    }

    val chatState: StateFlow<ChatUiState> = combine(
        combine(_classroomContext, groupChatMetaFlow, dmMetasFlow, _localReadCounts) { ctx, groupMeta, dmMetas, readCounts ->
            arrayOf<Any?>(ctx, groupMeta, dmMetas, readCounts)
        },
        combine(_activeRoom, latestRoomMessagesFlow, _olderMessagesByRoom, _optimisticMessagesByRoom, _optimisticDeletedIds) { room, latestMsgs, olderMap, optMap, deletedIds ->
            arrayOf<Any?>(room, latestMsgs, olderMap, optMap, deletedIds)
        },
        combine(_isLoadingOlder, _exhaustedOlderRooms, _isSendingMedia, _mediaErrorMessage, lookupStatusFlow) { loadingOlder, exhaustedRooms, sendingMedia, mediaError, lookupStatus ->
            arrayOf<Any?>(loadingOlder, exhaustedRooms, sendingMedia, mediaError, lookupStatus)
        }
    ) { metaPart, roomPart, pagingPart ->
        @Suppress("UNCHECKED_CAST")
        val ctx = metaPart[0] as ClassroomChatContext
        val groupMeta = metaPart[1] as GroupChatMeta?
        @Suppress("UNCHECKED_CAST")
        val dmMetas = metaPart[2] as List<DmChatMeta>
        @Suppress("UNCHECKED_CAST")
        val readCounts = metaPart[3] as Map<String, Long>

        val activeRoom = roomPart[0] as ActiveChatRoom?
        @Suppress("UNCHECKED_CAST")
        val latestCloudMsgs = roomPart[1] as List<ChatMessage>
        @Suppress("UNCHECKED_CAST")
        val olderMap = roomPart[2] as Map<String, List<ChatMessage>>
        @Suppress("UNCHECKED_CAST")
        val optMap = roomPart[3] as Map<String, List<ChatMessage>>
        @Suppress("UNCHECKED_CAST")
        val deletedIds = roomPart[4] as Set<String>

        val isLoadingOlder = pagingPart[0] as Boolean
        @Suppress("UNCHECKED_CAST")
        val exhaustedRooms = pagingPart[1] as Set<String>
        val isSendingMedia = pagingPart[2] as Boolean
        val mediaErrorMessage = pagingPart[3] as String?
        @Suppress("UNCHECKED_CAST")
        val lookupStatus = pagingPart[4] as Triple<Boolean, String?, String?>

        val groupRoomKey = "group_${ctx.groupId}"
        val groupTotal = groupMeta?.totalMessagesCount ?: 0L
        val groupRead = readCounts[groupRoomKey] ?: 0L

        val effectiveGroupUnread = if (
            ctx.groupId.isBlank() ||
            activeRoom?.roomKey == groupRoomKey ||
            (groupMeta != null && groupMeta.lastMessageSenderUid == currentUserId && groupTotal <= groupRead + 1L)
        ) {
            if (groupTotal > groupRead && (activeRoom?.roomKey == groupRoomKey || groupMeta?.lastMessageSenderUid == currentUserId)) {
                markRoomReadCountInternal(groupRoomKey, groupTotal)
            }
            0
        } else {
            (groupTotal - groupRead).coerceAtLeast(0L).toInt()
        }

        val dmUnreadMap = HashMap<String, Int>(dmMetas.size)
        var dmUnreadSum = 0
        for (dm in dmMetas) {
            val cId = dm.effectiveChatId
            val total = dm.totalMessagesCount
            val read = readCounts[cId] ?: 0L
            val unread = if (
                activeRoom?.roomKey == cId ||
                (dm.lastMessageSenderUid == currentUserId && total <= read + 1L)
            ) {
                if (total > read && (activeRoom?.roomKey == cId || dm.lastMessageSenderUid == currentUserId)) {
                    markRoomReadCountInternal(cId, total)
                }
                0
            } else {
                (total - read).coerceAtLeast(0L).toInt()
            }
            dmUnreadMap[cId] = unread
            dmUnreadSum += unread
        }

        val profileByUid = ctx.memberProfiles.associateBy { it.effectiveUid }
        val blockNameByUid = ctx.blocks
            .filter { it.servantId.isNotBlank() && it.servantName.isNotBlank() }
            .associate { it.servantId to it.servantName }

        val allCandidateUids = (ctx.memberUids + profileByUid.keys + blockNameByUid.keys)
            .filter { it.isNotBlank() && it != currentUserId }
            .distinct()

        val fellowServants = allCandidateUids.map { uid ->
            val memberProf = profileByUid[uid]
            val alias = ctx.servantAliases[uid]?.takeIf { it.isNotBlank() }
            val name = alias
                ?: memberProf?.displayName?.takeIf { it.isNotBlank() }
                ?: blockNameByUid[uid]?.takeIf { it.isNotBlank() }
                ?: (if (uid == ctx.adminId && ctx.adminName.isNotBlank()) ctx.adminName else "خادم")
            GroupFellowServantItem(
                uid = uid,
                displayName = name,
                memberProfile = memberProf
            )
        }.sortedBy { it.displayName }

        val mergedRoomMessages = if (activeRoom == null) {
            emptyList()
        } else {
            val rKey = activeRoom.roomKey
            val older = olderMap[rKey].orEmpty()
            val optimistic = optMap[rKey].orEmpty()
            val byId = LinkedHashMap<String, ChatMessage>()
            for (m in older) {
                byId[m.id] = applyOptimisticDelete(m, deletedIds)
            }
            for (m in latestCloudMsgs) {
                byId[m.id] = applyOptimisticDelete(m, deletedIds)
            }
            for (m in optimistic) {
                if (!byId.containsKey(m.id)) {
                    byId[m.id] = applyOptimisticDelete(m, deletedIds)
                }
            }
            byId.values.sortedWith(
                compareBy<ChatMessage> { it.effectiveTimeMillis }.thenBy { it.id }
            )
        }

        val canLoadOlder = activeRoom != null &&
            activeRoom.roomKey !in exhaustedRooms &&
            latestCloudMsgs.size >= ChatMessage.PAGE_SIZE

        ChatUiState(
            activeGroupId = ctx.groupId,
            activeGroupName = ctx.groupName,
            activeGroupAvatarBase64 = ctx.groupAvatarBase64,
            activeGroupAvatarHash = ctx.groupAvatarHash,
            groupMemberUids = ctx.memberUids,
            fellowServants = fellowServants,
            servantAliases = ctx.servantAliases,
            groupChatMeta = groupMeta,
            groupUnreadCount = effectiveGroupUnread,
            dmConversations = dmMetas,
            dmUnreadCounts = dmUnreadMap,
            totalUnreadCount = effectiveGroupUnread + dmUnreadSum,
            activeRoom = activeRoom,
            activeRoomMessages = mergedRoomMessages,
            isLoadingOlderMessages = isLoadingOlder,
            canLoadOlderMessages = canLoadOlder,
            isSendingMedia = isSendingMedia,
            mediaErrorMessage = mediaErrorMessage,
            isLookingUpEmail = lookupStatus.first,
            emailLookupError = lookupStatus.second,
            forwardStatusMessage = lookupStatus.third
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ChatUiState()
        )

    private fun applyOptimisticDelete(msg: ChatMessage, deletedIds: Set<String>): ChatMessage {
        return if (msg.id in deletedIds && !msg.isDeleted) {
            msg.copy(
                isDeleted = true,
                text = ChatMessage.DELETED_MESSAGE_PLACEHOLDER,
                mediaBase64 = "",
                mediaUrl = ""
            )
        } else {
            msg
        }
    }

    private fun markRoomReadCountInternal(roomKey: String, count: Long) {
        if (count <= 0L) return
        val current = _localReadCounts.value[roomKey] ?: 0L
        if (count > current) {
            val updated = LocalClassroomCache.saveChatLastReadCount(currentUserId, roomKey, count)
            _localReadCounts.value = updated
        }
    }

    fun clearMediaError() {
        _mediaErrorMessage.value = null
    }

    fun clearEmailLookupError() {
        _emailLookupError.value = null
    }

    fun clearForwardStatus() {
        _forwardStatusMessage.value = null
    }

    fun syncWithClassroomState(
        group: Group?,
        memberProfiles: List<ServantMemberProfile>,
        blocks: List<ServantBlock>,
        ownDisplayName: String,
        servantAliases: Map<String, String> = emptyMap()
    ) {
        val groupId = group?.inviteCode.orEmpty()
        val prevGroupId = _classroomContext.value.groupId
        if (prevGroupId.isNotBlank() && prevGroupId != groupId) {
            _activeRoom.value = null
        }
        _classroomContext.value = ClassroomChatContext(
            groupId = groupId,
            groupName = group?.name?.ifBlank { "فصل مدارس الأحد" } ?: "فصل مدارس الأحد",
            groupAvatarBase64 = group?.avatarBase64.orEmpty(),
            groupAvatarHash = group?.avatarHash.orEmpty(),
            adminId = group?.adminId.orEmpty(),
            adminName = group?.adminName.orEmpty(),
            memberUids = group?.memberUids.orEmpty(),
            memberProfiles = memberProfiles,
            blocks = blocks,
            ownDisplayName = ownDisplayName.ifBlank { initialDisplayName.ifBlank { "خادم" } },
            servantAliases = servantAliases
        )
    }

    fun openGroupChat() {
        val ctx = _classroomContext.value
        if (ctx.groupId.isBlank()) return
        _mediaErrorMessage.value = null
        val room = ActiveChatRoom.GroupRoom(
            groupId = ctx.groupId,
            groupName = ctx.groupName
        )
        _activeRoom.value = room
        val total = chatState.value.groupChatMeta?.totalMessagesCount ?: 0L
        if (total > 0L) {
            markRoomReadCountInternal(room.roomKey, total)
        }
    }

    fun openDirectChat(peerUid: String, peerName: String) {
        val ctx = _classroomContext.value
        if (peerUid.isBlank() || peerUid == currentUserId) return
        _mediaErrorMessage.value = null
        val resolvedName = ctx.servantAliases[peerUid]?.takeIf { it.isNotBlank() }
            ?: peerName.ifBlank { "خادم" }
        val chatId = DmChatMeta.buildDmChatId(currentUserId, peerUid)
        val room = ActiveChatRoom.DirectRoom(
            chatId = chatId,
            groupId = ctx.groupId,
            peerUid = peerUid,
            peerName = resolvedName
        )
        _activeRoom.value = room
        val dmMeta = chatState.value.dmConversations.firstOrNull { it.effectiveChatId == chatId }
        val total = dmMeta?.totalMessagesCount ?: 0L
        if (total > 0L) {
            markRoomReadCountInternal(chatId, total)
        }
    }

    fun openDirectChatByEmail(rawEmail: String, onOpened: () -> Unit = {}) {
        val cleaned = rawEmail.trim()
        if (cleaned.isBlank() || !cleaned.contains("@")) {
            _emailLookupError.value = "يرجى إدخال بريد إلكتروني صحيح"
            return
        }
        _emailLookupError.value = null
        _isLookingUpEmail.value = true
        viewModelScope.launch(Dispatchers.IO) {
            kamRepository.lookupUserByEmail(cleaned)
                .onSuccess { entry ->
                    if (entry == null || entry.uid.isBlank()) {
                        _emailLookupError.value = "لا يوجد حساب مسجل بهذا البريد حتى الآن. اطلب من الشخص فتح التطبيق أولاً."
                    } else if (entry.uid == currentUserId) {
                        _emailLookupError.value = "لا يمكنك فتح محادثة مع نفسك"
                    } else {
                        val ctx = _classroomContext.value
                        val resolvedPeerName = ctx.servantAliases[entry.uid]?.takeIf { it.isNotBlank() }
                            ?: entry.displayName.ifBlank { "خادم" }
                        chatRepository.ensureDirectChatMeta(
                            peerUid = entry.uid,
                            senderName = ctx.ownDisplayName.ifBlank { "خادم" },
                            peerName = resolvedPeerName,
                            groupId = ctx.groupId,
                            waitForServerAck = false
                        )
                        openDirectChat(entry.uid, resolvedPeerName)
                        onOpened()
                    }
                }
                .onFailure { err ->
                    _emailLookupError.value = err.message ?: "تعذّر البحث بالبريد الإلكتروني"
                }
            _isLookingUpEmail.value = false
        }
    }

    fun closeActiveChat() {
        val room = _activeRoom.value
        if (room != null) {
            markActiveRoomAsRead(room)
        }
        _activeRoom.value = null
    }

    private fun markActiveRoomAsRead(room: ActiveChatRoom) {
        val state = chatState.value
        when (room) {
            is ActiveChatRoom.GroupRoom -> {
                val total = state.groupChatMeta?.totalMessagesCount ?: 0L
                if (total > 0L) markRoomReadCountInternal(room.roomKey, total)
            }
            is ActiveChatRoom.DirectRoom -> {
                val total = state.dmConversations.firstOrNull { it.effectiveChatId == room.chatId }?.totalMessagesCount ?: 0L
                if (total > 0L) markRoomReadCountInternal(room.roomKey, total)
            }
        }
    }

    fun loadOlderMessages() {
        val state = chatState.value
        val room = state.activeRoom ?: return
        if (_isLoadingOlder.value || room.roomKey in _exhaustedOlderRooms.value) return
        val oldestTimestamp = state.activeRoomMessages
            .mapNotNull { it.createdAt }
            .minByOrNull { it.toDate().time } ?: return

        _isLoadingOlder.value = true
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.fetchOlderMessages(
                room = room,
                oldestCreatedAt = oldestTimestamp,
                limit = ChatMessage.PAGE_SIZE
            ).onSuccess { olderBatch ->
                if (olderBatch.size < ChatMessage.PAGE_SIZE) {
                    _exhaustedOlderRooms.update { it + room.roomKey }
                }
                if (olderBatch.isNotEmpty()) {
                    _olderMessagesByRoom.update { currentMap ->
                        val existing = currentMap[room.roomKey].orEmpty()
                        val combined = (olderBatch + existing).distinctBy { it.id }
                        currentMap + (room.roomKey to combined)
                    }
                }
            }
            _isLoadingOlder.value = false
        }
    }

    fun sendMessage(rawText: String, type: ChatMessageType = ChatMessageType.TEXT) {
        val cleanText = rawText.trim().take(ChatMessage.MAX_TEXT_LENGTH)
        if (cleanText.isBlank()) return
        val ctx = _classroomContext.value
        val room = _activeRoom.value ?: return
        val senderName = ctx.ownDisplayName.ifBlank { "خادم" }

        viewModelScope.launch(Dispatchers.IO) {
            sendRoomMessageInternal(
                room = room,
                ctx = ctx,
                senderName = senderName,
                text = cleanText,
                type = type,
                mediaPayload = null,
                appContextForCache = null
            )
        }
    }

    /**
     * Phase 2: Compresses an image on `Dispatchers.IO` (`<= 1024px`, `< 60KB` Base64) via `mediaStorage`,
     * sends it to the active room, and caches the raw bytes locally on disk.
     */
    fun sendImageMessage(context: Context, imageUri: Uri, captionText: String = "") {
        val appContext = context.applicationContext
        val ctx = _classroomContext.value
        val room = _activeRoom.value ?: return
        val senderName = ctx.ownDisplayName.ifBlank { "خادم" }

        _mediaErrorMessage.value = null
        _isSendingMedia.value = true

        viewModelScope.launch(Dispatchers.IO) {
            mediaStorage.prepareImagePayload(appContext, imageUri, captionText)
                .onSuccess { payload ->
                    val label = captionText.trim().take(ChatMessage.MAX_TEXT_LENGTH)
                        .ifBlank { ChatMessage.IMAGE_DEFAULT_LABEL }
                    sendRoomMessageInternal(
                        room = room,
                        ctx = ctx,
                        senderName = senderName,
                        text = label,
                        type = ChatMessageType.IMAGE,
                        mediaPayload = payload,
                        appContextForCache = appContext
                    )
                }
                .onFailure { err ->
                    _mediaErrorMessage.value = err.message ?: "تعذّر ضغط وإرسال الصورة"
                }
            _isSendingMedia.value = false
        }
    }

    /**
     * Phase 2: Encodes a recorded voice note (`<= 30s`, mono, `< 100KB` Base64) on `Dispatchers.IO`
     * via `mediaStorage`, sends it to the active room, and caches the `.m4a` file locally on disk.
     */
    fun sendAudioMessage(context: Context, audioFile: File, durationSec: Int) {
        val appContext = context.applicationContext
        val ctx = _classroomContext.value
        val room = _activeRoom.value ?: return
        val senderName = ctx.ownDisplayName.ifBlank { "خادم" }

        _mediaErrorMessage.value = null
        _isSendingMedia.value = true

        viewModelScope.launch(Dispatchers.IO) {
            mediaStorage.prepareAudioPayload(audioFile, durationSec)
                .onSuccess { payload ->
                    val label = "${ChatMessage.AUDIO_DEFAULT_LABEL} (${ChatFormatHelper.formatAudioDuration(payload.audioDurationSec)})"
                    sendRoomMessageInternal(
                        room = room,
                        ctx = ctx,
                        senderName = senderName,
                        text = label,
                        type = ChatMessageType.AUDIO,
                        mediaPayload = payload,
                        appContextForCache = appContext
                    )
                }
                .onFailure { err ->
                    _mediaErrorMessage.value = err.message ?: "تعذّر إرسال الرسالة الصوتية"
                }
            runCatching { if (audioFile.exists()) audioFile.delete() }
            _isSendingMedia.value = false
        }
    }

    private suspend fun sendRoomMessageInternal(
        room: ActiveChatRoom,
        ctx: ClassroomChatContext,
        senderName: String,
        text: String,
        type: ChatMessageType,
        mediaPayload: PreparedChatMediaPayload?,
        appContextForCache: Context?
    ) {
        when (room) {
            is ActiveChatRoom.GroupRoom -> {
                val currentTotal = chatState.value.groupChatMeta?.totalMessagesCount ?: 0L
                markRoomReadCountInternal(room.roomKey, currentTotal + 1L)
                chatRepository.sendGroupMessage(
                    groupId = room.groupId,
                    senderName = senderName,
                    text = text,
                    memberUids = ctx.memberUids.ifEmpty { listOf(currentUserId) },
                    type = type,
                    mediaPayload = mediaPayload,
                    waitForServerAck = false
                ).onSuccess { optimisticMsg ->
                    if (appContextForCache != null && mediaPayload != null) {
                        mediaStorage.saveOutgoingLocalCache(
                            context = appContextForCache,
                            messageId = optimisticMsg.id,
                            payload = mediaPayload
                        )
                    }
                    _optimisticMessagesByRoom.update { map ->
                        val list = map[room.roomKey].orEmpty() + optimisticMsg
                        map + (room.roomKey to list)
                    }
                }.onFailure { err ->
                    if (mediaPayload != null) {
                        _mediaErrorMessage.value = err.message ?: "تعذّر إرسال الوسائط"
                    }
                }
            }
            is ActiveChatRoom.DirectRoom -> {
                val currentTotal = chatState.value.dmConversations
                    .firstOrNull { it.effectiveChatId == room.chatId }
                    ?.totalMessagesCount ?: 0L
                markRoomReadCountInternal(room.roomKey, currentTotal + 1L)
                chatRepository.sendDirectMessage(
                    groupId = room.groupId,
                    peerUid = room.peerUid,
                    senderName = senderName,
                    peerName = room.peerName,
                    text = text,
                    type = type,
                    mediaPayload = mediaPayload,
                    waitForServerAck = false
                ).onSuccess { optimisticMsg ->
                    if (appContextForCache != null && mediaPayload != null) {
                        mediaStorage.saveOutgoingLocalCache(
                            context = appContextForCache,
                            messageId = optimisticMsg.id,
                            payload = mediaPayload
                        )
                    }
                    _optimisticMessagesByRoom.update { map ->
                        val list = map[room.roomKey].orEmpty() + optimisticMsg
                        map + (room.roomKey to list)
                    }
                }.onFailure { err ->
                    if (mediaPayload != null) {
                        _mediaErrorMessage.value = err.message ?: "تعذّر إرسال الوسائط"
                    }
                }
            }
        }
    }

    fun sendGroupSystemMessage(
        systemText: String,
        notificationCategory: NotificationCategory? = null,
        childId: String = "",
        childName: String = "",
        childBlockName: String = ""
    ) {
        val ctx = _classroomContext.value
        if (ctx.groupId.isBlank() || systemText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.sendGroupMessage(
                groupId = ctx.groupId,
                senderName = ctx.ownDisplayName.ifBlank { "النظام" },
                text = systemText.trim().take(ChatMessage.MAX_SYSTEM_TEXT_LENGTH),
                memberUids = ctx.memberUids.ifEmpty { listOf(currentUserId) },
                type = ChatMessageType.SYSTEM,
                notificationCategory = notificationCategory?.id.orEmpty(),
                childId = childId,
                childName = childName,
                childBlockName = childBlockName,
                waitForServerAck = false
            )
        }
    }

    fun forwardMessage(sourceMessage: ChatMessage, targetRoom: ActiveChatRoom) {
        val ctx = _classroomContext.value
        val senderName = ctx.ownDisplayName.ifBlank { "خادم" }
        val members = ctx.memberUids.ifEmpty { listOf(currentUserId) }
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.forwardMessageToRoom(
                sourceMessage = sourceMessage,
                targetRoom = targetRoom,
                senderName = senderName,
                memberUids = members,
                waitForServerAck = false
            ).onSuccess { forwardedMsg ->
                _optimisticMessagesByRoom.update { map ->
                    val list = map[targetRoom.roomKey].orEmpty() + forwardedMsg
                    map + (targetRoom.roomKey to list)
                }
                _forwardStatusMessage.value = "تم إعادة توجيه الرسالة"
            }.onFailure { err ->
                _forwardStatusMessage.value = err.message ?: "تعذّر إعادة التوجيه"
            }
        }
    }

    fun deleteOwnMessage(message: ChatMessage) {
        if (message.senderUid != currentUserId || message.isDeleted) return
        val room = _activeRoom.value ?: return
        val allMsgs = chatState.value.activeRoomMessages
        val isLatest = allMsgs.lastOrNull()?.id == message.id

        _optimisticDeletedIds.update { it + message.id }
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteOwnMessage(
                room = room,
                message = message,
                isLatestRoomMessage = isLatest,
                waitForServerAck = false
            )
        }
    }

    /**
     * Updates `lastSeenAt` in `groups/{groupId}/members/{currentUserId}` when opening or leaving the app,
     * throttled strictly to at most once every 5 minutes (`ChatFormatHelper.LAST_SEEN_WRITE_THROTTLE_MS`).
     */
    fun touchOwnLastSeenThrottled() {
        val ctx = _classroomContext.value
        if (ctx.groupId.isBlank() || currentUserId.isBlank()) return
        if (currentUserId !in ctx.memberUids && ctx.adminId != currentUserId) return

        val now = System.currentTimeMillis()
        val prev = lastSeenWriteMillis.get()
        if (prev > 0L && now - prev < ChatFormatHelper.LAST_SEEN_WRITE_THROTTLE_MS) {
            return
        }
        val existingOwnProfile = ctx.memberProfiles.firstOrNull { it.effectiveUid == currentUserId }
        val cloudLastSeenMillis = existingOwnProfile?.lastSeenAt?.toDate()?.time ?: 0L
        if (cloudLastSeenMillis > 0L && now - cloudLastSeenMillis < ChatFormatHelper.LAST_SEEN_WRITE_THROTTLE_MS) {
            lastSeenWriteMillis.set(cloudLastSeenMillis)
            return
        }

        if (!lastSeenWriteMillis.compareAndSet(prev, now)) return

        viewModelScope.launch(Dispatchers.IO) {
            kamRepository.upsertOwnMemberProfile(
                groupId = ctx.groupId,
                displayName = ctx.ownDisplayName.ifBlank { "خادم" },
                memberUids = ctx.memberUids.ifEmpty { listOf(currentUserId) },
                existingProfile = existingOwnProfile,
                touchLastSeen = true,
                waitForServerAck = false
            )
        }
    }

    class Factory(
        private val context: Context,
        private val currentUserId: String,
        private val initialDisplayName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appCtx = context.applicationContext
            return ChatViewModel(
                currentUserId = currentUserId,
                initialDisplayName = initialDisplayName,
                chatRepository = ChatRepository(appCtx),
                kamRepository = KamRepository(appCtx)
            ) as T
        }
    }
}
