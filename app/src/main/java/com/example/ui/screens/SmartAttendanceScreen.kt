package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.example.R
import com.example.data.cache.LocalClassroomCache
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.CustomCardConfig
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.data.repository.ChatRepository
import com.example.data.repository.KamRepository
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalKamExtendedColors
import android.widget.Toast
import com.example.ui.viewmodel.KamScreenState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Immutable
private data class AttendanceActionHistoryEntry(
    val modeCardType: CardType?,
    val student: Student,
    val previousRecord: AttendanceRecord?,
    val appliedStatus: AttendanceStatus,
    val appliedNote: String,
    val didAwardCardOnHome: Boolean = false,
    val removedCardEventFromHome: CardEvent? = null
)

private enum class ReportEventKind {
    PRESENT,
    ABSENT,
    NO_CARD,
    ISSUE
}

@Immutable
private data class PendingSystemChatEntry(
    val studentId: String,
    val studentName: String,
    val studentNumber: Long,
    val kind: ReportEventKind,
    val note: String = "",
    val cardLabel: String = ""
)

private val attendanceIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private data class ParsedBaselineStatusMap(
    val firstServantName: String,
    val statusByStudentId: Map<String, Pair<ReportEventKind, String>>
)

private data class CrossServantDiffEntry(
    val studentId: String,
    val studentNumber: Long,
    val studentName: String,
    val firstStatusLabel: String,
    val secondStatusLabel: String,
    val note: String
)

private fun sanitizeReportField(raw: String): String =
    raw.replace("|", " ").replace("\n", " ").replace("\r", " ").trim()

private fun reportStatusArabicLabel(kind: ReportEventKind?, isCardReport: Boolean): String = when (kind) {
    ReportEventKind.PRESENT -> if (isCardReport) "أخذ الكارت" else "حاضر"
    ReportEventKind.ABSENT -> "غائب"
    ReportEventKind.NO_CARD -> "ما خدش الكارت"
    ReportEventKind.ISSUE -> "مشكلة"
    null -> "غير مسجّل"
}

private fun parseBaselineAttendanceStatuses(rawText: String): ParsedBaselineStatusMap {
    var firstServant = "الخادم الأول"
    val statusMap = LinkedHashMap<String, Pair<ReportEventKind, String>>()
    for (rawLine in rawText.lineSequence()) {
        val line = rawLine.trim()
        when {
            line.startsWith("S|") -> {
                firstServant = line.removePrefix("S|").trim().ifBlank { "الخادم الأول" }
            }
            line.startsWith("PID|") -> {
                val ids = line.removePrefix("PID|").split(",").map { it.trim() }.filter { it.isNotBlank() }
                for (id in ids) {
                    statusMap[id] = ReportEventKind.PRESENT to ""
                }
            }
            line.startsWith("A|") -> {
                val parts = line.split("|")
                val id = parts.getOrNull(1)?.trim().orEmpty()
                if (id.isNotBlank()) statusMap[id] = ReportEventKind.ABSENT to ""
            }
            line.startsWith("N|") -> {
                val parts = line.split("|")
                val id = parts.getOrNull(1)?.trim().orEmpty()
                val note = parts.getOrNull(4)?.trim().orEmpty()
                if (id.isNotBlank()) statusMap[id] = ReportEventKind.NO_CARD to note
            }
            line.startsWith("I|") -> {
                val parts = line.split("|")
                val id = parts.getOrNull(1)?.trim().orEmpty()
                val note = parts.getOrNull(4)?.trim().orEmpty()
                if (id.isNotBlank()) statusMap[id] = ReportEventKind.ISSUE to note
            }
        }
    }
    return ParsedBaselineStatusMap(
        firstServantName = firstServant,
        statusByStudentId = statusMap
    )
}

/**
 * Formats session attendance/card outcomes into `[ATTENDANCE_V3]` format with explicit
 * report-type identification (`T|`, `TL|`, `IC|`, `CK|`, `W|`, `SU|`, `PID|`) so readers
 * immediately know whether it is an Attendance report or a specific Card report, and a
 * second servant can compute exact diffs against the first servant's baseline.
 */
private fun formatMergedAttendanceSystemMessage(
    weekKey: String,
    reportTypeKey: String,
    reportTypeLabel: String,
    reportIcon: String,
    reportColorKey: String,
    servantUid: String,
    servantName: String,
    entries: Collection<PendingSystemChatEntry>,
    presentStudentIds: List<String>,
    presentStudentNames: List<String>,
    cardContextTitle: String = ""
): String {
    if (entries.isEmpty() && presentStudentNames.isEmpty()) return ""
    val absentList = entries
        .filter { it.kind == ReportEventKind.ABSENT }
        .sortedBy { it.studentNumber }
    val noCardList = entries
        .filter { it.kind == ReportEventKind.NO_CARD }
        .sortedBy { it.studentNumber }
    val issueList = entries
        .filter { it.kind == ReportEventKind.ISSUE }
        .sortedBy { it.studentNumber }

    val cleanPresentNames = presentStudentNames
        .map { sanitizeReportField(it) }
        .filter { it.isNotBlank() }
        .distinct()
    val cleanPresentIds = presentStudentIds
        .map { sanitizeReportField(it) }
        .filter { it.isNotBlank() }
        .distinct()

    if (absentList.isEmpty() && noCardList.isEmpty() && issueList.isEmpty() && cleanPresentNames.isEmpty()) {
        return ""
    }

    val safeServant = sanitizeReportField(servantName).ifBlank { "خادم" }
    val safeCardTitle = sanitizeReportField(cardContextTitle)
    val safeTypeLabel = sanitizeReportField(reportTypeLabel).ifBlank {
        if (safeCardTitle.isNotBlank()) "كارت $safeCardTitle" else "الحضور"
    }
    val safeIcon = sanitizeReportField(reportIcon).ifBlank { "📋" }
    val headerTitle = "$safeIcon تقرير $safeTypeLabel (بواسطة $safeServant)"
    val summaryParts = buildList {
        if (cleanPresentNames.isNotEmpty()) {
            add(if (safeCardTitle.isNotBlank()) "أخذوا الكارت: ${cleanPresentNames.size}" else "حاضر: ${cleanPresentNames.size}")
        }
        if (absentList.isNotEmpty()) add("غائب: ${absentList.size}")
        if (noCardList.isNotEmpty()) add("بدون كارت: ${noCardList.size}")
        if (issueList.isNotEmpty()) add("مشكلة: ${issueList.size}")
    }

    val sb = StringBuilder()
    sb.append(ChatMessage.ATTENDANCE_REPORT_V3_PREFIX).append('\n')
    sb.append(headerTitle)
    if (summaryParts.isNotEmpty()) {
        sb.append(" — ").append(summaryParts.joinToString(" | "))
    }
    if (weekKey.isNotBlank()) {
        sb.append('\n').append("W|").append(sanitizeReportField(weekKey))
    }
    sb.append('\n').append("T|").append(sanitizeReportField(reportTypeKey))
    sb.append('\n').append("TL|").append(safeTypeLabel)
    sb.append('\n').append("IC|").append(safeIcon)
    if (reportColorKey.isNotBlank()) {
        sb.append('\n').append("CK|").append(sanitizeReportField(reportColorKey))
    }
    sb.append('\n').append("S|").append(safeServant)
    if (servantUid.isNotBlank()) {
        sb.append('\n').append("SU|").append(sanitizeReportField(servantUid))
    }
    if (safeCardTitle.isNotBlank()) {
        sb.append('\n').append("C|").append(safeCardTitle)
    }

    val maxLen = ChatMessage.MAX_SYSTEM_TEXT_LENGTH
    val presentIdsLine = if (cleanPresentIds.isNotEmpty()) {
        "\nPID|" + cleanPresentIds.joinToString(",")
    } else {
        ""
    }
    val presentLine = if (cleanPresentNames.isNotEmpty()) {
        "\nPL|" + cleanPresentNames.joinToString(" • ")
    } else {
        ""
    }
    val reservedForPresent = (presentLine.length + presentIdsLine.length).coerceAtMost(1200)
    val bodyBudget = (maxLen - reservedForPresent).coerceAtLeast(450)

    for (item in absentList) {
        val line = "\nA|${sanitizeReportField(item.studentId)}|${item.studentNumber}|${sanitizeReportField(item.studentName)}"
        if (sb.length + line.length <= bodyBudget) {
            sb.append(line)
        }
    }
    for (item in noCardList) {
        val label = sanitizeReportField(item.cardLabel.ifBlank { safeCardTitle })
        val line = "\nN|${sanitizeReportField(item.studentId)}|${item.studentNumber}|${sanitizeReportField(item.studentName)}|$label"
        if (sb.length + line.length <= bodyBudget) {
            sb.append(line)
        }
    }
    for (item in issueList) {
        val line = "\nI|${sanitizeReportField(item.studentId)}|${item.studentNumber}|${sanitizeReportField(item.studentName)}|${sanitizeReportField(item.note)}"
        if (sb.length + line.length <= bodyBudget) {
            sb.append(line)
        }
    }
    if (presentIdsLine.isNotEmpty() && sb.length + presentIdsLine.length <= maxLen - 20) {
        sb.append(presentIdsLine)
    }
    if (presentLine.isNotEmpty() && sb.length + 6 < maxLen) {
        val remaining = maxLen - sb.length
        sb.append(presentLine.take(remaining))
    }
    return sb.toString().take(maxLen)
}

/**
 * Formats ONLY the differences between a second servant's report and the first servant's
 * fixed baseline report for the same `(weekKey, reportTypeKey)`.
 */
private fun formatCrossServantDiffSystemMessage(
    weekKey: String,
    reportTypeKey: String,
    reportTypeLabel: String,
    reportIcon: String,
    reportColorKey: String,
    servantUid: String,
    servantName: String,
    firstServantName: String,
    cardContextTitle: String,
    diffs: List<CrossServantDiffEntry>
): String {
    if (diffs.isEmpty()) return ""
    val safeServant = sanitizeReportField(servantName).ifBlank { "خادم" }
    val safeFirst = sanitizeReportField(firstServantName).ifBlank { "الخادم الأول" }
    val safeCardTitle = sanitizeReportField(cardContextTitle)
    val safeTypeLabel = sanitizeReportField(reportTypeLabel).ifBlank {
        if (safeCardTitle.isNotBlank()) "كارت $safeCardTitle" else "الحضور"
    }
    val safeIcon = sanitizeReportField(reportIcon).ifBlank { "📋" }

    val sb = StringBuilder()
    sb.append(ChatMessage.ATTENDANCE_REPORT_V3_PREFIX).append('\n')
    sb.append("🔄 اختلافات تقرير ").append(safeTypeLabel)
        .append(" (").append(safeServant).append(" عن ").append(safeFirst).append(")")
        .append(" — ").append(diffs.size).append(" اختلاف")
    if (weekKey.isNotBlank()) {
        sb.append('\n').append("W|").append(sanitizeReportField(weekKey))
    }
    sb.append('\n').append("T|").append(sanitizeReportField(reportTypeKey))
    sb.append('\n').append("TL|").append(safeTypeLabel)
    sb.append('\n').append("IC|").append(safeIcon)
    if (reportColorKey.isNotBlank()) {
        sb.append('\n').append("CK|").append(sanitizeReportField(reportColorKey))
    }
    sb.append('\n').append("S|").append(safeServant)
    if (servantUid.isNotBlank()) {
        sb.append('\n').append("SU|").append(sanitizeReportField(servantUid))
    }
    if (safeCardTitle.isNotBlank()) {
        sb.append('\n').append("C|").append(safeCardTitle)
    }
    sb.append('\n').append("DIFF|").append(safeFirst)

    val maxLen = ChatMessage.MAX_SYSTEM_TEXT_LENGTH
    for (diff in diffs.sortedBy { it.studentNumber }) {
        val line = "\nD|${sanitizeReportField(diff.studentId)}|${diff.studentNumber}|${sanitizeReportField(diff.studentName)}|${sanitizeReportField(diff.firstStatusLabel)}|${sanitizeReportField(diff.secondStatusLabel)}|${sanitizeReportField(diff.note)}"
        if (sb.length + line.length <= maxLen) {
            sb.append(line)
        }
    }
    return sb.toString().take(maxLen)
}

@Composable
fun SmartAttendanceScreen(
    state: KamScreenState,
    currentUserId: String,
    onAwardCard: (Student, CardType) -> Unit = { _, _ -> },
    onUndoCardEvent: (CardEvent) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val groupId = state.group?.inviteCode?.ifBlank { state.group.id }.orEmpty()
    val memberUids = remember(state.group, currentUserId) {
        (state.group?.memberUids.orEmpty() + currentUserId).filter { it.isNotBlank() }.distinct()
    }
    val activeWeekKey = remember(state.selectedWeekKey) {
        state.selectedWeekKey?.takeIf { it.isNotBlank() } ?: SaturdayCalendarHelper.currentWeekKey()
    }
    val servantDisplayName = remember(state.blocks, state.userProfile, currentUserId) {
        state.blocks.firstOrNull { it.servantId == currentUserId }?.servantName?.takeIf { it.isNotBlank() }
            ?: state.userProfile?.displayName?.takeIf { it.isNotBlank() }
            ?: "خادم"
    }

    val customCardConfig = state.customCardConfig
    val availableCardTypes = remember(customCardConfig.enabled) {
        CardType.entries.filter { it != CardType.CUSTOM_ELDER || customCardConfig.enabled }
    }

    // Selected mode: null = Normal Attendance ("الحضور"), non-null = any enabled CardType entry
    var selectedCardTypeId by rememberSaveable(groupId, activeWeekKey) { mutableStateOf("") }
    val selectedCardType: CardType? = remember(selectedCardTypeId, customCardConfig.enabled) {
        if (selectedCardTypeId.isBlank()) {
            null
        } else {
            val found = CardType.fromId(selectedCardTypeId)
            if (found == CardType.CUSTOM_ELDER && !customCardConfig.enabled) null else found
        }
    }

    val firestoreDb = remember(context) {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        require(databaseId.isNotBlank()) { "Missing firestore_database_id string resource" }
        FirebaseFirestore.getInstance(databaseId)
    }
    val kamRepository = remember(firestoreDb) { KamRepository(firestoreDb) }
    val chatRepository = remember(firestoreDb) { ChatRepository(firestoreDb) }

    // 1. Order students strictly by Block sortOrder, then student position/sortOrder/studentNumber
    val orderedStudents = remember(state.students, state.blocks) {
        val studentComparator = compareBy<Student> { it.effectivePosition }
            .thenBy { it.sortOrder }
            .thenBy { it.studentNumber }
        val sortedBlocks = state.blocks.sortedBy { it.sortOrder }
        val validBlockIds = sortedBlocks.map { it.id }.toSet()
        val byBlock = state.students.groupBy { it.blockId }
        val result = ArrayList<Student>(state.students.size)
        for (block in sortedBlocks) {
            val blockStudents = byBlock[block.id].orEmpty().sortedWith(studentComparator)
            result.addAll(blockStudents)
        }
        val unassigned = state.students
            .filter { it.blockId.isBlank() || it.blockId !in validBlockIds }
            .sortedWith(studentComparator)
        result.addAll(unassigned)
        result
    }

    val blockById = remember(state.blocks) {
        state.blocks.associateBy { it.id }
    }

    // 2. Attendance records map keyed by studentId (hydrated from local cache + live Firestore listener + optimistic writes)
    val recordsByStudentId = remember(groupId, activeWeekKey) {
        val map = mutableStateMapOf<String, AttendanceRecord>()
        LocalClassroomCache.getMemoryWeeklyAttendance(groupId, activeWeekKey).forEach { rec ->
            if (rec.studentId.isNotBlank()) map[rec.studentId] = rec
        }
        map
    }
    val optimisticOverrides = remember(groupId, activeWeekKey) {
        mutableStateMapOf<String, AttendanceRecord?>()
    }

    // Per-card swipe session records keyed by "${cardType.id}_${studentId}"
    val cardSessionRecordsByKey = remember(groupId, activeWeekKey) {
        mutableStateMapOf<String, AttendanceRecord>()
    }

    // Map of active weekly CardEvents on the Home screen for the currently selected CardType
    val weeklyCardEventByStudentId = remember(state.cardEvents, selectedCardType, activeWeekKey) {
        val card = selectedCardType
        if (card == null) {
            emptyMap()
        } else {
            state.cardEvents
                .asSequence()
                .filter { it.cardType == card.id && it.weekKey == activeWeekKey && it.studentId.isNotBlank() }
                .groupBy { it.studentId }
                .mapValues { (_, events) -> events.first() }
        }
    }

    // Effective records map for the active mode (Attendance mode vs selected CardType mode)
    val activeRecordsByStudentId: Map<String, AttendanceRecord> = remember(
        selectedCardType,
        orderedStudents,
        recordsByStudentId.toMap(),
        cardSessionRecordsByKey.toMap(),
        weeklyCardEventByStudentId
    ) {
        val card = selectedCardType
        if (card == null) {
            recordsByStudentId.toMap()
        } else {
            val result = LinkedHashMap<String, AttendanceRecord>()
            for (stu in orderedStudents) {
                val sessionKey = "${card.id}_${stu.id}"
                val sessionRec = cardSessionRecordsByKey[sessionKey]
                val liveCardEvent = weeklyCardEventByStudentId[stu.id]
                if (sessionRec != null) {
                    // If session marked PRESENT, or ABSENT/ISSUE, reflect it; if awarded outside later, prefer PRESENT
                    if (sessionRec.attendanceStatus == AttendanceStatus.ABSENT && liveCardEvent != null) {
                        // Awarded from outside after session mark
                        result[stu.id] = AttendanceRecord(
                            id = "${activeWeekKey}_${card.id}_${stu.id}",
                            groupId = groupId,
                            studentId = stu.id,
                            studentName = stu.name,
                            status = AttendanceStatus.PRESENT.id,
                            weekKey = activeWeekKey,
                            servantId = liveCardEvent.servantId.ifBlank { currentUserId },
                            servantName = liveCardEvent.servantName.ifBlank { servantDisplayName },
                            note = "",
                            memberUids = memberUids
                        )
                    } else {
                        result[stu.id] = sessionRec
                    }
                } else if (liveCardEvent != null) {
                    result[stu.id] = AttendanceRecord(
                        id = "${activeWeekKey}_${card.id}_${stu.id}",
                        groupId = groupId,
                        studentId = stu.id,
                        studentName = stu.name,
                        status = AttendanceStatus.PRESENT.id,
                        weekKey = activeWeekKey,
                        servantId = liveCardEvent.servantId.ifBlank { currentUserId },
                        servantName = liveCardEvent.servantName.ifBlank { servantDisplayName },
                        note = "",
                        memberUids = memberUids
                    )
                }
            }
            result
        }
    }

    LaunchedEffect(groupId, activeWeekKey) {
        if (groupId.isBlank() || activeWeekKey.isBlank()) return@LaunchedEffect
        val diskRecords = LocalClassroomCache.loadWeeklyAttendanceAsync(groupId, activeWeekKey)
        for (rec in diskRecords) {
            if (rec.studentId.isNotBlank() && !optimisticOverrides.containsKey(rec.studentId)) {
                recordsByStudentId[rec.studentId] = rec
            }
        }
        kamRepository.observeWeeklyAttendance(groupId, activeWeekKey).collect { cloudList ->
            val currentIds = cloudList.map { it.studentId }.toSet()
            val toRemove = recordsByStudentId.keys.filter { it !in currentIds && !optimisticOverrides.containsKey(it) }
            toRemove.forEach { recordsByStudentId.remove(it) }
            for (rec in cloudList) {
                if (rec.studentId.isNotBlank()) {
                    recordsByStudentId[rec.studentId] = rec
                    if (optimisticOverrides[rec.studentId]?.status == rec.status &&
                        optimisticOverrides[rec.studentId]?.note == rec.note
                    ) {
                        optimisticOverrides.remove(rec.studentId)
                    }
                }
            }
            LocalClassroomCache.saveWeeklyAttendance(groupId, activeWeekKey, recordsByStudentId.values.toList())
        }
    }

    // 3. Batching PRESENT, ABSENT, NO_CARD & ISSUE events into a unified V3 SYSTEM group chat report
    val pendingChatEntries = remember { LinkedHashMap<String, PendingSystemChatEntry>() }
    var batchFlushJob by remember { mutableStateOf<Job?>(null) }

    fun flushPendingSystemChatNow() {
        batchFlushJob?.cancel()
        batchFlushJob = null
        if (pendingChatEntries.isEmpty() || groupId.isBlank()) return
        val snapshotEntries = pendingChatEntries.values.toList()
        pendingChatEntries.clear()

        val activeCard = selectedCardType
        val isCardReport = activeCard != null
        val cardContextTitle = activeCard?.displayTitle(customCardConfig).orEmpty()
        val reportTypeKey = if (activeCard == null) "ATTENDANCE" else "CARD:${activeCard.id}"
        val reportTypeLabel = if (activeCard == null) {
            "الحضور"
        } else {
            val cleanCardName = cardContextTitle.trim()
            if (cleanCardName.startsWith("كارت")) cleanCardName else "كارت $cleanCardName"
        }
        val reportIcon = activeCard?.displaySymbol(customCardConfig) ?: "📋"
        val reportColorKey = activeCard?.displayColorKey(customCardConfig) ?: "EMERALD"

        // Collect all students known to be PRESENT in this mode for this week
        val presentFromActiveRecords = orderedStudents.filter { stu ->
            activeRecordsByStudentId[stu.id]?.attendanceStatus == AttendanceStatus.PRESENT
        }
        val presentBatchEntries = snapshotEntries
            .filter { it.kind == ReportEventKind.PRESENT }
            .sortedBy { it.studentNumber }
        val combinedPresentIds = (presentFromActiveRecords.map { it.id } + presentBatchEntries.map { it.studentId }).distinct()
        val combinedPresentNames = (presentFromActiveRecords.map { it.name } + presentBatchEntries.map { it.studentName }).distinct()

        val appContext = context.applicationContext
        val targetGroupId = groupId
        val targetWeekKey = activeWeekKey
        val targetServantName = servantDisplayName
        val targetServantUid = currentUserId
        val targetMembers = memberUids

        attendanceIoScope.launch {
            val baselineMsg = chatRepository.findBaselineAttendanceReport(
                groupId = targetGroupId,
                weekKey = targetWeekKey,
                reportTypeKey = reportTypeKey,
                excludeSenderUid = targetServantUid
            )

            val messageTextToSend = if (baselineMsg != null) {
                val baseline = parseBaselineAttendanceStatuses(baselineMsg.text)
                val diffs = ArrayList<CrossServantDiffEntry>()
                for (entry in snapshotEntries) {
                    val basePair = baseline.statusByStudentId[entry.studentId]
                    val baseKind = basePair?.first
                    if (baseKind != entry.kind) {
                        diffs.add(
                            CrossServantDiffEntry(
                                studentId = entry.studentId,
                                studentNumber = entry.studentNumber,
                                studentName = entry.studentName,
                                firstStatusLabel = reportStatusArabicLabel(baseKind, isCardReport),
                                secondStatusLabel = reportStatusArabicLabel(entry.kind, isCardReport),
                                note = entry.note
                            )
                        )
                    }
                }

                if (diffs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            "✓ تقرير $reportTypeLabel مطابق تماماً لتقرير ${baseline.firstServantName} — لا توجد اختلافات للإرسال",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                formatCrossServantDiffSystemMessage(
                    weekKey = targetWeekKey,
                    reportTypeKey = reportTypeKey,
                    reportTypeLabel = reportTypeLabel,
                    reportIcon = reportIcon,
                    reportColorKey = reportColorKey,
                    servantUid = targetServantUid,
                    servantName = targetServantName,
                    firstServantName = baseline.firstServantName,
                    cardContextTitle = cardContextTitle,
                    diffs = diffs
                )
            } else {
                formatMergedAttendanceSystemMessage(
                    weekKey = targetWeekKey,
                    reportTypeKey = reportTypeKey,
                    reportTypeLabel = reportTypeLabel,
                    reportIcon = reportIcon,
                    reportColorKey = reportColorKey,
                    servantUid = targetServantUid,
                    servantName = targetServantName,
                    entries = snapshotEntries,
                    presentStudentIds = combinedPresentIds,
                    presentStudentNames = combinedPresentNames,
                    cardContextTitle = cardContextTitle
                )
            }

            if (messageTextToSend.isBlank()) return@launch
            chatRepository.sendGroupMessage(
                groupId = targetGroupId,
                senderName = targetServantName,
                text = messageTextToSend,
                memberUids = targetMembers,
                type = ChatMessageType.SYSTEM,
                waitForServerAck = false
            )
        }
    }

    fun queueOrUpdateSystemChatEvent(
        student: Student,
        kind: ReportEventKind?,
        note: String = "",
        cardLabel: String = ""
    ) {
        if (kind != null) {
            pendingChatEntries[student.id] = PendingSystemChatEntry(
                studentId = student.id,
                studentName = student.name,
                studentNumber = student.studentNumber,
                kind = kind,
                note = note.trim(),
                cardLabel = cardLabel.trim()
            )
            if (batchFlushJob == null || batchFlushJob?.isActive != true) {
                batchFlushJob = uiScope.launch {
                    // 2-minute session batch window
                    delay(120_000L)
                    flushPendingSystemChatNow()
                }
            }
        } else {
            pendingChatEntries.remove(student.id)
            if (pendingChatEntries.isEmpty()) {
                batchFlushJob?.cancel()
                batchFlushJob = null
            }
        }
    }

    DisposableEffect(groupId, activeWeekKey) {
        onDispose {
            flushPendingSystemChatNow()
        }
    }

    // 4. Navigation & Queue State
    var currentIndex by rememberSaveable(groupId, activeWeekKey, selectedCardTypeId) { mutableIntStateOf(0) }
    var showSummaryView by rememberSaveable(groupId, activeWeekKey, selectedCardTypeId) { mutableStateOf(false) }
    var isEditingSpecificPreparedStudent by rememberSaveable(groupId, activeWeekKey, selectedCardTypeId) { mutableStateOf(false) }
    var hasInitializedIndex by remember(groupId, activeWeekKey, selectedCardTypeId) { mutableStateOf(false) }
    var permissionErrorMessage by remember { mutableStateOf<String?>(null) }
    var studentPendingIssueNote by remember { mutableStateOf<Student?>(null) }
    val undoStack = remember { mutableStateListOf<AttendanceActionHistoryEntry>() }

    // Jump to the first unprepared student initially (or show summary if all are already prepared)
    LaunchedEffect(orderedStudents.size, activeRecordsByStudentId.size, selectedCardTypeId) {
        if (!hasInitializedIndex && orderedStudents.isNotEmpty()) {
            val firstUnprepared = orderedStudents.indexOfFirst { stu ->
                !activeRecordsByStudentId.containsKey(stu.id)
            }
            if (firstUnprepared >= 0) {
                currentIndex = firstUnprepared
                hasInitializedIndex = true
            } else if (activeRecordsByStudentId.size >= orderedStudents.size) {
                showSummaryView = true
                hasInitializedIndex = true
            }
        }
    }

    fun advanceToNextUnpreparedFrom(startIndex: Int, updatedStudentId: String? = null) {
        if (orderedStudents.isEmpty()) {
            showSummaryView = true
            return
        }
        isEditingSpecificPreparedStudent = false
        for (offset in 1..orderedStudents.size) {
            val candidateIdx = (startIndex + offset) % orderedStudents.size
            val candidateStu = orderedStudents[candidateIdx]
            if (candidateStu.id != updatedStudentId && !activeRecordsByStudentId.containsKey(candidateStu.id)) {
                currentIndex = candidateIdx
                return
            }
        }
        // All students have been prepared! Flush any pending system message and show summary
        flushPendingSystemChatNow()
        showSummaryView = true
    }

    // 5. Preload the closest 3 upcoming students' large photos in the background (Dispatchers.IO)
    LaunchedEffect(currentIndex, orderedStudents, activeRecordsByStudentId.size, showSummaryView) {
        if (showSummaryView || orderedStudents.isEmpty()) return@LaunchedEffect
        val upcoming = ArrayList<Student>(4)
        val safeStart = currentIndex.coerceIn(0, orderedStudents.lastIndex)
        upcoming.add(orderedStudents[safeStart])
        var step = 1
        while (upcoming.size < 4 && step < orderedStudents.size) {
            val idx = (safeStart + step) % orderedStudents.size
            val candidate = orderedStudents[idx]
            if (!activeRecordsByStudentId.containsKey(candidate.id) || upcoming.size < 4) {
                upcoming.add(candidate)
            }
            step++
        }
        // Preload current + next 3 photos on Dispatchers.IO
        StudentAvatarImageHelper.preloadNextAttendancePhotosAsync(
            context = context,
            upcomingStudents = upcoming,
            count = 4
        )
    }

    fun canModifyRecord(existing: AttendanceRecord?): Boolean {
        if (existing == null) return true
        return state.isAdmin || existing.servantId.isBlank() || existing.servantId == currentUserId
    }

    fun recordStudentAttendance(
        student: Student,
        status: AttendanceStatus,
        note: String = ""
    ) {
        val card = selectedCardType
        val existing = activeRecordsByStudentId[student.id]
        if (!canModifyRecord(existing)) {
            permissionErrorMessage =
                "هذا الطفل تم تسجيله بواسطة «${existing?.servantName.orEmpty()}» — الأمين فقط يمكنه تعديل أو حذف تسجيل خادم آخر."
            return
        }
        permissionErrorMessage = null
        val cleanNote = note.trim().take(AttendanceRecord.MAX_NOTE_LENGTH)

        if (card == null) {
            // Normal Attendance Mode
            undoStack.add(
                AttendanceActionHistoryEntry(
                    modeCardType = null,
                    student = student,
                    previousRecord = existing,
                    appliedStatus = status,
                    appliedNote = cleanNote
                )
            )

            val optimisticRec = AttendanceRecord(
                id = AttendanceRecord.buildDocId(activeWeekKey, student.id),
                groupId = groupId,
                studentId = student.id,
                studentName = student.name,
                status = status.id,
                weekKey = activeWeekKey,
                servantId = currentUserId,
                servantName = servantDisplayName,
                note = cleanNote,
                memberUids = memberUids,
                createdAt = existing?.createdAt,
                updatedAt = null
            )
            optimisticOverrides[student.id] = optimisticRec
            recordsByStudentId[student.id] = optimisticRec
            LocalClassroomCache.saveWeeklyAttendance(groupId, activeWeekKey, recordsByStudentId.values.toList())

            val reportKind = when (status) {
                AttendanceStatus.PRESENT -> ReportEventKind.PRESENT
                AttendanceStatus.ABSENT -> ReportEventKind.ABSENT
                AttendanceStatus.ISSUE -> ReportEventKind.ISSUE
            }
            queueOrUpdateSystemChatEvent(student, reportKind, cleanNote)

            attendanceIoScope.launch {
                kamRepository.upsertAttendanceRecord(
                    groupId = groupId,
                    student = student,
                    weekKey = activeWeekKey,
                    status = status,
                    servantName = servantDisplayName,
                    note = cleanNote,
                    memberUids = memberUids,
                    existingRecord = existing,
                    waitForServerAck = false
                )
            }
        } else {
            // Card Swipe Mode (any enabled CardType including Custom Elder Card) — syncs immediately with Home Screen
            val sessionKey = "${card.id}_${student.id}"
            val cardDisplayTitle = card.displayTitle(customCardConfig)
            val previousSessionRec = cardSessionRecordsByKey[sessionKey]
            val matchingHomeEvents = state.cardEvents.filter {
                it.studentId == student.id && it.cardType == card.id && it.weekKey == activeWeekKey
            }
            var didAwardOnHome = false
            var removedHomeEvent: CardEvent? = null

            when (status) {
                AttendanceStatus.PRESENT -> {
                    if (matchingHomeEvents.isEmpty()) {
                        onAwardCard(student, card)
                        didAwardOnHome = true
                    }
                    queueOrUpdateSystemChatEvent(
                        student = student,
                        kind = ReportEventKind.PRESENT,
                        cardLabel = cardDisplayTitle
                    )
                }
                AttendanceStatus.ABSENT -> {
                    if (matchingHomeEvents.isNotEmpty()) {
                        removedHomeEvent = matchingHomeEvents.first()
                        matchingHomeEvents.forEach { ev -> onUndoCardEvent(ev) }
                    }
                    // Child did NOT receive this card -> send same prominent photo + name alert in chat!
                    queueOrUpdateSystemChatEvent(
                        student = student,
                        kind = ReportEventKind.NO_CARD,
                        note = "لم يأخذ كارت $cardDisplayTitle",
                        cardLabel = cardDisplayTitle
                    )
                }
                AttendanceStatus.ISSUE -> {
                    val issueContextNote = if (cleanNote.isNotBlank()) {
                        "$cardDisplayTitle: $cleanNote"
                    } else {
                        "مشكلة أثناء $cardDisplayTitle"
                    }
                    queueOrUpdateSystemChatEvent(
                        student = student,
                        kind = ReportEventKind.ISSUE,
                        note = issueContextNote,
                        cardLabel = cardDisplayTitle
                    )
                }
            }

            undoStack.add(
                AttendanceActionHistoryEntry(
                    modeCardType = card,
                    student = student,
                    previousRecord = previousSessionRec,
                    appliedStatus = status,
                    appliedNote = cleanNote,
                    didAwardCardOnHome = didAwardOnHome,
                    removedCardEventFromHome = removedHomeEvent
                )
            )

            cardSessionRecordsByKey[sessionKey] = AttendanceRecord(
                id = "${activeWeekKey}_${card.id}_${student.id}",
                groupId = groupId,
                studentId = student.id,
                studentName = student.name,
                status = status.id,
                weekKey = activeWeekKey,
                servantId = currentUserId,
                servantName = servantDisplayName,
                note = cleanNote,
                memberUids = memberUids
            )
        }

        advanceToNextUnpreparedFrom(currentIndex, updatedStudentId = student.id)
    }

    fun undoLastGesture() {
        if (undoStack.isEmpty()) return
        val lastEntry = undoStack.removeAt(undoStack.lastIndex)
        val stu = lastEntry.student
        val prev = lastEntry.previousRecord
        val entryCard = lastEntry.modeCardType
        permissionErrorMessage = null

        if (entryCard == null) {
            if (prev == null) {
                optimisticOverrides[stu.id] = null
                recordsByStudentId.remove(stu.id)
                queueOrUpdateSystemChatEvent(stu, null, "")
                LocalClassroomCache.saveWeeklyAttendance(groupId, activeWeekKey, recordsByStudentId.values.toList())
                val docId = AttendanceRecord.buildDocId(activeWeekKey, stu.id)
                attendanceIoScope.launch {
                    kamRepository.deleteAttendanceRecord(
                        groupId = groupId,
                        docId = docId,
                        waitForServerAck = false
                    )
                }
            } else {
                optimisticOverrides[stu.id] = prev
                recordsByStudentId[stu.id] = prev
                val prevKind = when (prev.attendanceStatus) {
                    AttendanceStatus.PRESENT -> ReportEventKind.PRESENT
                    AttendanceStatus.ABSENT -> ReportEventKind.ABSENT
                    AttendanceStatus.ISSUE -> ReportEventKind.ISSUE
                }
                queueOrUpdateSystemChatEvent(stu, prevKind, prev.note)
                LocalClassroomCache.saveWeeklyAttendance(groupId, activeWeekKey, recordsByStudentId.values.toList())
                attendanceIoScope.launch {
                    kamRepository.upsertAttendanceRecord(
                        groupId = groupId,
                        student = stu,
                        weekKey = activeWeekKey,
                        status = prev.attendanceStatus,
                        servantName = prev.servantName,
                        note = prev.note,
                        memberUids = memberUids,
                        existingRecord = prev,
                        waitForServerAck = false
                    )
                }
            }
        } else {
            // Switch back to that card mode if needed
            if (selectedCardTypeId != entryCard.id) {
                selectedCardTypeId = entryCard.id
            }
            val sessionKey = "${entryCard.id}_${stu.id}"
            if (prev == null) {
                cardSessionRecordsByKey.remove(sessionKey)
            } else {
                cardSessionRecordsByKey[sessionKey] = prev
            }
            queueOrUpdateSystemChatEvent(stu, null)
            if (lastEntry.didAwardCardOnHome) {
                val currentEvent = state.cardEvents.firstOrNull {
                    it.studentId == stu.id && it.cardType == entryCard.id && it.weekKey == activeWeekKey
                }
                if (currentEvent != null) {
                    onUndoCardEvent(currentEvent)
                }
            } else if (lastEntry.removedCardEventFromHome != null) {
                val alreadyHas = state.cardEvents.any {
                    it.studentId == stu.id && it.cardType == entryCard.id && it.weekKey == activeWeekKey
                }
                if (!alreadyHas) {
                    onAwardCard(stu, entryCard)
                }
            }
        }

        val targetIdx = orderedStudents.indexOfFirst { it.id == stu.id }
        if (targetIdx >= 0) {
            currentIndex = targetIdx
            showSummaryView = false
            isEditingSpecificPreparedStudent = prev != null
        }
    }

    fun adminDeleteRecord(student: Student) {
        val existing = activeRecordsByStudentId[student.id] ?: return
        if (!state.isAdmin && existing.servantId.isNotBlank() && existing.servantId != currentUserId) {
            permissionErrorMessage = "الأمين فقط يمكنه حذف تسجيل خادم آخر."
            return
        }
        val card = selectedCardType
        if (card == null) {
            optimisticOverrides[student.id] = null
            recordsByStudentId.remove(student.id)
            queueOrUpdateSystemChatEvent(student, null, "")
            LocalClassroomCache.saveWeeklyAttendance(groupId, activeWeekKey, recordsByStudentId.values.toList())
            attendanceIoScope.launch {
                kamRepository.deleteAttendanceRecord(
                    groupId = groupId,
                    docId = existing.effectiveId,
                    waitForServerAck = false
                )
            }
        } else {
            val sessionKey = "${card.id}_${student.id}"
            cardSessionRecordsByKey.remove(sessionKey)
            val matchingHomeEvents = state.cardEvents.filter {
                it.studentId == student.id && it.cardType == card.id && it.weekKey == activeWeekKey
            }
            matchingHomeEvents.forEach { ev -> onUndoCardEvent(ev) }
        }
    }

    BackHandler(enabled = com.example.ui.theme.LocalIsTabActive.current) {
        flushPendingSystemChatNow()
        onClose()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .testTag("smart_attendance_screen")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar with Progress ("12 / 58") & Summary Toggle + Mode Selector
                val totalCount = orderedStudents.size
                val preparedCount = orderedStudents.count { activeRecordsByStudentId.containsKey(it.id) }
                val displayedStepNumber = if (totalCount == 0) {
                    0
                } else if (showSummaryView) {
                    totalCount
                } else {
                    (currentIndex.coerceIn(0, totalCount - 1) + 1)
                }

                SmartAttendanceTopHeader(
                    weekKey = activeWeekKey,
                    selectedCardType = selectedCardType,
                    availableCardTypes = availableCardTypes,
                    customCardConfig = customCardConfig,
                    onSelectCardType = { newCardType ->
                        flushPendingSystemChatNow()
                        selectedCardTypeId = newCardType?.id.orEmpty()
                        permissionErrorMessage = null
                        isEditingSpecificPreparedStudent = false
                        showSummaryView = false
                        hasInitializedIndex = false
                    },
                    currentPosition = displayedStepNumber,
                    preparedCount = preparedCount,
                    totalCount = totalCount,
                    showSummaryView = showSummaryView,
                    canUndo = undoStack.isNotEmpty(),
                    onUndo = ::undoLastGesture,
                    onToggleSummary = {
                        if (!showSummaryView) {
                            flushPendingSystemChatNow()
                        }
                        showSummaryView = !showSummaryView
                    },
                    onClose = {
                        flushPendingSystemChatNow()
                        onClose()
                    }
                )

                AnimatedVisibility(visible = permissionErrorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = permissionErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { permissionErrorMessage = null }) {
                                Text("إغلاق", color = MaterialTheme.colorScheme.onErrorContainer)
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
                        Text(
                            text = "لا يوجد أطفال مسجلون في الفصل حالياً.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (showSummaryView) {
                    SmartAttendanceSummaryView(
                        selectedCardType = selectedCardType,
                        customCardConfig = customCardConfig,
                        orderedStudents = orderedStudents,
                        recordsByStudentId = activeRecordsByStudentId,
                        blockById = blockById,
                        isAdmin = state.isAdmin,
                        currentUserId = currentUserId,
                        onSelectStudentToEdit = { index ->
                            currentIndex = index
                            isEditingSpecificPreparedStudent = true
                            showSummaryView = false
                        },
                        onAdminDeleteRecord = ::adminDeleteRecord,
                        onResumeUnprepared = {
                            val nextIdx = orderedStudents.indexOfFirst { !activeRecordsByStudentId.containsKey(it.id) }
                            if (nextIdx >= 0) {
                                currentIndex = nextIdx
                                isEditingSpecificPreparedStudent = false
                                showSummaryView = false
                            }
                        },
                        onFinishAndClose = {
                            flushPendingSystemChatNow()
                            onClose()
                        }
                    )
                } else {
                    val safeIndex = currentIndex.coerceIn(0, orderedStudents.lastIndex)
                    val currentStudent = orderedStudents[safeIndex]
                    val currentBlock = blockById[currentStudent.blockId]
                    val existingRecord = activeRecordsByStudentId[currentStudent.id]

                    // Automatically skip already-prepared student unless the user explicitly navigated to edit them
                    LaunchedEffect(currentStudent.id, existingRecord != null, isEditingSpecificPreparedStudent, selectedCardTypeId) {
                        if (existingRecord != null && !isEditingSpecificPreparedStudent) {
                            val hasAnyUnprepared = orderedStudents.any { !activeRecordsByStudentId.containsKey(it.id) }
                            if (hasAnyUnprepared) {
                                advanceToNextUnpreparedFrom(safeIndex)
                            } else {
                                showSummaryView = true
                            }
                        }
                    }

                    SmartAttendanceSwipeCardContent(
                        selectedCardType = selectedCardType,
                        customCardConfig = customCardConfig,
                        student = currentStudent,
                        block = currentBlock,
                        existingRecord = existingRecord,
                        currentIndex = safeIndex,
                        totalStudents = orderedStudents.size,
                        isAdmin = state.isAdmin,
                        currentUserId = currentUserId,
                        canUndo = undoStack.isNotEmpty(),
                        onSwipePresent = {
                            recordStudentAttendance(currentStudent, AttendanceStatus.PRESENT)
                        },
                        onSwipeAbsent = {
                            recordStudentAttendance(currentStudent, AttendanceStatus.ABSENT)
                        },
                        onSwipeIssue = {
                            if (!canModifyRecord(existingRecord)) {
                                permissionErrorMessage =
                                    "هذا الطفل تم تسجيله بواسطة «${existingRecord?.servantName.orEmpty()}» — الأمين فقط يمكنه تعديل أو حذف تسجيل خادم آخر."
                            } else {
                                studentPendingIssueNote = currentStudent
                            }
                        },
                        onUndo = ::undoLastGesture,
                        onNavigatePrev = {
                            if (orderedStudents.isNotEmpty()) {
                                isEditingSpecificPreparedStudent = true
                                currentIndex = if (safeIndex - 1 < 0) orderedStudents.lastIndex else safeIndex - 1
                            }
                        },
                        onNavigateNext = {
                            if (orderedStudents.isNotEmpty()) {
                                isEditingSpecificPreparedStudent = true
                                currentIndex = (safeIndex + 1) % orderedStudents.size
                            }
                        },
                        onAdminDeleteRecord = {
                            adminDeleteRecord(currentStudent)
                        }
                    )
                }
            }
        }

        // Optional Short Note Dialog when swiping down ("عمل مشكلة")
        val issueTarget = studentPendingIssueNote
        if (issueTarget != null) {
            val existingNote = activeRecordsByStudentId[issueTarget.id]?.note.orEmpty()
            IssueNotePromptDialog(
                studentName = issueTarget.name,
                initialNote = existingNote,
                onConfirm = { note ->
                    studentPendingIssueNote = null
                    recordStudentAttendance(issueTarget, AttendanceStatus.ISSUE, note)
                },
                onDismiss = { studentPendingIssueNote = null }
            )
        }
    }
}

@Composable
private fun SmartAttendanceTopHeader(
    weekKey: String,
    selectedCardType: CardType?,
    availableCardTypes: List<CardType>,
    customCardConfig: CustomCardConfig,
    onSelectCardType: (CardType?) -> Unit,
    currentPosition: Int,
    preparedCount: Int,
    totalCount: Int,
    showSummaryView: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onToggleSummary: () -> Unit,
    onClose: () -> Unit
) {
    var showModeDropdown by remember { mutableStateOf(false) }
    val progressFraction = if (totalCount > 0) {
        (preparedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val extColors = LocalKamExtendedColors.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
                        modifier = Modifier.testTag("close_smart_attendance_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع للرئيسية"
                        )
                    }
                    Column {
                        Text(
                            text = if (selectedCardType == null) {
                                "السحب الذكي — الحضور"
                            } else {
                                "سحب ${selectedCardType.displaySymbol(customCardConfig)} ${selectedCardType.displayTitle(customCardConfig)} (+${selectedCardType.displayPoints(customCardConfig)})"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedCardType == null) {
                                "الأسبوع: $weekKey (حضور بدون نقاط)"
                            } else {
                                "الأسبوع: $weekKey (يسمع تلقائياً في الرئيسية)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
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
                            modifier = Modifier.testTag("top_undo_attendance_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "تراجع عن آخر إيماءة",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onToggleSummary,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("toggle_attendance_summary_button")
                    ) {
                        Text(
                            text = if (showSummaryView) "بطاقات السحب" else "الملخص ($preparedCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Prominent Full-Width Mode & Card Selector Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "اختر نوع السحب (الحضور أو أي كارت):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Box {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clickable { showModeDropdown = true }
                                    .testTag("switch_swipe_mode_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (selectedCardType == null) {
                                            "📋 الحضور العادي ▾"
                                        } else {
                                            "${selectedCardType.displaySymbol(customCardConfig)} ${selectedCardType.displayTitle(customCardConfig)} (+${selectedCardType.displayPoints(customCardConfig)}) ▾"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showModeDropdown,
                                onDismissRequest = { showModeDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "📋 الحضور العادي (بدون نقاط)",
                                            fontWeight = if (selectedCardType == null) FontWeight.ExtraBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showModeDropdown = false
                                        onSelectCardType(null)
                                    }
                                )
                                HorizontalDivider()
                                availableCardTypes.forEach { card ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${card.displaySymbol(customCardConfig)} ${card.displayTitle(customCardConfig)} (+${card.displayPoints(customCardConfig)})",
                                                fontWeight = if (selectedCardType == card) FontWeight.ExtraBold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            showModeDropdown = false
                                            onSelectCardType(card)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Horizontal Scrollable Mode Selector Bar: Normal Attendance + Enabled Card Types
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("swipe_mode_selector_row"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item(key = "mode_attendance") {
                            FilterChip(
                                selected = selectedCardType == null,
                                onClick = { onSelectCardType(null) },
                                label = {
                                    Text(
                                        text = "📋 الحضور العادي",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("mode_chip_attendance")
                            )
                        }
                        items(
                            items = availableCardTypes,
                            key = { "mode_${it.id}" }
                        ) { card ->
                            val isSelected = selectedCardType == card
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectCardType(card) },
                                label = {
                                    Text(
                                        text = "${card.displaySymbol(customCardConfig)} ${card.displayTitle(customCardConfig)} (+${card.displayPoints(customCardConfig)})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("mode_chip_${card.id}")
                            )
                        }
                    }
                }
            }

            // Progress Bar ("12 / 58")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الطفل الحالي: $currentPosition / $totalCount",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("attendance_progress_text")
                )
                Text(
                    text = "تم تسجيل: $preparedCount / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("attendance_progress_bar")
            )
        }
        HorizontalDivider(thickness = 1.dp, color = extColors.hairline)
    }
}

@Composable
private fun SmartAttendanceSwipeCardContent(
    selectedCardType: CardType?,
    customCardConfig: CustomCardConfig,
    student: Student,
    block: ServantBlock?,
    existingRecord: AttendanceRecord?,
    currentIndex: Int,
    totalStudents: Int,
    isAdmin: Boolean,
    currentUserId: String,
    canUndo: Boolean,
    onSwipePresent: () -> Unit,
    onSwipeAbsent: () -> Unit,
    onSwipeIssue: () -> Unit,
    onUndo: () -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onAdminDeleteRecord: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val blockAccent = remember(block?.colorHex) {
        if (block != null) {
            KamSemanticColors.blockColor(block.colorHex)
        } else {
            KamSemanticColors.unassignedBlock
        }
    }
    val blockTitle = block?.servantName?.ifBlank { "بلوك" } ?: "بدون بلوك"

    // Load large photo asynchronously on Dispatchers.IO (or instant 0ms hit from 3-photo preloader)
    var largePhoto by remember(student.id, student.effectiveAvatarKey) {
        mutableStateOf(StudentAvatarImageHelper.getCachedLargeAttendancePhoto(student))
    }
    LaunchedEffect(student.id, student.effectiveAvatarKey) {
        if (largePhoto == null && (student.effectiveHasAvatar || student.avatarBase64.isNotBlank())) {
            largePhoto = StudentAvatarImageHelper.loadLargeAttendancePhotoBitmap(context, student)
        }
    }

    val offsetX = remember(student.id, selectedCardType?.id) { Animatable(0f) }
    val offsetY = remember(student.id, selectedCardType?.id) { Animatable(0f) }

    val dragDx = offsetX.value
    val dragDy = offsetY.value
    val horizontalThreshold = 150f
    val verticalDownThreshold = 150f

    // Visual gesture hint during drag
    val activeGesturePreview: AttendanceStatus? = when {
        abs(dragDx) >= abs(dragDy) && dragDx > 60f -> AttendanceStatus.PRESENT
        abs(dragDx) >= abs(dragDy) && dragDx < -60f -> AttendanceStatus.ABSENT
        dragDy > 60f -> AttendanceStatus.ISSUE
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val extColors = LocalKamExtendedColors.current
        // Direction Legend Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GestureLegendPill(
                label = if (selectedCardType == null) {
                    "يمين ← حاضر"
                } else {
                    "يمين ← حضر وأخذ الكارت (+${selectedCardType.displayPoints(customCardConfig)})"
                },
                color = extColors.onlineGreen
            )
            GestureLegendPill(
                label = "أسفل ↓ مشكلة",
                color = extColors.offlineAmber
            )
            GestureLegendPill(
                label = if (selectedCardType == null) {
                    "يسار → غايب"
                } else {
                    "يسار → ما حضرش"
                },
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Draggable Student Card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (activeGesturePreview != null) 2.dp else 1.5.dp,
                    color = when (activeGesturePreview) {
                        AttendanceStatus.PRESENT -> extColors.onlineGreen
                        AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
                        AttendanceStatus.ISSUE -> extColors.offlineAmber
                        null -> blockAccent.copy(alpha = 0.65f)
                    }
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Note: in RTL layout, physical swipe right has positive x in pointerInput
                        translationX = offsetX.value
                        translationY = offsetY.value.coerceAtLeast(-40f)
                        rotationZ = (offsetX.value / 28f).coerceIn(-12f, 12f)
                    }
                    .pointerInput(student.id, existingRecord?.id, selectedCardType?.id) {
                        detectDragGestures(
                            onDragEnd = {
                                val endX = offsetX.value
                                val endY = offsetY.value
                                when {
                                    abs(endX) >= abs(endY) && endX > horizontalThreshold -> {
                                        scope.launch {
                                            offsetX.snapTo(0f)
                                            offsetY.snapTo(0f)
                                            onSwipePresent()
                                        }
                                    }
                                    abs(endX) >= abs(endY) && endX < -horizontalThreshold -> {
                                        scope.launch {
                                            offsetX.snapTo(0f)
                                            offsetY.snapTo(0f)
                                            onSwipeAbsent()
                                        }
                                    }
                                    endY > verticalDownThreshold -> {
                                        scope.launch {
                                            offsetX.animateTo(0f, tween(150))
                                            offsetY.animateTo(0f, tween(150))
                                            onSwipeIssue()
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
                    .testTag("smart_attendance_student_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Metadata Row inside Card: Block Color Badge + Student Number + Existing Status Badge
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

                    // Existing status banner if already prepared this week
                    if (existingRecord != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExistingAttendanceBadgeStrip(
                            selectedCardType = selectedCardType,
                            customCardConfig = customCardConfig,
                            record = existingRecord,
                            canDelete = isAdmin || existingRecord.servantId.isBlank() || existingRecord.servantId == currentUserId,
                            onDelete = onAdminDeleteRecord
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Large Photo or Large Initials Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(blockAccent.copy(alpha = 0.14f))
                            .border(
                                width = 2.dp,
                                color = blockAccent.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val photoBitmap: ImageBitmap? = largePhoto
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap,
                                contentDescription = student.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("smart_attendance_large_photo")
                            )
                        } else {
                            // Large Initials Fallback Card
                            val initials = student.avatarInitials.ifBlank {
                                ArabicInitialsHelper.extractInitials(student.name)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                                    .testTag("smart_attendance_initials_card")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = blockAccent.copy(alpha = 0.25f),
                                    border = BorderStroke(3.dp, blockAccent),
                                    modifier = Modifier.size(132.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = initials,
                                            style = MaterialTheme.typography.displayMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 44.sp
                                            ),
                                            color = blockAccent
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "بدون صورة مضافة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Live Swipe Status Overlay Badge
                        if (activeGesturePreview != null) {
                            val (overlayText, overlayColor) = when (activeGesturePreview) {
                                AttendanceStatus.PRESENT -> if (selectedCardType == null) {
                                    "✓ حاضر في الكنيسة" to extColors.onlineGreen
                                } else {
                                    "✓ حضر وأخذ ${selectedCardType.displaySymbol(customCardConfig)} ${selectedCardType.displayTitle(customCardConfig)} (+${selectedCardType.displayPoints(customCardConfig)})" to extColors.onlineGreen
                                }
                                AttendanceStatus.ABSENT -> if (selectedCardType == null) {
                                    "✕ غايب" to MaterialTheme.colorScheme.error
                                } else {
                                    "✕ ما حضرش / بدون كارت" to MaterialTheme.colorScheme.error
                                }
                                AttendanceStatus.ISSUE -> "⚠ عمل مشكلة" to extColors.offlineAmber
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Student Name + Navigation between students (including already prepared ones for editing)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onNavigatePrev,
                            modifier = Modifier.testTag("attendance_prev_student_button")
                        ) {
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
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("smart_attendance_student_name")
                            )
                            Text(
                                text = "${currentIndex + 1} / $totalStudents",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = onNavigateNext,
                            modifier = Modifier.testTag("attendance_next_student_button")
                        ) {
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

        // Quick Action Buttons + Undo Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Present (Right)
            Button(
                onClick = onSwipePresent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.onlineGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_mark_present")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCardType == null) "حاضر" else "أخذ الكارت",
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }

            // Issue (Down)
            Button(
                onClick = onSwipeIssue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.offlineAmber,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_mark_issue")
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("مشكلة", fontWeight = FontWeight.ExtraBold)
            }

            // Absent (Left)
            Button(
                onClick = onSwipeAbsent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_mark_absent")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (selectedCardType == null) "غايب" else "ما حضرش",
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }

            // Undo Last Gesture
            OutlinedButton(
                onClick = onUndo,
                enabled = canUndo,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier.testTag("btn_undo_last_attendance")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "تراجع",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("تراجع", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GestureLegendPill(
    label: String,
    color: Color
) {
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
private fun ExistingAttendanceBadgeStrip(
    selectedCardType: CardType?,
    customCardConfig: CustomCardConfig,
    record: AttendanceRecord,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val status = record.attendanceStatus
    val badgeColor = when (status) {
        AttendanceStatus.PRESENT -> extColors.onlineGreen
        AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error
        AttendanceStatus.ISSUE -> extColors.offlineAmber
    }
    val statusText = if (selectedCardType == null) {
        status.arabicLabel
    } else {
        when (status) {
            AttendanceStatus.PRESENT -> "أخذ ${selectedCardType.displaySymbol(customCardConfig)} ${selectedCardType.displayTitle(customCardConfig)} (+${selectedCardType.displayPoints(customCardConfig)})"
            AttendanceStatus.ABSENT -> "ما حضرش / لم يأخذ الكارت"
            AttendanceStatus.ISSUE -> "مشكلة"
        }
    }
    Surface(
        color = badgeColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, badgeColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مسجّل هذا الأسبوع: $statusText (بواسطة ${record.servantName})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
                if (record.note.isNotBlank()) {
                    Text(
                        text = "ملاحظة: ${record.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("admin_delete_attendance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف التسجيل",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueNotePromptDialog(
    studentName: String,
    initialNote: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    var noteText by rememberSaveable { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = extColors.offlineAmber
                )
                Text(
                    text = "تسجيل مشكلة — $studentName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "يمكنك كتابة ملاحظة قصيرة اختيارية (تظهر في الملخص ورسالة شات الفصل):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it.take(AttendanceRecord.MAX_NOTE_LENGTH) },
                    placeholder = { Text("مثال: كلام أثناء الدرس / تأخير...") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("issue_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(noteText.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = extColors.offlineAmber,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_issue_note_button")
            ) {
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onConfirm("") },
                    modifier = Modifier.testTag("skip_issue_note_button")
                ) {
                    Text("بدون ملاحظة")
                }
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        }
    )
}

@Composable
private fun SmartAttendanceSummaryView(
    selectedCardType: CardType?,
    customCardConfig: CustomCardConfig,
    orderedStudents: List<Student>,
    recordsByStudentId: Map<String, AttendanceRecord>,
    blockById: Map<String, ServantBlock>,
    isAdmin: Boolean,
    currentUserId: String,
    onSelectStudentToEdit: (Int) -> Unit,
    onAdminDeleteRecord: (Student) -> Unit,
    onResumeUnprepared: () -> Unit,
    onFinishAndClose: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    val presentStudents = remember(orderedStudents, recordsByStudentId) {
        orderedStudents.filter { recordsByStudentId[it.id]?.attendanceStatus == AttendanceStatus.PRESENT }
    }
    val absentStudents = remember(orderedStudents, recordsByStudentId) {
        orderedStudents.filter { recordsByStudentId[it.id]?.attendanceStatus == AttendanceStatus.ABSENT }
    }
    val issueStudents = remember(orderedStudents, recordsByStudentId) {
        orderedStudents.filter { recordsByStudentId[it.id]?.attendanceStatus == AttendanceStatus.ISSUE }
    }
    val unpreparedCount = orderedStudents.size - (presentStudents.size + absentStudents.size + issueStudents.size)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("smart_attendance_summary_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Counts Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryStatCard(
                title = if (selectedCardType == null) "الحاضرين" else "أخذوا الكارت",
                count = presentStudents.size,
                color = extColors.onlineGreen,
                modifier = Modifier.weight(1f)
            )
            SummaryStatCard(
                title = if (selectedCardType == null) "الغايبين" else "ما حضروش",
                count = absentStudents.size,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            SummaryStatCard(
                title = "المشاكل",
                count = issueStudents.size,
                color = extColors.offlineAmber,
                modifier = Modifier.weight(1f)
            )
        }

        if (unpreparedCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "يتبقى $unpreparedCount طفل لم يتم تسجيلهم بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = onResumeUnprepared,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("استكمال السحب", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // 1. Names of Absent Students (أسماء الغايبين)
            item(key = "header_absent") {
                Text(
                    text = if (selectedCardType == null) {
                        "أسماء الغايبين (${absentStudents.size})"
                    } else {
                        "أسماء من لم يأخذوا الكارت (${absentStudents.size})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (absentStudents.isEmpty()) {
                item(key = "empty_absent") {
                    Text(
                        text = "لا يوجد غياب مسجل في هذا الأسبوع.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(
                    items = absentStudents,
                    key = { "absent_${it.id}" }
                ) { stu ->
                    val rec = recordsByStudentId[stu.id]
                    val idx = orderedStudents.indexOfFirst { it.id == stu.id }
                    SummaryStudentRow(
                        student = stu,
                        block = blockById[stu.blockId],
                        record = rec,
                        badgeColor = MaterialTheme.colorScheme.error,
                        canEdit = isAdmin || rec?.servantId == currentUserId,
                        canDelete = isAdmin || rec?.servantId == currentUserId,
                        onEdit = { if (idx >= 0) onSelectStudentToEdit(idx) },
                        onDelete = { onAdminDeleteRecord(stu) }
                    )
                }
            }

            // 2. Students with Issues (المشاكل)
            if (issueStudents.isNotEmpty()) {
                item(key = "header_issues") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "عملوا مشكلة (${issueStudents.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = extColors.offlineAmber
                    )
                }
                items(
                    items = issueStudents,
                    key = { "issue_${it.id}" }
                ) { stu ->
                    val rec = recordsByStudentId[stu.id]
                    val idx = orderedStudents.indexOfFirst { it.id == stu.id }
                    SummaryStudentRow(
                        student = stu,
                        block = blockById[stu.blockId],
                        record = rec,
                        badgeColor = extColors.offlineAmber,
                        canEdit = isAdmin || rec?.servantId == currentUserId,
                        canDelete = isAdmin || rec?.servantId == currentUserId,
                        onEdit = { if (idx >= 0) onSelectStudentToEdit(idx) },
                        onDelete = { onAdminDeleteRecord(stu) }
                    )
                }
            }

            // 3. Present Students (الحاضرين / أخذوا الكارت)
            if (presentStudents.isNotEmpty()) {
                item(key = "header_present") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (selectedCardType == null) {
                            "الحاضرين (${presentStudents.size})"
                        } else {
                            "أخذوا ${selectedCardType.displayTitle(customCardConfig)} (${presentStudents.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = extColors.onlineGreen
                    )
                }
                items(
                    items = presentStudents,
                    key = { "present_${it.id}" }
                ) { stu ->
                    val rec = recordsByStudentId[stu.id]
                    val idx = orderedStudents.indexOfFirst { it.id == stu.id }
                    SummaryStudentRow(
                        student = stu,
                        block = blockById[stu.blockId],
                        record = rec,
                        badgeColor = extColors.onlineGreen,
                        canEdit = isAdmin || rec?.servantId.isNullOrBlank() || rec?.servantId == currentUserId,
                        canDelete = isAdmin || rec?.servantId.isNullOrBlank() || rec?.servantId == currentUserId,
                        onEdit = { if (idx >= 0) onSelectStudentToEdit(idx) },
                        onDelete = { onAdminDeleteRecord(stu) }
                    )
                }
            }
        }

        Button(
            onClick = onFinishAndClose,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("finish_attendance_session_button")
        ) {
            Text(
                text = "إنهاء الجلسة والعودة للرئيسية",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun SummaryStatCard(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SummaryStudentRow(
    student: Student,
    block: ServantBlock?,
    record: AttendanceRecord?,
    badgeColor: Color,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#${student.studentNumber} ${student.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (block != null) {
                        Text(
                            text = "• ${block.servantName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!record?.note.isNullOrBlank()) {
                    Text(
                        text = "ملاحظة: ${record?.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!record?.servantName.isNullOrBlank()) {
                    Text(
                        text = "بواسطة: ${record?.servantName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canEdit) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "تعديل الحالة",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف التسجيل",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
