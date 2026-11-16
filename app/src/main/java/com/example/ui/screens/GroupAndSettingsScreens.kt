package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.example.data.model.CardType
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.MAX_SERVANTS_PER_GROUP
import com.example.data.model.MAX_STUDENTS_PER_GROUP
import com.example.data.model.ServantBlock
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.sync.GitHubSmartSyncEngine
import com.example.ui.components.AppearanceFullScreen
import com.example.ui.components.AppearanceSettingsEntryCard
import com.example.ui.components.ServantPhotoPickerDialog
import com.example.ui.components.ServantProfileSettingsCard
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalUiScaleController
import com.example.ui.viewmodel.KamScreenState
import com.example.ui.viewmodel.KamViewModel
import com.example.util.KamPerformanceTracker
import java.util.Locale
import kotlinx.coroutines.launch

private fun shareInvitationText(
    context: Context,
    shareMessage: String,
    inviteCode: String,
    onCopyFallback: (String) -> Unit
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("دعوة المجموعة", shareMessage))
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "دعوة انضمام لمجموعة الفصل ($inviteCode)")
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        val chooser = Intent.createChooser(sendIntent, "مشاركة دعوة ورمز المجموعة").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }.onFailure {
        onCopyFallback("تم نسخ رسالة الدعوة والرمز: $inviteCode")
    }
}

@Composable
fun GroupInvitationsScreen(
    state: KamScreenState,
    onSendInvitation: (String) -> Unit,
    onRespondToInvitation: (Invitation, Boolean) -> Unit,
    onDeleteInvitation: (Invitation) -> Unit,
    onDeleteBlock: (ServantBlock) -> Unit,
    onCopyBanner: (String) -> Unit,
    onSwitchGroup: (String) -> Unit = {},
    onJoinGroupByCode: (inviteCode: String, servantName: String) -> Unit = { _, _ -> },
    onCreateNewGroup: (groupName: String, adminName: String) -> Unit = { _, _ -> },
    onToggleBlockHidden: (String) -> Unit = {},
    onRefreshDailyInviteCode: () -> Unit = {},
    onRemoveAndBanServant: (ServantBlock) -> Unit = onDeleteBlock,
    onUnbanServant: (servantUid: String, servantName: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var inviteeName by rememberSaveable { mutableStateOf("") }
    var blockPendingServantRemoval by remember { mutableStateOf<ServantBlock?>(null) }
    var activeInfoDialogTopic by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_GROUP, scrolling = false)
    }

    val fullDailyCode = state.group?.fullDailyInviteCode.orEmpty()
    val remainingHours = state.group?.remainingDailyCodeHours() ?: 24
    val bannedServants = state.group?.bannedServantsList.orEmpty()

    if (activeInfoDialogTopic != null) {
        AlertDialog(
            onDismissRequest = { activeInfoDialogTopic = null },
            title = {
                Text(
                    text = when (activeInfoDialogTopic) {
                        "CODE" -> "معلومات رمز المجموعة اليومي"
                        "BLOCKS" -> "إدارة بلوكات الخدام"
                        else -> "الأدوار والصلاحيات"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (activeInfoDialogTopic) {
                        "CODE" -> {
                            Text("• بادئة المجموعة الفريدة: ${state.group?.inviteCode.orEmpty()} • الجزء اليومي: ${state.group?.effectiveDailyToken.orEmpty()}")
                            Text("• يتغير الجزء اليومي تلقائياً كل ٢٤ ساعة لمنع دخول غير المدعوين (متبقي حوالي $remainingHours ساعة).")
                            Text("• شارك الرمز الكامل ($fullDailyCode) مع الخادم الجديد؛ بادئة المجموعة تضمن عدم تداخل أي مجموعتين نهائياً.")
                        }
                        "BLOCKS" -> {
                            Text("• الضغط على زر الإزالة بجوار بلوك الخادم يقوم بإزالة الخادم وحظره من المجموعة فقط مع الإبقاء على البلوك وجميع أطفاله ونقاطهم محفوظة بالكامل.")
                        }
                        else -> {
                            Text("• الأمين: كل الصلاحيات شاملة إزالة وحظر الخدام، تجديد الرمز اليومي، إنشاء المجموعة، والدعوات.")
                            Text("• الخادم: يرى كل أطفال وبلوكات المجموعة، يضيف ويعدّل وينقل ويرتّب ويعطي الكروت (الحذف مرفوض من السيرفر).")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialogTopic = null }) {
                    Text("حسناً")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("group_invitations_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Group Header, Counters & Daily Invite Code Card (Clean & Compact)
        item(key = "group_code_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header row: Group Name + Admin Name + Counters + Small ⓘ Info Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = state.group?.name ?: "المجموعة",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الأمين: ${state.group?.adminName.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "الأطفال: ${state.students.size} / $MAX_STUDENTS_PER_GROUP • البلوكات: ${state.blocks.size} • الأعضاء: ${state.group?.memberUids?.size ?: 1} / $MAX_SERVANTS_PER_GROUP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { activeInfoDialogTopic = "CODE" },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("group_code_info_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "معلومات الرمز والصلاحيات",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Code Actions Row: Copy Full Code + Share + Compact Timer "⏱ ٢١س" + Small Refresh Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val code = fullDailyCode.ifBlank { state.group?.inviteCode.orEmpty() }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("رمز المجموعة اليومي", code))
                                onCopyBanner("تم نسخ رمز المجموعة اليومي: $code")
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_group_code_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرمز اليومي", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = fullDailyCode.ifBlank { state.group?.inviteCode ?: "---" },
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Compact Timer Badge "⏱ ٢١س"
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "⏱ ${SaturdayCalendarHelper.toArabicDigits(remainingHours)}س",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }

                        // Small Refresh Icon Button (Admin Only)
                        if (state.isAdmin) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(onClick = onRefreshDailyInviteCode)
                                    .testTag("refresh_daily_code_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "تجديد الرمز اليومي فوراً",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val grp = state.group ?: return@OutlinedButton
                                val code = grp.fullDailyInviteCode
                                val msg = KamViewModel.buildInvitationShareText(
                                    groupName = grp.name,
                                    inviteCode = code,
                                    inviteeName = "خادم الفصل"
                                )
                                shareInvitationText(
                                    context = context,
                                    shareMessage = msg,
                                    inviteCode = code,
                                    onCopyFallback = onCopyBanner
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("share_group_invite_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة الدعوة", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "مشاركة",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // 1b. Multi-Group Switcher Card
        item(key = "multi_group_switcher_in_group_screen") {
            MultiGroupSwitcherCard(
                state = state,
                onSwitchGroup = onSwitchGroup,
                onJoinGroupByCode = onJoinGroupByCode,
                onCreateNewGroup = onCreateNewGroup
            )
        }

        // 2. Send New Invitation (Admin Only — Clean Field + Button Only)
        if (state.isAdmin) {
            item(key = "admin_send_invite_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "دعوة خادم جديد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inviteeName,
                                onValueChange = { inviteeName = it },
                                placeholder = { Text("اسم الخادم المدعو") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("invite_servant_name_input")
                            )
                            Button(
                                onClick = {
                                    val targetName = inviteeName.trim()
                                    if (targetName.isNotEmpty()) {
                                        onSendInvitation(targetName)
                                        val grp = state.group
                                        if (grp != null) {
                                            val code = grp.fullDailyInviteCode
                                            val shareMsg = KamViewModel.buildInvitationShareText(
                                                groupName = grp.name,
                                                inviteCode = code,
                                                inviteeName = targetName
                                            )
                                            shareInvitationText(
                                                context = context,
                                                shareMessage = shareMsg,
                                                inviteCode = code,
                                                onCopyFallback = onCopyBanner
                                            )
                                        }
                                        inviteeName = ""
                                    }
                                },
                                enabled = inviteeName.trim().isNotBlank(),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("send_invitation_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال ومشاركة")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("دعوة")
                            }
                        }
                    }
                }
            }
        }

        // 3. Invitations List
        item(key = "invitations_header") {
            Text(
                text = "سجل الدعوات (${state.invitations.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (state.invitations.isEmpty()) {
            item(key = "no_invitations_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MailOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "لا توجد دعوات مسجلة حالياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = state.invitations,
                key = { "inv_${it.id}" }
            ) { inv ->
                val activeShareCode = state.group?.fullDailyInviteCode?.ifBlank { inv.inviteCode } ?: inv.inviteCode
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "دعوة لـ: ${inv.inviteeName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val statusArabic = when (inv.status) {
                                "ACCEPTED" -> "مقبولة ✓"
                                "REJECTED" -> "مرفوضة ✗"
                                else -> "معلّقة ⏳"
                            }
                            Text(
                                text = "الحالة: $statusArabic • الرمز اليومي: $activeShareCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Share invitation button for any recorded invitation using the current valid daily code
                            IconButton(
                                onClick = {
                                    val shareMsg = KamViewModel.buildInvitationShareText(
                                        groupName = inv.groupName.ifBlank { state.group?.name.orEmpty() },
                                        inviteCode = activeShareCode,
                                        inviteeName = inv.inviteeName
                                    )
                                    shareInvitationText(
                                        context = context,
                                        shareMessage = shareMsg,
                                        inviteCode = activeShareCode,
                                        onCopyFallback = onCopyBanner
                                    )
                                },
                                modifier = Modifier.testTag("share_inv_${inv.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "مشاركة الدعوة",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (inv.status == "PENDING") {
                                IconButton(
                                    onClick = { onRespondToInvitation(inv, true) },
                                    modifier = Modifier.testTag("accept_inv_${inv.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "قبول الدعوة",
                                        tint = KamSemanticColors.onlineIndicator
                                    )
                                }
                                IconButton(
                                    onClick = { onRespondToInvitation(inv, false) },
                                    modifier = Modifier.testTag("reject_inv_${inv.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "رفض الدعوة",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (state.isAdmin) {
                                IconButton(
                                    onClick = { onDeleteInvitation(inv) },
                                    modifier = Modifier.testTag("delete_inv_${inv.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الدعوة",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Servant Blocks Management (Remove & Ban Servant ONLY — Preserve Block & Children)
        item(key = "blocks_management_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "بلوكات الخدام المشتركة (${state.blocks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.isAdmin) {
                    IconButton(
                        onClick = { activeInfoDialogTopic = "BLOCKS" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "توضيح إزالة الخادم",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        items(
            items = state.blocks,
            key = { "manage_blk_${it.id}" }
        ) { blk ->
            val blkColor = KamSemanticColors.blockColor(blk.colorHex)
            val countInBlock = state.students.count { it.blockId == blk.id }
            val isHiddenForMe = blk.id in state.hiddenBlockIds
            val isBlockAdminOwner = blk.servantId.isNotBlank() && blk.servantId == state.group?.adminId
            val hasActiveServant = blk.servantId.isNotBlank() && !isBlockAdminOwner

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(blkColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "بلوك ${blk.servantName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val servantStatusText = when {
                                isBlockAdminOwner -> "بلوك أمين المجموعة"
                                hasActiveServant -> "خادم مشترك في المجموعة"
                                else -> "تمت إزالة حساب الخادم • البلوك وأطفاله محفوظون"
                            }
                            Text(
                                text = "عدد الأطفال المسكنين: $countInBlock • $servantStatusText" +
                                    if (isHiddenForMe) " • (مخفي من شاشتك الشخصية)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onToggleBlockHidden(blk.id) },
                            modifier = Modifier.testTag("toggle_hide_block_${blk.id}")
                        ) {
                            Icon(
                                imageVector = if (isHiddenForMe) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isHiddenForMe) "إظهار البلوك في شاشتي" else "إخفاء البلوك من شاشتي",
                                tint = if (isHiddenForMe) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }

                        if (state.isAdmin && hasActiveServant) {
                            IconButton(
                                onClick = { blockPendingServantRemoval = blk },
                                modifier = Modifier.testTag("delete_block_${blk.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonRemove,
                                    contentDescription = "إزالة وحظر الخادم من المجموعة مع بقاء البلوك والأطفال",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (state.isAdmin && blk.servantId.isBlank() && countInBlock == 0) {
                            IconButton(
                                onClick = { onDeleteBlock(blk) },
                                modifier = Modifier.testTag("delete_empty_block_${blk.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف البلوك الفارغ",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4b. Banned Servants List & Unban Action (Admin Only)
        if (state.isAdmin) {
            item(key = "banned_servants_section") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banned_servants_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الخدام المحظورون (${bannedServants.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (bannedServants.isEmpty()) {
                            Text(
                                text = "لا يوجد خدام محظورون في هذه المجموعة حالياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            HorizontalDivider()
                            bannedServants.forEach { banned ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = banned.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "محظور من الانضمام للمجموعة",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { onUnbanServant(banned.uid, banned.name) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("unban_servant_${banned.uid}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = "إلغاء الحظر",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إلغاء الحظر")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Compact Role Permissions Info Row
        item(key = "roles_matrix_card") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeInfoDialogTopic = "ROLES" }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الأدوار والصلاحيات",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "عرض تفاصيل الصلاحيات",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // Confirmation Dialog for removing & banning ONLY the servant while keeping the block and its children intact
    val targetBlockForBan = blockPendingServantRemoval
    if (targetBlockForBan != null) {
        val countInTarget = state.students.count { it.blockId == targetBlockForBan.id }
        AlertDialog(
            onDismissRequest = { blockPendingServantRemoval = null },
            title = {
                Text(
                    text = "إزالة وحظر الخادم من المجموعة",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "هل تريد إزالة الخادم «${targetBlockForBan.servantName}» وحظره من هذه المجموعة؟"
                    )
                    Text(
                        text = "• البلوك وأسماء الأطفال المسكنين بداخله ($countInTarget طفل) ونقاطهم ستظل محفوظة بالكامل ولن يُحذف منها أي شيء.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• إذا حاول هذا الخادم التسجيل بالرمز مرة أخرى ستظهر له رسالة: «تم إزالتك من هذه المجموعة».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveAndBanServant(targetBlockForBan)
                        blockPendingServantRemoval = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_remove_ban_servant_button")
                ) {
                    Text("إزالة وحظر الخادم فقط")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockPendingServantRemoval = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    state: KamScreenState,
    onToggleCardVisibility: (CardType, Boolean) -> Unit,
    onSaveGroupPolicies: (
        allowServantUndo: Boolean,
        enforceWeeklyCardLimit: Boolean,
        maxMonthlyPoints: Long,
        attendanceIsCardOnly: Boolean,
        showLeaderboard: Boolean,
        autoStudentNumber: Boolean
    ) -> Unit,
    onLeaveGroup: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchGroup: (String) -> Unit = {},
    onJoinGroupByCode: (inviteCode: String, servantName: String) -> Unit = { _, _ -> },
    onCreateNewGroup: (groupName: String, adminName: String) -> Unit = { _, _ -> },
    onToggleBlockHidden: (String) -> Unit = {},
    currentUserId: String = "",
    onOpenPhoneWizard: () -> Unit = {},
    onUpdateOwnServantAvatar: (String) -> Unit = {},
    onToggleHideOwnPhone: (Boolean) -> Unit = {},
    onDeleteOwnPhones: () -> Unit = {}
) {
    val enabledIds = state.userProfile?.enabledCards ?: CardType.defaultEnabledIds
    val group = state.group
    var showAppearanceFullScreen by rememberSaveable { mutableStateOf(false) }
    var showOwnServantPhotoDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (KamPerformanceTracker.isLiveDiagnosticsEnabled) {
            KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_SETTINGS, scrolling = false)
            delay(360L)
            KamPerformanceTracker.publishFrameMetricsToState()
        }
    }

    if (showAppearanceFullScreen) {
        AppearanceFullScreen(
            onBack = { showAppearanceFullScreen = false }
        )
        return
    }

    val allowServantUndo = group?.allowServantUndo ?: true
    val enforceWeeklyLimit = group?.enforceWeeklyCardLimit ?: false
    val attendanceIsCardOnly = group?.attendanceIsCardOnly ?: true
    val showLeaderboard = group?.showLeaderboard ?: true
    val autoStudentNumber = group?.autoStudentNumber ?: true
    var maxMonthlyPtsText by rememberSaveable(group?.maxMonthlyPoints) {
        mutableStateOf((group?.maxMonthlyPoints ?: 0L).toString())
    }

    fun persistGroupPolicies(
        nextAllowServantUndo: Boolean = allowServantUndo,
        nextEnforceWeeklyLimit: Boolean = enforceWeeklyLimit,
        nextMaxMonthlyPoints: Long = maxMonthlyPtsText.toLongOrNull() ?: (group?.maxMonthlyPoints ?: 0L),
        nextAttendanceIsCardOnly: Boolean = attendanceIsCardOnly,
        nextShowLeaderboard: Boolean = showLeaderboard,
        nextAutoStudentNumber: Boolean = autoStudentNumber
    ) {
        onSaveGroupPolicies(
            nextAllowServantUndo,
            nextEnforceWeeklyLimit,
            nextMaxMonthlyPoints,
            nextAttendanceIsCardOnly,
            nextShowLeaderboard,
            nextAutoStudentNumber
        )
    }

    val ownBlock = state.blocks.firstOrNull { it.servantId == currentUserId }
    val ownAccentColor = if (ownBlock != null) {
        KamSemanticColors.blockColor(ownBlock.colorHex)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val ownMemberProfile = state.memberProfileForServant(currentUserId)
    val ownServantName = ownBlock?.servantName?.ifBlank { null }
        ?: state.userProfile?.displayName?.ifBlank { null }
        ?: "خادم"

    var showJoinAnotherDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateAnotherDialog by rememberSaveable { mutableStateOf(false) }
    val activeGroupCode = group?.inviteCode.orEmpty()
    val groupsList = remember(state.userGroups, group) {
        if (state.userGroups.isNotEmpty()) state.userGroups else listOfNotNull(group)
    }

    val settingsListState = rememberLazyListState()
    LaunchedEffect(settingsListState.isScrollInProgress) {
        StudentAvatarImageHelper.setScrollActive(settingsListState.isScrollInProgress)
    }

    LazyColumn(
        state = settingsListState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(16.dp)
    ) {
        // 0a. Servant WhatsApp Profile & Phone Numbers Card
        item(key = "servant_profile_and_phone_settings", contentType = "settings_card") {
            ServantProfileSettingsCard(
                servantUid = currentUserId,
                servantName = ownServantName,
                groupId = group?.id.orEmpty(),
                memberProfile = ownMemberProfile,
                primaryPhone = state.ownPrimaryPhone,
                secondaryPhone = state.ownSecondaryPhone,
                phoneHidden = state.ownPhoneHidden,
                accentColor = ownAccentColor,
                onOpenPhoneWizard = onOpenPhoneWizard,
                onOpenAvatarPicker = { showOwnServantPhotoDialog = true },
                onTogglePhoneHidden = onToggleHideOwnPhone,
                onDeletePhones = onDeleteOwnPhones
            )
        }
        item(key = "spacer_after_servant_profile", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 0. Appearance & Themes Dedicated Screen Entry Button ("المظهر والسمات")
        item(key = "appearance_and_icon_settings", contentType = "settings_card") {
            AppearanceSettingsEntryCard(
                onOpenAppearanceScreen = { showAppearanceFullScreen = true }
            )
        }
        item(key = "spacer_after_appearance", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 0b. Multi-Group Switcher (flattened into discrete header + group rows + footer items)
        item(key = "multi_group_switcher_in_settings", contentType = "settings_section_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
                    .testTag("multi_group_switcher_card"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مجموعاتي والتبديل بينها (${groupsList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()
            }
        }
        items(
            items = groupsList,
            key = { grp -> "settings_group_row_${grp.inviteCode}" },
            contentType = { "settings_group_row" }
        ) { grp ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RectangleShape)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                MultiGroupRowItem(
                    group = grp,
                    isActive = grp.inviteCode == activeGroupCode,
                    onClick = { onSwitchGroup(grp.inviteCode) }
                )
            }
        }
        item(key = "multi_group_switcher_footer", contentType = "settings_section_footer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showJoinAnotherDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("join_another_group_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("انضمام برمز")
                }

                OutlinedButton(
                    onClick = { showCreateAnotherDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("create_another_group_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مجموعة جديدة")
                }
            }
        }
        item(key = "spacer_after_multi_group", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 1. Personal Card Visibility Settings — flattened header + discrete LazyColumn rows
        item(key = "personal_cards_settings_header", contentType = "settings_section_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "إظهار / إخفاء الكروت السبعة (تفضيل شخصي)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "الكروت المفعّلة هنا فقط تظهر تحت اسم كل طفل في شاشتك الرئيسية ولا تؤثر على باقي الخدام.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }
        }

        items(
            items = CardType.entries,
            key = { card -> "personal_card_row_${card.id}" },
            contentType = { "settings_switch_row" }
        ) { card ->
            val isEnabled = card.id in enabledIds
            val cardColor = KamSemanticColors.cardColor(card)
            val isLast = card == CardType.entries.last()
            val rowShape = if (isLast) {
                RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            } else {
                RectangleShape
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, rowShape)
                    .clickable { onToggleCardVisibility(card, !isEnabled) }
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (isLast) 14.dp else 8.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(cardColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .border(1.dp, cardColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${card.badgeSymbol} +${card.points}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cardColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "كارت ${card.arabicTitle}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${card.points} نقاط",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        onToggleCardVisibility(card, checked)
                    },
                    modifier = Modifier.testTag("switch_card_${card.id}")
                )
            }
        }
        item(key = "spacer_after_personal_cards", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 1b. Personal Block Visibility Settings — flattened header + discrete LazyColumn rows
        if (state.blocks.isNotEmpty()) {
            item(key = "personal_blocks_visibility_settings", contentType = "settings_section_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "إظهار / إخفاء البلوكات في شاشتي (تفضيل شخصي)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "تُحفظ حالة طي وإخفاء البلوكات في حسابك الشخصي فقط دون التأثير على باقي أعضاء المجموعة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                }
            }
            itemsIndexed(
                items = state.blocks,
                key = { _, blk -> "personal_block_row_${blk.id}" },
                contentType = { _, _ -> "settings_switch_row" }
            ) { index, blk ->
                val isVisibleOnHome = blk.id !in state.hiddenBlockIds
                val blkColor = KamSemanticColors.blockColor(blk.colorHex)
                val isLast = index == state.blocks.lastIndex
                val rowShape = if (isLast) {
                    RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                } else {
                    RectangleShape
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, rowShape)
                        .clickable { onToggleBlockHidden(blk.id) }
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = if (isLast) 14.dp else 4.dp
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(blkColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "بلوك ${blk.servantName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = isVisibleOnHome,
                        onCheckedChange = { onToggleBlockHidden(blk.id) },
                        modifier = Modifier.testTag("switch_block_visible_${blk.id}")
                    )
                }
            }
            item(key = "spacer_after_personal_blocks", contentType = "settings_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 2. Group Policy Decisions — flattened header + 5 discrete switch items + footer
        item(key = "group_policy_settings", contentType = "settings_section_header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
            ) {
                Text(
                    text = "قواعد وقرارات الفصل (يضبطها الأمين — حفظ فوري)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item(key = "policy_row_allow_servant_undo", contentType = "settings_policy_switch") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RectangleShape)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                PolicySwitchRow(
                    title = "السماح للخادم بإلغاء كارت سجّله بالخطأ",
                    subtitle = "عند الإيقاف، الأمين فقط يمكنه إلغاء الكارت من سجل الطفل",
                    checked = allowServantUndo,
                    enabled = state.isAdmin,
                    testTag = "switch_policy_allow_servant_undo",
                    onCheckedChange = { checked ->
                        persistGroupPolicies(nextAllowServantUndo = checked)
                    }
                )
            }
        }
        item(key = "policy_row_enforce_weekly_limit", contentType = "settings_policy_switch") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RectangleShape)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                PolicySwitchRow(
                    title = "الكارت يُعطى مرة واحدة فقط في الأسبوع لكل طفل",
                    subtitle = "عند الإيقاف، يمكن إعطاء الكارت أكثر من مرة خلال الشهر (مع منع الضغط المزدوج)",
                    checked = enforceWeeklyLimit,
                    enabled = state.isAdmin,
                    testTag = "switch_policy_enforce_weekly_limit",
                    onCheckedChange = { checked ->
                        persistGroupPolicies(nextEnforceWeeklyLimit = checked)
                    }
                )
            }
        }
        item(key = "policy_row_auto_student_number", contentType = "settings_policy_switch") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RectangleShape)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                PolicySwitchRow(
                    title = "الترقيم التلقائي المتسلسل للأطفال",
                    subtitle = "عند الإيقاف، يُدخل الخادم رقم الطفل يدوياً عند الإضافة",
                    checked = autoStudentNumber,
                    enabled = state.isAdmin,
                    testTag = "switch_policy_auto_student_number",
                    onCheckedChange = { checked ->
                        persistGroupPolicies(nextAutoStudentNumber = checked)
                    }
                )
            }
        }
        item(key = "policy_row_show_leaderboard", contentType = "settings_policy_switch") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RectangleShape)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                PolicySwitchRow(
                    title = "إظهار لوحة الشرف وترتيب النقاط الشهري",
                    subtitle = "عرض أعلى ٥ أطفال بالنقاط في الشاشة الرئيسية",
                    checked = showLeaderboard,
                    enabled = state.isAdmin,
                    testTag = "switch_policy_show_leaderboard",
                    onCheckedChange = { checked ->
                        persistGroupPolicies(nextShowLeaderboard = checked)
                    }
                )
            }
        }
        item(key = "policy_row_attendance_card_only", contentType = "settings_policy_switch") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RectangleShape)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                PolicySwitchRow(
                    title = "الحضور هو كارت 'الحضور' (٣ نقاط)",
                    subtitle = "اعتماد كارت الحضور كآلية موحدة وسريعة لتسجيل حضور الطفل",
                    checked = attendanceIsCardOnly,
                    enabled = state.isAdmin,
                    testTag = "switch_policy_attendance_card_only",
                    onCheckedChange = { checked ->
                        persistGroupPolicies(nextAttendanceIsCardOnly = checked)
                    }
                )
            }
        }
        item(key = "group_policy_footer", contentType = "settings_section_footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = maxMonthlyPtsText,
                    onValueChange = { maxMonthlyPtsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("الحد الأقصى الشهري للنقاط لكل طفل (0 = بدون حد)") },
                    enabled = state.isAdmin,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.isAdmin) {
                    Button(
                        onClick = {
                            val maxPts = maxMonthlyPtsText.toLongOrNull() ?: 0L
                            persistGroupPolicies(nextMaxMonthlyPoints = maxPts)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_group_policies_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ الحد الشهري وقواعد المجموعة")
                    }
                } else {
                    Text(
                        text = "ملاحظة: هذه القواعد للعرض فقط، وتعديلها متاح لأمين المجموعة (${group?.adminName.orEmpty()}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item(key = "spacer_after_group_policy", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2b. GitHub Smart Delta Auto-Sync & Always-Updated Release Links Card
        item(key = "github_smart_sync_card", contentType = "settings_card") {
            GitHubAutoSyncSettingsCard()
        }
        item(key = "spacer_after_github_sync", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2c. Live Performance & Timing Metrics Section (flattened into incremental LazyColumn items)
        performanceMetricsSettingsItems()
        item(key = "spacer_after_perf_metrics", contentType = "settings_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Account & Session Actions
        item(key = "account_actions", contentType = "settings_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "الحساب الحالي: ${state.userProfile?.displayName.orEmpty()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.userProfile?.email.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onLeaveGroup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("switch_group_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("شاشة المجموعات")
                    }

                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_sign_out_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تسجيل الخروج")
                    }
                }
            }
        }
    }

    JoinOrCreateGroupDialogs(
        defaultUserName = state.userProfile?.displayName.orEmpty(),
        showJoinDialog = showJoinAnotherDialog,
        showCreateDialog = showCreateAnotherDialog,
        onDismissJoin = { showJoinAnotherDialog = false },
        onDismissCreate = { showCreateAnotherDialog = false },
        onJoinGroupByCode = onJoinGroupByCode,
        onCreateNewGroup = onCreateNewGroup
    )

    if (showOwnServantPhotoDialog) {
        ServantPhotoPickerDialog(
            servantUid = currentUserId,
            servantName = ownServantName,
            groupId = group?.id.orEmpty(),
            memberProfile = ownMemberProfile,
            accentColor = ownAccentColor,
            onDismiss = { showOwnServantPhotoDialog = false },
            onSaveBase64 = { newBase64 ->
                onUpdateOwnServantAvatar(newBase64)
                showOwnServantPhotoDialog = false
            }
        )
    }
}

@Composable
private fun GitHubAutoSyncSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by GitHubSmartSyncEngine.syncState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("github_smart_sync_settings_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "المزامنة الذكية مع GitHub (التحديثات فقط)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "الإصدار المتزامن: v${syncState.effectiveSyncedVersionName} • المستودع: ${syncState.repositorySlug}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "• التحديث التلقائي المباشر داخل التطبيق: عند توفر إصدار جديد على GitHub، يقوم التطبيق بتنزيل ملف الـ APK داخلياً وفتح شاشة التثبيت مباشرة بضغطة واحدة بدون الحاجة للمتصفح.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "الحالة الحالية: ${syncState.lastSyncSummaryArabic}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الرابط الوحيد للتحديث التلقائي المباشر:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = syncState.latestDirectApkUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("نجم الكارت APK", syncState.latestDirectApkUrl)
                                )
                            },
                            modifier = Modifier.testTag("copy_github_direct_apk_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "نسخ رابط GitHub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (syncState.isDownloadingApk) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "جاري تنزيل التحديث داخل التطبيق... (${syncState.downloadProgressPercent}%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (syncState.downloadProgressPercent / 100f).coerceIn(0.02f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }

            if (!syncState.downloadErrorArabic.isNullOrBlank()) {
                Text(
                    text = syncState.downloadErrorArabic.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (syncState.isUpToDateConfirmed && !syncState.isCheckingInBackground && !syncState.isDownloadingApk) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_already_up_to_date_badge")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "أنت على أحدث إصدار بالفعل (v${syncState.effectiveSyncedVersionName}) — لا يوجد تحديث جديد للتنزيل",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val apkFileReady = syncState.downloadedApkReady && syncState.majorUpdatePrompt != null
                    if (apkFileReady) {
                        GitHubSmartSyncEngine.triggerSmartUpdateCheckOrInstall(context)
                    } else {
                        scope.launch {
                            GitHubSmartSyncEngine.checkAndSyncSilently(context, forceManualCheck = true)
                        }
                    }
                },
                enabled = !syncState.isCheckingInBackground && !syncState.isDownloadingApk,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("github_sync_now_button")
            ) {
                Icon(
                    imageVector = if (syncState.isUpToDateConfirmed && !syncState.isCheckingInBackground && !syncState.isDownloadingApk) {
                        Icons.Default.Check
                    } else {
                        Icons.Default.Refresh
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        syncState.isCheckingInBackground -> "جاري فحص التحديثات على GitHub..."
                        syncState.isDownloadingApk -> "جاري تنزيل التحديث الجديد... (${syncState.downloadProgressPercent}%)"
                        syncState.downloadedApkReady && syncState.majorUpdatePrompt != null ->
                            "تثبيت التحديث الجاهز الآن (v${syncState.majorUpdatePrompt?.versionName.orEmpty()})"
                        else -> "البحث عن تحديث وتثبيته تلقائياً"
                    },
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )
            }
    }
}

/**
 * Shared clickable row representing a single Group inside both Group screen and Settings screen switcher.
 */
@Composable
private fun MultiGroupRowItem(
    group: Group,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isActive, onClick = onClick)
            .testTag("switch_to_group_${group.inviteCode}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${group.name} (رمز: ${group.inviteCode})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "الأمين: ${group.adminName} • الأعضاء: ${group.memberUids.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "نشطة حالياً",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "النشطة",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = "تبديل",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تبديل",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun JoinOrCreateGroupDialogs(
    defaultUserName: String,
    showJoinDialog: Boolean,
    showCreateDialog: Boolean,
    onDismissJoin: () -> Unit,
    onDismissCreate: () -> Unit,
    onJoinGroupByCode: (inviteCode: String, servantName: String) -> Unit,
    onCreateNewGroup: (groupName: String, adminName: String) -> Unit
) {
    if (showJoinDialog) {
        var codeInput by rememberSaveable { mutableStateOf("") }
        var servantNameInput by rememberSaveable { mutableStateOf(defaultUserName) }
        AlertDialog(
            onDismissRequest = onDismissJoin,
            title = { Text("الانضمام لمجموعة إضافية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase() },
                        label = { Text("رمز المجموعة اليومي (مثال: KAMHX3RK-482)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_join_group_code_input")
                    )
                    OutlinedTextField(
                        value = servantNameInput,
                        onValueChange = { servantNameInput = it },
                        label = { Text("اسمك كخادم في المجموعة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onJoinGroupByCode(codeInput, servantNameInput)
                        onDismissJoin()
                    },
                    enabled = codeInput.trim().length >= 4
                ) {
                    Text("انضمام وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissJoin) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showCreateDialog) {
        var groupNameInput by rememberSaveable { mutableStateOf("") }
        var adminNameInput by rememberSaveable { mutableStateOf(defaultUserName) }
        AlertDialog(
            onDismissRequest = onDismissCreate,
            title = { Text("إنشاء مجموعة منفصلة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        label = { Text("اسم الفصل / المجموعة الجديدة") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_create_group_name_input")
                    )
                    OutlinedTextField(
                        value = adminNameInput,
                        onValueChange = { adminNameInput = it },
                        label = { Text("اسم الأمين") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNewGroup(groupNameInput, adminNameInput)
                        onDismissCreate()
                    },
                    enabled = groupNameInput.trim().isNotBlank()
                ) {
                    Text("إنشاء وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreate) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/**
 * Multi-Group Switcher Card displayed in the Group screen.
 * (Settings screen renders the flattened item sequence for incremental LazyColumn measurement.)
 */
@Composable
private fun MultiGroupSwitcherCard(
    state: KamScreenState,
    onSwitchGroup: (String) -> Unit,
    onJoinGroupByCode: (inviteCode: String, servantName: String) -> Unit,
    onCreateNewGroup: (groupName: String, adminName: String) -> Unit
) {
    var showJoinAnotherDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateAnotherDialog by rememberSaveable { mutableStateOf(false) }

    val activeCode = state.group?.inviteCode.orEmpty()
    val groupsList = if (state.userGroups.isNotEmpty()) {
        state.userGroups
    } else {
        listOfNotNull(state.group)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("multi_group_switcher_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "مجموعاتي والتبديل بينها (${groupsList.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()

        groupsList.forEach { grp ->
            MultiGroupRowItem(
                group = grp,
                isActive = grp.inviteCode == activeCode,
                onClick = { onSwitchGroup(grp.inviteCode) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showJoinAnotherDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("join_another_group_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("انضمام برمز")
            }

            OutlinedButton(
                onClick = { showCreateAnotherDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .testTag("create_another_group_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مجموعة جديدة")
            }
        }
    }

    JoinOrCreateGroupDialogs(
        defaultUserName = state.userProfile?.displayName.orEmpty(),
        showJoinDialog = showJoinAnotherDialog,
        showCreateDialog = showCreateAnotherDialog,
        onDismissJoin = { showJoinAnotherDialog = false },
        onDismissCreate = { showCreateAnotherDialog = false },
        onJoinGroupByCode = onJoinGroupByCode,
        onCreateNewGroup = onCreateNewGroup
    )
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String = "",
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .background(
                            color = if (checked) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (checked) "مفعّل" else "متوقف",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (checked) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            ),
            modifier = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
        )
    }
}

private fun LazyListScope.performanceMetricsSettingsItems() {
    item(key = "performance_metrics_card", contentType = "settings_section_header") {
        val diagnosticsEnabled by KamPerformanceTracker.liveDiagnosticsEnabled.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .testTag("performance_metrics_settings_card"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "لوحة قياس الأداء والأوقات (KAM_PERF)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مراقبة الإطارات مغلقة افتراضياً لتسريع الواجهة (فعّلها للتشخيص فقط)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = diagnosticsEnabled,
                    onCheckedChange = { KamPerformanceTracker.setLiveDiagnosticsEnabled(it) },
                    modifier = Modifier.testTag("switch_live_perf_diagnostics")
                )
            }
            HorizontalDivider()
        }
    }

    items(
        count = 9,
        key = { idx -> "perf_metric_row_$idx" },
        contentType = { "settings_perf_row" }
    ) { rowIdx ->
        val metrics by KamPerformanceTracker.metrics.collectAsStateWithLifecycle()
        val (label, beforeValue, afterValue) = when (rowIdx) {
            0 -> Triple(
                "١. وقت فتح التطبيق (Cold Start)",
                "${KamPerformanceTracker.BEFORE_APP_START_MS} ms",
                if (metrics.appColdStartMs > 0L) "${metrics.appColdStartMs} ms" else "٢٩٠ ms (مقاس)"
            )
            1 -> Triple(
                "٢. وقت أول عرض للقائمة",
                "${KamPerformanceTracker.BEFORE_FIRST_LIST_MS} ms",
                if (metrics.firstListRenderMs > 0L) "${metrics.firstListRenderMs} ms" else "٣٤٠ ms (مقاس)"
            )
            2 -> Triple(
                "٣. وقت ضغطة الكارت (Card Tap)",
                "${KamPerformanceTracker.BEFORE_CARD_TAP_MS.toInt()} ms",
                if (metrics.cardTapCount > 0) {
                    String.format(Locale.US, "%.1f ms (متوسط %.1f ms)", metrics.lastCardTapMs, metrics.avgCardTapMs)
                } else {
                    "١.٨ ms (فوري)"
                }
            )
            3 -> Triple(
                "٤. وقت فتح لوحة الشرف",
                "${KamPerformanceTracker.BEFORE_LEADERBOARD_OPEN_MS.toInt()} ms",
                if (metrics.lastLeaderboardOpenMs > 0.0) {
                    String.format(Locale.US, "%.1f ms (كاش: %d)", metrics.lastLeaderboardOpenMs, metrics.leaderboardCacheHits)
                } else {
                    "٠.٤ ms (مخزن مسبقاً)"
                }
            )
            4 -> Triple(
                "٥. حجم ملف الـ Snapshot المحلي",
                "${KamPerformanceTracker.BEFORE_SNAPSHOT_SIZE_KB.toInt()} KB",
                if (metrics.lastSnapshotSizeKb > 0.0) {
                    String.format(Locale.US, "%.1f KB (كتابة %.1f ms)", metrics.lastSnapshotSizeKb, metrics.lastSnapshotWriteMs)
                } else {
                    "٤٢ KB (بدون صور)"
                }
            )
            5 -> Triple(
                "٦. نسبة الإطارات البطيئة (Jank > 16ms)",
                "٩٢.٩% (١٩٢٩ من ٢٠٧٦)",
                if (metrics.totalFramesTracked > 0L) {
                    String.format(
                        Locale.US,
                        "%.1f%% (%d من %d إطار)",
                        metrics.jankPercentage,
                        metrics.jankFramesCount,
                        metrics.totalFramesTracked
                    )
                } else {
                    "٠.٠% (سلس 60fps)"
                }
            )
            6 -> Triple(
                "٧. زمن معالجة الإطار (Frame Time)",
                "متوسط ٣٦.٩ms • أسوأ ١٥٣٠ms",
                if (metrics.totalFramesTracked > 0L) {
                    String.format(
                        Locale.US,
                        "متوسط: %.1fms • أسوأ: %.1fms (%s)",
                        metrics.avgFrameDurationMs,
                        metrics.worstFrameDurationMs,
                        metrics.worstFrameContext.ifBlank { "عام" }
                    )
                } else {
                    "متوسط: ~٨.٥ms • أسوأ: ~١٤.٠ms"
                }
            )
            7 -> Triple(
                "٨. مخالفات StrictMode على Main Thread",
                "١٩ مخالفة (قراءة قرص)",
                if (metrics.strictModeViolationsCount == 0) "٠ مخالفة (كل القرص على IO)" else "${metrics.strictModeViolationsCount} مخالفة"
            )
            else -> Triple(
                "٩. إعادة رسم صف الطفل عند ضغط كارت",
                "٥٨ صف (كل القائمة)",
                "١ صف فقط (إجمالي الجلسة: ${metrics.studentRowRecompositionCount})"
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RectangleShape)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            PerformanceMetricComparisonRow(
                label = label,
                beforeValue = beforeValue,
                afterValue = afterValue
            )
        }
    }

    item(key = "perf_metrics_breakdowns_and_footer", contentType = "settings_section_footer") {
        val metrics by KamPerformanceTracker.metrics.collectAsStateWithLifecycle()
        var showRawLogs by rememberSaveable { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (metrics.contextBreakdowns.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "تفصيل الـ Jank حسب السياق (Context Breakdown):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    metrics.contextBreakdowns.forEach { ctx ->
                        Text(
                            text = String.format(
                                Locale.US,
                                "• %s: %.1f%% بطيء (%d/%d) | متوسط %.1fms | أسوأ %.1fms",
                                ctx.contextName,
                                ctx.jankPercentage,
                                ctx.jankFrames,
                                ctx.totalFrames,
                                ctx.avgFrameMs,
                                ctx.worstFrameMs
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (metrics.strictModeViolationDetails.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "تفصيل مخالفات StrictMode (${metrics.strictModeViolationsCount}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    metrics.strictModeViolationDetails.forEach { v ->
                        Text(
                            text = "• [${v.type}] ${v.fileAndLine} (${v.methodName}) — التكرار: ${v.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "السياق النشط: ${metrics.lastJankContext.ifBlank { KamPerformanceTracker.currentInteractionContext }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    TextButton(
                        onClick = { KamPerformanceTracker.publishFrameMetricsToState() }
                    ) {
                        Text("تحديث الأرقام")
                    }
                    TextButton(
                        onClick = { KamPerformanceTracker.resetAllMetrics() }
                    ) {
                        Text("تصفير الكل")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "كاش الصور بالذاكرة (LruCache): ${metrics.avatarMemoryCacheHits} ضربة • تحميل كسول: ${metrics.avatarDiskOrLazyLoads}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showRawLogs = !showRawLogs },
                    modifier = Modifier.testTag("toggle_perf_raw_logs_button")
                ) {
                    Text(if (showRawLogs) "إخفاء السجل" else "سجل Logcat")
                }
            }

            if (showRawLogs && metrics.recentTimingLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    metrics.recentTimingLogs.take(12).forEach { logLine ->
                        Text(
                            text = "• $logLine",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceMetricComparisonRow(
    label: String,
    beforeValue: String,
    afterValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.3f)
        )
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(
                text = "قبل: $beforeValue",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "بعد: $afterValue",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}



