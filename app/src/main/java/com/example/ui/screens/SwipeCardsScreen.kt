package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ChildNote
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.theme.KamSemanticColors
import com.example.ui.viewmodel.KamScreenState
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class CardSwipeDecision {
    AWARDED,
    NOT_RECEIVED,
    NOTED
}

@Immutable
private data class CardSwipeSessionEntry(
    val student: Student,
    val decision: CardSwipeDecision,
    val note: String = ""
)

@Composable
fun SwipeCardsScreen(
    state: KamScreenState,
    currentUserId: String,
    onAwardCard: (Student, CardType) -> Unit,
    onUndoCardEvent: (CardEvent) -> Unit,
    onAddQuickComment: (Student, String) -> Unit,
    onPostNotReceivedReport: (Student, CardType, String) -> Unit,
    onOpenMemorizationSheet: (Student) -> Unit,
    onOpenSmartAttendance: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activeWeekKey = remember(state.selectedWeekKey, state.selectedMonthKey, state.currentWeekKey) {
        state.selectedWeekKey?.takeIf { it.isNotBlank() }
            ?: SaturdayCalendarHelper.buildWeeksForMonth(
                year = state.selectedMonthKey.substringBefore("-").toIntOrNull() ?: 2026,
                monthNumber = state.selectedMonthKey.substringAfter("-").toIntOrNull() ?: 1
            ).lastOrNull()?.weekKey
            ?: state.currentWeekKey.ifBlank { SaturdayCalendarHelper.currentWeekKey() }
    }

    val availableCards = remember(state.enabledCardTypes) {
        state.enabledCardTypes.ifEmpty { CardType.entries }
    }
    var selectedCardId by rememberSaveable { mutableStateOf(availableCards.first().id) }
    val selectedCard = remember(selectedCardId, availableCards) {
        availableCards.firstOrNull { it.id == selectedCardId } ?: availableCards.first()
    }

    // Order students by block sort order then student position/number
    val orderedStudents = remember(state.students, state.blocks) {
        val studentComparator = compareBy<Student> { it.effectivePosition }
            .thenBy { it.sortOrder }
            .thenBy { it.studentNumber }
        val sortedBlocks = state.blocks.sortedBy { it.sortOrder }
        val validBlockIds = sortedBlocks.map { it.id }.toSet()
        val byBlock = state.students.groupBy { it.blockId }
        val result = ArrayList<Student>(state.students.size)
        for (block in sortedBlocks) {
            result.addAll(byBlock[block.id].orEmpty().sortedWith(studentComparator))
        }
        result.addAll(
            state.students
                .filter { it.blockId.isBlank() || it.blockId !in validBlockIds }
                .sortedWith(studentComparator)
        )
        result
    }
    val blockById = remember(state.blocks) { state.blocks.associateBy { it.id } }

    // Per-card session state keyed by "${cardType.id}_$activeWeekKey"
    val scopeKey = "${selectedCard.id}_$activeWeekKey"
    val sessionDecisions = remember(scopeKey) { mutableStateMapOf<String, CardSwipeSessionEntry>() }
    val undoStack = remember(scopeKey) { mutableStateListOf<CardSwipeSessionEntry>() }
    val sentNotReceivedKeys = remember { mutableSetOf<String>() }

    // Children who ALREADY received this card type in this week are automatically hidden
    val alreadyEarnedStudentIds = remember(state.effectiveAllCardEvents, selectedCard.id, activeWeekKey) {
        state.effectiveAllCardEvents
            .filter { it.cardType == selectedCard.id && it.weekKey == activeWeekKey }
            .map { it.studentId }
            .toSet()
    }

    val remainingStudents = remember(orderedStudents, alreadyEarnedStudentIds, sessionDecisions.size) {
        orderedStudents.filter { stu ->
            stu.id !in alreadyEarnedStudentIds && !sessionDecisions.containsKey(stu.id)
        }
    }

    var currentIndex by rememberSaveable(scopeKey) { mutableIntStateOf(0) }
    var showSummaryView by rememberSaveable(scopeKey) { mutableStateOf(false) }
    var studentPendingNote by remember { mutableStateOf<Student?>(null) }

    LaunchedEffect(remainingStudents.size) {
        if (remainingStudents.isEmpty() && sessionDecisions.isNotEmpty()) {
            showSummaryView = true
        } else if (currentIndex >= remainingStudents.size && remainingStudents.isNotEmpty()) {
            currentIndex = remainingStudents.lastIndex
        }
    }

    // Rule 2: Preload ONLY currently visible + next 1 child (count = 2) on Dispatchers.IO
    LaunchedEffect(currentIndex, remainingStudents, showSummaryView) {
        if (showSummaryView || remainingStudents.isEmpty()) return@LaunchedEffect
        val safeIdx = currentIndex.coerceIn(0, remainingStudents.lastIndex)
        val upcoming = remainingStudents.drop(safeIdx).take(2)
        StudentAvatarImageHelper.preloadNextAttendancePhotosAsync(
            context = context,
            upcomingStudents = upcoming,
            count = 2
        )
    }

    fun flushPendingNotReceivedReports() {
        val toSend = sessionDecisions.values.filter { entry ->
            entry.decision == CardSwipeDecision.NOT_RECEIVED &&
                sentNotReceivedKeys.add("${entry.student.id}_${selectedCard.id}_$activeWeekKey")
        }
        toSend.forEach { entry ->
            onPostNotReceivedReport(
                entry.student,
                selectedCard,
                entry.note
            )
        }
    }

    DisposableEffect(scopeKey) {
        onDispose {
            flushPendingNotReceivedReports()
        }
    }

    BackHandler {
        flushPendingNotReceivedReports()
        onClose()
    }

    val awardedEntries = sessionDecisions.values.filter { it.decision == CardSwipeDecision.AWARDED }
    val notReceivedEntries = sessionDecisions.values.filter { it.decision == CardSwipeDecision.NOT_RECEIVED }
    val notedEntries = sessionDecisions.values.filter { it.decision == CardSwipeDecision.NOTED }

    fun handleSwipeAward(student: Student) {
        onAwardCard(student, selectedCard)
        val entry = CardSwipeSessionEntry(student, CardSwipeDecision.AWARDED)
        sessionDecisions[student.id] = entry
        undoStack.add(entry)
    }

    fun handleSwipeNotReceived(student: Student, note: String = "") {
        val entry = CardSwipeSessionEntry(student, CardSwipeDecision.NOT_RECEIVED, note)
        sessionDecisions[student.id] = entry
        undoStack.add(entry)
    }

    fun handleSaveQuickNote(student: Student, noteText: String) {
        val cleanNote = noteText.trim().take(ChildNote.MAX_TEXT_LENGTH)
        if (cleanNote.isNotBlank()) {
            onAddQuickComment(student, cleanNote)
        }
        val entry = CardSwipeSessionEntry(student, CardSwipeDecision.NOTED, cleanNote)
        sessionDecisions[student.id] = entry
        undoStack.add(entry)
        studentPendingNote = null
    }

    fun handleUndoLast() {
        val last = undoStack.removeLastOrNull() ?: return
        sessionDecisions.remove(last.student.id)
        if (last.decision == CardSwipeDecision.AWARDED) {
            val matchingEvt = state.cardEvents.firstOrNull {
                it.studentId == last.student.id &&
                    it.cardType == selectedCard.id &&
                    it.weekKey == activeWeekKey &&
                    it.servantId == currentUserId
            }
            if (matchingEvt != null) {
                onUndoCardEvent(matchingEvt)
            }
        }
        showSummaryView = false
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("swipe_cards_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header Bar
            SwipeCardsTopHeader(
                weekKey = activeWeekKey,
                selectedCard = selectedCard,
                remainingCount = remainingStudents.size,
                awardedCount = awardedEntries.size + alreadyEarnedStudentIds.size,
                notReceivedCount = notReceivedEntries.size,
                notedCount = notedEntries.size,
                showSummaryView = showSummaryView,
                canUndo = undoStack.isNotEmpty(),
                onUndo = ::handleUndoLast,
                onToggleSummary = { showSummaryView = !showSummaryView },
                onSwitchToAttendance = {
                    flushPendingNotReceivedReports()
                    onOpenSmartAttendance()
                },
                onClose = {
                    flushPendingNotReceivedReports()
                    onClose()
                }
            )

            // 7 CardType Selector Strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableCards, key = { it.id }) { card ->
                    val cardColor = KamSemanticColors.cardColor(card)
                    val isSelected = card.id == selectedCard.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) cardColor.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) cardColor else cardColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .clickable {
                                flushPendingNotReceivedReports()
                                selectedCardId = card.id
                                showSummaryView = false
                            }
                            .testTag("swipe_card_type_chip_${card.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(text = card.badgeSymbol, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = card.arabicTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) cardColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "+${card.points}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = cardColor
                            )
                        }
                    }
                }
            }

            if (orderedStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "لا يوجد أطفال مسجلون في المجموعة بعد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = onClose) {
                            Text("عودة للرئيسية")
                        }
                    }
                }
            } else if (showSummaryView || remainingStudents.isEmpty()) {
                SwipeCardsSummaryView(
                    selectedCard = selectedCard,
                    alreadyEarnedCount = alreadyEarnedStudentIds.size,
                    awardedEntries = awardedEntries,
                    notReceivedEntries = notReceivedEntries,
                    notedEntries = notedEntries,
                    blockById = blockById,
                    resolvedBlockOwnerName = { blk ->
                        state.resolvedServantName(blk.servantId, blk.servantName)
                    },
                    onSendReportsNow = {
                        flushPendingNotReceivedReports()
                    },
                    onResumeCards = { showSummaryView = false },
                    onFinishAndClose = {
                        flushPendingNotReceivedReports()
                        onClose()
                    }
                )
            } else {
                val safeIdx = currentIndex.coerceIn(0, remainingStudents.lastIndex)
                val activeStudent = remainingStudents[safeIdx]
                val ownerBlock = blockById[activeStudent.blockId]
                val blockTitle = ownerBlock?.let {
                    "بلوك ${state.resolvedServantName(it.servantId, it.servantName)}"
                } ?: "بدون بلوك"

                SwipeCardsDeckContent(
                    student = activeStudent,
                    selectedCard = selectedCard,
                    block = ownerBlock,
                    blockTitle = blockTitle,
                    currentIndex = safeIdx,
                    totalRemaining = remainingStudents.size,
                    canUndo = undoStack.isNotEmpty(),
                    onSwipeAward = { handleSwipeAward(activeStudent) },
                    onSwipeNotReceived = { handleSwipeNotReceived(activeStudent) },
                    onSwipeNote = { studentPendingNote = activeStudent },
                    onOpenMemorizationSheet = if (selectedCard == CardType.MEMORIZATION) {
                        { onOpenMemorizationSheet(activeStudent) }
                    } else null,
                    onUndo = ::handleUndoLast,
                    onNavigatePrev = {
                        currentIndex = if (safeIdx > 0) safeIdx - 1 else remainingStudents.lastIndex
                    },
                    onNavigateNext = {
                        currentIndex = (safeIdx + 1) % remainingStudents.size
                    }
                )
            }
        }

        studentPendingNote?.let { targetStu ->
            QuickSwipeNoteDialog(
                studentName = targetStu.name,
                cardTitle = selectedCard.arabicTitle,
                onConfirm = { noteText ->
                    handleSaveQuickNote(targetStu, noteText)
                },
                onDismiss = { studentPendingNote = null }
            )
        }
    }
}

@Composable
private fun SwipeCardsTopHeader(
    weekKey: String,
    selectedCard: CardType,
    remainingCount: Int,
    awardedCount: Int,
    notReceivedCount: Int,
    notedCount: Int,
    showSummaryView: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onToggleSummary: () -> Unit,
    onSwitchToAttendance: () -> Unit,
    onClose: () -> Unit
) {
    val cardColor = KamSemanticColors.cardColor(selectedCard)
    val totalInCycle = (remainingCount + awardedCount + notReceivedCount + notedCount).coerceAtLeast(1)
    val doneInCycle = awardedCount + notReceivedCount + notedCount
    val progressFraction = (doneInCycle.toFloat() / totalInCycle.toFloat()).coerceIn(0f, 1f)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_swipe_cards_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع للرئيسية"
                        )
                    }
                    Column {
                        Text(
                            text = "توزيع الكروت بالسحب",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${selectedCard.badgeSymbol} ${selectedCard.arabicTitle} • $weekKey",
                            style = MaterialTheme.typography.labelSmall,
                            color = cardColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canUndo) {
                        IconButton(
                            onClick = onUndo,
                            modifier = Modifier.testTag("top_undo_card_swipe_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "تراجع عن آخر سحب",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSwitchToAttendance,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("switch_to_attendance_from_cards_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FactCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تحضير",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleSummary,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("toggle_swipe_cards_summary_button")
                    ) {
                        Text(
                            text = if (showSummaryView) "البطاقات" else "الملخص",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3 Live Counters Strip (تم التسليم / لم يستلم / ملاحظة)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CounterMiniBadge("تم التسليم", awardedCount, KamSemanticColors.NotificationCardPositive, Modifier.weight(1f))
                CounterMiniBadge("لم يستلم", notReceivedCount, KamSemanticColors.NotificationAbsentIssue, Modifier.weight(1f))
                CounterMiniBadge("ملاحظة", notedCount, KamSemanticColors.NotificationChildComment, Modifier.weight(1f))
                CounterMiniBadge("متبقي", remainingCount, cardColor, Modifier.weight(1f))
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                color = cardColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun CounterMiniBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwipeCardsDeckContent(
    student: Student,
    selectedCard: CardType,
    block: ServantBlock?,
    blockTitle: String,
    currentIndex: Int,
    totalRemaining: Int,
    canUndo: Boolean,
    onSwipeAward: () -> Unit,
    onSwipeNotReceived: () -> Unit,
    onSwipeNote: () -> Unit,
    onOpenMemorizationSheet: (() -> Unit)?,
    onUndo: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blockAccent = remember(block?.colorHex) {
        if (block != null) KamSemanticColors.blockColor(block.colorHex)
        else KamSemanticColors.unassignedBlock
    }

    // Load large photo asynchronously from local disk / memory cache (only visible + next 1 are preloaded)
    var largePhoto by remember(student.id, student.effectiveAvatarKey) {
        mutableStateOf(StudentAvatarImageHelper.getCachedLargeAttendancePhoto(student))
    }
    LaunchedEffect(student.id, student.effectiveAvatarKey) {
        if (largePhoto == null && (student.effectiveHasAvatar || student.avatarBase64.isNotBlank())) {
            largePhoto = StudentAvatarImageHelper.loadLargeAttendancePhotoBitmap(context, student)
        }
    }

    val offsetX = remember(student.id, selectedCard.id) { Animatable(0f) }
    val offsetY = remember(student.id, selectedCard.id) { Animatable(0f) }

    val dragDx = offsetX.value
    val dragDy = offsetY.value
    val horizontalThreshold = 150f
    val verticalDownThreshold = 150f

    val activePreview: CardSwipeDecision? = when {
        abs(dragDx) >= abs(dragDy) && dragDx > 60f -> CardSwipeDecision.AWARDED
        abs(dragDx) >= abs(dragDy) && dragDx < -60f -> CardSwipeDecision.NOT_RECEIVED
        dragDy > 60f -> CardSwipeDecision.NOTED
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Direction Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CardLegendPill("يمين ← أخذ الكارت", KamSemanticColors.NotificationCardPositive)
            CardLegendPill("أسفل ↓ ملاحظة", KamSemanticColors.NotificationChildComment)
            CardLegendPill("يسار → لم يأخذ", KamSemanticColors.NotificationAbsentIssue)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Draggable Card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                ),
                border = BorderStroke(
                    width = 2.5.dp,
                    color = when (activePreview) {
                        CardSwipeDecision.AWARDED -> KamSemanticColors.NotificationCardPositive
                        CardSwipeDecision.NOT_RECEIVED -> KamSemanticColors.NotificationAbsentIssue
                        CardSwipeDecision.NOTED -> KamSemanticColors.NotificationChildComment
                        null -> blockAccent
                    }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value.coerceAtLeast(-40f)
                        rotationZ = (offsetX.value / 28f).coerceIn(-12f, 12f)
                    }
                    .pointerInput(student.id, selectedCard.id) {
                        detectDragGestures(
                            onDragEnd = {
                                val endX = offsetX.value
                                val endY = offsetY.value
                                when {
                                    abs(endX) >= abs(endY) && endX > horizontalThreshold -> {
                                        scope.launch {
                                            offsetX.snapTo(0f)
                                            offsetY.snapTo(0f)
                                            onSwipeAward()
                                        }
                                    }
                                    abs(endX) >= abs(endY) && endX < -horizontalThreshold -> {
                                        scope.launch {
                                            offsetX.snapTo(0f)
                                            offsetY.snapTo(0f)
                                            onSwipeNotReceived()
                                        }
                                    }
                                    endY > verticalDownThreshold -> {
                                        scope.launch {
                                            offsetX.animateTo(0f, tween(150))
                                            offsetY.animateTo(0f, tween(150))
                                            onSwipeNote()
                                        }
                                    }
                                    else -> {
                                        scope.launch {
                                            launch { offsetX.animateTo(0f, tween(180)) }
                                            launch { offsetY.animateTo(0f, tween(180)) }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    launch { offsetX.animateTo(0f, tween(180)) }
                                    launch { offsetY.animateTo(0f, tween(180)) }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x)
                                    offsetY.snapTo((offsetY.value + dragAmount.y).coerceAtLeast(-40f))
                                }
                            }
                        )
                    }
                    .testTag("swipe_cards_student_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = blockAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, blockAccent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(blockAccent)
                                )
                                Text(
                                    text = blockTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onOpenMemorizationSheet != null) {
                                Surface(
                                    color = KamSemanticColors.NotificationMemorization.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, KamSemanticColors.NotificationMemorization),
                                    modifier = Modifier.clickable(onClick = onOpenMemorizationSheet)
                                ) {
                                    Text(
                                        text = "📖 التسميع",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = KamSemanticColors.NotificationMemorization,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "رقم #${student.studentNumber}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(blockAccent.copy(alpha = 0.14f))
                            .border(2.dp, blockAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        val photoBitmap: ImageBitmap? = largePhoto
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap,
                                contentDescription = student.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val initials = student.avatarInitials.ifBlank {
                                ArabicInitialsHelper.extractInitials(student.name)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = blockAccent.copy(alpha = 0.25f),
                                    border = BorderStroke(3.dp, blockAccent),
                                    modifier = Modifier.size(128.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 42.sp
                                            ),
                                            color = blockAccent
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "بدون صورة مضافة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (activePreview != null) {
                            val (overlayText, overlayColor) = when (activePreview) {
                                CardSwipeDecision.AWARDED -> "✓ أخذ ${selectedCard.arabicTitle} (+${selectedCard.points})" to KamSemanticColors.NotificationCardPositive
                                CardSwipeDecision.NOT_RECEIVED -> "✕ لم يأخذ الكارت" to KamSemanticColors.NotificationAbsentIssue
                                CardSwipeDecision.NOTED -> "✎ كتابة ملاحظة" to KamSemanticColors.NotificationChildComment
                            }
                            Surface(
                                color = overlayColor.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 18.dp)
                            ) {
                                Text(
                                    text = overlayText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onNavigatePrev) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "الطفل السابق"
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = student.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentIndex + 1} / $totalRemaining متبقي",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onNavigateNext) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "الطفل التالي"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // One-handed Bottom Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onSwipeAward,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KamSemanticColors.NotificationCardPositive,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_card_awarded")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("أخذ الكارت", fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }

            Button(
                onClick = onSwipeNote,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KamSemanticColors.NotificationChildComment,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_card_quick_note")
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ملاحظة", fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }

            Button(
                onClick = onSwipeNotReceived,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KamSemanticColors.NotificationAbsentIssue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("btn_card_not_received")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("لم يأخذ", fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }

            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_undo_card_swipe")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "تراجع",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CardLegendPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun QuickSwipeNoteDialog(
    studentName: String,
    cardTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = KamSemanticColors.NotificationChildComment
                )
                Text(
                    text = "ملاحظة على $studentName ($cardTitle)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "تُحفظ الملاحظة في ملف الطفل وتُرسل تلقائياً لجروب الخدّام بصورة الطفل واسمه وبلوكه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(ChildNote.MAX_TEXT_LENGTH) },
                    placeholder = { Text("اكتب ملاحظة سريعة...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("swipe_card_quick_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(noteText) },
                enabled = noteText.trim().isNotBlank(),
                modifier = Modifier.testTag("confirm_swipe_card_note_button")
            ) {
                Text("حفظ وإرسال")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun SwipeCardsSummaryView(
    selectedCard: CardType,
    alreadyEarnedCount: Int,
    awardedEntries: List<CardSwipeSessionEntry>,
    notReceivedEntries: List<CardSwipeSessionEntry>,
    notedEntries: List<CardSwipeSessionEntry>,
    blockById: Map<String, ServantBlock>,
    resolvedBlockOwnerName: (ServantBlock) -> String,
    onSendReportsNow: () -> Unit,
    onResumeCards: () -> Unit,
    onFinishAndClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("swipe_cards_summary_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = KamSemanticColors.cardColor(selectedCard).copy(alpha = 0.14f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, KamSemanticColors.cardColor(selectedCard).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${selectedCard.badgeSymbol} ملخص كارت ${selectedCard.arabicTitle} — مسجل مسبقاً: $alreadyEarnedCount • هذه الجلسة: +${awardedEntries.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(12.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (notReceivedEntries.isNotEmpty()) {
                item(key = "hdr_not_rec") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لم يستلموا الكارت (${notReceivedEntries.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = KamSemanticColors.NotificationAbsentIssue
                        )
                        TextButton(onClick = onSendReportsNow) {
                            Text("إرسال لجروب الخدّام")
                        }
                    }
                }
                items(notReceivedEntries, key = { "nr_${it.student.id}" }) { entry ->
                    val blkName = blockById[entry.student.blockId]?.let { "بلوك ${resolvedBlockOwnerName(it)}" } ?: "بدون بلوك"
                    Surface(
                        color = KamSemanticColors.NotificationAbsentIssue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, KamSemanticColors.NotificationAbsentIssue.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "#${entry.student.studentNumber} ${entry.student.name} • $blkName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            if (notedEntries.isNotEmpty()) {
                item(key = "hdr_noted") {
                    Text(
                        text = "ملاحظات سريعة (${notedEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = KamSemanticColors.NotificationChildComment
                    )
                }
                items(notedEntries, key = { "nt_${it.student.id}" }) { entry ->
                    Surface(
                        color = KamSemanticColors.NotificationChildComment.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = entry.student.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (entry.note.isNotBlank()) {
                                Text(
                                    text = entry.note,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            if (awardedEntries.isNotEmpty()) {
                item(key = "hdr_awarded") {
                    Text(
                        text = "أخذوا الكارت في هذه الجلسة (${awardedEntries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = KamSemanticColors.NotificationCardPositive
                    )
                }
                items(awardedEntries, key = { "aw_${it.student.id}" }) { entry ->
                    Surface(
                        color = KamSemanticColors.NotificationCardPositive.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ #${entry.student.studentNumber} ${entry.student.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onResumeCards,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text("رجوع للبطاقات")
            }
            Button(
                onClick = onFinishAndClose,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("finish_swipe_cards_button")
            ) {
                Text("إنهاء وإغلاق", fontWeight = FontWeight.Bold)
            }
        }
    }
}
