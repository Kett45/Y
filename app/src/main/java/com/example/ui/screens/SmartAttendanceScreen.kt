package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.example.ui.viewmodel.KamScreenState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private const val SMART_SWIPE_ONBOARDING_PREFS = "kam_smart_swipe_onboarding_v1"
private const val MODE_KEY_UNSELECTED = ""
private const val MODE_KEY_NORMAL_ATTENDANCE = "NORMAL_ATTENDANCE"

private sealed interface SwipeSessionMode {
    data object Unselected : SwipeSessionMode
    data object NormalAttendance : SwipeSessionMode
    data class CardMode(val cardType: CardType) : SwipeSessionMode
}

private fun formatCardRegistrationLabel(card: CardType, customCardConfig: CustomCardConfig): String {
    val cleanTitle = card.displayTitle(customCardConfig).trim()
    return if (cleanTitle.startsWith("كارت")) cleanTitle else "كارت $cleanTitle"
}

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
        if (safeCardTitle.isNotBlank()) "كارت $safeCardTitle" else "حضور عادي"
    }
    val safeIcon = sanitizeReportField(reportIcon).ifBlank { "📋" }
    val headerTitle = "$safeIcon تقرير $safeTypeLabel (بواسطة $safeServant)"
    val summaryParts = buildList {
        if (cleanPresentNames.isNotEmpty()) {
            add(if (safeCardTitle.isNotBlank()) "أخذوا الكارت: ${cleanPresentNames.size}" else "حاضر: ${cleanPresentNames.size}")
        }
        if (absentList.isNotEmpty()) add("غائب: ${absentList.size}")
        if (noCardList.isNotEmpty()) add("بدون كارت: ${noCardList.size}")
        if (issueList.isNotEmpty()) add("مشاغب: ${issueList.size}")
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
    val bodyReserveForPresent = 900
    val bodyBudget = (maxLen - bodyReserveForPresent).coerceAtLeast(600)

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

    if (cleanPresentIds.isNotEmpty() && sb.length + 10 < maxLen) {
        val idsBuilder = StringBuilder("\nPID|")
        var addedAnyId = false
        for (id in cleanPresentIds) {
            val sep = if (addedAnyId) "," else ""
            if (sb.length + idsBuilder.length + sep.length + id.length <= maxLen - 20) {
                idsBuilder.append(sep).append(id)
                addedAnyId = true
            } else {
                break
            }
        }
        if (addedAnyId) {
            sb.append(idsBuilder)
        }
    }

    if (cleanPresentNames.isNotEmpty() && sb.length + 10 < maxLen) {
        val namesBuilder = StringBuilder("\nPL|")
        var addedAnyName = false
        for (name in cleanPresentNames) {
            val sep = if (addedAnyName) " • " else ""
            if (sb.length + namesBuilder.length + sep.length + name.length <= maxLen) {
                namesBuilder.append(sep).append(name)
                addedAnyName = true
            } else {
                break
            }
        }
        if (addedAnyName) {
            sb.append(namesBuilder)
        }
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

    // Selected mode starts UNSELECTED ("") until the servant explicitly picks from the Star button
    var selectedModeKey by rememberSaveable(groupId, activeWeekKey) { mutableStateOf(MODE_KEY_UNSELECTED) }
    val sessionMode: SwipeSessionMode = remember(selectedModeKey, customCardConfig.enabled) {
        when (selectedModeKey) {
            MODE_KEY_UNSELECTED -> SwipeSessionMode.Unselected
            MODE_KEY_NORMAL_ATTENDANCE -> SwipeSessionMode.NormalAttendance
            else -> {
                val found = CardType.fromId(selectedModeKey)
                if (found == null || (found == CardType.CUSTOM_ELDER && !customCardConfig.enabled)) {
                    SwipeSessionMode.Unselected
                } else {
                    SwipeSessionMode.CardMode(found)
                }
            }
        }
    }
    val isModeSelected = sessionMode != SwipeSessionMode.Unselected
    val selectedCardType: CardType? = (sessionMode as? SwipeSessionMode.CardMode)?.cardType

    // Star button menu, first-time Spotlight coach-mark, and quick "not selected" prompt state
    var starMenuExpanded by remember { mutableStateOf(false) }
    var showSelectCardPrompt by remember { mutableStateOf(false) }
    var starPulseTrigger by remember { mutableIntStateOf(0) }
    var showStarSpotlight by remember { mutableStateOf(false) }
    var starButtonBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    var rootOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(currentUserId) {
        val seen = withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences(
                SMART_SWIPE_ONBOARDING_PREFS,
                Context.MODE_PRIVATE
            )
            val key = "seen_star_spotlight_${currentUserId.ifBlank { "default" }}"
            prefs.getBoolean(key, false)
        }
        if (!seen) {
            showStarSpotlight = true
        }
    }

    fun markStarSpotlightSeenAndOpenMenu() {
        showStarSpotlight = false
        starMenuExpanded = true
        val appCtx = context.applicationContext
        val key = "seen_star_spotlight_${currentUserId.ifBlank { "default" }}"
        attendanceIoScope.launch {
            appCtx.getSharedPreferences(SMART_SWIPE_ONBOARDING_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(key, true)
                .apply()
        }
    }

    LaunchedEffect(showSelectCardPrompt, starPulseTrigger) {
        if (showSelectCardPrompt) {
            delay(4_500L)
            showSelectCardPrompt = false
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

    // Effective records map for the active mode (empty while Unselected; Normal Attendance vs selected CardType)
    val activeRecordsByStudentId: Map<String, AttendanceRecord> = remember(
        sessionMode,
        orderedStudents,
        recordsByStudentId.toMap(),
        cardSessionRecordsByKey.toMap(),
        weeklyCardEventByStudentId
    ) {
        when (sessionMode) {
            SwipeSessionMode.Unselected -> emptyMap()
            SwipeSessionMode.NormalAttendance -> recordsByStudentId.toMap()
            is SwipeSessionMode.CardMode -> {
                val card = sessionMode.cardType
                val result = LinkedHashMap<String, AttendanceRecord>()
                for (stu in orderedStudents) {
                    val sessionKey = "${card.id}_${stu.id}"
                    val sessionRec = cardSessionRecordsByKey[sessionKey]
                    val liveCardEvent = weeklyCardEventByStudentId[stu.id]
                    if (sessionRec != null) {
                        if (sessionRec.attendanceStatus == AttendanceStatus.ABSENT && liveCardEvent != null) {
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

    // 3. Collect ALL session choices and dispatch ONE consolidated V3 SYSTEM group chat message
    // ONLY when the servant completes all children, taps Finish, exits the screen, or switches card type.
    val pendingChatEntries = remember { LinkedHashMap<String, PendingSystemChatEntry>() }
    var pendingChatMode by remember { mutableStateOf<SwipeSessionMode>(SwipeSessionMode.Unselected) }

    fun flushPendingSystemChatNow() {
        if (pendingChatEntries.isEmpty() || groupId.isBlank()) return
        val modeToReport = pendingChatMode.takeIf { it != SwipeSessionMode.Unselected } ?: sessionMode
        if (modeToReport == SwipeSessionMode.Unselected) {
            pendingChatEntries.clear()
            return
        }
        val snapshotEntries = pendingChatEntries.values.toList()
        pendingChatEntries.clear()
        pendingChatMode = SwipeSessionMode.Unselected

        val activeCard = (modeToReport as? SwipeSessionMode.CardMode)?.cardType
        val cardContextTitle = activeCard?.displayTitle(customCardConfig).orEmpty()
        val reportTypeKey = if (activeCard == null) "ATTENDANCE" else "CARD:${activeCard.id}"
        val reportTypeLabel = if (activeCard == null) {
            "حضور عادي"
        } else {
            formatCardRegistrationLabel(activeCard, customCardConfig)
        }
        val reportIcon = activeCard?.displaySymbol(customCardConfig) ?: "📋"
        val reportColorKey = activeCard?.displayColorKey(customCardConfig) ?: "EMERALD"

        // Collect all students marked PRESENT / received card in this session (names only in the single message)
        val presentBatchEntries = snapshotEntries
            .filter { it.kind == ReportEventKind.PRESENT }
            .sortedBy { it.studentNumber }
        val nonPresentBatchIds = snapshotEntries
            .filter { it.kind != ReportEventKind.PRESENT }
            .map { it.studentId }
            .toSet()
        val presentFromActiveRecords = if (modeToReport == sessionMode) {
            orderedStudents.filter { stu ->
                stu.id !in nonPresentBatchIds &&
                    activeRecordsByStudentId[stu.id]?.attendanceStatus == AttendanceStatus.PRESENT
            }
        } else {
            emptyList()
        }
        val presentPairs = LinkedHashMap<String, Pair<String, Long>>()
        for (item in presentBatchEntries) {
            if (item.studentId.isNotBlank()) {
                presentPairs[item.studentId] = item.studentName to item.studentNumber
            }
        }
        for (stu in presentFromActiveRecords) {
            if (stu.id.isNotBlank() && !presentPairs.containsKey(stu.id)) {
                presentPairs[stu.id] = stu.name to stu.studentNumber
            }
        }
        val sortedPresentPairs = presentPairs.entries.sortedBy { it.value.second }
        val combinedPresentIds = sortedPresentPairs.map { it.key }
        val combinedPresentNames = sortedPresentPairs.map { it.value.first }

        val messageTextToSend = formatMergedAttendanceSystemMessage(
            weekKey = activeWeekKey,
            reportTypeKey = reportTypeKey,
            reportTypeLabel = reportTypeLabel,
            reportIcon = reportIcon,
            reportColorKey = reportColorKey,
            servantUid = currentUserId,
            servantName = servantDisplayName,
            entries = snapshotEntries,
            presentStudentIds = combinedPresentIds,
            presentStudentNames = combinedPresentNames,
            cardContextTitle = cardContextTitle
        )
        if (messageTextToSend.isBlank()) return

        val appContext = context.applicationContext
        val targetGroupId = groupId
        val targetServantName = servantDisplayName
        val targetMembers = memberUids

        attendanceIoScope.launch {
            val sendResult = chatRepository.sendGroupMessage(
                groupId = targetGroupId,
                senderName = targetServantName,
                text = messageTextToSend,
                memberUids = targetMembers,
                type = ChatMessageType.SYSTEM,
                waitForServerAck = false
            )
            if (sendResult.isSuccess) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        "تم إرسال تقرير $reportTypeLabel إلى المراسلة ✓",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun queueOrUpdateSystemChatEvent(
        student: Student,
        kind: ReportEventKind?,
        note: String = "",
        cardLabel: String = "",
        modeForEntry: SwipeSessionMode = sessionMode
    ) {
        if (kind != null) {
            pendingChatMode = modeForEntry
            pendingChatEntries[student.id] = PendingSystemChatEntry(
                studentId = student.id,
                studentName = student.name,
                studentNumber = student.studentNumber,
                kind = kind,
                note = note.trim(),
                cardLabel = cardLabel.trim()
            )
        } else {
            pendingChatEntries.remove(student.id)
            if (pendingChatEntries.isEmpty()) {
                pendingChatMode = SwipeSessionMode.Unselected
            }
        }
    }

    DisposableEffect(groupId, activeWeekKey) {
        onDispose {
            flushPendingSystemChatNow()
        }
    }

    // 4. Navigation & Queue State
    var currentIndex by rememberSaveable(groupId, activeWeekKey, selectedModeKey) { mutableIntStateOf(0) }
    var showSummaryView by rememberSaveable(groupId, activeWeekKey, selectedModeKey) { mutableStateOf(false) }
    var isEditingSpecificPreparedStudent by rememberSaveable(groupId, activeWeekKey, selectedModeKey) { mutableStateOf(false) }
    var hasInitializedIndex by remember(groupId, activeWeekKey, selectedModeKey) { mutableStateOf(false) }
    var permissionErrorMessage by remember { mutableStateOf<String?>(null) }
    var studentPendingIssueNote by remember { mutableStateOf<Student?>(null) }
    val undoStack = remember { mutableStateListOf<AttendanceActionHistoryEntry>() }

    // Jump to the first unprepared student once a session mode has been chosen from the Star button
    LaunchedEffect(orderedStudents.size, activeRecordsByStudentId.size, selectedModeKey, isModeSelected) {
        if (!isModeSelected) {
            currentIndex = 0
            showSummaryView = false
            hasInitializedIndex = false
            return@LaunchedEffect
        }
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

    fun triggerSelectCardPrompt() {
        showSelectCardPrompt = true
        starPulseTrigger += 1
    }

    fun recordStudentAttendance(
        student: Student,
        status: AttendanceStatus,
        note: String = ""
    ) {
        if (!isModeSelected) {
            triggerSelectCardPrompt()
            return
        }
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
            if (selectedModeKey != entryCard.id) {
                selectedModeKey = entryCard.id
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    rootOriginInRoot = coords.positionInRoot()
                }
        ) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("smart_attendance_screen")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact Top Bar with Progress & Single Prominent Star Button next to Summary/Swipe Toggle
                    val totalCount = orderedStudents.size
                    val preparedCount = if (isModeSelected) {
                        orderedStudents.count { activeRecordsByStudentId.containsKey(it.id) }
                    } else {
                        0
                    }
                    val displayedStepNumber = if (totalCount == 0) {
                        0
                    } else if (showSummaryView) {
                        totalCount
                    } else {
                        (currentIndex.coerceIn(0, totalCount - 1) + 1)
                    }

                    SmartAttendanceTopHeader(
                        weekKey = activeWeekKey,
                        sessionMode = sessionMode,
                        availableCardTypes = availableCardTypes,
                        customCardConfig = customCardConfig,
                        starMenuExpanded = starMenuExpanded,
                        onStarMenuExpandedChange = { expanded ->
                            starMenuExpanded = expanded
                            if (expanded) {
                                showSelectCardPrompt = false
                            }
                        },
                        starPulseTrigger = starPulseTrigger,
                        onStarButtonBoundsChanged = { bounds ->
                            starButtonBoundsInRoot = bounds
                        },
                        onSelectSessionMode = { newMode ->
                            val newKey = when (newMode) {
                                SwipeSessionMode.Unselected -> MODE_KEY_UNSELECTED
                                SwipeSessionMode.NormalAttendance -> MODE_KEY_NORMAL_ATTENDANCE
                                is SwipeSessionMode.CardMode -> newMode.cardType.id
                            }
                            if (newKey != selectedModeKey) {
                                flushPendingSystemChatNow()
                                selectedModeKey = newKey
                                permissionErrorMessage = null
                                isEditingSpecificPreparedStudent = false
                                showSummaryView = false
                                hasInitializedIndex = false
                            }
                            showSelectCardPrompt = false
                            starMenuExpanded = false
                        },
                        currentPosition = displayedStepNumber,
                        preparedCount = preparedCount,
                        totalCount = totalCount,
                        showSummaryView = showSummaryView,
                        canUndo = undoStack.isNotEmpty(),
                        onUndo = ::undoLastGesture,
                        onToggleSummary = {
                            if (!isModeSelected) {
                                triggerSelectCardPrompt()
                            } else {
                                showSummaryView = !showSummaryView
                            }
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
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    } else if (showSummaryView && isModeSelected) {
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

                        // Automatically skip already-prepared student once a mode is active unless explicitly editing
                        LaunchedEffect(currentStudent.id, existingRecord != null, isEditingSpecificPreparedStudent, selectedModeKey, isModeSelected) {
                            if (isModeSelected && existingRecord != null && !isEditingSpecificPreparedStudent) {
                                val hasAnyUnprepared = orderedStudents.any { !activeRecordsByStudentId.containsKey(it.id) }
                                if (hasAnyUnprepared) {
                                    advanceToNextUnpreparedFrom(safeIndex)
                                } else {
                                    showSummaryView = true
                                }
                            }
                        }

                        SmartAttendanceSwipeCardContent(
                            isModeSelected = isModeSelected,
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
                            onRequireModeSelection = ::triggerSelectCardPrompt,
                            onSwipePresent = {
                                recordStudentAttendance(currentStudent, AttendanceStatus.PRESENT)
                            },
                            onSwipeAbsent = {
                                recordStudentAttendance(currentStudent, AttendanceStatus.ABSENT)
                            },
                            onSwipeIssue = {
                                if (!isModeSelected) {
                                    triggerSelectCardPrompt()
                                } else if (!canModifyRecord(existingRecord)) {
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

            // Quick Floating Notification ("لسه ما اخترتش كارت") — dismisses immediately on tap anywhere
            SelectCardQuickAlertOverlay(
                visible = showSelectCardPrompt && !showStarSpotlight,
                onDismiss = { showSelectCardPrompt = false }
            )

            // First-time-only Professional Spotlight Coach-Mark highlighting ONLY the Star button
            if (showStarSpotlight && starButtonBoundsInRoot != Rect.Zero) {
                StarButtonSpotlightOverlay(
                    starBoundsInRoot = starButtonBoundsInRoot,
                    rootOriginInRoot = rootOriginInRoot,
                    onTapStarTarget = ::markStarSpotlightSeenAndOpenMenu
                )
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
    sessionMode: SwipeSessionMode,
    availableCardTypes: List<CardType>,
    customCardConfig: CustomCardConfig,
    starMenuExpanded: Boolean,
    onStarMenuExpandedChange: (Boolean) -> Unit,
    starPulseTrigger: Int,
    onStarButtonBoundsChanged: (Rect) -> Unit,
    onSelectSessionMode: (SwipeSessionMode) -> Unit,
    currentPosition: Int,
    preparedCount: Int,
    totalCount: Int,
    showSummaryView: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onToggleSummary: () -> Unit,
    onClose: () -> Unit
) {
    val progressFraction = if (totalCount > 0) {
        (preparedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val extColors = LocalKamExtendedColors.current
    val isModeSelected = sessionMode != SwipeSessionMode.Unselected
    val selectedCard = (sessionMode as? SwipeSessionMode.CardMode)?.cardType

    // Brief energetic bounce when the servant tries to swipe before picking from the Star button
    var isPulsingStar by remember { mutableStateOf(false) }
    LaunchedEffect(starPulseTrigger) {
        if (starPulseTrigger > 0) {
            isPulsingStar = true
            delay(280L)
            isPulsingStar = false
        }
    }
    val starScale by animateFloatAsState(
        targetValue = if (isPulsingStar) 1.24f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "star_button_pulse_scale"
    )

    val starAccentColor = when (sessionMode) {
        SwipeSessionMode.Unselected -> extColors.cardGold
        SwipeSessionMode.NormalAttendance -> extColors.onlineGreen
        is SwipeSessionMode.CardMode -> KamSemanticColors.cardColor(sessionMode.cardType, customCardConfig)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (sessionMode) {
                                SwipeSessionMode.Unselected -> "السحب الذكي"
                                SwipeSessionMode.NormalAttendance -> "📋 حضور عادي"
                                is SwipeSessionMode.CardMode -> {
                                    val c = sessionMode.cardType
                                    "${c.displaySymbol(customCardConfig)} ${formatCardRegistrationLabel(c, customCardConfig)}"
                                }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (sessionMode) {
                                SwipeSessionMode.Unselected -> "اختر النوع من النجمة ⭐"
                                SwipeSessionMode.NormalAttendance -> "$weekKey • بدون نقاط"
                                is SwipeSessionMode.CardMode -> "$weekKey • +${sessionMode.cardType.displayPoints(customCardConfig)} نقطة"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isModeSelected) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                extColors.cardGold
                            },
                            fontWeight = if (isModeSelected) FontWeight.Normal else FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                    // Single Prominent Star Button right beside the Summary/Swipe Toggle
                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isModeSelected) {
                                starAccentColor.copy(alpha = 0.20f)
                            } else {
                                extColors.cardGold.copy(alpha = 0.22f)
                            },
                            border = BorderStroke(
                                width = if (isModeSelected) 1.5.dp else 2.dp,
                                color = starAccentColor
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                }
                                .onGloballyPositioned { coords ->
                                    onStarButtonBoundsChanged(coords.boundsInRoot())
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onStarMenuExpandedChange(true) }
                                .testTag("smart_swipe_star_selector_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "اختيار الحضور أو نوع الكارت",
                                    tint = starAccentColor,
                                    modifier = Modifier.size(30.dp)
                                )
                                if (isModeSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(extColors.onlineGreen)
                                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = starMenuExpanded,
                            onDismissRequest = { onStarMenuExpandedChange(false) },
                            modifier = Modifier.testTag("smart_swipe_star_mode_menu")
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "📋 حضور عادي (بدون نقاط)",
                                            fontWeight = if (sessionMode == SwipeSessionMode.NormalAttendance) {
                                                FontWeight.ExtraBold
                                            } else {
                                                FontWeight.Medium
                                            },
                                            color = if (sessionMode == SwipeSessionMode.NormalAttendance) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (sessionMode == SwipeSessionMode.NormalAttendance) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSelectSessionMode(SwipeSessionMode.NormalAttendance)
                                },
                                modifier = Modifier.testTag("star_menu_normal_attendance")
                            )
                            HorizontalDivider()
                            availableCardTypes.forEach { card ->
                                val isSelected = selectedCard == card
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "${card.displaySymbol(customCardConfig)} ${formatCardRegistrationLabel(card, customCardConfig)} (+${card.displayPoints(customCardConfig)})",
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectSessionMode(SwipeSessionMode.CardMode(card))
                                    },
                                    modifier = Modifier.testTag("star_menu_card_${card.id}")
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onToggleSummary,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("toggle_attendance_summary_button")
                    ) {
                        Text(
                            text = if (showSummaryView) "السحب" else "الملخص ($preparedCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Slim Progress Strip ("12 / 58")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الطفل: $currentPosition / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
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
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .testTag("attendance_progress_bar")
            )
        }
        HorizontalDivider(thickness = 1.dp, color = extColors.hairline)
    }
}

@Composable
private fun SelectCardQuickAlertOverlay(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    val extColors = LocalKamExtendedColors.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.92f, animationSpec = tween(180)),
        exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.94f, animationSpec = tween(140)),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(25f)
    ) {
        // Transparent full-screen touch interceptor: tapping ANYWHERE immediately dismisses the alert
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .pointerInput(Unit) {
                    detectTapGestures { onDismiss() }
                }
                .testTag("select_card_quick_alert_overlay"),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, extColors.cardGold),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .padding(top = 86.dp, start = 20.dp, end = 20.dp)
                    .widthIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = extColors.cardGold.copy(alpha = 0.22f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = extColors.cardGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "لسه ما اخترتش كارت!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.inverseOnSurface
                        )
                        Text(
                            text = "اضغط على النجمة ⭐ فوق لاختيار الحضور العادي أو نوع الكارت أولاً",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.88f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarButtonSpotlightOverlay(
    starBoundsInRoot: Rect,
    rootOriginInRoot: Offset,
    onTapStarTarget: () -> Unit
) {
    val density = LocalDensity.current
    val extColors = LocalKamExtendedColors.current
    val localCenter = remember(starBoundsInRoot, rootOriginInRoot) {
        Offset(
            x = starBoundsInRoot.center.x - rootOriginInRoot.x,
            y = starBoundsInRoot.center.y - rootOriginInRoot.y
        )
    }
    val baseRadiusPx = remember(starBoundsInRoot, density) {
        val maxSide = max(starBoundsInRoot.width, starBoundsInRoot.height)
        max(maxSide * 0.66f, with(density) { 34.dp.toPx() })
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spotlight_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spotlight_ring_pulse"
    )
    var wrongTapBump by remember { mutableIntStateOf(0) }
    val tooltipBumpScale by animateFloatAsState(
        targetValue = if (wrongTapBump % 2 == 1) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "spotlight_tooltip_bump"
    )
    LaunchedEffect(wrongTapBump) {
        if (wrongTapBump % 2 == 1) {
            delay(180L)
            wrongTapBump += 1
        }
    }

    val pointerTopPx = (localCenter.y + baseRadiusPx + with(density) { 10.dp.toPx() }).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .pointerInput(localCenter, baseRadiusPx) {
                detectTapGestures { tapOffset ->
                    val dist = hypot(
                        (tapOffset.x - localCenter.x).toDouble(),
                        (tapOffset.y - localCenter.y).toDouble()
                    ).toFloat()
                    if (dist <= baseRadiusPx * 1.45f) {
                        onTapStarTarget()
                    } else {
                        wrongTapBump += 1
                    }
                }
            }
            .testTag("star_spotlight_onboarding_overlay")
    ) {
        // Full-screen dim layer with transparent circular cutout strictly over the Star button
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.78f))
            // Cut out the Star button spotlight hole
            drawCircle(
                color = Color.Transparent,
                radius = baseRadiusPx,
                center = localCenter,
                blendMode = BlendMode.Clear
            )
            // Crisp static highlight border around the Star cutout
            drawCircle(
                color = extColors.cardGold,
                radius = baseRadiusPx + 3.dp.toPx(),
                center = localCenter,
                style = Stroke(width = 2.5.dp.toPx())
            )
            // Animated expanding radar/pulse halo guiding the servant's eye directly to the Star
            drawCircle(
                color = extColors.cardGold.copy(alpha = (1f - pulseProgress) * 0.7f),
                radius = baseRadiusPx + (pulseProgress * 22.dp.toPx()),
                center = localCenter,
                style = Stroke(width = 2.dp.toPx())
            )
            // Upward triangle arrow drawn in exact physical canvas coordinates pointing straight at the Star
            val arrowHalfW = 10.dp.toPx()
            val arrowH = 11.dp.toPx()
            val arrowTopY = pointerTopPx.toFloat()
            val triangle = Path().apply {
                moveTo(localCenter.x, arrowTopY)
                lineTo(localCenter.x + arrowHalfW, arrowTopY + arrowH)
                lineTo(localCenter.x - arrowHalfW, arrowTopY + arrowH)
                close()
            }
            drawPath(path = triangle, color = extColors.cardGold)
        }

        // Professional Coach-Mark Tooltip Card underneath the cutout
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.8.dp, extColors.cardGold),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
                .offset { IntOffset(x = 0, y = pointerTopPx + with(density) { 10.dp.toPx() }.roundToInt()) }
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    scaleX = tooltipBumpScale
                    scaleY = tooltipBumpScale
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = extColors.cardGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "اضغط على النجمة هنا أولاً",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "قبل البدء في السحب، اضغط على زر النجمة المضيء بالأعلى لاختيار نوع الجلسة (حضور عادي أو أي كارت).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
                Surface(
                    color = extColors.cardGold.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "⭐ اضغط على النجمة المضيئة للمتابعة",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = extColors.cardGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartAttendanceSwipeCardContent(
    isModeSelected: Boolean,
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
    onRequireModeSelection: () -> Unit,
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

    val offsetX = remember(student.id, selectedCardType?.id, isModeSelected) { Animatable(0f) }
    val offsetY = remember(student.id, selectedCardType?.id, isModeSelected) { Animatable(0f) }

    val dragDx = offsetX.value
    val dragDy = offsetY.value
    val horizontalThreshold = 150f
    val verticalDownThreshold = 150f

    // Visual gesture hint during drag (only shown when a mode is selected)
    val activeGesturePreview: AttendanceStatus? = if (!isModeSelected) {
        null
    } else {
        when {
            abs(dragDx) >= abs(dragDy) && dragDx > 60f -> AttendanceStatus.PRESENT
            abs(dragDx) >= abs(dragDy) && dragDx < -60f -> AttendanceStatus.ABSENT
            dragDy > 60f -> AttendanceStatus.ISSUE
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        val extColors = LocalKamExtendedColors.current
        // Compact Direction Legend Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GestureLegendPill(
                label = if (selectedCardType == null) {
                    "يمين ← حاضر"
                } else {
                    "يمين ← أخذ الكارت (+${selectedCardType.displayPoints(customCardConfig)})"
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
                    "يسار → ما خدش"
                },
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Draggable Student Card (fills all reclaimed top space so photo is much larger!)
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
                    .pointerInput(student.id, existingRecord?.id, selectedCardType?.id, isModeSelected) {
                        detectDragGestures(
                            onDragEnd = {
                                val endX = offsetX.value
                                val endY = offsetY.value
                                val crossedAnyThreshold =
                                    (abs(endX) >= abs(endY) && abs(endX) > horizontalThreshold) ||
                                        (endY > verticalDownThreshold)

                                if (!isModeSelected) {
                                    scope.launch {
                                        launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                        launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                    }
                                    if (crossedAnyThreshold || abs(endX) > 45f || endY > 45f) {
                                        onRequireModeSelection()
                                    }
                                    return@detectDragGestures
                                }

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
                                val damping = if (isModeSelected) 1f else 0.45f
                                scope.launch {
                                    offsetX.snapTo(offsetX.value + dragAmount.x * damping)
                                    offsetY.snapTo((offsetY.value + dragAmount.y * damping).coerceAtLeast(-40f))
                                }
                            }
                        )
                    }
                    .testTag("smart_attendance_student_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Metadata Row inside Card: Block Color Badge + Student Number
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
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
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
                        Spacer(modifier = Modifier.height(6.dp))
                        ExistingAttendanceBadgeStrip(
                            selectedCardType = selectedCardType,
                            customCardConfig = customCardConfig,
                            record = existingRecord,
                            canDelete = isAdmin || existingRecord.servantId.isBlank() || existingRecord.servantId == currentUserId,
                            onDelete = onAdminDeleteRecord
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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

                    Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

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
