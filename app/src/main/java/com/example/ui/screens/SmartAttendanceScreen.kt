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
import com.example.R
import com.example.data.cache.LocalClassroomCache
import com.example.data.model.ArabicInitialsHelper
import com.example.data.model.AttendanceRecord
import com.example.data.model.AttendanceStatus
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageType
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.data.repository.ChatRepository
import com.example.data.repository.KamRepository
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.theme.KamSemanticColors
import com.example.ui.viewmodel.KamScreenState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Immutable
private data class AttendanceActionHistoryEntry(
    val student: Student,
    val previousRecord: AttendanceRecord?,
    val appliedStatus: AttendanceStatus,
    val appliedNote: String
)

@Immutable
private data class PendingSystemChatEntry(
    val studentId: String,
    val studentName: String,
    val studentNumber: Long,
    val status: AttendanceStatus,
    val note: String
)

private val attendanceIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Merges `ABSENT` and `ISSUE` events recorded in the same attendance session (within a 2-minute window
 * or when closing/finishing the session) into a single `type = "SYSTEM"` message in the group chat.
 */
private fun formatMergedAttendanceSystemMessage(
    servantName: String,
    entries: Collection<PendingSystemChatEntry>
): String {
    if (entries.isEmpty()) return ""
    val absentList = entries
        .filter { it.status == AttendanceStatus.ABSENT }
        .sortedBy { it.studentNumber }
    val issueList = entries
        .filter { it.status == AttendanceStatus.ISSUE }
        .sortedBy { it.studentNumber }
    if (absentList.isEmpty() && issueList.isEmpty()) return ""

    fun sanitizeField(raw: String): String =
        raw.replace("|", " ").replace("\n", " ").replace("\r", " ").trim()

    val safeServant = sanitizeField(servantName).ifBlank { "خادم" }
    val sb = StringBuilder()
    sb.append(ChatMessage.ATTENDANCE_REPORT_V2_PREFIX).append('\n')
    sb.append("📋 تحضير الفصل (بواسطة $safeServant) — غائب: ${absentList.size} | مشكلة: ${issueList.size}")
    sb.append('\n')
    sb.append("S|").append(safeServant)

    for (item in absentList) {
        val line = "\nA|${sanitizeField(item.studentId)}|${item.studentNumber}|${sanitizeField(item.studentName)}"
        if (sb.length + line.length <= ChatMessage.MAX_SYSTEM_TEXT_LENGTH) {
            sb.append(line)
        }
    }
    for (item in issueList) {
        val line = "\nI|${sanitizeField(item.studentId)}|${item.studentNumber}|${sanitizeField(item.studentName)}|${sanitizeField(item.note)}"
        if (sb.length + line.length <= ChatMessage.MAX_SYSTEM_TEXT_LENGTH) {
            sb.append(line)
        }
    }
    return sb.toString().take(ChatMessage.MAX_SYSTEM_TEXT_LENGTH)
}

@Composable
fun SmartAttendanceScreen(
    state: KamScreenState,
    currentUserId: String,
    onOpenSwipeCards: (() -> Unit)? = null,
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

    // 3. Batching ABSENT & ISSUE events within a 2-minute window into a single SYSTEM group chat message
    val pendingChatEntries = remember { LinkedHashMap<String, PendingSystemChatEntry>() }
    var batchFlushJob by remember { mutableStateOf<Job?>(null) }

    fun flushPendingSystemChatNow() {
        batchFlushJob?.cancel()
        batchFlushJob = null
        if (pendingChatEntries.isEmpty() || groupId.isBlank()) return
        val snapshotEntries = pendingChatEntries.values.toList()
        pendingChatEntries.clear()
        val messageText = formatMergedAttendanceSystemMessage(servantDisplayName, snapshotEntries)
        if (messageText.isBlank()) return
        attendanceIoScope.launch {
            chatRepository.sendGroupMessage(
                groupId = groupId,
                senderName = servantDisplayName,
                text = messageText,
                memberUids = memberUids,
                type = ChatMessageType.SYSTEM,
                waitForServerAck = false
            )
        }
    }

    fun queueOrUpdateSystemChatEvent(student: Student, status: AttendanceStatus?, note: String) {
        if (status == AttendanceStatus.ABSENT || status == AttendanceStatus.ISSUE) {
            pendingChatEntries[student.id] = PendingSystemChatEntry(
                studentId = student.id,
                studentName = student.name,
                studentNumber = student.studentNumber,
                status = status,
                note = note.trim()
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
    var currentIndex by rememberSaveable(groupId, activeWeekKey) { mutableIntStateOf(0) }
    var showSummaryView by rememberSaveable(groupId, activeWeekKey) { mutableStateOf(false) }
    var isEditingSpecificPreparedStudent by rememberSaveable(groupId, activeWeekKey) { mutableStateOf(false) }
    var hasInitializedIndex by remember(groupId, activeWeekKey) { mutableStateOf(false) }
    var permissionErrorMessage by remember { mutableStateOf<String?>(null) }
    var studentPendingIssueNote by remember { mutableStateOf<Student?>(null) }
    val undoStack = remember { mutableStateListOf<AttendanceActionHistoryEntry>() }

    // Jump to the first unprepared student initially (or show summary if all are already prepared)
    LaunchedEffect(orderedStudents.size, recordsByStudentId.size) {
        if (!hasInitializedIndex && orderedStudents.isNotEmpty()) {
            val firstUnprepared = orderedStudents.indexOfFirst { stu ->
                !recordsByStudentId.containsKey(stu.id)
            }
            if (firstUnprepared >= 0) {
                currentIndex = firstUnprepared
                hasInitializedIndex = true
            } else if (recordsByStudentId.size >= orderedStudents.size) {
                showSummaryView = true
                hasInitializedIndex = true
            }
        }
    }

    fun advanceToNextUnpreparedFrom(startIndex: Int) {
        if (orderedStudents.isEmpty()) {
            showSummaryView = true
            return
        }
        isEditingSpecificPreparedStudent = false
        for (offset in 1..orderedStudents.size) {
            val candidateIdx = (startIndex + offset) % orderedStudents.size
            val candidateStu = orderedStudents[candidateIdx]
            if (!recordsByStudentId.containsKey(candidateStu.id)) {
                currentIndex = candidateIdx
                return
            }
        }
        // All students have been prepared! Flush any pending system message and show summary
        flushPendingSystemChatNow()
        showSummaryView = true
    }

    // 5. Preload the closest 3 upcoming students' large photos in the background (Dispatchers.IO)
    LaunchedEffect(currentIndex, orderedStudents, recordsByStudentId.size, showSummaryView) {
        if (showSummaryView || orderedStudents.isEmpty()) return@LaunchedEffect
        val upcoming = ArrayList<Student>(4)
        val safeStart = currentIndex.coerceIn(0, orderedStudents.lastIndex)
        upcoming.add(orderedStudents[safeStart])
        var step = 1
        while (upcoming.size < 4 && step < orderedStudents.size) {
            val idx = (safeStart + step) % orderedStudents.size
            val candidate = orderedStudents[idx]
            if (!recordsByStudentId.containsKey(candidate.id) || upcoming.size < 4) {
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
        return state.isAdmin || existing.servantId == currentUserId
    }

    fun recordStudentAttendance(
        student: Student,
        status: AttendanceStatus,
        note: String = ""
    ) {
        val existing = recordsByStudentId[student.id]
        if (!canModifyRecord(existing)) {
            permissionErrorMessage =
                "هذا الطفل تم تحضيره بواسطة «${existing?.servantName.orEmpty()}» — الأمين فقط يمكنه تعديل أو حذف تسجيل خادم آخر."
            return
        }
        permissionErrorMessage = null
        val cleanNote = note.trim().take(AttendanceRecord.MAX_NOTE_LENGTH)

        // Push to undo stack
        undoStack.add(
            AttendanceActionHistoryEntry(
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

        // Queue or update 2-minute merged SYSTEM chat message for ABSENT / ISSUE
        queueOrUpdateSystemChatEvent(student, status, cleanNote)

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

        advanceToNextUnpreparedFrom(currentIndex)
    }

    fun undoLastGesture() {
        if (undoStack.isEmpty()) return
        val lastEntry = undoStack.removeAt(undoStack.lastIndex)
        val stu = lastEntry.student
        val prev = lastEntry.previousRecord
        permissionErrorMessage = null

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
            queueOrUpdateSystemChatEvent(stu, prev.attendanceStatus, prev.note)
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

        val targetIdx = orderedStudents.indexOfFirst { it.id == stu.id }
        if (targetIdx >= 0) {
            currentIndex = targetIdx
            showSummaryView = false
            isEditingSpecificPreparedStudent = prev != null
        }
    }

    fun adminDeleteRecord(student: Student) {
        val existing = recordsByStudentId[student.id] ?: return
        if (!state.isAdmin && existing.servantId != currentUserId) {
            permissionErrorMessage = "الأمين فقط يمكنه حذف تسجيل خادم آخر."
            return
        }
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
                // Top Bar with Progress ("12 / 58") & Summary Toggle
                val totalCount = orderedStudents.size
                val preparedCount = orderedStudents.count { recordsByStudentId.containsKey(it.id) }
                val displayedStepNumber = if (totalCount == 0) {
                    0
                } else if (showSummaryView) {
                    totalCount
                } else {
                    (currentIndex.coerceIn(0, totalCount - 1) + 1)
                }

                SmartAttendanceTopHeader(
                    weekKey = activeWeekKey,
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
                    onOpenSwipeCards = if (onOpenSwipeCards != null) {
                        {
                            flushPendingSystemChatNow()
                            onOpenSwipeCards()
                        }
                    } else null,
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
                        orderedStudents = orderedStudents,
                        recordsByStudentId = recordsByStudentId,
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
                            val nextIdx = orderedStudents.indexOfFirst { !recordsByStudentId.containsKey(it.id) }
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
                    val existingRecord = recordsByStudentId[currentStudent.id]

                    // Automatically skip already-prepared student unless the user explicitly navigated to edit them
                    LaunchedEffect(currentStudent.id, existingRecord != null, isEditingSpecificPreparedStudent) {
                        if (existingRecord != null && !isEditingSpecificPreparedStudent) {
                            val hasAnyUnprepared = orderedStudents.any { !recordsByStudentId.containsKey(it.id) }
                            if (hasAnyUnprepared) {
                                advanceToNextUnpreparedFrom(safeIndex)
                            } else {
                                showSummaryView = true
                            }
                        }
                    }

                    SmartAttendanceSwipeCardContent(
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
                                    "هذا الطفل تم تحضيره بواسطة «${existingRecord?.servantName.orEmpty()}» — الأمين فقط يمكنه تعديل أو حذف تسجيل خادم آخر."
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
            val existingNote = recordsByStudentId[issueTarget.id]?.note.orEmpty()
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
    currentPosition: Int,
    preparedCount: Int,
    totalCount: Int,
    showSummaryView: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onToggleSummary: () -> Unit,
    onOpenSwipeCards: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val progressFraction = if (totalCount > 0) {
        (preparedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            text = "التحضير الذكي بالسحب",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "الأسبوع: $weekKey (منفصل عن نقاط الكروت)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    if (onOpenSwipeCards != null) {
                        OutlinedButton(
                            onClick = onOpenSwipeCards,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("switch_to_swipe_cards_button")
                        ) {
                            Text(
                                text = "⭐ كروت",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
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
                    text = "تم تحضير: $preparedCount / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .testTag("attendance_progress_bar")
            )
        }
    }
}

@Composable
private fun SmartAttendanceSwipeCardContent(
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

    val offsetX = remember(student.id) { Animatable(0f) }
    val offsetY = remember(student.id) { Animatable(0f) }

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
        // Direction Legend Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GestureLegendPill(
                label = "يمين ← حاضر",
                color = Color(0xFF10B981)
            )
            GestureLegendPill(
                label = "أسفل ↓ مشكلة",
                color = Color(0xFFF59E0B)
            )
            GestureLegendPill(
                label = "يسار → غايب",
                color = Color(0xFFEF4444)
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
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                ),
                border = BorderStroke(
                    width = 2.5.dp,
                    color = when (activeGesturePreview) {
                        AttendanceStatus.PRESENT -> Color(0xFF10B981)
                        AttendanceStatus.ABSENT -> Color(0xFFEF4444)
                        AttendanceStatus.ISSUE -> Color(0xFFF59E0B)
                        null -> blockAccent
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
                    .pointerInput(student.id, existingRecord?.id) {
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
                            record = existingRecord,
                            canDelete = isAdmin || existingRecord.servantId == currentUserId,
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
                                AttendanceStatus.PRESENT -> "✓ حاضر في الكنيسة" to Color(0xFF10B981)
                                AttendanceStatus.ABSENT -> "✕ غايب" to Color(0xFFEF4444)
                                AttendanceStatus.ISSUE -> "⚠ عمل مشكلة" to Color(0xFFF59E0B)
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
                    containerColor = Color(0xFF10B981),
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
                Text("حاضر", fontWeight = FontWeight.ExtraBold)
            }

            // Issue (Down)
            Button(
                onClick = onSwipeIssue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF59E0B),
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
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_mark_absent")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("غايب", fontWeight = FontWeight.ExtraBold)
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
    record: AttendanceRecord,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val status = record.attendanceStatus
    val badgeColor = when (status) {
        AttendanceStatus.PRESENT -> Color(0xFF10B981)
        AttendanceStatus.ABSENT -> Color(0xFFEF4444)
        AttendanceStatus.ISSUE -> Color(0xFFF59E0B)
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
                    text = "مسجّل هذا الأسبوع: ${status.arabicLabel} (بواسطة ${record.servantName})",
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
                    tint = Color(0xFFF59E0B)
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
                    containerColor = Color(0xFFF59E0B),
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
                title = "الحاضرين",
                count = presentStudents.size,
                color = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )
            SummaryStatCard(
                title = "الغايبين",
                count = absentStudents.size,
                color = Color(0xFFEF4444),
                modifier = Modifier.weight(1f)
            )
            SummaryStatCard(
                title = "المشاكل",
                count = issueStudents.size,
                color = Color(0xFFF59E0B),
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
                        text = "يتبقى $unpreparedCount طفل لم يتم تحضيرهم بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = onResumeUnprepared,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("استكمال التحضير", fontWeight = FontWeight.Bold)
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
                    text = "أسماء الغايبين (${absentStudents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFEF4444),
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
                        badgeColor = Color(0xFFEF4444),
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
                        color = Color(0xFFF59E0B)
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
                        badgeColor = Color(0xFFF59E0B),
                        canEdit = isAdmin || rec?.servantId == currentUserId,
                        canDelete = isAdmin || rec?.servantId == currentUserId,
                        onEdit = { if (idx >= 0) onSelectStudentToEdit(idx) },
                        onDelete = { onAdminDeleteRecord(stu) }
                    )
                }
            }

            // 3. Present Students (الحاضرين)
            if (presentStudents.isNotEmpty()) {
                item(key = "header_present") {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "الحاضرين (${presentStudents.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981)
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
                        badgeColor = Color(0xFF10B981),
                        canEdit = isAdmin || rec?.servantId == currentUserId,
                        canDelete = isAdmin || rec?.servantId == currentUserId,
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
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.55f)),
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
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
