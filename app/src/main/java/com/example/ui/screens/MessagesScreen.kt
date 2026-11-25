package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.media.ChatAudioPlayerController
import com.example.data.media.ChatMediaStorageProvider
import com.example.data.media.ChatVoiceRecorderHelper
import com.example.data.media.InlineFirestoreChatMediaStorageProvider
import com.example.data.model.ActiveChatRoom
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.AttendanceStatus
import com.example.data.model.CardType
import com.example.data.model.ChatFormatHelper
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.CustomCardConfig
import com.example.data.model.DmChatMeta
import com.example.data.model.NotificationCategory
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.model.Student
import com.example.ui.components.ChatImageEditorDialog
import com.example.ui.components.ServantAvatarCircle
import com.example.ui.components.StudentAvatarCircle
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.components.StudentPhotoPickerDialog
import com.example.ui.theme.KamBackgroundDark
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalKamExtendedColors
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.GroupFellowServantItem
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private val ServantSenderPalette = listOf(
    Color(0xFF7B87EB), // Periwinkle Indigo
    Color(0xFFE5A436), // Warm Amber
    Color(0xFF9F7AEA), // Soft Violet
    Color(0xFFDF5C9B), // Rose
    Color(0xFF2CB578), // Emerald
    Color(0xFFE0753D), // Terracotta Orange
    Color(0xFF2EA9C4)  // Cyan
)

private fun senderDeterministicColor(senderUid: String): Color {
    if (senderUid.isBlank()) return ServantSenderPalette[0]
    val idx = abs(senderUid.hashCode()) % ServantSenderPalette.size
    return ServantSenderPalette[idx]
}

@Composable
fun MessagesScreen(
    chatState: ChatUiState,
    memberProfiles: List<ServantMemberProfile>,
    students: List<Student> = emptyList(),
    blocks: List<ServantBlock> = emptyList(),
    customCardConfig: CustomCardConfig = CustomCardConfig(),
    currentUserId: String,
    mediaStorage: ChatMediaStorageProvider = InlineFirestoreChatMediaStorageProvider,
    onOpenGroupChat: () -> Unit,
    onOpenDirectChat: (peerUid: String, peerName: String) -> Unit,
    onOpenDirectChatByEmail: (String, () -> Unit) -> Unit = { _, _ -> },
    onClearEmailLookupError: () -> Unit = {},
    onCloseActiveChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImageMessage: (Uri, String) -> Unit = { _, _ -> },
    onSendEditedImageMessage: (Bitmap, String) -> Unit = { _, _ -> },
    onSendAudioMessage: (File, Int) -> Unit = { _, _ -> },
    onClearMediaError: () -> Unit = {},
    onDeleteOwnMessage: (ChatMessage) -> Unit,
    onForwardMessage: (ChatMessage, ActiveChatRoom) -> Unit = { _, _ -> },
    onClearForwardStatus: () -> Unit = {},
    onUpdateGroupAvatar: (String) -> Unit = {},
    onSavePersonalAlias: (String, String) -> Unit = { _, _ -> },
    onLoadOlderMessages: () -> Unit
) {
    val context = LocalContext.current
    var showNewDmPickerDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(chatState.forwardStatusMessage) {
        val msg = chatState.forwardStatusMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onClearForwardStatus()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val activeRoom = chatState.activeRoom
        if (activeRoom != null) {
            ChatConversationView(
                room = activeRoom,
                chatState = chatState,
                memberProfiles = memberProfiles,
                students = students,
                blocks = blocks,
                customCardConfig = customCardConfig,
                currentUserId = currentUserId,
                mediaStorage = mediaStorage,
                onBack = onCloseActiveChat,
                onOpenDirectChat = onOpenDirectChat,
                onSendMessage = onSendMessage,
                onSendImageMessage = onSendImageMessage,
                onSendEditedImageMessage = onSendEditedImageMessage,
                onSendAudioMessage = onSendAudioMessage,
                onClearMediaError = onClearMediaError,
                onDeleteOwnMessage = onDeleteOwnMessage,
                onForwardMessage = onForwardMessage,
                onUpdateGroupAvatar = onUpdateGroupAvatar,
                onSavePersonalAlias = onSavePersonalAlias,
                onLoadOlderMessages = onLoadOlderMessages
            )
        } else {
            ConversationsListView(
                chatState = chatState,
                memberProfiles = memberProfiles,
                currentUserId = currentUserId,
                onOpenGroupChat = onOpenGroupChat,
                onOpenDirectChat = onOpenDirectChat,
                onOpenNewDmPicker = {
                    onClearEmailLookupError()
                    showNewDmPickerDialog = true
                },
                onUpdateGroupAvatar = onUpdateGroupAvatar
            )
        }

        if (showNewDmPickerDialog) {
            NewPrivateChatServantPickerDialog(
                groupName = chatState.activeGroupName,
                fellowServants = chatState.fellowServants,
                groupId = chatState.activeGroupId,
                isLookingUpEmail = chatState.isLookingUpEmail,
                emailLookupError = chatState.emailLookupError,
                onLookupByEmail = { email ->
                    onOpenDirectChatByEmail(email) {
                        showNewDmPickerDialog = false
                    }
                },
                onClearEmailError = onClearEmailLookupError,
                onSelectServant = { item ->
                    showNewDmPickerDialog = false
                    onOpenDirectChat(item.uid, item.displayName)
                },
                onDismiss = {
                    onClearEmailLookupError()
                    showNewDmPickerDialog = false
                }
            )
        }
    }
}

@Composable
private fun GroupChatAvatarCircle(
    groupId: String,
    groupName: String,
    avatarBase64: String,
    avatarHash: String,
    size: androidx.compose.ui.unit.Dp = 50.dp,
    isEditable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val hasCustomAvatar = avatarBase64.isNotBlank() || avatarHash.isNotBlank() ||
        StudentAvatarImageHelper.peekCachedLocalAlbumRaw("group_$groupId").isNotBlank()

    val syntheticGroupStudent = remember(groupId, groupName, avatarBase64, avatarHash) {
        Student(
            id = "group_${groupId.ifBlank { "default" }}",
            groupId = groupId,
            name = groupName.ifBlank { "جروب الفصل" },
            avatarInitials = ArabicInitialsHelper.extractInitials(groupName.ifBlank { "جروب الفصل" }),
            avatarBase64 = avatarBase64,
            hasAvatar = avatarBase64.isNotBlank() || avatarHash.isNotBlank(),
            avatarHash = avatarHash
        )
    }

    Box(contentAlignment = Alignment.BottomEnd) {
        if (hasCustomAvatar) {
            StudentAvatarCircle(
                student = syntheticGroupStudent,
                blockAccent = MaterialTheme.colorScheme.primary,
                size = size,
                onClick = onClick ?: {},
                testTag = "group_avatar_${groupId.ifBlank { "main" }}",
                blockLabel = groupName.ifBlank { "جروب الفصل" }
            )
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .then(
                        if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                    )
                    .testTag("group_avatar_${groupId.ifBlank { "main" }}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = groupName.ifBlank { "جروب الفصل" },
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(size * 0.54f)
                    )
                }
            }
        }

        if (isEditable && onClick != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .clickable(onClick = onClick)
                    .testTag("edit_group_avatar_badge"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "تغيير صورة الجروب",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun ConversationsListView(
    chatState: ChatUiState,
    memberProfiles: List<ServantMemberProfile>,
    currentUserId: String,
    onOpenGroupChat: () -> Unit,
    onOpenDirectChat: (peerUid: String, peerName: String) -> Unit,
    onOpenNewDmPicker: () -> Unit,
    onUpdateGroupAvatar: (String) -> Unit
) {
    val profileByUid = remember(memberProfiles) {
        memberProfiles.associateBy { it.effectiveUid }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedSearch by rememberSaveable { mutableStateOf("") }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var showGroupPhotoDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            debouncedSearch = ""
        } else {
            kotlinx.coroutines.delay(180L)
            debouncedSearch = trimmed
        }
    }

    val cleanSearch = debouncedSearch
    val filteredDms = remember(chatState.dmConversations, cleanSearch, memberProfiles, chatState.servantAliases, currentUserId) {
        if (cleanSearch.isBlank()) {
            chatState.dmConversations
        } else {
            val normQuery = ArabicInitialsHelper.normalizeArabicName(cleanSearch)
            chatState.dmConversations.filter { dm ->
                val pName = dm.peerDisplayName(currentUserId, memberProfiles, chatState.servantAliases)
                ArabicInitialsHelper.normalizeArabicName(pName).contains(normQuery, ignoreCase = true) ||
                    ArabicInitialsHelper.normalizeArabicName(dm.lastMessageText).contains(normQuery, ignoreCase = true)
            }
        }
    }

    val showPinnedGroup = remember(cleanSearch, chatState.activeGroupName, chatState.groupChatMeta) {
        if (cleanSearch.isBlank()) {
            true
        } else {
            val normQuery = ArabicInitialsHelper.normalizeArabicName(cleanSearch)
            ArabicInitialsHelper.normalizeArabicName(chatState.activeGroupName).contains(normQuery, ignoreCase = true) ||
                ArabicInitialsHelper.normalizeArabicName("جروب الفصل").contains(normQuery, ignoreCase = true) ||
                (chatState.groupChatMeta?.lastMessageText?.let {
                    ArabicInitialsHelper.normalizeArabicName(it).contains(normQuery, ignoreCase = true)
                } == true)
        }
    }

    val extColors = LocalKamExtendedColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("conversations_list"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Telegram-Style Sleek Top Header Bar
            item(key = "messages_header") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extColors.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "المحادثات",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (chatState.activeGroupName.isNotBlank()) {
                                        "${chatState.activeGroupName} • تواصل فوري مع الخدام"
                                    } else {
                                        "جروب الخدام والمحادثات الخاصة"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            isSearchExpanded = !isSearchExpanded
                                            if (!isSearchExpanded) searchQuery = ""
                                        }
                                        .testTag("toggle_conversations_search_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                            contentDescription = "بحث في المحادثات",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = onOpenNewDmPicker)
                                        .testTag("start_private_chat_top_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "محادثة خاصة جديدة",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "جديدة",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = isSearchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث بالاسم أو نص الرسالة...") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(999.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("conversations_search_input")
                            )
                        }
                    }
                }
            }

            // 1. Pinned Group Chat Room (Displays real Group Name + Circular Group Avatar)
            if (showPinnedGroup) {
                item(key = "pinned_group_chat_room") {
                    val totalServants = maxOf(chatState.groupMemberUids.size, chatState.fellowServants.size + 1).coerceAtLeast(1)
                    PinnedGroupChatCard(
                        groupId = chatState.activeGroupId,
                        groupName = chatState.activeGroupName,
                        groupAvatarBase64 = chatState.activeGroupAvatarBase64,
                        groupAvatarHash = chatState.activeGroupAvatarHash,
                        servantsCount = totalServants,
                        groupMeta = chatState.groupChatMeta,
                        unreadCount = chatState.groupUnreadCount,
                        currentUserId = currentUserId,
                        servantAliases = chatState.servantAliases,
                        onClick = onOpenGroupChat,
                        onAvatarClick = { showGroupPhotoDialog = true }
                    )
                }
            }

            // 2. Private Conversations Section Header
            item(key = "dm_section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "المحادثات الخاصة",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${filteredDms.size} محادثة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (filteredDms.isEmpty()) {
                item(key = "empty_dms_placeholder") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (cleanSearch.isNotBlank()) "لا توجد محادثات مطابقة للبحث" else "لا توجد محادثات خاصة بعد",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "اضغط على زر (+) لاختيار خادم من الفصل أو كتابة حساب Gmail لبدء محادثة فورية.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredDms,
                    key = { dm -> "dm_row_${dm.effectiveChatId}" }
                ) { dm ->
                    val peerUid = dm.peerUid(currentUserId)
                    val peerProfile = profileByUid[peerUid]
                    val peerName = dm.peerDisplayName(currentUserId, memberProfiles, chatState.servantAliases)
                    val unread = chatState.dmUnreadCounts[dm.effectiveChatId] ?: 0
                    DmConversationRowCard(
                        dm = dm,
                        peerUid = peerUid,
                        peerName = peerName,
                        peerProfile = peerProfile,
                        groupId = chatState.activeGroupId,
                        unreadCount = unread,
                        currentUserId = currentUserId,
                        onClick = { onOpenDirectChat(peerUid, peerName) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenNewDmPicker,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 88.dp)
                .testTag("new_dm_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "بدء محادثة خاصة"
            )
        }
    }

    if (showGroupPhotoDialog) {
        val syntheticGroupStudent = remember(
            chatState.activeGroupId,
            chatState.activeGroupName,
            chatState.activeGroupAvatarBase64,
            chatState.activeGroupAvatarHash
        ) {
            Student(
                id = "group_${chatState.activeGroupId.ifBlank { "default" }}",
                groupId = chatState.activeGroupId,
                name = chatState.activeGroupName.ifBlank { "جروب الفصل" },
                avatarInitials = ArabicInitialsHelper.extractInitials(chatState.activeGroupName.ifBlank { "جروب الفصل" }),
                avatarBase64 = chatState.activeGroupAvatarBase64,
                hasAvatar = chatState.activeGroupAvatarBase64.isNotBlank() || chatState.activeGroupAvatarHash.isNotBlank(),
                avatarHash = chatState.activeGroupAvatarHash
            )
        }
        StudentPhotoPickerDialog(
            student = syntheticGroupStudent,
            blockAccent = MaterialTheme.colorScheme.primary,
            isAdmin = true,
            onDismiss = { showGroupPhotoDialog = false },
            onSaveAvatarBase64 = { newBase64 ->
                onUpdateGroupAvatar(newBase64)
                showGroupPhotoDialog = false
            }
        )
    }
}

@Composable
private fun PinnedGroupChatCard(
    groupId: String,
    groupName: String,
    groupAvatarBase64: String,
    groupAvatarHash: String,
    servantsCount: Int,
    groupMeta: com.example.data.model.GroupChatMeta?,
    unreadCount: Int,
    currentUserId: String,
    servantAliases: Map<String, String>,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val resolvedLastSender = remember(groupMeta?.lastMessageSenderUid, groupMeta?.lastMessageSenderName, servantAliases) {
        val sUid = groupMeta?.lastMessageSenderUid.orEmpty()
        servantAliases[sUid]?.takeIf { it.isNotBlank() }
            ?: groupMeta?.lastMessageSenderName.orEmpty()
    }
    val lastText = when {
        groupMeta == null || groupMeta.lastMessageText.isBlank() ->
            "غرفة خدام الفصل ($servantsCount خدام) — اضغط للفتح"
        groupMeta.lastMessageType == ChatMessageType.SYSTEM.id ->
            groupMeta.lastMessageText
        groupMeta.lastMessageSenderUid == currentUserId ->
            "أنت: ${groupMeta.lastMessageText}"
        resolvedLastSender.isNotBlank() ->
            "$resolvedLastSender: ${groupMeta.lastMessageText}"
        else -> groupMeta.lastMessageText
    }
    val timeLabel = ChatFormatHelper.formatConversationListTime(groupMeta?.lastMessageAt)
    val effectiveTitle = groupName.trim().ifBlank { "جروب الفصل" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pinned_group_chat_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (unreadCount > 0) 1.5.dp else 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (unreadCount > 0) 0.72f else 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
                    .padding(2.dp)
            ) {
                GroupChatAvatarCircle(
                    groupId = groupId,
                    groupName = effectiveTitle,
                    avatarBase64 = groupAvatarBase64,
                    avatarHash = groupAvatarHash,
                    size = 54.dp,
                    isEditable = true,
                    onClick = onAvatarClick
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = effectiveTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "$servantsCount عضو",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (timeLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lastText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadCountPill(count = unreadCount)
                    }
                }
            }
        }
    }
}

@Composable
private fun DmConversationRowCard(
    dm: DmChatMeta,
    peerUid: String,
    peerName: String,
    peerProfile: ServantMemberProfile?,
    groupId: String,
    unreadCount: Int,
    currentUserId: String,
    onClick: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val isActiveNow = ChatFormatHelper.isServantActiveNow(peerProfile?.lastSeenAt)
    val isLastFromMe = dm.lastMessageSenderUid == currentUserId && dm.lastMessageText.isNotBlank()
    val lastText = when {
        dm.lastMessageText.isBlank() -> "اضغط لبدء المحادثة"
        isLastFromMe -> "أنت: ${dm.lastMessageText}"
        else -> dm.lastMessageText
    }
    val timeLabel = ChatFormatHelper.formatConversationListTime(dm.lastMessageAt ?: dm.updatedAt)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("dm_card_$peerUid"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (unreadCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
            } else {
                extColors.hairline
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                ServantAvatarCircle(
                    servantUid = peerUid,
                    servantName = peerName,
                    groupId = groupId,
                    memberProfile = peerProfile,
                    accentColor = senderDeterministicColor(peerUid),
                    size = 52.dp,
                    isEditable = false
                )
                if (isActiveNow) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(extColors.onlineGreen)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (timeLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (isLastFromMe) {
                                Icon(
                                    imageVector = Icons.Filled.DoneAll,
                                    contentDescription = "تم الإرسال",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lastText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadCountPill(count = unreadCount)
                    }
                }
            }
        }
    }
}

@Composable
fun UnreadCountPill(
    count: Int,
    modifier: Modifier = Modifier
) {
    val label = ChatFormatHelper.formatUnreadBadge(count)
    if (label.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NewPrivateChatServantPickerDialog(
    groupName: String,
    fellowServants: List<GroupFellowServantItem>,
    groupId: String,
    isLookingUpEmail: Boolean,
    emailLookupError: String?,
    onLookupByEmail: (String) -> Unit,
    onClearEmailError: () -> Unit,
    onSelectServant: (GroupFellowServantItem) -> Unit,
    onDismiss: () -> Unit
) {
    var emailInput by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "بدء محادثة خاصة جديدة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (groupName.isNotBlank()) {
                        Text(
                            text = "اختر من خدام $groupName أو ابحث بحساب Gmail",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Direct Chat by Gmail Lookup Section
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "بدء محادثة عبر Gmail",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                if (emailLookupError != null) onClearEmailError()
                            },
                            placeholder = { Text("example@gmail.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gmail_lookup_input")
                        )
                        if (!emailLookupError.isNullOrBlank()) {
                            Text(
                                text = emailLookupError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { onLookupByEmail(emailInput.trim()) },
                            enabled = emailInput.trim().contains("@") && !isLookingUpEmail,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .testTag("gmail_lookup_button")
                        ) {
                            if (isLookingUpEmail) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري البحث...")
                            } else {
                                Text("فتح محادثة بالبريد الإلكتروني", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "أعضاء وخدام المجموعة (${fellowServants.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (fellowServants.isEmpty()) {
                    Text(
                        text = "لا يوجد خدام آخرون في نفس المجموعة حالياً. يمكنك البحث عبر Gmail أعلاه أو دعوة خدام من تبويب المجموعة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = fellowServants,
                            key = { it.uid }
                        ) { servant ->
                            val isActive = ChatFormatHelper.isServantActiveNow(servant.memberProfile?.lastSeenAt)
                            val statusText = ChatFormatHelper.formatLastSeenStatus(servant.memberProfile?.lastSeenAt)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectServant(servant) }
                                    .testTag("pick_dm_servant_${servant.uid}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ServantAvatarCircle(
                                        servantUid = servant.uid,
                                        servantName = servant.displayName,
                                        groupId = groupId,
                                        memberProfile = servant.memberProfile,
                                        accentColor = senderDeterministicColor(servant.uid),
                                        size = 42.dp,
                                        isEditable = false
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = servant.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val extColors = LocalKamExtendedColors.current
                                        val emailSub = servant.memberProfile?.email.orEmpty()
                                        Text(
                                            text = if (emailSub.isNotBlank()) "$statusText • $emailSub" else statusText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isActive) extColors.onlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

private sealed interface ChatTimelineRowItem {
    val itemKey: String

    data class DayHeader(
        val dayKey: String,
        val label: String
    ) : ChatTimelineRowItem {
        override val itemKey: String
            get() = "day_$dayKey"
    }

    data class MessageRow(
        val message: ChatMessage
    ) : ChatTimelineRowItem {
        override val itemKey: String
            get() = "msg_${message.id}"
    }
}

@Composable
private fun ChatConversationView(
    room: ActiveChatRoom,
    chatState: ChatUiState,
    memberProfiles: List<ServantMemberProfile>,
    students: List<Student>,
    blocks: List<ServantBlock>,
    customCardConfig: CustomCardConfig,
    currentUserId: String,
    mediaStorage: ChatMediaStorageProvider,
    onBack: () -> Unit,
    onOpenDirectChat: (peerUid: String, peerName: String) -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImageMessage: (Uri, String) -> Unit,
    onSendEditedImageMessage: (Bitmap, String) -> Unit,
    onSendAudioMessage: (File, Int) -> Unit,
    onClearMediaError: () -> Unit,
    onDeleteOwnMessage: (ChatMessage) -> Unit,
    onForwardMessage: (ChatMessage, ActiveChatRoom) -> Unit,
    onUpdateGroupAvatar: (String) -> Unit,
    onSavePersonalAlias: (String, String) -> Unit,
    onLoadOlderMessages: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPlayer = remember { ChatAudioPlayerController() }
    val voiceRecorder = remember(context) { ChatVoiceRecorderHelper(context.applicationContext) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var localVoiceError by remember { mutableStateOf<String?>(null) }
    var fullscreenImagePreview by remember { mutableStateOf<Pair<ChatMessage, Bitmap>?>(null) }
    var inspectedAttendanceStudent by remember { mutableStateOf<Pair<Student, String>?>(null) }
    var messagePendingForward by remember { mutableStateOf<ChatMessage?>(null) }
    var showGroupAvatarEditor by rememberSaveable { mutableStateOf(false) }
    var showGroupMembersSheet by rememberSaveable { mutableStateOf(false) }
    var showPeerAliasDialog by rememberSaveable { mutableStateOf(false) }
    var pendingImageUriForEditor by remember { mutableStateOf<Uri?>(null) }

    DisposableEffect(room.roomKey) {
        onDispose {
            audioPlayer.stop()
            voiceRecorder.cancelRecording()
            isRecordingAudio = false
        }
    }

    BackHandler(enabled = com.example.ui.theme.LocalIsTabActive.current) {
        if (isRecordingAudio) {
            voiceRecorder.cancelRecording()
            isRecordingAudio = false
        } else {
            onBack()
        }
    }

    var draftText by rememberSaveable(room.roomKey) { mutableStateOf("") }
    var messagePendingDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Zero-permission Photo Picker → opens Telegram-style Crop/Rotate/Caption Editor before sending
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUriForEditor = uri
        }
    }

    val startVoiceRecordingInternal: () -> Unit = {
        audioPlayer.stop()
        localVoiceError = null
        voiceRecorder.startRecording { maxReachedFile: File, maxSec: Int ->
            isRecordingAudio = false
            recordingSeconds = 0
            onSendAudioMessage(maxReachedFile, maxSec)
        }.onSuccess {
            recordingSeconds = 0
            isRecordingAudio = true
        }.onFailure { err ->
            localVoiceError = err.message ?: "تعذّر بدء التسجيل الصوتي"
            isRecordingAudio = false
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecordingInternal()
        } else {
            localVoiceError = "يلزم السماح بإذن الميكروفون لتسجيل رسالة صوتية"
        }
    }

    // Live timer while recording voice (max 30 seconds)
    LaunchedEffect(isRecordingAudio) {
        if (isRecordingAudio) {
            while (isRecordingAudio && recordingSeconds < ChatMessage.MAX_AUDIO_DURATION_SEC) {
                delay(1000L)
                if (isRecordingAudio) {
                    recordingSeconds = voiceRecorder.elapsedSeconds()
                        .coerceAtMost(ChatMessage.MAX_AUDIO_DURATION_SEC)
                }
            }
        } else {
            recordingSeconds = 0
        }
    }

    val messages = chatState.activeRoomMessages
    val timelineItems = remember(messages) {
        val list = ArrayList<ChatTimelineRowItem>(messages.size + 6)
        var lastDayKey = ""
        for (msg in messages) {
            val dKey = ChatFormatHelper.daySeparatorKey(msg.effectiveTimeMillis)
            if (dKey != lastDayKey) {
                lastDayKey = dKey
                list.add(
                    ChatTimelineRowItem.DayHeader(
                        dayKey = dKey,
                        label = ChatFormatHelper.formatDaySeparatorLabel(msg.effectiveTimeMillis)
                    )
                )
            }
            list.add(ChatTimelineRowItem.MessageRow(msg))
        }
        list
    }

    val listState = rememberLazyListState()
    var lastAutoScrolledMessageId by remember(room.roomKey) { mutableStateOf("") }

    // Auto-scroll to bottom when opening the room or when a new message arrives at the end
    val newestMessageId = messages.lastOrNull()?.id.orEmpty()
    LaunchedEffect(newestMessageId, timelineItems.size) {
        if (timelineItems.isNotEmpty() && newestMessageId != lastAutoScrolledMessageId) {
            lastAutoScrolledMessageId = newestMessageId
            listState.animateScrollToItem(timelineItems.size - 1)
        }
    }

    // Trigger pagination when user scrolls up near the top
    val isNearTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 1 && timelineItems.size >= ChatMessage.PAGE_SIZE
        }
    }
    LaunchedEffect(isNearTop, chatState.canLoadOlderMessages, chatState.isLoadingOlderMessages) {
        if (isNearTop && chatState.canLoadOlderMessages && !chatState.isLoadingOlderMessages) {
            onLoadOlderMessages()
        }
    }

    val peerProfile = remember(room, memberProfiles) {
        when (room) {
            is ActiveChatRoom.GroupRoom -> null
            is ActiveChatRoom.DirectRoom -> memberProfiles.firstOrNull { it.effectiveUid == room.peerUid }
        }
    }

    val extColors = LocalKamExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_conversation_screen")
    ) {
        // Conversation Header Bar (Nocturnal flat surface + 1dp hairline)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = {
                        audioPlayer.stop()
                        voiceRecorder.cancelRecording()
                        isRecordingAudio = false
                        onBack()
                    },
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع لقائمة المحادثات"
                    )
                }

                when (room) {
                    is ActiveChatRoom.GroupRoom -> {
                        val effectiveGroupTitle = room.groupName.ifBlank { chatState.activeGroupName.ifBlank { "جروب الفصل" } }
                        val totalServants = maxOf(chatState.groupMemberUids.size, chatState.fellowServants.size + 1).coerceAtLeast(1)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showGroupMembersSheet = true }
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .testTag("group_header_info_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GroupChatAvatarCircle(
                                groupId = room.groupId,
                                groupName = effectiveGroupTitle,
                                avatarBase64 = chatState.activeGroupAvatarBase64,
                                avatarHash = chatState.activeGroupAvatarHash,
                                size = 44.dp,
                                isEditable = false,
                                onClick = { showGroupMembersSheet = true }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = effectiveGroupTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$totalServants عضو • اضغط لعرض الأعضاء",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    is ActiveChatRoom.DirectRoom -> {
                        val isActiveNow = ChatFormatHelper.isServantActiveNow(peerProfile?.lastSeenAt)
                        val statusText = ChatFormatHelper.formatLastSeenStatus(peerProfile?.lastSeenAt)
                        val resolvedPeerName = chatState.servantAliases[room.peerUid]?.takeIf { it.isNotBlank() }
                            ?: peerProfile?.displayName?.takeIf { it.isNotBlank() }
                            ?: room.peerName
                        ServantAvatarCircle(
                            servantUid = room.peerUid,
                            servantName = resolvedPeerName,
                            groupId = room.groupId,
                            memberProfile = peerProfile,
                            accentColor = senderDeterministicColor(room.peerUid),
                            size = 44.dp,
                            isEditable = false
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showPeerAliasDialog = true }
                        ) {
                            Text(
                                text = resolvedPeerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActiveNow) extColors.onlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isActiveNow) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        IconButton(
                            onClick = { showPeerAliasDialog = true },
                            modifier = Modifier.testTag("edit_peer_alias_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تسمية خاصة للخادم في جهازك",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = extColors.hairline)

        // Messages Area with subtle Telegram chat surface contrast
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = if (extColors.isDark) 0.22f else 0.35f
                    )
                )
        ) {
            if (timelineItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "لا توجد رسائل بعد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اكتب رسالة أو أرسل صورة أو رسالة صوتية لبدء المحادثة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chatState.canLoadOlderMessages || chatState.isLoadingOlderMessages) {
                        item(key = "load_older_header") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (chatState.isLoadingOlderMessages) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    TextButton(
                                        onClick = onLoadOlderMessages,
                                        modifier = Modifier.testTag("load_older_messages_button")
                                    ) {
                                        Text("تحميل رسائل أقدم")
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = timelineItems,
                        key = { it.itemKey }
                    ) { row ->
                        when (row) {
                            is ChatTimelineRowItem.DayHeader -> {
                                DaySeparatorChip(label = row.label)
                            }
                            is ChatTimelineRowItem.MessageRow -> {
                                val msg = row.message
                                val resolvedSender = chatState.servantAliases[msg.senderUid]?.takeIf { it.isNotBlank() }
                                    ?: msg.senderName
                                if (msg.messageType == ChatMessageType.SYSTEM) {
                                    SystemMessageCenteredPill(
                                        message = msg,
                                        students = students,
                                        blocks = blocks,
                                        customCardConfig = customCardConfig,
                                        onInspectStudentPhoto = { stu, note ->
                                            inspectedAttendanceStudent = stu to note
                                        },
                                        onRequestForward = {
                                            if (!msg.isDeleted) {
                                                messagePendingForward = msg
                                            }
                                        }
                                    )
                                } else {
                                    TelegramStyleMessageBubble(
                                        message = msg,
                                        resolvedSenderName = resolvedSender,
                                        isOwnMessage = msg.senderUid == currentUserId,
                                        showSenderName = room is ActiveChatRoom.GroupRoom && msg.senderUid != currentUserId,
                                        mediaStorage = mediaStorage,
                                        audioPlayer = audioPlayer,
                                        onOpenFullscreenImage = { bmp ->
                                            fullscreenImagePreview = msg to bmp
                                        },
                                        onRequestForward = {
                                            if (!msg.isDeleted) {
                                                messagePendingForward = msg
                                            }
                                        },
                                        onRequestDelete = {
                                            if (msg.senderUid == currentUserId && !msg.isDeleted) {
                                                messagePendingDelete = msg
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Media error or sending indicator strip
        val activeError = localVoiceError ?: chatState.mediaErrorMessage
        AnimatedVisibility(visible = activeError != null || chatState.isSendingMedia) {
            Surface(
                color = if (activeError != null) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (chatState.isSendingMedia && activeError == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "جاري ضغط وإرسال الوسائط...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    } else if (activeError != null) {
                        Text(
                            text = activeError,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                localVoiceError = null
                                onClearMediaError()
                            }
                        ) {
                            Text("إغلاق", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = extColors.hairline)

        // Bottom Input Bar (Text + Image Picker <=1024px/<60KB + Voice Recorder <=30s/<100KB)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (isRecordingAudio) {
                    // Active Voice Recording Bar (0:00 / 0:30)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("voice_recording_active_bar"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Text(
                            text = "جاري التسجيل ${ChatFormatHelper.formatAudioDuration(recordingSeconds)} / 0:30",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                voiceRecorder.cancelRecording()
                                isRecordingAudio = false
                                recordingSeconds = 0
                            },
                            modifier = Modifier.testTag("cancel_voice_recording_button")
                        ) {
                            Text("إلغاء", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val (recordedFile, recordedSec) = voiceRecorder.stopRecording()
                                isRecordingAudio = false
                                recordingSeconds = 0
                                if (recordedFile != null) {
                                    onSendAudioMessage(recordedFile, recordedSec)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("send_voice_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "إرسال التسجيل",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إرسال", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val canSendText = draftText.trim().isNotEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Visual RIGHT (first in RTL): Voice Record (Mic) button
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !chatState.isSendingMedia) {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        startVoiceRecordingInternal()
                                    } else {
                                        recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                                .testTag("chat_record_voice_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = "تسجيل رسالة صوتية",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // 2. Center: Telegram Pill Text Field with inline Gallery/Image Attach icon
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { next ->
                                if (next.length <= ChatMessage.MAX_TEXT_LENGTH) {
                                    draftText = next
                                } else {
                                    draftText = next.take(ChatMessage.MAX_TEXT_LENGTH)
                                }
                            },
                            placeholder = {
                                Text(
                                    text = "رسالة...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (!chatState.isSendingMedia) {
                                            imagePickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    },
                                    enabled = !chatState.isSendingMedia,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("chat_attach_image_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = "إرفاق صورة",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                                unfocusedBorderColor = extColors.hairline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        // 3. Visual LEFT (last in RTL): Always-Visible Dedicated Send Button
                        Surface(
                            shape = CircleShape,
                            color = if (canSendText) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canSendText) {
                                    val toSend = draftText.trim()
                                    if (toSend.isNotEmpty()) {
                                        draftText = ""
                                        onSendMessage(toSend)
                                    }
                                }
                                .testTag("chat_send_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = if (canSendText) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                    },
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = draftText.length >= 850) {
                    Text(
                        text = "${draftText.length} / ${ChatMessage.MAX_TEXT_LENGTH}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (draftText.length >= ChatMessage.MAX_TEXT_LENGTH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 2.dp, start = 12.dp)
                    )
                }
            }
        }
    }

    // Fullscreen Image Preview Dialog with Save-to-Device button
    val previewPair = fullscreenImagePreview
    if (previewPair != null) {
        FullscreenChatImageDialog(
            bitmap = previewPair.second,
            onSaveToDevice = {
                scope.launch {
                    mediaStorage.saveImageToDeviceGallery(context, previewPair.first)
                        .onSuccess {
                            Toast.makeText(
                                context,
                                "تم حفظ الصورة في المعرض (Pictures/kAM_Lite) ✓",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .onFailure { err ->
                            Toast.makeText(
                                context,
                                err.localizedMessage ?: "تعذّر حفظ الصورة",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            },
            onDismiss = { fullscreenImagePreview = null }
        )
    }

    // Inspected Student Photo from Visual Attendance Report Card
    val inspectedPair = inspectedAttendanceStudent
    if (inspectedPair != null) {
        AttendanceReportedChildPhotoDialog(
            student = inspectedPair.first,
            note = inspectedPair.second,
            onDismiss = { inspectedAttendanceStudent = null }
        )
    }

    // Delete Own Message Confirmation Dialog
    val targetDelete = messagePendingDelete
    if (targetDelete != null) {
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            title = {
                Text(
                    text = "حذف الرسالة؟",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "سيتم حذف الرسالة لدى جميع الأطراف وتظهر عبارة «تم حذف الرسالة».",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteOwnMessage(targetDelete)
                        messagePendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_message_button")
                ) {
                    Text("حذف لدى الجميع")
                }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDelete = null }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // Forward Message Dialog
    val targetForward = messagePendingForward
    if (targetForward != null) {
        ForwardMessagePickerDialog(
            message = targetForward,
            chatState = chatState,
            memberProfiles = memberProfiles,
            currentUserId = currentUserId,
            onSelectTargetRoom = { targetRoom ->
                onForwardMessage(targetForward, targetRoom)
                messagePendingForward = null
            },
            onDismiss = { messagePendingForward = null }
        )
    }

    // Group Avatar Picker inside Conversation View
    if (showGroupAvatarEditor && room is ActiveChatRoom.GroupRoom) {
        val syntheticGroupStudent = remember(
            room.groupId,
            room.groupName,
            chatState.activeGroupAvatarBase64,
            chatState.activeGroupAvatarHash
        ) {
            Student(
                id = "group_${room.groupId.ifBlank { "default" }}",
                groupId = room.groupId,
                name = room.groupName.ifBlank { "جروب الفصل" },
                avatarInitials = ArabicInitialsHelper.extractInitials(room.groupName.ifBlank { "جروب الفصل" }),
                avatarBase64 = chatState.activeGroupAvatarBase64,
                hasAvatar = chatState.activeGroupAvatarBase64.isNotBlank() || chatState.activeGroupAvatarHash.isNotBlank(),
                avatarHash = chatState.activeGroupAvatarHash
            )
        }
        StudentPhotoPickerDialog(
            student = syntheticGroupStudent,
            blockAccent = MaterialTheme.colorScheme.primary,
            isAdmin = true,
            onDismiss = { showGroupAvatarEditor = false },
            onSaveAvatarBase64 = { newBase64 ->
                onUpdateGroupAvatar(newBase64)
                showGroupAvatarEditor = false
            }
        )
    }

    // Personal Alias Editor Dialog for Direct Chat Peer
    if (showPeerAliasDialog && room is ActiveChatRoom.DirectRoom) {
        val currentAlias = chatState.servantAliases[room.peerUid].orEmpty()
        var aliasDraft by rememberSaveable(room.peerUid, currentAlias) {
            mutableStateOf(currentAlias.ifBlank { room.peerName })
        }
        AlertDialog(
            onDismissRequest = { showPeerAliasDialog = false },
            title = {
                Text(
                    text = "تغيير اسم الخادم (في جهازك فقط)",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "هذا الاسم يظهر في تليفونك أنت فقط في المحادثات والبلوكات دون أن يتغير عند باقي الخدام.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aliasDraft,
                        onValueChange = { aliasDraft = it.take(80) },
                        label = { Text("اللقب / الاسم المفضل") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("peer_alias_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePersonalAlias(room.peerUid, aliasDraft.trim())
                        showPeerAliasDialog = false
                    },
                    modifier = Modifier.testTag("save_peer_alias_button")
                ) {
                    Text("حفظ اللقب")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (currentAlias.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onSavePersonalAlias(room.peerUid, "")
                                showPeerAliasDialog = false
                            }
                        ) {
                            Text("استعادة الأصلي")
                        }
                    }
                    TextButton(onClick = { showPeerAliasDialog = false }) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }

    // Group Info & Members Sheet when tapping the Group Header
    if (showGroupMembersSheet && room is ActiveChatRoom.GroupRoom) {
        GroupMembersInfoDialog(
            groupId = room.groupId,
            groupName = room.groupName.ifBlank { chatState.activeGroupName.ifBlank { "جروب الفصل" } },
            groupAvatarBase64 = chatState.activeGroupAvatarBase64,
            groupAvatarHash = chatState.activeGroupAvatarHash,
            adminUid = chatState.activeGroupAdminId,
            adminName = chatState.activeGroupAdminName,
            assistantAdminIds = chatState.activeGroupAssistantAdminIds,
            ownDisplayName = chatState.ownDisplayName,
            currentUserId = currentUserId,
            groupMemberUids = chatState.groupMemberUids,
            fellowServants = chatState.fellowServants,
            memberProfiles = memberProfiles,
            servantAliases = chatState.servantAliases,
            onChangeGroupAvatar = {
                showGroupMembersSheet = false
                showGroupAvatarEditor = true
            },
            onSelectMemberForDm = { memberUid, memberName ->
                showGroupMembersSheet = false
                onOpenDirectChat(memberUid, memberName)
            },
            onDismiss = { showGroupMembersSheet = false }
        )
    }

    // Telegram-Style Pre-Send Image Editor Dialog (Crop, Rotate 90°, Caption)
    val uriToEdit = pendingImageUriForEditor
    if (uriToEdit != null) {
        ChatImageEditorDialog(
            imageUri = uriToEdit,
            initialCaption = draftText.trim(),
            onConfirmSend = { editedBitmap, finalCaption ->
                pendingImageUriForEditor = null
                draftText = ""
                onSendEditedImageMessage(editedBitmap, finalCaption)
            },
            onDismiss = { pendingImageUriForEditor = null }
        )
    }
}

private data class GroupMemberListEntry(
    val uid: String,
    val displayName: String,
    val isCurrentUser: Boolean,
    val isAdmin: Boolean,
    val isAssistantAdmin: Boolean,
    val profile: ServantMemberProfile?
)

@Composable
private fun GroupMembersInfoDialog(
    groupId: String,
    groupName: String,
    groupAvatarBase64: String,
    groupAvatarHash: String,
    adminUid: String,
    adminName: String,
    assistantAdminIds: List<String>,
    ownDisplayName: String,
    currentUserId: String,
    groupMemberUids: List<String>,
    fellowServants: List<GroupFellowServantItem>,
    memberProfiles: List<ServantMemberProfile>,
    servantAliases: Map<String, String>,
    onChangeGroupAvatar: () -> Unit,
    onSelectMemberForDm: (uid: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val allMembers = remember(
        groupMemberUids,
        fellowServants,
        memberProfiles,
        servantAliases,
        currentUserId,
        adminUid,
        adminName,
        assistantAdminIds,
        ownDisplayName
    ) {
        val profileByUid = memberProfiles.associateBy { it.effectiveUid }
        val fellowByUid = fellowServants.associateBy { it.uid }
        val assistantSet = assistantAdminIds.toSet()
        val uids = LinkedHashSet<String>()
        if (adminUid.isNotBlank()) uids.add(adminUid)
        if (currentUserId.isNotBlank()) uids.add(currentUserId)
        groupMemberUids.filter { it.isNotBlank() }.forEach { uids.add(it) }
        fellowServants.map { it.uid }.filter { it.isNotBlank() }.forEach { uids.add(it) }
        memberProfiles.map { it.effectiveUid }.filter { it.isNotBlank() }.forEach { uids.add(it) }

        uids.map { uid ->
            val prof = profileByUid[uid]
            val fellow = fellowByUid[uid]
            val isMe = uid == currentUserId
            val isAdmin = adminUid.isNotBlank() && uid == adminUid
            val isAssistant = !isAdmin && uid in assistantSet
            val resolvedName = when {
                isMe -> prof?.displayName?.takeIf { it.isNotBlank() }
                    ?: ownDisplayName.takeIf { it.isNotBlank() }
                    ?: if (isAdmin && adminName.isNotBlank()) adminName else "أنت"
                else -> servantAliases[uid]?.takeIf { it.isNotBlank() }
                    ?: prof?.displayName?.takeIf { it.isNotBlank() }
                    ?: fellow?.displayName?.takeIf { it.isNotBlank() }
                    ?: if (isAdmin && adminName.isNotBlank()) adminName else "خادم"
            }
            GroupMemberListEntry(
                uid = uid,
                displayName = resolvedName,
                isCurrentUser = isMe,
                isAdmin = isAdmin,
                isAssistantAdmin = isAssistant,
                profile = prof ?: fellow?.memberProfile
            )
        }.sortedWith(
            compareByDescending<GroupMemberListEntry> { it.isAdmin }
                .thenByDescending { it.isAssistantAdmin }
                .thenByDescending { it.isCurrentUser }
                .thenBy { it.displayName }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .heightIn(max = 640.dp)
                        .clickable(enabled = false) {}
                        .testTag("group_members_info_sheet")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Drag handle pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )

                        // Group Identity Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GroupChatAvatarCircle(
                                groupId = groupId,
                                groupName = groupName,
                                avatarBase64 = groupAvatarBase64,
                                avatarHash = groupAvatarHash,
                                size = 60.dp,
                                isEditable = true,
                                onClick = onChangeGroupAvatar
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${allMembers.size} عضو في جروب الفصل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("group_members_sheet_close")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق"
                                )
                            }
                        }

                        // Quick Action: Change Group Photo
                        OutlinedButton(
                            onClick = onChangeGroupAvatar,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("group_sheet_change_avatar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تغيير صورة الجروب",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = extColors.hairline)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "أعضاء الجروب (${allMembers.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "اضغط على أي خادم لفتح محادثة خاصة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = allMembers,
                                key = { "group_member_${it.uid}" }
                            ) { member ->
                                val isActiveNow = ChatFormatHelper.isServantActiveNow(member.profile?.lastSeenAt)
                                val statusText = if (member.isCurrentUser) {
                                    "متصل الآن"
                                } else {
                                    ChatFormatHelper.formatLastSeenStatus(member.profile?.lastSeenAt)
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                    border = BorderStroke(1.dp, extColors.hairline),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(enabled = !member.isCurrentUser) {
                                            onSelectMemberForDm(member.uid, member.displayName)
                                        }
                                        .testTag("group_member_row_${member.uid}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box {
                                            ServantAvatarCircle(
                                                servantUid = member.uid,
                                                servantName = member.displayName,
                                                groupId = groupId,
                                                memberProfile = member.profile,
                                                accentColor = senderDeterministicColor(member.uid),
                                                size = 44.dp
                                            )
                                            if (isActiveNow || member.isCurrentUser) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .align(Alignment.BottomEnd)
                                                        .clip(CircleShape)
                                                        .background(extColors.onlineGreen)
                                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = member.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (member.isAdmin) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                    ) {
                                                        Text(
                                                            text = "أمين الفصل",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                } else if (member.isAssistantAdmin) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                                    ) {
                                                        Text(
                                                            text = "مساعد أمين",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (member.isCurrentUser) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "أنت",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActiveNow || member.isCurrentUser) {
                                                    extColors.onlineGreen
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardMessagePickerDialog(
    message: ChatMessage,
    chatState: ChatUiState,
    memberProfiles: List<ServantMemberProfile>,
    currentUserId: String,
    onSelectTargetRoom: (ActiveChatRoom) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إعادة توجيه الرسالة إلى...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.displayText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Target 1: Pinned Group Chat
                if (chatState.activeGroupId.isNotBlank()) {
                    val gTitle = chatState.activeGroupName.ifBlank { "جروب الفصل" }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTargetRoom(
                                    ActiveChatRoom.GroupRoom(
                                        groupId = chatState.activeGroupId,
                                        groupName = gTitle
                                    )
                                )
                            }
                            .testTag("forward_target_group")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GroupChatAvatarCircle(
                                groupId = chatState.activeGroupId,
                                groupName = gTitle,
                                avatarBase64 = chatState.activeGroupAvatarBase64,
                                avatarHash = chatState.activeGroupAvatarHash,
                                size = 38.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = gTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "جروب الخدام المشترك",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Target 2: Fellow Servants / Existing DMs
                val candidateTargets = remember(chatState.fellowServants, chatState.dmConversations, memberProfiles, chatState.servantAliases, currentUserId) {
                    val map = LinkedHashMap<String, Pair<String, ServantMemberProfile?>>()
                    chatState.fellowServants.forEach { fs ->
                        map[fs.uid] = fs.displayName to fs.memberProfile
                    }
                    chatState.dmConversations.forEach { dm ->
                        val pUid = dm.peerUid(currentUserId)
                        if (pUid.isNotBlank() && pUid != currentUserId && !map.containsKey(pUid)) {
                            val prof = memberProfiles.firstOrNull { it.effectiveUid == pUid }
                            val pName = dm.peerDisplayName(currentUserId, memberProfiles, chatState.servantAliases)
                            map[pUid] = pName to prof
                        }
                    }
                    map.entries.toList()
                }

                if (candidateTargets.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = candidateTargets,
                            key = { it.key }
                        ) { (peerUid, nameAndProf) ->
                            val (peerName, peerProf) = nameAndProf
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val chatId = DmChatMeta.buildDmChatId(currentUserId, peerUid)
                                        onSelectTargetRoom(
                                            ActiveChatRoom.DirectRoom(
                                                chatId = chatId,
                                                groupId = chatState.activeGroupId,
                                                peerUid = peerUid,
                                                peerName = peerName
                                            )
                                        )
                                    }
                                    .testTag("forward_target_dm_$peerUid")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ServantAvatarCircle(
                                        servantUid = peerUid,
                                        servantName = peerName,
                                        groupId = chatState.activeGroupId,
                                        memberProfile = peerProf,
                                        accentColor = senderDeterministicColor(peerUid),
                                        size = 38.dp
                                    )
                                    Text(
                                        text = peerName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun FullscreenChatImageDialog(
    bitmap: Bitmap,
    onSaveToDevice: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "معاينة الصورة",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 72.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White
                )
            }
            Button(
                onClick = onSaveToDevice,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .heightIn(min = 48.dp)
                    .testTag("fullscreen_save_chat_image_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "حفظ الصورة على الجهاز",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "حفظ الصورة على الجهاز",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DaySeparatorChip(label: String) {
    val extColors = LocalKamExtendedColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, extColors.hairline)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private enum class ReportChildCategory {
    ABSENT,
    NO_CARD,
    ISSUE,
    DIFF
}

private data class ParsedAttendanceChildEntry(
    val student: Student,
    val studentNumber: Long,
    val studentName: String,
    val status: AttendanceStatus,
    val category: ReportChildCategory = if (status == AttendanceStatus.ISSUE) ReportChildCategory.ISSUE else ReportChildCategory.ABSENT,
    val note: String = "",
    val firstStatusLabel: String = "",
    val secondStatusLabel: String = ""
)

private data class ParsedAttendanceChatReport(
    val servantName: String,
    val absentEntries: List<ParsedAttendanceChildEntry>,
    val noCardEntries: List<ParsedAttendanceChildEntry> = emptyList(),
    val issueEntries: List<ParsedAttendanceChildEntry>,
    val diffEntries: List<ParsedAttendanceChildEntry> = emptyList(),
    val presentNames: List<String> = emptyList(),
    val cardContextTitle: String = "",
    val reportTypeKey: String = "",
    val reportTypeLabel: String = "",
    val reportIcon: String = "",
    val reportColorKey: String = "",
    val diffAgainstServantName: String = ""
)

private enum class VisitationReportChildKind {
    VISITED,
    FOLLOWUP,
    NOT_VISITED
}

private data class ParsedVisitationChildEntry(
    val student: Student,
    val studentNumber: Long,
    val studentName: String,
    val kind: VisitationReportChildKind,
    val note: String = ""
)

private data class ParsedVisitationChatReport(
    val servantName: String,
    val scopeLabel: String,
    val visitedEntries: List<ParsedVisitationChildEntry>,
    val followupEntries: List<ParsedVisitationChildEntry>,
    val notVisitedEntries: List<ParsedVisitationChildEntry>
)

private data class BlockGroupedSummaryLine(
    val blockName: String,
    val blockColorHex: String?,
    val count: Int,
    val numbersLabel: String
)

private data class ReportCategorySummarySection(
    val title: String,
    val accentColor: Color,
    val totalCount: Int,
    val blockLines: List<BlockGroupedSummaryLine>,
    val emptyFallbackText: String = ""
)

private val LEGACY_ATTENDANCE_SPLIT_REGEX = Regex("""\s*[،,]\s*(?=[^()]*\(#\d+)""")
private val LEGACY_ATTENDANCE_ITEM_REGEX = Regex("""^(.+?)\s*\(\s*#(\d+)(?:\s*-\s*(.+?))?\s*\)$""")

private fun matchStudentForAttendanceReport(
    studentId: String,
    studentNumber: Long,
    studentName: String,
    students: List<Student>
): Student {
    val cleanId = studentId.trim()
    val cleanName = studentName.trim()
    val matched = students.firstOrNull { cleanId.isNotBlank() && it.id == cleanId }
        ?: students.firstOrNull { studentNumber > 0L && it.studentNumber == studentNumber }
        ?: students.firstOrNull { cleanName.isNotBlank() && it.name.trim() == cleanName }
    return matched ?: Student(
        id = cleanId.ifBlank { "tmp_${studentNumber}_${cleanName.hashCode()}" },
        name = cleanName.ifBlank { "طفل" },
        studentNumber = studentNumber,
        hasAvatar = false
    )
}

private fun buildBlockGroupedLines(
    entries: List<Pair<Student, Long>>,
    blocks: List<ServantBlock>
): List<BlockGroupedSummaryLine> {
    if (entries.isEmpty()) return emptyList()
    val sortedBlocks = blocks.sortedBy { it.sortOrder }
    val validBlockIds = sortedBlocks.map { it.id }.toSet()
    val grouped = entries.groupBy { (stu, _) ->
        if (stu.blockId.isNotBlank() && stu.blockId in validBlockIds) stu.blockId else ""
    }
    val lines = mutableListOf<BlockGroupedSummaryLine>()
    for (blk in sortedBlocks) {
        val list = grouped[blk.id].orEmpty()
        if (list.isNotEmpty()) {
            val nums = list
                .sortedBy { (_, num) -> num }
                .map { (stu, num) ->
                    if (num > 0L) "#$num" else stu.name.take(16)
                }
                .joinToString("، ")
            lines.add(
                BlockGroupedSummaryLine(
                    blockName = "بلوك ${blk.servantName}",
                    blockColorHex = blk.colorHex,
                    count = list.size,
                    numbersLabel = nums
                )
            )
        }
    }
    val unassigned = grouped[""].orEmpty()
    if (unassigned.isNotEmpty()) {
        val nums = unassigned
            .sortedBy { (_, num) -> num }
            .map { (stu, num) ->
                if (num > 0L) "#$num" else stu.name.take(16)
            }
            .joinToString("، ")
        lines.add(
            BlockGroupedSummaryLine(
                blockName = "بدون بلوك",
                blockColorHex = null,
                count = unassigned.size,
                numbersLabel = nums
            )
        )
    }
    return lines
}

private fun parseVisitationSystemMessage(
    rawText: String,
    students: List<Student>
): ParsedVisitationChatReport? {
    val trimmed = rawText.trim()
    if (!trimmed.startsWith(ChatMessage.VISITATION_REPORT_V1_PREFIX)) return null

    var servant = "خادم"
    var scopeLabel = ""
    val visited = mutableListOf<ParsedVisitationChildEntry>()
    val followup = mutableListOf<ParsedVisitationChildEntry>()
    val notVisited = mutableListOf<ParsedVisitationChildEntry>()

    for (rawLine in trimmed.lineSequence()) {
        val line = rawLine.trim()
        when {
            line.startsWith("S|") -> {
                servant = line.removePrefix("S|").trim().ifBlank { "خادم" }
            }
            line.startsWith("B|") -> {
                scopeLabel = line.removePrefix("B|").trim()
            }
            line.startsWith("V|") -> {
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val sId = parts[1].trim()
                    val sNum = parts[2].trim().toLongOrNull() ?: 0L
                    val sName = parts[3].trim()
                    val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                    visited.add(
                        ParsedVisitationChildEntry(
                            student = matched,
                            studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                            studentName = sName.ifBlank { matched.name },
                            kind = VisitationReportChildKind.VISITED
                        )
                    )
                }
            }
            line.startsWith("F|") -> {
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val sId = parts[1].trim()
                    val sNum = parts[2].trim().toLongOrNull() ?: 0L
                    val sName = parts[3].trim()
                    val note = parts.getOrNull(4).orEmpty().trim()
                    val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                    followup.add(
                        ParsedVisitationChildEntry(
                            student = matched,
                            studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                            studentName = sName.ifBlank { matched.name },
                            kind = VisitationReportChildKind.FOLLOWUP,
                            note = note
                        )
                    )
                }
            }
            line.startsWith("U|") -> {
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val sId = parts[1].trim()
                    val sNum = parts[2].trim().toLongOrNull() ?: 0L
                    val sName = parts[3].trim()
                    val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                    notVisited.add(
                        ParsedVisitationChildEntry(
                            student = matched,
                            studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                            studentName = sName.ifBlank { matched.name },
                            kind = VisitationReportChildKind.NOT_VISITED
                        )
                    )
                }
            }
        }
    }

    if (visited.isEmpty() && followup.isEmpty() && notVisited.isEmpty()) return null
    return ParsedVisitationChatReport(
        servantName = servant,
        scopeLabel = scopeLabel,
        visitedEntries = visited.sortedBy { it.studentNumber },
        followupEntries = followup.sortedBy { it.studentNumber },
        notVisitedEntries = notVisited.sortedBy { it.studentNumber }
    )
}

private fun parseAttendanceSystemMessage(
    rawText: String,
    students: List<Student>
): ParsedAttendanceChatReport? {
    val trimmed = rawText.trim()
    if (trimmed.isBlank()) return null

    if (trimmed.startsWith(ChatMessage.ATTENDANCE_REPORT_V3_PREFIX) ||
        trimmed.startsWith(ChatMessage.ATTENDANCE_REPORT_V2_PREFIX)
    ) {
        var servant = "خادم"
        var cardContextTitle = ""
        var reportTypeKey = ""
        var reportTypeLabel = ""
        var reportIcon = ""
        var reportColorKey = ""
        var diffAgainstServant = ""
        val absent = mutableListOf<ParsedAttendanceChildEntry>()
        val noCard = mutableListOf<ParsedAttendanceChildEntry>()
        val issue = mutableListOf<ParsedAttendanceChildEntry>()
        val diffEntries = mutableListOf<ParsedAttendanceChildEntry>()
        val presentNames = mutableListOf<String>()

        for (rawLine in trimmed.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("S|") -> {
                    servant = line.removePrefix("S|").trim().ifBlank { "خادم" }
                }
                line.startsWith("C|") -> {
                    cardContextTitle = line.removePrefix("C|").trim()
                }
                line.startsWith("T|") -> {
                    reportTypeKey = line.removePrefix("T|").trim()
                }
                line.startsWith("TL|") -> {
                    reportTypeLabel = line.removePrefix("TL|").trim()
                }
                line.startsWith("IC|") -> {
                    reportIcon = line.removePrefix("IC|").trim()
                }
                line.startsWith("CK|") -> {
                    reportColorKey = line.removePrefix("CK|").trim()
                }
                line.startsWith("DIFF|") -> {
                    diffAgainstServant = line.removePrefix("DIFF|").trim()
                }
                line.startsWith("PL|") -> {
                    val rawNames = line.removePrefix("PL|").trim()
                    if (rawNames.isNotBlank()) {
                        rawNames.split("•").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                            presentNames.add(it)
                        }
                    }
                }
                line.startsWith("P|") -> {
                    val parts = line.split("|")
                    val pName = parts.getOrNull(2)?.trim().orEmpty().ifBlank { parts.getOrNull(1)?.trim().orEmpty() }
                    if (pName.isNotBlank()) presentNames.add(pName)
                }
                line.startsWith("D|") -> {
                    val parts = line.split("|")
                    if (parts.size >= 6) {
                        val sId = parts[1].trim()
                        val sNum = parts[2].trim().toLongOrNull() ?: 0L
                        val sName = parts[3].trim()
                        val firstLbl = parts[4].trim()
                        val secondLbl = parts[5].trim()
                        val note = parts.getOrNull(6).orEmpty().trim()
                        val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                        diffEntries.add(
                            ParsedAttendanceChildEntry(
                                student = matched,
                                studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                                studentName = sName.ifBlank { matched.name },
                                status = AttendanceStatus.ISSUE,
                                category = ReportChildCategory.DIFF,
                                note = note,
                                firstStatusLabel = firstLbl,
                                secondStatusLabel = secondLbl
                            )
                        )
                    }
                }
                line.startsWith("A|") -> {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val sId = parts[1].trim()
                        val sNum = parts[2].trim().toLongOrNull() ?: 0L
                        val sName = parts[3].trim()
                        val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                        absent.add(
                            ParsedAttendanceChildEntry(
                                student = matched,
                                studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                                studentName = sName.ifBlank { matched.name },
                                status = AttendanceStatus.ABSENT,
                                category = ReportChildCategory.ABSENT
                            )
                        )
                    }
                }
                line.startsWith("N|") -> {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val sId = parts[1].trim()
                        val sNum = parts[2].trim().toLongOrNull() ?: 0L
                        val sName = parts[3].trim()
                        val cardLabel = parts.getOrNull(4).orEmpty().trim()
                        val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                        noCard.add(
                            ParsedAttendanceChildEntry(
                                student = matched,
                                studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                                studentName = sName.ifBlank { matched.name },
                                status = AttendanceStatus.ABSENT,
                                category = ReportChildCategory.NO_CARD,
                                note = if (cardLabel.isNotBlank()) "بدون كارت $cardLabel" else "ما خدش كارت"
                            )
                        )
                    }
                }
                line.startsWith("I|") -> {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val sId = parts[1].trim()
                        val sNum = parts[2].trim().toLongOrNull() ?: 0L
                        val sName = parts[3].trim()
                        val note = parts.getOrNull(4).orEmpty().trim()
                        val matched = matchStudentForAttendanceReport(sId, sNum, sName, students)
                        issue.add(
                            ParsedAttendanceChildEntry(
                                student = matched,
                                studentNumber = if (sNum > 0L) sNum else matched.studentNumber,
                                studentName = sName.ifBlank { matched.name },
                                status = AttendanceStatus.ISSUE,
                                category = ReportChildCategory.ISSUE,
                                note = note
                            )
                        )
                    }
                }
            }
        }
        if (absent.isNotEmpty() || noCard.isNotEmpty() || issue.isNotEmpty() || diffEntries.isNotEmpty() || presentNames.isNotEmpty()) {
            return ParsedAttendanceChatReport(
                servantName = servant,
                absentEntries = absent.sortedBy { it.studentNumber },
                noCardEntries = noCard.sortedBy { it.studentNumber },
                issueEntries = issue.sortedBy { it.studentNumber },
                diffEntries = diffEntries.sortedBy { it.studentNumber },
                presentNames = presentNames.distinct(),
                cardContextTitle = cardContextTitle,
                reportTypeKey = reportTypeKey,
                reportTypeLabel = reportTypeLabel,
                reportIcon = reportIcon,
                reportColorKey = reportColorKey,
                diffAgainstServantName = diffAgainstServant
            )
        }
    }

    // Legacy compatibility: "📋 تحضير الفصل (بواسطة X) — غائب (N): name (#5)، ... | عمل مشكلة (M): name (#47)"
    if (trimmed.contains("تحضير الفصل") && (trimmed.contains("غائب (") || trimmed.contains("عمل مشكلة ("))) {
        val servantMatch = Regex("""بواسطة\s+([^)]+)\)""").find(trimmed)
        val servant = servantMatch?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank { "خادم" }
        val bodyAfterDash = trimmed.substringAfter("—", trimmed)
        val segments = bodyAfterDash.split("|")
        val absent = mutableListOf<ParsedAttendanceChildEntry>()
        val issue = mutableListOf<ParsedAttendanceChildEntry>()

        for (seg in segments) {
            val cleanSeg = seg.trim()
            val isAbsentSeg = cleanSeg.startsWith("غائب")
            val isIssueSeg = cleanSeg.startsWith("عمل مشكلة")
            if (!isAbsentSeg && !isIssueSeg) continue
            val listText = cleanSeg.substringAfter(":", "").trim()
            if (listText.isBlank()) continue
            val items = listText.split(LEGACY_ATTENDANCE_SPLIT_REGEX)
            for (rawItem in items) {
                val item = rawItem.trim()
                val m = LEGACY_ATTENDANCE_ITEM_REGEX.find(item) ?: continue
                val name = m.groupValues[1].trim()
                val num = m.groupValues[2].trim().toLongOrNull() ?: 0L
                val note = m.groupValues[3].trim()
                val matched = matchStudentForAttendanceReport("", num, name, students)
                val entry = ParsedAttendanceChildEntry(
                    student = matched,
                    studentNumber = if (num > 0L) num else matched.studentNumber,
                    studentName = name.ifBlank { matched.name },
                    status = if (isAbsentSeg) AttendanceStatus.ABSENT else AttendanceStatus.ISSUE,
                    category = if (isAbsentSeg) ReportChildCategory.ABSENT else ReportChildCategory.ISSUE,
                    note = note
                )
                if (isAbsentSeg) absent.add(entry) else issue.add(entry)
            }
        }
        if (absent.isNotEmpty() || issue.isNotEmpty()) {
            return ParsedAttendanceChatReport(
                servantName = servant,
                absentEntries = absent.sortedBy { it.studentNumber },
                issueEntries = issue.sortedBy { it.studentNumber },
                reportTypeKey = "ATTENDANCE",
                reportTypeLabel = "الحضور",
                reportIcon = "📋",
                reportColorKey = "EMERALD"
            )
        }
    }
    return null
}

@Composable
private fun BlockGroupedNumbersFooter(
    sections: List<ReportCategorySummarySection>,
    grandTotalText: String,
    grandTotalAccent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (sec in sections) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (sec.totalCount == 0 && sec.emptyFallbackText.isNotBlank()) {
                        sec.emptyFallbackText
                    } else {
                        "• ${sec.title} (${sec.totalCount} طفل):"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = sec.accentColor
                )
                for (blkLine in sec.blockLines) {
                    val blkColor = if (!blkLine.blockColorHex.isNullOrBlank()) {
                        KamSemanticColors.blockColor(blkLine.blockColorHex)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(blkColor)
                        )
                        Text(
                            text = "${blkLine.blockName} (${blkLine.count}): ${blkLine.numbersLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (grandTotalText.isNotBlank()) {
            HorizontalDivider(color = grandTotalAccent.copy(alpha = 0.25f))
            Surface(
                color = grandTotalAccent.copy(alpha = 0.13f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = grandTotalText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = grandTotalAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun VisitationReportCard(
    message: ChatMessage,
    report: ParsedVisitationChatReport,
    blocks: List<ServantBlock>,
    onInspectStudentPhoto: (Student, String) -> Unit,
    onRequestForward: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val visitationAccent = KamSemanticColors.NotificationVisitation
    val photoAlertEntries = remember(report) {
        report.followupEntries + report.notVisitedEntries
    }

    val footerSections = remember(report, blocks, extColors) {
        buildList {
            if (report.followupEntries.isNotEmpty()) {
                add(
                    ReportCategorySummarySection(
                        title = "عملوا مشكلة / يحتاجون متابعة",
                        accentColor = extColors.offlineAmber,
                        totalCount = report.followupEntries.size,
                        blockLines = buildBlockGroupedLines(
                            report.followupEntries.map { it.student to it.studentNumber },
                            blocks
                        )
                    )
                )
            }
            if (report.notVisitedEntries.isNotEmpty()) {
                add(
                    ReportCategorySummarySection(
                        title = "لم يتم افتقادهم",
                        accentColor = KamSemanticColors.NotificationAbsentIssue,
                        totalCount = report.notVisitedEntries.size,
                        blockLines = buildBlockGroupedLines(
                            report.notVisitedEntries.map { it.student to it.studentNumber },
                            blocks
                        )
                    )
                )
            }
        }
    }

    val totalAlerts = photoAlertEntries.size
    val totalAll = report.visitedEntries.size + totalAlerts
    val grandTotalText = buildString {
        append("المجموع الكلي: $totalAll طفل (تم افتقاده: ${report.visitedEntries.size}")
        if (report.followupEntries.isNotEmpty()) append(" • متابعة/مشكلة: ${report.followupEntries.size}")
        if (report.notVisitedEntries.isNotEmpty()) append(" • لم يُفتقد: ${report.notVisitedEntries.size}")
        append(")")
    }

    Surface(
        color = visitationAccent.copy(alpha = 0.11f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, visitationAccent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .testTag("visitation_merged_report_card_${message.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Clear Visitation Badge + Scope + Servant + Forward
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = visitationAccent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🏠 تقرير الافتقاد",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    if (report.scopeLabel.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, visitationAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = report.scopeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "بواسطة ${report.servantName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = onRequestForward,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("forward_message_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "إعادة توجيه التقرير",
                            tint = visitationAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // TOP SECTION: Visited Children — Small Font Stacked Vertically One Under Another (NO photos)
            if (report.visitedEntries.isNotEmpty()) {
                Surface(
                    color = extColors.onlineGreen.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extColors.onlineGreen.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("visitation_visited_names_list_${message.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "✓ تم افتقادهم (${report.visitedEntries.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = extColors.onlineGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        for (entry in report.visitedEntries) {
                            val numPrefix = if (entry.studentNumber > 0L) "#${entry.studentNumber} — " else "• "
                            Text(
                                text = "$numPrefix${entry.studentName}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                ),
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // BOTTOM SECTION: Photo + Name Cards ONLY for Children with Problems/Follow-Up OR Not Visited
            if (photoAlertEntries.isNotEmpty()) {
                Text(
                    text = "⚠️ يحتاجون متابعة / لم يتم افتقادهم (${photoAlertEntries.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
                val rows = remember(photoAlertEntries) { photoAlertEntries.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowItems in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (entry in rowItems) {
                                val (badgeColor, badgeLabel) = when (entry.kind) {
                                    VisitationReportChildKind.FOLLOWUP -> extColors.offlineAmber to "متابعة / مشكلة"
                                    VisitationReportChildKind.NOT_VISITED -> MaterialTheme.colorScheme.error to "لم يتم افتقاده"
                                    VisitationReportChildKind.VISITED -> extColors.onlineGreen to "تم افتقاده"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.50f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onInspectStudentPhoto(entry.student, entry.note) }
                                        .testTag("visitation_alert_card_${entry.student.id}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Surface(
                                            color = badgeColor.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = badgeColor,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        StudentAvatarCircle(
                                            student = entry.student,
                                            blockAccent = badgeColor,
                                            size = 62.dp,
                                            onClick = { onInspectStudentPhoto(entry.student, entry.note) },
                                            testTag = "visitation_alert_avatar_${entry.student.id}"
                                        )
                                        if (entry.studentNumber > 0L) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "#${entry.studentNumber}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = entry.studentName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (entry.note.isNotBlank()) {
                                            Text(
                                                text = entry.note,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = badgeColor,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Block-grouped Footer + Grand Total
            BlockGroupedNumbersFooter(
                sections = footerSections,
                grandTotalText = grandTotalText,
                grandTotalAccent = visitationAccent
            )

            // Timestamp footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = ChatFormatHelper.formatMessageTime(message.effectiveTimeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SystemMessageCenteredPill(
    message: ChatMessage,
    students: List<Student>,
    blocks: List<ServantBlock> = emptyList(),
    customCardConfig: CustomCardConfig = CustomCardConfig(),
    onInspectStudentPhoto: (Student, String) -> Unit,
    onRequestForward: () -> Unit = {}
) {
    // 1. Check Merged Live Visitation Report ([VISITATION_V1]) first
    val parsedVisitationReport = remember(message.displayText, students) {
        if (message.isDeleted) null else parseVisitationSystemMessage(message.displayText, students)
    }
    if (parsedVisitationReport != null) {
        VisitationReportCard(
            message = message,
            report = parsedVisitationReport,
            blocks = blocks,
            onInspectStudentPhoto = onInspectStudentPhoto,
            onRequestForward = onRequestForward
        )
        return
    }

    // 2. Check Attendance / Card / Cross-Servant Diff Report ([ATTENDANCE_V3] / [ATTENDANCE_V2] / Legacy)
    val parsedReport = remember(message.displayText, students) {
        if (message.isDeleted) null else parseAttendanceSystemMessage(message.displayText, students)
    }

    if (parsedReport == null) {
        val category = remember(message.notificationCategory, message.displayText) {
            NotificationCategory.fromId(message.notificationCategory) ?: when {
                message.displayText.contains("مرتين") || message.displayText.contains("تضارب") -> NotificationCategory.CARD_CONFLICT
                message.displayText.contains("غياب") || message.displayText.contains("غائب") || message.displayText.contains("مشكلة") -> NotificationCategory.ABSENT_ISSUE
                message.displayText.contains("افتقاد") -> NotificationCategory.VISITATION
                message.displayText.contains("تسميع") || message.displayText.contains("محفوظات") -> NotificationCategory.MEMORIZATION
                message.displayText.contains("تعليق") -> NotificationCategory.CHILD_COMMENT
                else -> NotificationCategory.GENERAL_SYSTEM
            }
        }
        val accentColor = KamSemanticColors.notificationColor(category)
        val hasChildMetadata = message.childId.isNotBlank() || message.childName.isNotBlank()

        if (hasChildMetadata && !message.isDeleted) {
            val matchedStudent = remember(message.childId, message.childName, students) {
                students.firstOrNull { message.childId.isNotBlank() && it.id == message.childId }
                    ?: students.firstOrNull { message.childName.isNotBlank() && it.name.trim() == message.childName.trim() }
                    ?: Student(
                        id = message.childId.ifBlank { "notify_${message.id}" },
                        name = message.childName.ifBlank { "طفل" },
                        avatarInitials = ArabicInitialsHelper.extractInitials(message.childName.ifBlank { "طفل" })
                    )
            }
            val effectiveChildName = message.childName.ifBlank { matchedStudent.name.ifBlank { "طفل" } }
            val effectiveBlockName = message.childBlockName.ifBlank { "بدون بلوك" }

            // Rule 3 & Rule 4: Triple-Rule Child Notification Card with Fixed Category Color
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp)
                    .testTag("child_notification_card_${message.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (message.forwardedFromName.isNotBlank()) {
                        Text(
                            text = "↪ محوّلة من ${message.forwardedFromName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Header Row: Category Chip + Block Badge + Forward Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = accentColor,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${category.badgeEmoji} ${category.arabicLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = effectiveBlockName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onRequestForward,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("forward_message_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "إعادة توجيه الإشعار",
                                tint = accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Mandatory Triple Row: Child Avatar + Child Name + Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudentAvatarCircle(
                            student = matchedStudent,
                            blockAccent = accentColor,
                            size = 50.dp,
                            onClick = { onInspectStudentPhoto(matchedStudent, message.displayText) },
                            testTag = "notify_child_avatar_${message.id}"
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (matchedStudent.studentNumber > 0L) {
                                    "#${matchedStudent.studentNumber} — $effectiveChildName"
                                } else {
                                    effectiveChildName
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = message.displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.senderName.isNotBlank()) {
                            Text(
                                text = "بواسطة: ${message.senderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        Text(
                            text = ChatFormatHelper.formatMessageTime(message.effectiveTimeMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 20.dp)
                .testTag("system_message_${message.id}"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
            ) {
                Text(
                    text = message.displayText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    val extColors = LocalKamExtendedColors.current

    // Resolve explicit Report Type Badge (Attendance vs Specific Card) with exact icon & color
    val resolvedCardType = remember(parsedReport.reportTypeKey) {
        if (parsedReport.reportTypeKey.startsWith("CARD:")) {
            CardType.fromId(parsedReport.reportTypeKey.removePrefix("CARD:"))
        } else {
            null
        }
    }
    val isCardReport = resolvedCardType != null ||
        parsedReport.cardContextTitle.isNotBlank() ||
        parsedReport.noCardEntries.isNotEmpty()
    val effectiveTypeLabel = remember(parsedReport, resolvedCardType, customCardConfig) {
        when {
            parsedReport.reportTypeLabel.isNotBlank() -> parsedReport.reportTypeLabel
            resolvedCardType != null -> {
                val t = resolvedCardType.displayTitle(customCardConfig).trim()
                if (t.startsWith("كارت")) t else "كارت $t"
            }
            parsedReport.cardContextTitle.isNotBlank() -> {
                val t = parsedReport.cardContextTitle.trim()
                if (t.startsWith("كارت")) t else "كارت $t"
            }
            else -> "حضور عادي"
        }
    }
    val effectiveIcon = remember(parsedReport, resolvedCardType, customCardConfig) {
        parsedReport.reportIcon.ifBlank {
            resolvedCardType?.displaySymbol(customCardConfig) ?: if (isCardReport) "⭐" else "📋"
        }
    }
    val reportHeaderColor = remember(parsedReport.reportColorKey, resolvedCardType, customCardConfig, isCardReport, extColors) {
        when {
            parsedReport.reportColorKey.isNotBlank() -> KamSemanticColors.cardColorByKey(parsedReport.reportColorKey)
            resolvedCardType != null -> KamSemanticColors.cardColor(resolvedCardType, customCardConfig)
            isCardReport -> extColors.cardGold
            else -> extColors.cardAttendance
        }
    }

    // Visual Style 0: Cross-Servant Diff Report (ONLY differences compared to Servant A's fixed baseline)
    if (parsedReport.diffAgainstServantName.isNotBlank() && parsedReport.diffEntries.isNotEmpty()) {
        val diffAccent = KamSemanticColors.NotificationCardConflict
        val diffFooterSections = remember(parsedReport.diffEntries, blocks, diffAccent) {
            listOf(
                ReportCategorySummarySection(
                    title = "الاختلافات حسب البلوك",
                    accentColor = diffAccent,
                    totalCount = parsedReport.diffEntries.size,
                    blockLines = buildBlockGroupedLines(
                        parsedReport.diffEntries.map { it.student to it.studentNumber },
                        blocks
                    )
                )
            )
        }
        Surface(
            color = diffAccent.copy(alpha = 0.11f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.5.dp, diffAccent.copy(alpha = 0.62f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp)
                .testTag("attendance_diff_report_card_${message.id}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = diffAccent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "🔄 اختلافات تقرير $effectiveTypeLabel",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = onRequestForward,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("forward_message_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "إعادة توجيه التقرير",
                            tint = diffAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Clear comparison banner: Servant B vs Servant A (baseline)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, diffAccent.copy(alpha = 0.38f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$effectiveIcon تقرير ${parsedReport.servantName} يختلف عن تقرير ${parsedReport.diffAgainstServantName} في ${parsedReport.diffEntries.size} طفل:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // 3-Column Photo Grid for Differing Children
                val diffRows = remember(parsedReport.diffEntries) { parsedReport.diffEntries.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (rowItems in diffRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (entry in rowItems) {
                                val diffSummaryText = buildString {
                                    append("عند ${parsedReport.diffAgainstServantName}: ${entry.firstStatusLabel}\n")
                                    append("عند ${parsedReport.servantName}: ${entry.secondStatusLabel}")
                                    if (entry.note.isNotBlank()) append("\nملاحظة: ${entry.note}")
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, diffAccent.copy(alpha = 0.55f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onInspectStudentPhoto(entry.student, diffSummaryText) }
                                        .testTag("diff_child_card_${entry.student.id}")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            color = diffAccent.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "اختلاف",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = diffAccent,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                        StudentAvatarCircle(
                                            student = entry.student,
                                            blockAccent = diffAccent,
                                            size = 60.dp,
                                            onClick = { onInspectStudentPhoto(entry.student, diffSummaryText) },
                                            testTag = "diff_child_avatar_${entry.student.id}"
                                        )
                                        if (entry.studentNumber > 0L) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "#${entry.studentNumber}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = entry.studentName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            color = diffAccent.copy(alpha = 0.11f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "${parsedReport.diffAgainstServantName}: ${entry.firstStatusLabel}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "← ${parsedReport.servantName}: ${entry.secondStatusLabel}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = diffAccent,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (entry.note.isNotBlank()) {
                                            Text(
                                                text = entry.note,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                                color = extColors.offlineAmber,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                BlockGroupedNumbersFooter(
                    sections = diffFooterSections,
                    grandTotalText = "المجموع الكلي للاختلافات: ${parsedReport.diffEntries.size} طفل",
                    grandTotalAccent = diffAccent
                )
            }
        }
        return
    }

    val combinedAlertEntries = remember(parsedReport) {
        parsedReport.absentEntries + parsedReport.noCardEntries + parsedReport.issueEntries
    }

    // Visual Style 1: Present-Only Compact Notification (explicit Report Type Header + small-font names only, no photo cards)
    if (combinedAlertEntries.isEmpty() && parsedReport.presentNames.isNotEmpty()) {
        Surface(
            color = reportHeaderColor.copy(alpha = 0.09f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, reportHeaderColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .testTag("attendance_present_only_pill_${message.id}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = reportHeaderColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$effectiveIcon تقرير $effectiveTypeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "بواسطة ${parsedReport.servantName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isCardReport) {
                        "✓ أخذوا $effectiveTypeLabel (${parsedReport.presentNames.size} طفل):"
                    } else {
                        "✓ الحاضرون (${parsedReport.presentNames.size} طفل):"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = extColors.onlineGreen
                )
                Text(
                    text = parsedReport.presentNames.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(color = reportHeaderColor.copy(alpha = 0.25f))
                AttendanceReportBottomSummaryFooter(
                    presentCount = parsedReport.presentNames.size,
                    troublemakerCount = parsedReport.issueEntries.size,
                    absentCount = parsedReport.absentEntries.size,
                    noCardCount = parsedReport.noCardEntries.size,
                    isCardReport = isCardReport,
                    servantName = parsedReport.servantName,
                    registrationTypeLabel = effectiveTypeLabel,
                    registrationIcon = effectiveIcon,
                    accentColor = reportHeaderColor,
                    timestampMillis = message.effectiveTimeMillis,
                    messageId = message.id
                )
            }
        }
        return
    }

    // Visual Style 2: Prominent Alert Report (Explicit Report Type Header + Photo Cards + Compact Present List + Block-Grouped Footer & Grand Total)
    val errorColor = MaterialTheme.colorScheme.error
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val alertFooterSections = remember(parsedReport, blocks, errorColor, tertiaryColor, extColors, isCardReport, effectiveTypeLabel) {
        buildList {
            if (parsedReport.absentEntries.isNotEmpty() || (!isCardReport && parsedReport.noCardEntries.isEmpty())) {
                add(
                    ReportCategorySummarySection(
                        title = "غائب النهاردة",
                        accentColor = errorColor,
                        totalCount = parsedReport.absentEntries.size,
                        blockLines = buildBlockGroupedLines(
                            parsedReport.absentEntries.map { it.student to it.studentNumber },
                            blocks
                        ),
                        emptyFallbackText = "• غائب النهاردة: 0 أطفال"
                    )
                )
            }
            if (parsedReport.noCardEntries.isNotEmpty()) {
                add(
                    ReportCategorySummarySection(
                        title = "ما خدوش $effectiveTypeLabel",
                        accentColor = tertiaryColor,
                        totalCount = parsedReport.noCardEntries.size,
                        blockLines = buildBlockGroupedLines(
                            parsedReport.noCardEntries.map { it.student to it.studentNumber },
                            blocks
                        )
                    )
                )
            }
            if (parsedReport.issueEntries.isNotEmpty() || (!isCardReport && parsedReport.noCardEntries.isEmpty())) {
                add(
                    ReportCategorySummarySection(
                        title = "عندهم مشكلة النهاردة",
                        accentColor = extColors.offlineAmber,
                        totalCount = parsedReport.issueEntries.size,
                        blockLines = buildBlockGroupedLines(
                            parsedReport.issueEntries.map { it.student to it.studentNumber },
                            blocks
                        ),
                        emptyFallbackText = "• عندهم مشكلة النهاردة: 0 أطفال"
                    )
                )
            }
        }
    }

    val grandTotalAlertText = remember(parsedReport, combinedAlertEntries.size, isCardReport) {
        val breakdownParts = buildList {
            if (parsedReport.absentEntries.isNotEmpty()) add("غائب: ${parsedReport.absentEntries.size}")
            if (parsedReport.noCardEntries.isNotEmpty()) add("بدون كارت: ${parsedReport.noCardEntries.size}")
            if (parsedReport.issueEntries.isNotEmpty()) add("مشكلة: ${parsedReport.issueEntries.size}")
            if (parsedReport.presentNames.isNotEmpty()) {
                add(if (isCardReport) "أخذوا الكارت: ${parsedReport.presentNames.size}" else "حاضر: ${parsedReport.presentNames.size}")
            }
        }
        val mainCountLabel = if (isCardReport) {
            "المجموع الكلي (لم يأخذ/مشكلة): ${combinedAlertEntries.size} طفل"
        } else {
            "المجموع الكلي (غائب/مشكلة): ${combinedAlertEntries.size} طفل"
        }
        if (breakdownParts.isNotEmpty()) {
            "$mainCountLabel — (${breakdownParts.joinToString(" • ")})"
        } else {
            mainCountLabel
        }
    }

    Surface(
        color = reportHeaderColor.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, reportHeaderColor.copy(alpha = 0.52f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 6.dp)
            .testTag("attendance_visual_report_card_${message.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Header with unmistakable Report Type Badge (Attendance vs Specific Card)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = reportHeaderColor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("report_type_header_badge_${message.id}")
                ) {
                    Text(
                        text = "$effectiveIcon تقرير $effectiveTypeLabel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "بواسطة ${parsedReport.servantName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    IconButton(
                        onClick = onRequestForward,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("forward_message_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "إعادة توجيه التقرير",
                            tint = reportHeaderColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // 3-Column Visual Photo Grid (Photo + Number & Name for Absent / No-Card / Issue)
            val rows = remember(combinedAlertEntries) { combinedAlertEntries.chunked(3) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rowItems in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (entry in rowItems) {
                            val (badgeColor, badgeLabel) = when (entry.category) {
                                ReportChildCategory.ABSENT -> MaterialTheme.colorScheme.error to "غائب"
                                ReportChildCategory.NO_CARD -> MaterialTheme.colorScheme.tertiary to "ما خدش كارت"
                                ReportChildCategory.ISSUE -> extColors.offlineAmber to "مشكلة"
                                ReportChildCategory.DIFF -> KamSemanticColors.NotificationCardConflict to "اختلاف"
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.48f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onInspectStudentPhoto(entry.student, entry.note) }
                                    .testTag("report_child_card_${entry.student.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = badgeColor,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }

                                    StudentAvatarCircle(
                                        student = entry.student,
                                        blockAccent = badgeColor,
                                        size = 62.dp,
                                        onClick = { onInspectStudentPhoto(entry.student, entry.note) },
                                        testTag = "report_child_avatar_${entry.student.id}"
                                    )

                                    if (entry.studentNumber > 0L) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "#${entry.studentNumber}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = entry.studentName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (entry.note.isNotBlank()) {
                                        Text(
                                            text = entry.note,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = badgeColor,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Compact Small-Font Section: "اللي جه (الحاضرون)" / "أخذوا الكارت" inside the SAME report
            if (parsedReport.presentNames.isNotEmpty()) {
                Surface(
                    color = extColors.onlineGreen.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, extColors.onlineGreen.copy(alpha = 0.32f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_present_names_strip_${message.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (isCardReport) {
                                "✓ أخذوا $effectiveTypeLabel (${parsedReport.presentNames.size}):"
                            } else {
                                "✓ اللي جه (${parsedReport.presentNames.size}):"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = extColors.onlineGreen
                        )
                        Text(
                            text = parsedReport.presentNames.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            HorizontalDivider(color = reportHeaderColor.copy(alpha = 0.30f))

            // Block-Grouped Footer Summary + Counts + Numbers + Grand Total
            BlockGroupedNumbersFooter(
                sections = alertFooterSections,
                grandTotalText = grandTotalAlertText,
                grandTotalAccent = reportHeaderColor
            )

            // Bottom-most Summary Footer: Troublemakers count, Present count, Recorder Name & Registration Type
            AttendanceReportBottomSummaryFooter(
                presentCount = parsedReport.presentNames.size,
                troublemakerCount = parsedReport.issueEntries.size,
                absentCount = parsedReport.absentEntries.size,
                noCardCount = parsedReport.noCardEntries.size,
                isCardReport = isCardReport,
                servantName = parsedReport.servantName,
                registrationTypeLabel = effectiveTypeLabel,
                registrationIcon = effectiveIcon,
                accentColor = reportHeaderColor,
                timestampMillis = message.effectiveTimeMillis,
                messageId = message.id
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendanceReportBottomSummaryFooter(
    presentCount: Int,
    troublemakerCount: Int,
    absentCount: Int,
    noCardCount: Int,
    isCardReport: Boolean,
    servantName: String,
    registrationTypeLabel: String,
    registrationIcon: String,
    accentColor: Color,
    timestampMillis: Long,
    messageId: String
) {
    val extColors = LocalKamExtendedColors.current
    val timeLabel = remember(timestampMillis) {
        if (timestampMillis > 0L) ChatFormatHelper.formatMessageTime(timestampMillis) else ""
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_bottom_summary_footer_$messageId")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    color = extColors.onlineGreen.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, extColors.onlineGreen.copy(alpha = 0.40f))
                ) {
                    Text(
                        text = if (isCardReport) "✓ حاضر (أخذ الكارت): $presentCount" else "✓ حاضر: $presentCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = extColors.onlineGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = extColors.offlineAmber.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, extColors.offlineAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "⚠️ مشاغب: $troublemakerCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = extColors.offlineAmber,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (absentCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.38f))
                    ) {
                        Text(
                            text = "✕ غائب: $absentCount",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                if (noCardCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.40f))
                    ) {
                        Text(
                            text = "✕ بدون كارت: $noCardCount",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.18f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "المسجّل: ${servantName.ifBlank { "خادم" }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "نوع التسجيل: $registrationIcon $registrationTypeLabel",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (timeLabel.isNotBlank()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttendanceReportedChildPhotoDialog(
    student: Student,
    note: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullBitmap by remember(student.id, student.effectiveAvatarKey) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var packedAvatar by remember(student.id, student.effectiveAvatarKey) {
        mutableStateOf("")
    }

    LaunchedEffect(student.id, student.effectiveAvatarKey) {
        val packed = StudentAvatarImageHelper.resolveStudentPackedAvatar(context, student)
        packedAvatar = packed
        if (packed.isNotBlank()) {
            val main = StudentAvatarImageHelper.extractMainPhotoBase64(packed)
            fullBitmap = withContext(Dispatchers.Default) {
                StudentAvatarImageHelper.decodeFullPhotoToImageBitmap(context, main)
                    ?: StudentAvatarImageHelper.decodeBase64ToImageBitmap(main, context)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (student.studentNumber > 0L) "#${student.studentNumber} — ${student.name}" else student.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val extColors = LocalKamExtendedColors.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(KamBackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = fullBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "صورة ${student.name}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = student.avatarInitials,
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                if (note.isNotBlank()) {
                    Text(
                        text = "ملاحظة: $note",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = extColors.offlineAmber
                    )
                }
                if (packedAvatar.isNotBlank()) {
                    val mainEntry = remember(packedAvatar) {
                        StudentAvatarImageHelper.extractMainPhotoBase64(packedAvatar)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    StudentAvatarImageHelper.saveFullPhotoToDeviceGallery(
                                        context = context,
                                        studentName = student.name,
                                        photoEntry = mainEntry
                                    )
                                }
                                res.onSuccess {
                                    Toast.makeText(
                                        context,
                                        "تم حفظ صورة ${student.name} في المعرض ✓",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }.onFailure {
                                    Toast.makeText(context, "تعذر حفظ الصورة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ صورة الطفل على الجهاز", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق")
            }
        }
    )
}

@Composable
private fun TelegramStyleMessageBubble(
    message: ChatMessage,
    resolvedSenderName: String,
    isOwnMessage: Boolean,
    showSenderName: Boolean,
    mediaStorage: ChatMediaStorageProvider,
    audioPlayer: ChatAudioPlayerController,
    onOpenFullscreenImage: (Bitmap) -> Unit,
    onRequestForward: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val bubbleBgColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bubbleTextColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleSubtleColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleShape = if (isOwnMessage) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_bubble_${message.id}"),
        horizontalArrangement = if (isOwnMessage) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = bubbleBgColor,
            shape = bubbleShape,
            border = if (isOwnMessage) null else BorderStroke(1.dp, extColors.hairline),
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(min = 104.dp, max = 315.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (showSenderName && resolvedSenderName.isNotBlank()) {
                    Text(
                        text = resolvedSenderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = senderDeterministicColor(message.senderUid),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (message.forwardedFromName.isNotBlank() && !message.isDeleted) {
                    Text(
                        text = "↪ محوّلة من ${message.forwardedFromName}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    message.isDeleted -> {
                        Text(
                            text = ChatMessage.DELETED_MESSAGE_PLACEHOLDER,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = bubbleSubtleColor
                        )
                    }

                    message.messageType == ChatMessageType.IMAGE -> {
                        ChatImageBubbleContent(
                            message = message,
                            mediaStorage = mediaStorage,
                            textColor = bubbleTextColor,
                            subtleColor = bubbleSubtleColor,
                            onOpenFullscreenImage = onOpenFullscreenImage
                        )
                    }

                    message.messageType == ChatMessageType.AUDIO -> {
                        ChatAudioBubbleContent(
                            message = message,
                            isOwnMessage = isOwnMessage,
                            mediaStorage = mediaStorage,
                            audioPlayer = audioPlayer,
                            textColor = bubbleTextColor,
                            subtleColor = bubbleSubtleColor
                        )
                    }

                    else -> {
                        Text(
                            text = message.displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = bubbleTextColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (!message.isDeleted) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "إعادة توجيه الرسالة",
                            tint = bubbleSubtleColor,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable(onClick = onRequestForward)
                                .testTag("forward_message_${message.id}")
                        )
                    }

                    if (isOwnMessage && !message.isDeleted) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الرسالة",
                            tint = bubbleSubtleColor,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable(onClick = onRequestDelete)
                                .testTag("delete_message_${message.id}")
                        )
                    }

                    Text(
                        text = ChatFormatHelper.formatMessageTime(message.effectiveTimeMillis),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = bubbleSubtleColor
                    )

                    if (isOwnMessage) {
                        if (message.isPendingOffline) {
                            Text(
                                text = "⏱",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = bubbleSubtleColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "تم الإرسال",
                                tint = bubbleSubtleColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatImageBubbleContent(
    message: ChatMessage,
    mediaStorage: ChatMediaStorageProvider,
    textColor: Color,
    subtleColor: Color,
    onOpenFullscreenImage: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(message.id, message.mediaBase64) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(message.id, message.mediaBase64) { mutableStateOf(true) }
    var isSavingImage by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id, message.mediaBase64) {
        isLoading = true
        bitmap = mediaStorage.loadOrCacheImageBitmap(context, message)
        isLoading = false
    }

    val aspect = if (message.imageWidth > 0 && message.imageHeight > 0) {
        (message.imageWidth.toFloat() / message.imageHeight.toFloat()).coerceIn(0.65f, 1.85f)
    } else {
        1.25f
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 240.dp)
                .aspectRatio(aspect, matchHeightConstraintsFirst = false)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .clickable(enabled = bitmap != null) {
                    bitmap?.let(onOpenFullscreenImage)
                }
                .testTag("chat_image_bubble_${message.id}"),
            contentAlignment = Alignment.Center
        ) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = "صورة مرسلة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = textColor
                )
            } else {
                Text(
                    text = "📷 تعذّر عرض الصورة",
                    style = MaterialTheme.typography.labelMedium,
                    color = subtleColor
                )
            }
        }

        val customCaption = message.text.trim()
        if (customCaption.isNotEmpty() && customCaption != ChatMessage.IMAGE_DEFAULT_LABEL) {
            Text(
                text = customCaption,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }

        if (bitmap != null || message.mediaBase64.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = textColor.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isSavingImage) {
                        isSavingImage = true
                        scope.launch {
                            mediaStorage.saveImageToDeviceGallery(context, message)
                                .onSuccess {
                                    Toast.makeText(
                                        context,
                                        "تم حفظ الصورة في المعرض (Pictures/kAM_Lite) ✓",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        err.localizedMessage ?: "تعذّر حفظ الصورة",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            isSavingImage = false
                        }
                    }
                    .testTag("save_chat_image_${message.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSavingImage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = textColor
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "حفظ الصورة على الجهاز",
                            tint = textColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSavingImage) "جاري الحفظ..." else "حفظ الصورة على الجهاز",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatAudioBubbleContent(
    message: ChatMessage,
    isOwnMessage: Boolean,
    mediaStorage: ChatMediaStorageProvider,
    audioPlayer: ChatAudioPlayerController,
    textColor: Color,
    subtleColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cachedFile by remember(message.id, message.mediaBase64) { mutableStateOf<File?>(null) }
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var progress by remember(message.id) { mutableFloatStateOf(0f) }
    var isSavingAudio by remember(message.id) { mutableStateOf(false) }

    LaunchedEffect(message.id, message.mediaBase64) {
        cachedFile = mediaStorage.loadOrCacheAudioFile(context, message)
    }

    LaunchedEffect(isPlaying, message.id) {
        while (isPlaying) {
            if (audioPlayer.activeMessageId != message.id || !audioPlayer.isPlaying) {
                isPlaying = false
                progress = 0f
                break
            }
            val dur = audioPlayer.durationMs.coerceAtLeast(1)
            val pos = audioPlayer.currentPositionMs.coerceAtLeast(0)
            progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            delay(150L)
        }
    }

    val durationLabel = ChatFormatHelper.formatAudioDuration(
        message.audioDurationSec.coerceIn(1, ChatMessage.MAX_AUDIO_DURATION_SEC)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("chat_audio_bubble_${message.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = cachedFile != null) {
                    val file = cachedFile ?: return@clickable
                    audioPlayer.playOrToggle(
                        messageId = message.id,
                        audioFile = file,
                        onCompletion = {
                            isPlaying = false
                            progress = 0f
                        },
                        onStateChanged = {
                            isPlaying = audioPlayer.activeMessageId == message.id && audioPlayer.isPlaying
                        }
                    )
                }
                .testTag("play_audio_message_${message.id}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "تشغيل الرسالة الصوتية",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress },
                color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                trackColor = subtleColor.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "رسالة صوتية",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtleColor
                )
            }
        }

        if (cachedFile != null || message.mediaBase64.isNotBlank()) {
            IconButton(
                onClick = {
                    if (isSavingAudio) return@IconButton
                    isSavingAudio = true
                    scope.launch {
                        mediaStorage.saveAudioToDeviceDownloads(context, message)
                            .onSuccess {
                                Toast.makeText(
                                    context,
                                    "تم حفظ التسجيل الصوتي في التنزيلات (Downloads/kAM_Lite) ✓",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            .onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.localizedMessage ?: "تعذّر حفظ التسجيل الصوتي",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        isSavingAudio = false
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("save_chat_audio_${message.id}")
            ) {
                if (isSavingAudio) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = textColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "حفظ التسجيل الصوتي على الجهاز",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
