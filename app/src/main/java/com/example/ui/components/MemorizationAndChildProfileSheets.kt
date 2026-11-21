package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ChildNote
import com.example.data.model.ChildNoteType
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalKamExtendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemorizationNotesSheet(
    student: Student,
    blockName: String,
    blockAccent: Color,
    memorizationNotes: List<ChildNote>,
    alreadyHasMemorizationCardThisWeek: Boolean,
    isAdmin: Boolean,
    currentUserId: String,
    onAddMemorizationNote: (text: String, awardCard: Boolean) -> Unit,
    onToggleMemorizationCardOnly: () -> Unit,
    onDeleteNote: (ChildNote) -> Unit,
    onDismiss: () -> Unit
) {
    var noteInput by rememberSaveable(student.id) { mutableStateOf("") }
    var awardCardChecked by rememberSaveable(student.id) { mutableStateOf(!alreadyHasMemorizationCardThisWeek) }
    val timeFormatter = remember { SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()) }
    val memColor = KamSemanticColors.NotificationMemorization
    val extColors = LocalKamExtendedColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extColors.hairline),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("memorization_notes_sheet")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(memColor.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        StudentAvatarCircle(
                            student = student,
                            blockAccent = blockAccent,
                            size = 44.dp,
                            onClick = {},
                            blockLabel = blockName
                        )
                        Column {
                            Text(
                                text = "كارت المحفوظات • ${student.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = blockName,
                                style = MaterialTheme.typography.labelMedium,
                                color = blockAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Highlight Card: "آخر حاجة سمّعها الطفل"
                    item(key = "last_memorized_highlight") {
                        Surface(
                            color = memColor.copy(alpha = 0.11f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, memColor.copy(alpha = 0.55f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoStories,
                                        contentDescription = null,
                                        tint = memColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "آخر حاجة سمّعها الطفل:",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = memColor
                                    )
                                }
                                if (student.lastMemorizedText.isNotBlank()) {
                                    Text(
                                        text = student.lastMemorizedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val dateStr = if (student.lastMemorizedAtMillis > 0L) {
                                        timeFormatter.format(Date(student.lastMemorizedAtMillis))
                                    } else {
                                        ""
                                    }
                                    val meta = buildString {
                                        if (student.lastMemorizedBy.isNotBlank()) append("بواسطة ${student.lastMemorizedBy}")
                                        if (dateStr.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(dateStr)
                                        }
                                    }
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "لم يُسجَّل تسميع لهذا الطفل بعد — اكتب أول تسميع بالأسفل.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Quick Toggle Award Card only without note OR keep switch
                    item(key = "quick_mem_card_toggle") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (alreadyHasMemorizationCardThisWeek) {
                                            "تم تسجيل كارت المحفوظات هذا الأسبوع"
                                        } else {
                                            "تسجيل كارت المحفوظات بضغطة واحدة"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${CardType.MEMORIZATION.badgeSymbol} +${CardType.MEMORIZATION.points} نقاط",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = memColor
                                    )
                                }
                                OutlinedButton(onClick = onToggleMemorizationCardOnly) {
                                    Text(
                                        text = if (alreadyHasMemorizationCardThisWeek) "إلغاء الكارت" else "منح الكارت الآن"
                                    )
                                }
                            }
                        }
                    }

                    // Composer for new Memorization Note
                    item(key = "memorization_composer") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = noteInput,
                                onValueChange = { noteInput = it.take(ChildNote.MAX_TEXT_LENGTH) },
                                label = { Text("اكتب ما سمّعه الطفل الآن أو ملاحظة...") },
                                placeholder = { Text("مثال: مزمور ٢٣ كامل مع الشرح") },
                                maxLines = 4,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("memorization_note_input")
                            )
                            if (!alreadyHasMemorizationCardThisWeek) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "منح كارت المحفوظات مع الحفظ (+${CardType.MEMORIZATION.points})",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = awardCardChecked,
                                        onCheckedChange = { awardCardChecked = it }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    if (noteInput.trim().isNotBlank()) {
                                        onAddMemorizationNote(noteInput.trim(), !alreadyHasMemorizationCardThisWeek && awardCardChecked)
                                        noteInput = ""
                                    }
                                },
                                enabled = noteInput.trim().isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = memColor,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("save_memorization_note_button")
                            ) {
                                Text("حفظ ملاحظة المحفوظات وإرسالها للجروب", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    item(key = "memorization_history_header") {
                        Text(
                            text = "سجل ملاحظات المحفوظات (${memorizationNotes.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (memorizationNotes.isEmpty()) {
                        item(key = "memorization_history_empty") {
                            Text(
                                text = "لا توجد ملاحظات تسميع سابقة لهذا الطفل.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(memorizationNotes, key = { it.id }) { note ->
                            val dateStr = remember(note.effectiveTimestampMillis) {
                                if (note.effectiveTimestampMillis > 0L) {
                                    timeFormatter.format(Date(note.effectiveTimestampMillis))
                                } else {
                                    "الآن"
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, memColor.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = note.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${note.authorName} • $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isAdmin || note.authorUid == currentUserId) {
                                        IconButton(onClick = { onDeleteNote(note) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف الملاحظة",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildProfileFullSheet(
    student: Student,
    blocks: List<ServantBlock>,
    resolvedServantName: (ServantBlock) -> String,
    monthKey: String,
    monthPoints: Long,
    leaderboardRank: Int?,
    totalStudentsCount: Int,
    events: List<CardEvent>,
    comments: List<ChildNote>,
    isAdmin: Boolean,
    currentUserId: String,
    canServantUndoOwnEvents: Boolean,
    onDismiss: () -> Unit,
    onSaveEdits: (String, Int, String) -> Unit,
    onDeleteStudent: () -> Unit,
    onUndoCardEvent: (CardEvent) -> Unit,
    onOpenPhotoPickerDialog: () -> Unit,
    onUpdateStudentAvatar: (String) -> Unit,
    onAddChildComment: (String) -> Unit,
    onDeleteNote: (ChildNote) -> Unit,
    onOpenMemorizationSheet: () -> Unit
) {
    var isEditing by rememberSaveable(student.id) { mutableStateOf(false) }
    var editedName by rememberSaveable(student.id) { mutableStateOf(student.name) }
    var editedNumber by rememberSaveable(student.id) { mutableStateOf(student.studentNumber.toString()) }
    var editedBlockId by rememberSaveable(student.id) { mutableStateOf(student.blockId) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var commentInput by rememberSaveable(student.id) { mutableStateOf("") }

    // Sync draft fields from student ONLY when not actively editing so live Firestore snapshots never interrupt typing
    LaunchedEffect(student.id, student.name, student.studentNumber, student.blockId, isEditing) {
        if (!isEditing) {
            editedName = student.name
            editedNumber = student.studentNumber.toString()
            editedBlockId = student.blockId
        }
    }

    val timeFormatter = remember { SimpleDateFormat("dd/MM hh:mm a", Locale.getDefault()) }
    val ownerBlock = blocks.firstOrNull { it.id == student.blockId }
    val blockAccent = if (ownerBlock != null) {
        KamSemanticColors.blockColor(ownerBlock.colorHex)
    } else {
        KamSemanticColors.unassignedBlock
    }
    val blockLabel = ownerBlock?.let { "بلوك ${resolvedServantName(it)}" } ?: "بدون بلوك"
    val extColors = LocalKamExtendedColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extColors.hairline),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("child_profile_full_sheet")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Stylish Colored Header: Large Photo + Name + #Number + Block Badge + Edit + Admin Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(blockAccent.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        StudentAvatarCircle(
                            student = student,
                            blockAccent = blockAccent,
                            size = 62.dp,
                            onClick = onOpenPhotoPickerDialog,
                            testTag = "details_dialog_student_avatar_${student.id}",
                            blockLabel = blockLabel
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = blockAccent.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, blockAccent)
                                ) {
                                    Text(
                                        text = blockLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#${student.studentNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل بيانات الطفل",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isAdmin) {
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.testTag("admin_delete_student_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "حذف الطفل",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (isEditing) {
                        item(key = "edit_section") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "تعديل بيانات الطفل",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedTextField(
                                        value = editedName,
                                        onValueChange = { if (it.length <= 100) editedName = it },
                                        label = { Text("اسم الطفل") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_student_name_input")
                                    )
                                    OutlinedTextField(
                                        value = editedNumber,
                                        onValueChange = { raw ->
                                            val digits = raw.filter { ch -> ch.isDigit() }.take(4)
                                            editedNumber = digits
                                        },
                                        label = { Text("رقم الطفل") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_student_number_input")
                                    )

                                    // Block / Group Position Picker (Existing Blocks or Unassigned)
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "البلوك (مكان الطفل في المجموعات):",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            blocks.forEach { blk ->
                                                val blkColor = KamSemanticColors.blockColor(blk.colorHex)
                                                val isPicked = editedBlockId == blk.id
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isPicked) blkColor.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        width = if (isPicked) 2.dp else 1.dp,
                                                        color = if (isPicked) blkColor else extColors.hairline
                                                    ),
                                                    modifier = Modifier
                                                        .heightIn(min = 44.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { editedBlockId = blk.id }
                                                        .testTag("edit_student_block_option_${blk.id}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .clip(CircleShape)
                                                                .background(blkColor)
                                                        )
                                                        Text(
                                                            text = "${if (isPicked) "✓ " else ""}${resolvedServantName(blk)}",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = if (isPicked) FontWeight.ExtraBold else FontWeight.Medium,
                                                            color = if (isPicked) blkColor else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                            val isUnassignedPicked = editedBlockId.isBlank()
                                            val unassignedColor = KamSemanticColors.unassignedBlock
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isUnassignedPicked) unassignedColor.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    width = if (isUnassignedPicked) 2.dp else 1.dp,
                                                    color = if (isUnassignedPicked) unassignedColor else extColors.hairline
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { editedBlockId = "" }
                                                    .testTag("edit_student_block_option_unassigned")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(CircleShape)
                                                            .background(unassignedColor)
                                                    )
                                                    Text(
                                                        text = "${if (isUnassignedPicked) "✓ " else ""}بدون بلوك",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isUnassignedPicked) FontWeight.ExtraBold else FontWeight.Medium,
                                                        color = if (isUnassignedPicked) unassignedColor else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val cleanName = editedName.trim()
                                                if (cleanName.isNotEmpty()) {
                                                    val num = (editedNumber.toIntOrNull() ?: student.studentNumber.toInt())
                                                        .coerceIn(1, 2000)
                                                    onSaveEdits(cleanName, num, editedBlockId)
                                                    isEditing = false
                                                }
                                            },
                                            enabled = editedName.trim().isNotEmpty(),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("save_student_edits_button")
                                        ) {
                                            Text("حفظ التعديلات")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                editedName = student.name
                                                editedNumber = student.studentNumber.toString()
                                                editedBlockId = student.blockId
                                                isEditing = false
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                        ) {
                                            Text("إلغاء")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Monthly Summary Card (Points, Rank, Card Count, Last Memorized)
                    item(key = "monthly_summary_card") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "ملخص شهر $monthKey",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "$monthPoints نقطة • ${events.size} كارت",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (leaderboardRank != null && totalStudentsCount > 0) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "الترتيب #$leaderboardRank / $totalStudentsCount",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = KamSemanticColors.NotificationMemorization.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, KamSemanticColors.NotificationMemorization.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenMemorizationSheet)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "📖 آخر حاجة سمّعها الطفل:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = KamSemanticColors.NotificationMemorization
                                            )
                                            Text(
                                                text = student.lastMemorizedText.ifBlank { "اضغط لتسجيل تسميع أو فتح كارت المحفوظات" },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "فتح",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = KamSemanticColors.NotificationMemorization
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Card History Section
                    item(key = "card_history_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "سجل الكروت التفصيلي (${events.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (events.isEmpty()) {
                        item(key = "card_history_empty") {
                            Text(
                                text = "لا توجد كروت مسجلة لهذا الطفل في شهر $monthKey.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(events, key = { it.id }) { evt ->
                            val cardType = CardType.fromId(evt.cardType)
                            val canUndoThis = isAdmin || (canServantUndoOwnEvents && evt.servantId == currentUserId)
                            val dateStr = evt.createdAt?.toDate()?.let { timeFormatter.format(it) } ?: "الآن"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${cardType?.badgeSymbol.orEmpty()} ${cardType?.arabicTitle ?: evt.cardType} (+${evt.points})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "بواسطة: ${evt.servantName} • $dateStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (canUndoThis) {
                                    IconButton(onClick = { onUndoCardEvent(evt) }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = "إلغاء الكارت",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "comments_divider") { HorizontalDivider() }

                    // 4. Child Comments Section + Inline Composer
                    item(key = "child_comments_header_and_composer") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Comment,
                                    contentDescription = null,
                                    tint = KamSemanticColors.NotificationChildComment,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "تعليقات الخدّام على الطفل (${comments.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = commentInput,
                                    onValueChange = { commentInput = it.take(ChildNote.MAX_TEXT_LENGTH) },
                                    placeholder = { Text("أضف تعليقاً فورياً على الطفل...") },
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("profile_child_comment_input")
                                )
                                Button(
                                    onClick = {
                                        if (commentInput.trim().isNotBlank()) {
                                            onAddChildComment(commentInput.trim())
                                            commentInput = ""
                                        }
                                    },
                                    enabled = commentInput.trim().isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KamSemanticColors.NotificationChildComment,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("profile_send_comment_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "إرسال التعليق",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (comments.isEmpty()) {
                        item(key = "child_comments_empty") {
                            Text(
                                text = "لا توجد تعليقات مضافة لهذا الطفل بعد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(comments, key = { it.id }) { comment ->
                            val dateStr = remember(comment.effectiveTimestampMillis) {
                                if (comment.effectiveTimestampMillis > 0L) {
                                    timeFormatter.format(Date(comment.effectiveTimestampMillis))
                                } else {
                                    "الآن"
                                }
                            }
                            Surface(
                                color = KamSemanticColors.NotificationChildComment.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, KamSemanticColors.NotificationChildComment.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = comment.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${comment.authorName} • $dateStr",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isAdmin || comment.authorUid == currentUserId) {
                                        IconButton(onClick = { onDeleteNote(comment) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف التعليق",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "album_divider") { HorizontalDivider() }

                    // 5. Photo Album
                    item(key = "photo_album") {
                        StudentPhotoAlbumSection(
                            student = student,
                            isAdmin = isAdmin,
                            onUpdatePackedAvatarField = onUpdateStudentAvatar
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف الطفل", fontWeight = FontWeight.ExtraBold) },
            text = { Text("هل أنت متأكد من حذف '${student.name}' نهائياً من المجموعة؟") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteStudent()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف نهائي")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
