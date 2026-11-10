package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CardType
import com.example.data.model.Invitation
import com.example.data.model.MAX_STUDENTS_PER_GROUP
import com.example.data.model.ServantBlock
import com.example.data.sync.GitHubSmartSyncEngine
import com.example.ui.components.AppearanceFullScreen
import com.example.ui.components.AppearanceSettingsEntryCard
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalUiScaleController
import com.example.ui.viewmodel.KamScreenState
import com.example.ui.viewmodel.KamViewModel
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

    val fullDailyCode = state.group?.fullDailyInviteCode.orEmpty()
    val remainingHours = state.group?.remainingDailyCodeHours() ?: 24
    val bannedServants = state.group?.bannedServantsList.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("group_invitations_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Group Daily Invite Code Card (24-Hour Rotating Code with Unique Group Prefix)
        item(key = "group_code_card") {
            val isCompactMobile = LocalUiScaleController.current.isCompactMobile
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isCompactMobile) {
                        // On mobile: Full-width group title & stats on top, followed by full-width invite code buttons row below
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = state.group?.name ?: "المجموعة",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "الأمين: ${state.group?.adminName.orEmpty()} • الدور: ${if (state.isAdmin) "الأمين" else "خادم"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "الأطفال: ${state.students.size} / $MAX_STUDENTS_PER_GROUP • البلوكات: ${state.blocks.size} • الأعضاء: ${state.group?.memberUids?.size ?: 1} / 7",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                modifier = Modifier.testTag("share_group_invite_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "مشاركة الدعوة", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "مشاركة الرمز",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        // Tablet / Expanded layout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.group?.name ?: "المجموعة",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الأمين: ${state.group?.adminName.orEmpty()} • الدور: ${if (state.isAdmin) "الأمين" else "خادم"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "إجمالي الأطفال المشتركين: ${state.students.size} / $MAX_STUDENTS_PER_GROUP • البلوكات: ${state.blocks.size} • الأعضاء: ${state.group?.memberUids?.size ?: 1} / 7",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val code = fullDailyCode.ifBlank { state.group?.inviteCode.orEmpty() }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("رمز المجموعة اليومي", code))
                                        onCopyBanner("تم نسخ رمز المجموعة اليومي: $code")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("copy_group_code_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ الرمز اليومي", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = fullDailyCode.ifBlank { state.group?.inviteCode ?: "---" },
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    modifier = Modifier.testTag("share_group_invite_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "مشاركة الدعوة", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "مشاركة الرمز", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "بادئة المجموعة الفريدة: ${state.group?.inviteCode.orEmpty()} • الجزء اليومي: ${state.group?.effectiveDailyToken.orEmpty()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "يتغير تلقائياً كل ٢٤ ساعة لمنع دخول غير المدعوين (متبقي حوالي $remainingHours ساعة)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (state.isAdmin && !isCompactMobile) {
                                    OutlinedButton(
                                        onClick = onRefreshDailyInviteCode,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("refresh_daily_code_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "تجديد الرمز فوراً",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تجديد فوراً", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            if (state.isAdmin && isCompactMobile) {
                                OutlinedButton(
                                    onClick = onRefreshDailyInviteCode,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("refresh_daily_code_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "تجديد الرمز فوراً",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تجديد الرمز اليومي فوراً", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    Text(
                        text = "شارك الرمز اليومي الكامل ($fullDailyCode) مع الخادم الجديد؛ بادئة المجموعة تضمن عدم تداخل أي مجموعتين نهائياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 1b. Multi-Group Switcher Card (Only in Group & Settings screens to prevent accidental switching)
        item(key = "multi_group_switcher_in_group_screen") {
            MultiGroupSwitcherCard(
                state = state,
                onSwitchGroup = onSwitchGroup,
                onJoinGroupByCode = onJoinGroupByCode,
                onCreateNewGroup = onCreateNewGroup
            )
        }

        // 2. Send New Invitation (Admin Only)
        if (state.isAdmin) {
            item(key = "admin_send_invite_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "دعوة خادم جديد ومشاركة الرمز اليومي (للأمين)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اكتب اسم الخادم واضغط 'دعوة' لحفظ الدعوة وفتح نافذة مشاركة الرسالة والرمز اليومي (صالح لمدة يوم واحد):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inviteeName,
                                onValueChange = { inviteeName = it },
                                label = { Text("اسم الخادم المدعو") },
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
                                    .height(54.dp)
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
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "بلوكات الخدام المشتركة (${state.blocks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.isAdmin) {
                    Text(
                        text = "الضغط على زر الإزالة بجوار بلوك الخادم يقوم بإزالة الخادم وحظره من المجموعة فقط مع الإبقاء على البلوك وجميع أطفاله ونقاطهم كما هم تماماً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                .clip(CircleShape)
                                .background(blkColor)
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
                            Column {
                                Text(
                                    text = "قائمة الخدام المحظورين من المجموعة (${bannedServants.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "أي خادم في هذه القائمة تظهر له رسالة «تم إزالتك من هذه المجموعة» إذا حاول التسجيل بالرمز، ويمكنك إلغاء الحظر عنه في أي وقت:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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

        // 5. Role Permissions Matrix Summary
        item(key = "roles_matrix_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الأدوار والصلاحيات المحمية في Firebase Security Rules",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• الأمين: كل الصلاحيات شاملة إزالة وحظر الخدام، تجديد الرمز اليومي، إنشاء المجموعة، والدعوات.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• الخادم: يرى كل أطفال وبلوكات المجموعة، يضيف ويعدّل وينقل ويرتّب ويعطي الكروت (الحذف مرفوض من السيرفر).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• أمان الرمز اليومي: لكل مجموعة بادئة فريدة خاصة بها مع رمز يومي صالح لمدة ٢٤ ساعة يمنع أي تشابه أو تداخل بين المجموعات.",
                        style = MaterialTheme.typography.bodySmall
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
    onToggleBlockHidden: (String) -> Unit = {}
) {
    val enabledIds = state.userProfile?.enabledCards ?: CardType.defaultEnabledIds
    val group = state.group
    var showAppearanceFullScreen by rememberSaveable { mutableStateOf(false) }

    if (showAppearanceFullScreen) {
        AppearanceFullScreen(
            onBack = { showAppearanceFullScreen = false }
        )
        return
    }

    var allowServantUndo by rememberSaveable(group?.allowServantUndo) {
        mutableStateOf(group?.allowServantUndo ?: true)
    }
    var enforceWeeklyLimit by rememberSaveable(group?.enforceWeeklyCardLimit) {
        mutableStateOf(group?.enforceWeeklyCardLimit ?: false)
    }
    var maxMonthlyPtsText by rememberSaveable(group?.maxMonthlyPoints) {
        mutableStateOf((group?.maxMonthlyPoints ?: 0L).toString())
    }
    var attendanceIsCardOnly by rememberSaveable(group?.attendanceIsCardOnly) {
        mutableStateOf(group?.attendanceIsCardOnly ?: true)
    }
    var showLeaderboard by rememberSaveable(group?.showLeaderboard) {
        mutableStateOf(group?.showLeaderboard ?: true)
    }
    var autoStudentNumber by rememberSaveable(group?.autoStudentNumber) {
        mutableStateOf(group?.autoStudentNumber ?: true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Appearance & Themes Dedicated Screen Entry Button ("المظهر والسمات")
        item(key = "appearance_and_icon_settings") {
            AppearanceSettingsEntryCard(
                onOpenAppearanceScreen = { showAppearanceFullScreen = true }
            )
        }

        // 0. Multi-Group Switcher Card ("التبديل بين المجموعات من شاشة المجموعة والإعدادات فقط")
        item(key = "multi_group_switcher_in_settings") {
            MultiGroupSwitcherCard(
                state = state,
                onSwitchGroup = onSwitchGroup,
                onJoinGroupByCode = onJoinGroupByCode,
                onCreateNewGroup = onCreateNewGroup
            )
        }

        // 1. Personal Card Visibility Settings ("كل خادم يفعّل ويخفي الكروت من الإعدادات بشكل مستقل")
        item(key = "personal_cards_settings") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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

                    CardType.entries.forEach { card ->
                        val isEnabled = card.id in enabledIds
                        val cardColor = KamSemanticColors.cardColor(card)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = cardColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, cardColor)
                                ) {
                                    Text(
                                        text = "${card.badgeSymbol} +${card.points}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = cardColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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
                }
            }
        }

        // 1b. Personal Block Visibility Settings ("إخفاء بلوك عندي فقط - تفضيل شخصي")
        if (state.blocks.isNotEmpty()) {
            item(key = "personal_blocks_visibility_settings") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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

                        state.blocks.forEach { blk ->
                            val isVisibleOnHome = blk.id !in state.hiddenBlockIds
                            val blkColor = KamSemanticColors.blockColor(blk.colorHex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(blkColor)
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
                    }
                }
            }
        }

        // 2. Group Policy Decisions (Section 5 rules configurable by Admin)
        item(key = "group_policy_settings") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "قواعد وقرارات الفصل (يضبطها الأمين)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    PolicySwitchRow(
                        title = "السماح للخادم بإلغاء كارت سجّله بالخطأ",
                        subtitle = "عند الإيقاف، الأمين فقط يمكنه إلغاء الكارت من سجل الطفل",
                        checked = allowServantUndo,
                        enabled = state.isAdmin,
                        onCheckedChange = { allowServantUndo = it }
                    )

                    PolicySwitchRow(
                        title = "الكارت يُعطى مرة واحدة فقط في الأسبوع لكل طفل",
                        subtitle = "عند الإيقاف، يمكن إعطاء الكارت أكثر من مرة خلال الشهر (مع منع الضغط المزدوج)",
                        checked = enforceWeeklyLimit,
                        enabled = state.isAdmin,
                        onCheckedChange = { enforceWeeklyLimit = it }
                    )

                    PolicySwitchRow(
                        title = "الترقيم التلقائي المتسلسل للأطفال",
                        subtitle = "عند الإيقاف، يُدخل الخادم رقم الطفل يدوياً عند الإضافة",
                        checked = autoStudentNumber,
                        enabled = state.isAdmin,
                        onCheckedChange = { autoStudentNumber = it }
                    )

                    PolicySwitchRow(
                        title = "إظهار لوحة الشرف وترتيب النقاط الشهري",
                        subtitle = "عرض أعلى ٥ أطفال بالنقاط في الشاشة الرئيسية",
                        checked = showLeaderboard,
                        enabled = state.isAdmin,
                        onCheckedChange = { showLeaderboard = it }
                    )

                    PolicySwitchRow(
                        title = "الحضور هو كارت 'الحضور' (٣ نقاط)",
                        subtitle = "اعتماد كارت الحضور كآلية موحدة وسريعة لتسجيل حضور الطفل",
                        checked = attendanceIsCardOnly,
                        enabled = state.isAdmin,
                        onCheckedChange = { attendanceIsCardOnly = it }
                    )

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
                                onSaveGroupPolicies(
                                    allowServantUndo,
                                    enforceWeeklyLimit,
                                    maxPts,
                                    attendanceIsCardOnly,
                                    showLeaderboard,
                                    autoStudentNumber
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_group_policies_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ قواعد المجموعة")
                        }
                    } else {
                        Text(
                            text = "ملاحظة: هذه القواعد للعرض، وتعديلها متاح لأمين المجموعة فقط.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2b. GitHub Smart Delta Auto-Sync & Always-Updated Release Links Card
        item(key = "github_smart_sync_card") {
            GitHubAutoSyncSettingsCard()
        }

        // 3. Account & Session Actions
        item(key = "account_actions") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
    }
}

@Composable
private fun GitHubAutoSyncSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by GitHubSmartSyncEngine.syncState.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("github_smart_sync_settings_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                            .clip(RoundedCornerShape(4.dp))
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            GitHubSmartSyncEngine.checkAndSyncSilently(context, forceManualCheck = true)
                        }
                    },
                    enabled = !syncState.isCheckingInBackground && !syncState.isDownloadingApk,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("github_sync_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (syncState.isCheckingInBackground) "جاري الفحص..." else "فحص وتحديث الآن",
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("نجم الكارت v1.6 Latest APK", syncState.latestDirectApkUrl)
                        )
                        GitHubSmartSyncEngine.startInAppApkDownloadAndInstall(context, autoLaunchInstaller = true)
                    },
                    enabled = !syncState.isDownloadingApk,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("github_download_latest_apk_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (syncState.downloadedApkReady) "تثبيت التحديث الآن" else "تنزيل وتثبيت APK",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Multi-Group Switcher Card displayed in both the Group screen and Settings screen
 * (never on the Home screen to prevent accidental switching during active service).
 * Allows switching between all groups the user belongs to, or joining/creating another group.
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("multi_group_switcher_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "مجموعاتي والتبديل بينها (${groupsList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "كل مجموعة منفصلة تماماً ببياناتها وأطفالها. اضغط على أي مجموعة للتبديل إليها فوراً:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            groupsList.forEach { grp ->
                val isActive = grp.inviteCode == activeCode
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
                        .clickable(enabled = !isActive) { onSwitchGroup(grp.inviteCode) }
                        .testTag("switch_to_group_${grp.inviteCode}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${grp.name} (رمز: ${grp.inviteCode})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "الأمين: ${grp.adminName} • الأعضاء: ${grp.memberUids.size}",
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
    }

    if (showJoinAnotherDialog) {
        var codeInput by rememberSaveable { mutableStateOf("") }
        var servantNameInput by rememberSaveable {
            mutableStateOf(state.userProfile?.displayName ?: "")
        }
        AlertDialog(
            onDismissRequest = { showJoinAnotherDialog = false },
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
                        showJoinAnotherDialog = false
                    },
                    enabled = codeInput.trim().length >= 4
                ) {
                    Text("انضمام وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinAnotherDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showCreateAnotherDialog) {
        var groupNameInput by rememberSaveable { mutableStateOf("") }
        var adminNameInput by rememberSaveable {
            mutableStateOf(state.userProfile?.displayName ?: "")
        }
        AlertDialog(
            onDismissRequest = { showCreateAnotherDialog = false },
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
                        showCreateAnotherDialog = false
                    },
                    enabled = groupNameInput.trim().isNotBlank()
                ) {
                    Text("إنشاء وتفعيل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAnotherDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun PolicySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}


