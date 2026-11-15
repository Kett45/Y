package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.model.ChatFormatHelper
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.DmChatMeta
import com.example.data.model.ServantMemberProfile
import com.example.ui.components.ServantAvatarCircle
import com.example.ui.viewmodel.ChatUiState
import com.example.ui.viewmodel.GroupFellowServantItem
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

private val ServantSenderPalette = listOf(
    Color(0xFF38BDF8), // Sky Blue
    Color(0xFFF59E0B), // Amber
    Color(0xFFA855F7), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF10B981), // Emerald
    Color(0xFFF97316), // Orange
    Color(0xFF06B6D4)  // Cyan
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
    currentUserId: String,
    mediaStorage: ChatMediaStorageProvider = InlineFirestoreChatMediaStorageProvider,
    onOpenGroupChat: () -> Unit,
    onOpenDirectChat: (peerUid: String, peerName: String) -> Unit,
    onCloseActiveChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImageMessage: (Uri, String) -> Unit = { _, _ -> },
    onSendAudioMessage: (File, Int) -> Unit = { _, _ -> },
    onClearMediaError: () -> Unit = {},
    onDeleteOwnMessage: (ChatMessage) -> Unit,
    onLoadOlderMessages: () -> Unit
) {
    var showNewDmPickerDialog by rememberSaveable { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val activeRoom = chatState.activeRoom
        if (activeRoom != null) {
            ChatConversationView(
                room = activeRoom,
                chatState = chatState,
                memberProfiles = memberProfiles,
                currentUserId = currentUserId,
                mediaStorage = mediaStorage,
                onBack = onCloseActiveChat,
                onSendMessage = onSendMessage,
                onSendImageMessage = onSendImageMessage,
                onSendAudioMessage = onSendAudioMessage,
                onClearMediaError = onClearMediaError,
                onDeleteOwnMessage = onDeleteOwnMessage,
                onLoadOlderMessages = onLoadOlderMessages
            )
        } else {
            ConversationsListView(
                chatState = chatState,
                memberProfiles = memberProfiles,
                currentUserId = currentUserId,
                onOpenGroupChat = onOpenGroupChat,
                onOpenDirectChat = onOpenDirectChat,
                onOpenNewDmPicker = { showNewDmPickerDialog = true }
            )
        }

        if (showNewDmPickerDialog) {
            NewPrivateChatServantPickerDialog(
                groupName = chatState.activeGroupName,
                fellowServants = chatState.fellowServants,
                groupId = chatState.activeGroupId,
                onSelectServant = { item ->
                    showNewDmPickerDialog = false
                    onOpenDirectChat(item.uid, item.displayName)
                },
                onDismiss = { showNewDmPickerDialog = false }
            )
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
    onOpenNewDmPicker: () -> Unit
) {
    val profileByUid = remember(memberProfiles) {
        memberProfiles.associateBy { it.effectiveUid }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("conversations_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            item(key = "messages_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "الرسائل",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (chatState.activeGroupName.isNotBlank()) {
                                "تواصل خدام ${chatState.activeGroupName}"
                            } else {
                                "شات الفصل والمحادثات الخاصة"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenNewDmPicker,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("start_private_chat_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "محادثة خاصة جديدة",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "محادثة خاصة",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 1. Pinned Group Chat Room ("جروب الفصل")
            item(key = "pinned_group_chat_room") {
                PinnedGroupChatCard(
                    groupName = chatState.activeGroupName,
                    servantsCount = chatState.groupMemberUids.size.coerceAtLeast(1),
                    groupMeta = chatState.groupChatMeta,
                    unreadCount = chatState.groupUnreadCount,
                    currentUserId = currentUserId,
                    onClick = onOpenGroupChat
                )
            }

            // 2. Private Conversations Section Header
            item(key = "dm_section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
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
                        text = "${chatState.dmConversations.size} محادثة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (chatState.dmConversations.isEmpty()) {
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
                                text = "لا توجد محادثات خاصة بعد",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "اضغط على زر (+) لبدء محادثة خاصة مع أي خادم مشترك معك في نفس الفصل.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = chatState.dmConversations,
                    key = { dm -> "dm_row_${dm.effectiveChatId}" }
                ) { dm ->
                    val peerUid = dm.peerUid(currentUserId)
                    val peerProfile = profileByUid[peerUid]
                    val peerName = dm.peerDisplayName(currentUserId, memberProfiles)
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
                .padding(18.dp)
                .testTag("new_dm_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "بدء محادثة خاصة"
            )
        }
    }
}

@Composable
private fun PinnedGroupChatCard(
    groupName: String,
    servantsCount: Int,
    groupMeta: com.example.data.model.GroupChatMeta?,
    unreadCount: Int,
    currentUserId: String,
    onClick: () -> Unit
) {
    val lastText = when {
        groupMeta == null || groupMeta.lastMessageText.isBlank() ->
            "غرفة خدام الفصل ($servantsCount خدام) — اضغط للفتح"
        groupMeta.lastMessageType == ChatMessageType.SYSTEM.id ->
            groupMeta.lastMessageText
        groupMeta.lastMessageSenderUid == currentUserId ->
            "أنت: ${groupMeta.lastMessageText}"
        groupMeta.lastMessageSenderName.isNotBlank() ->
            "${groupMeta.lastMessageSenderName}: ${groupMeta.lastMessageText}"
        else -> groupMeta.lastMessageText
    }
    val timeLabel = ChatFormatHelper.formatConversationListTime(groupMeta?.lastMessageAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("pinned_group_chat_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "جروب الفصل",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "جروب الفصل",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "مثبّت",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                if (groupName.isNotBlank()) {
                    Text(
                        text = groupName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
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
    val isActiveNow = ChatFormatHelper.isServantActiveNow(peerProfile?.lastSeenAt)
    val lastText = when {
        dm.lastMessageText.isBlank() -> "اضغط لبدء المحادثة"
        dm.lastMessageSenderUid == currentUserId -> "أنت: ${dm.lastMessageText}"
        else -> dm.lastMessageText
    }
    val timeLabel = ChatFormatHelper.formatConversationListTime(dm.lastMessageAt ?: dm.updatedAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("dm_card_$peerUid"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (unreadCount > 0) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    size = 48.dp,
                    isEditable = false
                )
                if (isActiveNow) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
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
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal
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
                        fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
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
        color = Color(0xFFEF4444),
        contentColor = Color.White,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun NewPrivateChatServantPickerDialog(
    groupName: String,
    fellowServants: List<GroupFellowServantItem>,
    groupId: String,
    onSelectServant: (GroupFellowServantItem) -> Unit,
    onDismiss: () -> Unit
) {
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
                        text = "بدء محادثة خاصة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (groupName.isNotBlank()) {
                        Text(
                            text = "خدام $groupName",
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
            if (fellowServants.isEmpty()) {
                Text(
                    text = "لا يوجد خدام آخرون في نفس المجموعة حالياً. يمكنك دعوة خدام الفصل من تبويب المجموعة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
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
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
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
    currentUserId: String,
    mediaStorage: ChatMediaStorageProvider,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendImageMessage: (Uri, String) -> Unit,
    onSendAudioMessage: (File, Int) -> Unit,
    onClearMediaError: () -> Unit,
    onDeleteOwnMessage: (ChatMessage) -> Unit,
    onLoadOlderMessages: () -> Unit
) {
    val context = LocalContext.current
    val audioPlayer = remember { ChatAudioPlayerController() }
    val voiceRecorder = remember(context) { ChatVoiceRecorderHelper(context.applicationContext) }

    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var localVoiceError by remember { mutableStateOf<String?>(null) }
    var fullscreenBitmapPreview by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(room.roomKey) {
        onDispose {
            audioPlayer.stop()
            voiceRecorder.cancelRecording()
            isRecordingAudio = false
        }
    }

    BackHandler {
        if (isRecordingAudio) {
            voiceRecorder.cancelRecording()
            isRecordingAudio = false
        } else {
            onBack()
        }
    }

    var draftText by rememberSaveable(room.roomKey) { mutableStateOf("") }
    var messagePendingDelete by remember { mutableStateOf<ChatMessage?>(null) }

    // Zero-permission Photo Picker for Chat Images (<= 1024px, < 60KB)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val caption = draftText.trim()
            draftText = ""
            onSendImageMessage(uri, caption)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_conversation_screen")
    ) {
        // Conversation Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            tonalElevation = 3.dp,
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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = "جروب الفصل",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "جروب الفصل — ${room.groupName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "غرفة خدام الفصل (${chatState.groupMemberUids.size.coerceAtLeast(1)} خدام)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is ActiveChatRoom.DirectRoom -> {
                        val isActiveNow = ChatFormatHelper.isServantActiveNow(peerProfile?.lastSeenAt)
                        val statusText = ChatFormatHelper.formatLastSeenStatus(peerProfile?.lastSeenAt)
                        ServantAvatarCircle(
                            servantUid = room.peerUid,
                            servantName = room.peerName,
                            groupId = room.groupId,
                            memberProfile = peerProfile,
                            accentColor = senderDeterministicColor(room.peerUid),
                            size = 42.dp,
                            isEditable = false
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = room.peerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActiveNow) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isActiveNow) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Messages Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                                if (msg.messageType == ChatMessageType.SYSTEM) {
                                    SystemMessageCenteredPill(message = msg)
                                } else {
                                    WhatsAppStyleMessageBubble(
                                        message = msg,
                                        isOwnMessage = msg.senderUid == currentUserId,
                                        showSenderName = room is ActiveChatRoom.GroupRoom && msg.senderUid != currentUserId,
                                        mediaStorage = mediaStorage,
                                        audioPlayer = audioPlayer,
                                        onOpenFullscreenImage = { bmp -> fullscreenBitmapPreview = bmp },
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

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        // Bottom Input Bar (Text + Image Picker <=1024px/<60KB + Voice Recorder <=30s/<100KB)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
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
                                .background(Color(0xFFEF4444))
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
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Attach Image Button (<= 1024px, < 60KB)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(enabled = !chatState.isSendingMedia) {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("chat_attach_image_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "📷",
                                    fontSize = 20.sp
                                )
                            }
                        }

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
                                    text = "اكتب رسالة...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        val canSendText = draftText.trim().isNotEmpty()
                        if (canSendText) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
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
                                        tint = Color.White
                                    )
                                }
                            }
                        } else {
                            // Record Voice Note Button (<= 30s, mono, < 100KB)
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
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
                                    Text(
                                        text = "🎤",
                                        fontSize = 20.sp
                                    )
                                }
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

    // Fullscreen Image Preview Dialog
    val previewBitmap = fullscreenBitmapPreview
    if (previewBitmap != null) {
        FullscreenChatImageDialog(
            bitmap = previewBitmap,
            onDismiss = { fullscreenBitmapPreview = null }
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
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun FullscreenChatImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "معاينة الصورة",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun DaySeparatorChip(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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

@Composable
private fun SystemMessageCenteredPill(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 20.dp)
            .testTag("system_message_${message.id}"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0xFFF59E0B).copy(alpha = 0.16f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
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
}

@Composable
private fun WhatsAppStyleMessageBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    showSenderName: Boolean,
    mediaStorage: ChatMediaStorageProvider,
    audioPlayer: ChatAudioPlayerController,
    onOpenFullscreenImage: (Bitmap) -> Unit,
    onRequestDelete: () -> Unit
) {
    val context = LocalContext.current
    val bubbleBgColor = if (isOwnMessage) {
        Color(0xFF065F46) // WhatsApp dark emerald outgoing bubble
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleTextColor = if (isOwnMessage) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleSubtleColor = if (isOwnMessage) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bubbleShape = if (isOwnMessage) {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
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
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(min = 96.dp, max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showSenderName && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = senderDeterministicColor(message.senderUid),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
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
                        Text(
                            text = if (message.isPendingOffline) "⏱" else "✓",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = bubbleSubtleColor
                        )
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
    var bitmap by remember(message.id, message.mediaBase64) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(message.id, message.mediaBase64) { mutableStateOf(true) }

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
    var cachedFile by remember(message.id, message.mediaBase64) { mutableStateOf<File?>(null) }
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var progress by remember(message.id) { mutableFloatStateOf(0f) }

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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isOwnMessage) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(40.dp)
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
                                .background(if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onPrimary)
                        )
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onPrimary)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "تشغيل الرسالة الصوتية",
                        tint = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onPrimary
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
                color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.primary,
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
                Text(
                    text = "🎤 رسالة صوتية",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtleColor
                )
            }
        }
    }
}
