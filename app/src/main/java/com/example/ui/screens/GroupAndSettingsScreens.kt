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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.data.model.BlockColorPreset
import com.example.data.model.CardType
import com.example.data.model.Group
import com.example.data.model.Invitation
import com.example.data.model.MAX_SERVANTS_PER_GROUP
import com.example.data.model.MAX_STUDENTS_PER_GROUP
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.sync.GitHubSmartSyncEngine
import com.example.ui.components.AppearanceFullScreen
import com.example.ui.components.AppearanceSettingsEntryCard
import com.example.ui.components.ServantAvatarCircle
import com.example.ui.components.ServantPhotoPickerDialog
import com.example.ui.components.ServantProfileSettingsCard
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.components.openDialerForPhone
import com.example.ui.components.openWhatsAppForPhone
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalKamExtendedColors
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

@OptIn(ExperimentalLayoutApi::class)
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
    onUnbanServant: (servantUid: String, servantName: String) -> Unit = { _, _ -> },
    currentUserId: String = "",
    onCreateBlock: (servantName: String, colorHex: String, servantUid: String) -> Unit = { _, _, _ -> },
    onUpdateBlock: (block: ServantBlock, newName: String, newColorHex: String, newServantId: String?) -> Unit = { _, _, _, _ -> },
    onDeleteBlockKeepingChildren: (ServantBlock) -> Unit = onDeleteBlock,
    onDeleteBlockWithChildren: (ServantBlock) -> Unit = {},
    onSavePersonalServantAlias: (targetUid: String, alias: String) -> Unit = { _, _ -> },
    onEditOwnDisplayName: (String) -> Unit = {},
    onToggleAssistantAdmin: (targetUid: String, targetName: String, makeAssistant: Boolean) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var inviteeName by rememberSaveable { mutableStateOf("") }
    var blockPendingServantRemoval by remember { mutableStateOf<ServantBlock?>(null) }
    var activeInfoDialogTopic by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateBlockDialog by rememberSaveable { mutableStateOf(false) }
    var blockBeingEdited by remember { mutableStateOf<ServantBlock?>(null) }
    var blockPendingSafeDelete by remember { mutableStateOf<ServantBlock?>(null) }
    var blockPendingHardDeleteConfirm by remember { mutableStateOf<ServantBlock?>(null) }
    var memberPendingAlias by remember { mutableStateOf<Pair<String, String>?>(null) }
    var aliasInputText by rememberSaveable { mutableStateOf("") }
    var showEditOwnNameDialog by rememberSaveable { mutableStateOf(false) }
    var ownNameDraft by rememberSaveable { mutableStateOf("") }
    var memberPendingAssistantToggle by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }

    LaunchedEffect(Unit) {
        KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_GROUP, scrolling = false)
    }

    val extColors = LocalKamExtendedColors.current
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
                            if (state.isAdmin) {
                                Text("• بادئة المجموعة الفريدة: ${state.group?.inviteCode.orEmpty()} • الجزء اليومي: ${state.group?.effectiveDailyToken.orEmpty()}")
                                Text("• يتغير الجزء اليومي تلقائياً كل ٢٤ ساعة لمنع دخول غير المدعوين (متبقي حوالي $remainingHours ساعة).")
                                Text("• شارك الرمز الكامل ($fullDailyCode) مع الخادم الجديد؛ بادئة المجموعة تضمن عدم تداخل أي مجموعتين نهائياً.")
                            } else {
                                Text("• كود الدعوة اليومي متاح لأمين المجموعة ومساعد الأمين فقط لحماية خصوصية الفصل.")
                            }
                        }
                        "BLOCKS" -> {
                            Text("• الضغط على زر الإزالة بجوار بلوك الخادم يقوم بإزالة الخادم وحظره من المجموعة فقط مع الإبقاء على البلوك وجميع أطفاله ونقاطهم محفوظة بالكامل.")
                        }
                        else -> {
                            Text("• الأمين الأساسي: كل الصلاحيات شاملة تعيين/إلغاء مساعد الأمين، كود الدعوة، إزالة وحظر الخدام، وتعديل قواعد المجموعة.")
                            Text("• مساعد الأمين: ينال كافة صلاحيات الأمين (كود الدعوة اليومي، إنشاء وتعديل وحذف البلوكات، حذف الأطفال، تعديل قواعد المجموعة، وإدارة الخدام والدعوات).")
                            Text("• الخادم: يرى كل أطفال وبلوكات المجموعة، يضيف ويعدّل وينقل ويرتّب ويعطي الكروت (كود الدعوة والحذف للأمين ومساعده فقط).")
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

    val pendingAssistant = memberPendingAssistantToggle
    if (pendingAssistant != null) {
        val (targetUid, targetName, makeAssistant) = pendingAssistant
        AlertDialog(
            onDismissRequest = { memberPendingAssistantToggle = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = if (makeAssistant) "تعيين مساعد أمين" else "إلغاء صلاحية مساعد الأمين",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (makeAssistant) {
                        "هل تريد تعيين \"$targetName\" كمساعد أمين للمجموعة؟\n\nسينال المساعد كامل الصلاحيات: رؤية ومشاركة وتجديد كود الدعوة، إدارة البلوكات والأطفال، تعديل قواعد المجموعة، وإدارة الخدام."
                    } else {
                        "هل تريد إلغاء صلاحية مساعد الأمين عن \"$targetName\"؟\n\nسيعود كخادم عادي في المجموعة ولن يتمكن بعد الآن من رؤية كود الدعوة أو تعديل قواعد المجموعة."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        memberPendingAssistantToggle = null
                        onToggleAssistantAdmin(targetUid, targetName, makeAssistant)
                    },
                    modifier = Modifier.testTag("confirm_toggle_assistant_admin_button")
                ) {
                    Text(if (makeAssistant) "تأكيد التعيين" else "تأكيد الإلغاء")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingAssistantToggle = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("group_invitations_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Group Header, Counters & Daily Invite Code Card (Clean & Compact)
        item(key = "group_code_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
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
                            onClick = { activeInfoDialogTopic = if (state.isAdmin) "CODE" else "ROLES" },
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

                    // Invite Code Actions: strictly visible ONLY to Primary Admin & Assistant Admins
                    if (state.isAdmin) {
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

                            // Small Refresh Icon Button (Admin & Assistant Admin)
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
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_code_admin_only_notice")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "كود الدعوة متاح لأمين المجموعة ومساعد الأمين فقط",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1b. Multi-Group Switcher Card
        item(key = "multi_group_switcher_in_group_screen") {
            MultiGroupSwitcherCard(
                state = state,
                currentUserId = currentUserId,
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extColors.hairline)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extColors.hairline)
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extColors.hairline)
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
                                text = if (state.isAdmin) {
                                    "الحالة: $statusArabic • الرمز اليومي: $activeShareCode"
                                } else {
                                    "الحالة: $statusArabic"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Share invitation button (Admin & Assistant Admin only)
                            if (state.isAdmin) {
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

        // 4. Servant Blocks Management (Admin-Only Create/Edit/Delete + Personal Visibility)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCreateBlockDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("group_add_block_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة بلوك", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = { activeInfoDialogTopic = "BLOCKS" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "توضيح إدارة البلوكات",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
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
            val assignedServantProfile = state.memberProfileForBlock(blk)
            val displayOwnerName = state.resolvedBlockOwnerName(blk)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, extColors.hairline)
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
                        ServantAvatarCircle(
                            servantUid = blk.servantId,
                            servantName = displayOwnerName,
                            groupId = blk.groupId.ifBlank { state.group?.id.orEmpty() },
                            memberProfile = assignedServantProfile,
                            size = 38.dp,
                            accentColor = blkColor,
                            blockId = blk.id
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "بلوك ${blk.servantName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val servantStatusText = when {
                                isBlockAdminOwner -> "بلوك أمين المجموعة ($displayOwnerName)"
                                hasActiveServant -> "الخادم المسؤول: $displayOwnerName"
                                else -> "بلوك بدون خادم مرتبط حالياً"
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

                        if (state.isAdmin) {
                            IconButton(
                                onClick = { blockBeingEdited = blk },
                                modifier = Modifier.testTag("edit_block_${blk.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "تعديل اسم ولون وخادم البلوك",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (hasActiveServant) {
                                IconButton(
                                    onClick = { blockPendingServantRemoval = blk },
                                    modifier = Modifier.testTag("ban_servant_block_${blk.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonRemove,
                                        contentDescription = "إزالة وحظر الخادم من المجموعة مع بقاء البلوك والأطفال",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            IconButton(
                                onClick = { blockPendingSafeDelete = blk },
                                modifier = Modifier.testTag("delete_block_${blk.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف البلوك",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4a. Personal Block Visibility Settings (Moved from SettingsScreen to Group tab)
        if (state.blocks.isNotEmpty()) {
            item(key = "personal_blocks_visibility_in_group") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personal_blocks_visibility_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extColors.hairline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "إظهار / إخفاء البلوكات في شاشتي (تفضيل شخصي)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تُحفظ حالة إظهار أو إخفاء البلوكات في حسابك الشخصي فقط دون التأثير على باقي الخدام.",
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
                                    .clickable { onToggleBlockHidden(blk.id) }
                                    .padding(vertical = 4.dp),
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
                                        style = MaterialTheme.typography.bodyMedium,
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

        // 4b. Group Members Directory (All Members with Phone/WhatsApp, Own Name Edit & Personal Alias)
        item(key = "group_members_directory_section") {
            val directoryMembers = remember(state.memberProfiles, state.blocks, state.group, state.userProfile, currentUserId) {
                val byUid = linkedMapOf<String, ServantMemberProfile>()
                state.memberProfiles.forEach { profile ->
                    val uid = profile.effectiveUid
                    if (uid.isNotBlank()) {
                        byUid[uid] = profile
                    }
                }
                state.blocks.forEach { blk ->
                    if (blk.servantId.isNotBlank() && blk.servantId !in byUid) {
                        byUid[blk.servantId] = ServantMemberProfile(
                            id = blk.servantId,
                            uid = blk.servantId,
                            displayName = blk.servantName,
                            groupId = blk.groupId
                        )
                    }
                }
                if (currentUserId.isNotBlank() && currentUserId !in byUid) {
                    byUid[currentUserId] = ServantMemberProfile(
                        id = currentUserId,
                        uid = currentUserId,
                        displayName = state.userProfile?.displayName ?: "خادم",
                        groupId = state.group?.id.orEmpty(),
                        primaryPhone = state.ownPrimaryPhone,
                        secondaryPhone = state.ownSecondaryPhone
                    )
                }
                byUid.values.toList()
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_members_directory_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, extColors.hairline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
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
                            text = "قائمة الأعضاء والخدام في المجموعة (${directoryMembers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = extColors.hairline)

                    directoryMembers.forEach { member ->
                        val memberUid = member.effectiveUid
                        val isMe = memberUid == currentUserId
                        val isMemberPrimaryAdmin = memberUid == state.group?.adminId
                        val isMemberAssistantAdmin = state.group?.isAssistantAdmin(memberUid) == true
                        val memberBlock = state.blocks.firstOrNull { it.servantId == memberUid }
                        val accentColor = if (memberBlock != null) {
                            KamSemanticColors.blockColor(memberBlock.colorHex)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        val officialName = memberBlock?.servantName?.takeIf { it.isNotBlank() }
                            ?: member.displayName.ifBlank { "خادم" }
                        val resolvedName = state.resolvedServantName(memberUid, officialName)
                        val personalAlias = state.servantAliases[memberUid].orEmpty()

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, extColors.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_member_row_$memberUid")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        ServantAvatarCircle(
                                            servantUid = memberUid,
                                            servantName = resolvedName,
                                            groupId = state.group?.id.orEmpty(),
                                            memberProfile = member,
                                            size = 40.dp,
                                            accentColor = accentColor
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = resolvedName + if (isMe) " (أنت)" else "",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (isMemberPrimaryAdmin) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = "الأمين",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                } else if (isMemberAssistantAdmin) {
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.AdminPanelSettings,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Text(
                                                                text = "مساعد أمين",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            if (personalAlias.isNotBlank() && personalAlias != officialName) {
                                                Text(
                                                    text = "الاسم الأصلي: $officialName",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (isMe && !state.userProfile?.email.isNullOrBlank()) {
                                                Text(
                                                    text = state.userProfile?.email.orEmpty(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    if (isMe) {
                                        TextButton(
                                            onClick = {
                                                ownNameDraft = officialName
                                                showEditOwnNameDialog = true
                                            },
                                            modifier = Modifier.testTag("group_edit_own_name_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تعديل اسمي", style = MaterialTheme.typography.labelMedium)
                                        }
                                    } else {
                                        TextButton(
                                            onClick = {
                                                aliasInputText = personalAlias.ifBlank { officialName }
                                                memberPendingAlias = memberUid to officialName
                                            },
                                            modifier = Modifier.testTag("group_alias_member_$memberUid")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تسمية خاصة بك", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                val phones = member.nonEmptyPhones
                                val canManageAssistantRole = state.isPrimaryAdmin && !isMe && !isMemberPrimaryAdmin
                                if (phones.isNotEmpty() || canManageAssistantRole) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (canManageAssistantRole) {
                                            OutlinedButton(
                                                onClick = {
                                                    memberPendingAssistantToggle = Triple(
                                                        memberUid,
                                                        resolvedName,
                                                        !isMemberAssistantAdmin
                                                    )
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = if (isMemberAssistantAdmin) {
                                                    ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.error
                                                    )
                                                } else {
                                                    ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.primary
                                                    )
                                                },
                                                modifier = Modifier.testTag("toggle_assistant_admin_$memberUid")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AdminPanelSettings,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isMemberAssistantAdmin) "إلغاء صلاحية المساعد" else "تعيين كمساعد أمين",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        if (phones.isNotEmpty()) {
                                            val firstPhone = phones.first()
                                            OutlinedButton(
                                                onClick = { openDialerForPhone(context, firstPhone) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(firstPhone, style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedButton(
                                                onClick = { openWhatsAppForPhone(context, firstPhone) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("واتساب", style = MaterialTheme.typography.labelSmall)
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

        // 4c. Banned Servants List & Unban Action (Admin Only)
        if (state.isAdmin) {
            item(key = "banned_servants_section") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banned_servants_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, extColors.hairline)
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
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extColors.hairline),
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

    // Admin Create Block Dialog
    if (showCreateBlockDialog && state.isAdmin) {
        var newBlockName by rememberSaveable { mutableStateOf("") }
        var selectedColorHex by rememberSaveable { mutableStateOf(BlockColorPreset.entries.first().hex) }
        var selectedServantUid by rememberSaveable { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateBlockDialog = false },
            title = { Text("إنشاء بلوك خادم جديد", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBlockName,
                        onValueChange = { newBlockName = it },
                        label = { Text("اسم البلوك / الخادم") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_create_block_name_input")
                    )
                    Text("اختر لون البلوك:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlockColorPreset.entries.forEach { preset ->
                            val hex = preset.hex
                            val label = preset.arabicName
                            val color = KamSemanticColors.blockColor(hex)
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = color.copy(alpha = if (isSelected) 0.25f else 0.12f),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, color),
                                modifier = Modifier.clickable { selectedColorHex = hex }
                            ) {
                                Text(
                                    text = label,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    val servantCandidates = state.allGroupServantCandidates
                    Text("تعيين خادم مسؤول عن هذا البلوك:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val unassignedSelected = selectedServantUid.isBlank()
                        OutlinedButton(
                            onClick = { selectedServantUid = "" },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                width = if (unassignedSelected) 2.dp else 1.dp,
                                color = if (unassignedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (unassignedSelected) "✓ خادم جديد / بدون ربط حالياً" else "خادم جديد / بدون ربط حالياً",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (unassignedSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        servantCandidates.forEach { member ->
                            val mUid = member.effectiveUid
                            val selected = selectedServantUid == mUid
                            val isMemberAdmin = mUid == state.group?.adminId
                            OutlinedButton(
                                onClick = {
                                    selectedServantUid = if (selected) "" else mUid
                                    if (!selected && newBlockName.isBlank()) {
                                        newBlockName = member.displayName
                                    }
                                },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = buildString {
                                        if (selected) append("✓ ")
                                        append(member.displayName)
                                        if (isMemberAdmin) append(" (الأمين)")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newBlockName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateBlock(trimmed, selectedColorHex, selectedServantUid)
                            showCreateBlockDialog = false
                        }
                    },
                    enabled = newBlockName.trim().isNotEmpty(),
                    modifier = Modifier.testTag("dialog_confirm_create_block_button")
                ) {
                    Text("إنشاء البلوك")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBlockDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Admin Edit Block Dialog (Name, Color, Assigned Servant)
    val editingBlock = blockBeingEdited
    if (editingBlock != null && state.isAdmin) {
        var editedName by rememberSaveable(editingBlock.id) { mutableStateOf(editingBlock.servantName) }
        var editedColorHex by rememberSaveable(editingBlock.id) { mutableStateOf(editingBlock.colorHex) }
        var editedServantId by rememberSaveable(editingBlock.id) { mutableStateOf(editingBlock.servantId) }

        AlertDialog(
            onDismissRequest = { blockBeingEdited = null },
            title = { Text("تعديل بلوك «${editingBlock.servantName}»", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("اسم البلوك / الخادم") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_edit_block_name_input")
                    )
                    Text("لون البلوك:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BlockColorPreset.entries.forEach { preset ->
                            val hex = preset.hex
                            val label = preset.arabicName
                            val color = KamSemanticColors.blockColor(hex)
                            val isSelected = editedColorHex.equals(hex, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = color.copy(alpha = if (isSelected) 0.25f else 0.12f),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, color),
                                modifier = Modifier.clickable { editedColorHex = hex }
                            ) {
                                Text(
                                    text = label,
                                    color = color,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    val servantCandidates = state.allGroupServantCandidates
                    Text("تعيين الخادم المسؤول عن البلوك:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val unassignedSelected = editedServantId.isBlank()
                        OutlinedButton(
                            onClick = { editedServantId = "" },
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(
                                width = if (unassignedSelected) 2.dp else 1.dp,
                                color = if (unassignedSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (unassignedSelected) "✓ بدون ربط بحساب خادم" else "بدون ربط بحساب خادم",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (unassignedSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        servantCandidates.forEach { member ->
                            val mUid = member.effectiveUid
                            val selected = editedServantId == mUid
                            val isMemberAdmin = mUid == state.group?.adminId
                            OutlinedButton(
                                onClick = {
                                    editedServantId = if (selected) "" else mUid
                                },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = buildString {
                                        if (selected) append("✓ ")
                                        append(member.displayName)
                                        if (isMemberAdmin) append(" (الأمين)")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editedName.trim()
                        if (trimmed.isNotEmpty()) {
                            onUpdateBlock(editingBlock, trimmed, editedColorHex, editedServantId)
                            blockBeingEdited = null
                        }
                    },
                    enabled = editedName.trim().isNotEmpty(),
                    modifier = Modifier.testTag("dialog_save_edit_block_button")
                ) {
                    Text("حفظ التعديلات")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockBeingEdited = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 3-Tier Safe Delete Block Dialog (Tier 1 & Tier 2 options)
    val safeDeleteTarget = blockPendingSafeDelete
    if (safeDeleteTarget != null && state.isAdmin) {
        val countInTarget = state.students.count { it.blockId == safeDeleteTarget.id }
        AlertDialog(
            onDismissRequest = { blockPendingSafeDelete = null },
            title = {
                Text(
                    text = "خيارات حذف «بلوك ${safeDeleteTarget.servantName}»",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "يحتوي هذا البلوك على $countInTarget طفل. اختر طريقة الحذف المطلوبة:"
                    )
                    Button(
                        onClick = {
                            onDeleteBlockKeepingChildren(safeDeleteTarget)
                            blockPendingSafeDelete = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_block_keep_children_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "حذف البلوك فقط (الاحتفاظ بالأطفال بدون بلوك)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            blockPendingHardDeleteConfirm = safeDeleteTarget
                            blockPendingSafeDelete = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_block_with_children_danger_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "حذف البلوك مع كل أطفاله نهائياً ($countInTarget طفل)",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { blockPendingSafeDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Second Confirmation Dialog for Hard-Deleting Block WITH All Its Children
    val hardDeleteTarget = blockPendingHardDeleteConfirm
    if (hardDeleteTarget != null && state.isAdmin) {
        val countInTarget = state.students.count { it.blockId == hardDeleteTarget.id }
        AlertDialog(
            onDismissRequest = { blockPendingHardDeleteConfirm = null },
            title = {
                Text(
                    text = "تأكيد نهائي لحذف البلوك والأطفال",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "تحذير: سيتم حذف «بلوك ${hardDeleteTarget.servantName}» وحذف جميع أطفاله ($countInTarget طفل) ونقاطهم وسجل حضورهم نهائياً. هل أنت متأكد تماماً؟"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBlockWithChildren(hardDeleteTarget)
                        blockPendingHardDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_block_with_children_button")
                ) {
                    Text("نعم، احذف البلوك وكل أطفاله")
                }
            },
            dismissButton = {
                TextButton(onClick = { blockPendingHardDeleteConfirm = null }) {
                    Text("تراجع")
                }
            }
        )
    }

    // Personal Servant Alias Dialog (Visible only to current user)
    val aliasTarget = memberPendingAlias
    if (aliasTarget != null) {
        val (targetUid, officialName) = aliasTarget
        AlertDialog(
            onDismissRequest = { memberPendingAlias = null },
            title = { Text("تسمية خاصة لـ «$officialName»", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "هذا الاسم يظهر لك أنت فقط في المحادثات وقائمة الأعضاء (مثل الأسماء المحفوظة على هاتفك).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = aliasInputText,
                        onValueChange = { aliasInputText = it },
                        label = { Text("الاسم المفضل لديك (اتركه فارغاً للاسم الأصلي)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_member_alias_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePersonalServantAlias(targetUid, aliasInputText.trim())
                        memberPendingAlias = null
                    },
                    modifier = Modifier.testTag("group_member_save_alias_button")
                ) {
                    Text("حفظ التسمية")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingAlias = null }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Edit Own Display Name Dialog from Group Members List
    if (showEditOwnNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditOwnNameDialog = false },
            title = { Text("تعديل اسمك في الخدمة", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = ownNameDraft,
                    onValueChange = { ownNameDraft = it },
                    label = { Text("اسمك الظاهر لباقي الخدام") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_edit_own_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = ownNameDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onEditOwnDisplayName(trimmed)
                            showEditOwnNameDialog = false
                        }
                    },
                    enabled = ownNameDraft.trim().isNotEmpty(),
                    modifier = Modifier.testTag("group_save_own_name_button")
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditOwnNameDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
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
    currentUserEmail: String = "",
    onOpenPhoneWizard: () -> Unit = {},
    onUpdateOwnServantAvatar: (String) -> Unit = {},
    onDeleteOwnPhones: () -> Unit = {},
    onEditOwnDisplayName: (String) -> Unit = {}
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

    val extColors = LocalKamExtendedColors.current

    LazyColumn(
        state = settingsListState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_lazy_column"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
    ) {
        // 0a. Servant WhatsApp Profile & Phone Numbers Card
        item(key = "servant_profile_and_phone_settings", contentType = "settings_card") {
            ServantProfileSettingsCard(
                servantUid = currentUserId,
                servantName = ownServantName,
                email = currentUserEmail.ifBlank { state.userProfile?.email.orEmpty() },
                groupId = group?.id.orEmpty(),
                memberProfile = ownMemberProfile,
                primaryPhone = state.ownPrimaryPhone,
                secondaryPhone = state.ownSecondaryPhone,
                accentColor = ownAccentColor,
                onOpenPhoneWizard = onOpenPhoneWizard,
                onOpenAvatarPicker = { showOwnServantPhotoDialog = true },
                onDeletePhones = onDeleteOwnPhones,
                onEditDisplayName = onEditOwnDisplayName
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
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, extColors.hairline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                HorizontalDivider(color = extColors.hairline)
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
                    showInviteCode = grp.isUserAdmin(currentUserId),
                    onClick = { onSwitchGroup(grp.inviteCode) }
                )
            }
        }
        item(key = "multi_group_switcher_footer", contentType = "settings_section_footer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
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
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, extColors.hairline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                HorizontalDivider(color = extColors.hairline)
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
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
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

        // 2. Group Policy Decisions — flattened header + 5 discrete switch items + footer
        item(key = "group_policy_settings", contentType = "settings_section_header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .border(1.dp, extColors.hairline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
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
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
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

        // 3. Account & Session Actions
        item(key = "account_actions", contentType = "settings_card") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, extColors.hairline, RoundedCornerShape(16.dp))
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
    val extColors = LocalKamExtendedColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by GitHubSmartSyncEngine.syncState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, extColors.hairline, RoundedCornerShape(16.dp))
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
    showInviteCode: Boolean = false,
    onClick: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else extColors.hairline
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
                    text = if (showInviteCode) "${group.name} (رمز: ${group.inviteCode})" else group.name,
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
    currentUserId: String,
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

    val extColors = LocalKamExtendedColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, extColors.hairline, RoundedCornerShape(16.dp))
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
                showInviteCode = grp.isUserAdmin(currentUserId),
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




