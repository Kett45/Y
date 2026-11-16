package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.BlockColorPreset
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.ChildNote
import com.example.data.model.ChildNoteType
import com.example.data.model.NotificationCategory
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.model.Student
import com.example.ui.components.ChildProfileFullSheet
import com.example.ui.components.CompactCardButtonsRow
import com.example.ui.components.MemorizationNotesSheet
import com.example.ui.components.ServantAvatarCircle
import com.example.ui.components.ServantBlockContactRows
import com.example.ui.components.ServantPhotoPickerDialog
import com.example.ui.components.StudentAvatarCircle
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.components.StudentPhotoPickerDialog
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.LocalAppAppearanceController
import com.example.ui.viewmodel.BulkImportLinePreview
import com.example.ui.viewmodel.KamScreenState
import com.example.util.KamPerformanceTracker

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: KamScreenState,
    currentUserId: String,
    onSearchQueryChange: (String) -> Unit,
    onMonthOffset: (Int) -> Unit,
    onSelectMonthAndWeek: (String, String?) -> Unit = { _, _ -> },
    onAwardCard: (Student, CardType) -> Unit,
    onUndoCardEvent: (CardEvent) -> Unit,
    onAddSingleStudent: (name: String, customNumber: Int?, blockId: String) -> Unit,
    onAnalyzeBulkImport: (String) -> List<BulkImportLinePreview>,
    onConfirmBulkImport: (List<String>) -> Unit,
    onCreateBlock: (servantName: String, colorHex: String, servantUid: String) -> Unit,
    onMoveStudentToBlock: (Student, String) -> Unit,
    onReorderStudent: (Student, Boolean) -> Unit,
    onUpdateStudent: (Student, String, Int, String) -> Unit,
    onUpdateStudentAvatar: (Student, String) -> Unit = { _, _ -> },
    onDeleteStudent: (Student) -> Unit,
    onToggleBlockCollapsed: (String) -> Unit = {},
    onMergeDuplicateStudents: (Student, Student) -> Unit = { _, _ -> },
    onKeepBothDuplicateStudents: (String) -> Unit = {},
    onUpdateOwnServantAvatar: (String) -> Unit = {},
    onOpenPhoneWizard: () -> Unit = {},
    onAddChildComment: (Student, String) -> Unit = { _, _ -> },
    onAddMemorizationNote: (Student, String, Boolean) -> Unit = { _, _, _ -> },
    onPostChildGroupNotification: (Student, NotificationCategory, String) -> Unit = { _, _, _ -> },
    onDeleteChildNote: (ChildNote) -> Unit = {}
) {
    val localCollapsedOverrides = remember { mutableStateMapOf<String, Boolean>() }
    var showLeaderboardExpanded by rememberSaveable { mutableStateOf(false) }
    var showSmartAttendanceScreen by rememberSaveable { mutableStateOf(false) }
    var showSwipeCardsScreen by rememberSaveable { mutableStateOf(false) }

    // Dialog & Sheet states
    var showAddMenuDialog by rememberSaveable { mutableStateOf(false) }
    var showAddStudentDialog by rememberSaveable { mutableStateOf(false) }
    var showBulkImportDialog by rememberSaveable { mutableStateOf(false) }
    var showAddBlockDialog by rememberSaveable { mutableStateOf(false) }
    var showMonthWeekTreeDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateAlertsDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateNamesDialog by rememberSaveable { mutableStateOf(false) }
    var showOwnServantPhotoDialog by rememberSaveable { mutableStateOf(false) }
    var studentForMoveSheet by remember { mutableStateOf<Student?>(null) }
    var studentForDetailsSheet by remember { mutableStateOf<Student?>(null) }
    var studentForPhotoDialog by remember { mutableStateOf<Student?>(null) }
    var studentForQuickComment by remember { mutableStateOf<Student?>(null) }
    var studentForMemorizationSheet by remember { mutableStateOf<Student?>(null) }

    val query = state.searchQuery.trim()
    val searchMatchedStudents = remember(state.students, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            state.students.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.studentNumber.toString().contains(query)
            }
        }
    }

    val studentsByBlockId = remember(state.students) {
        val comparator = compareBy<Student> { it.effectivePosition }
            .thenBy { it.sortOrder }
            .thenBy { it.studentNumber }
        state.students
            .groupBy { it.blockId }
            .mapValues { (_, list) -> list.sortedWith(comparator) }
    }

    // All students in blocks remain intact so collapsed blocks stay collapsed during search
    // and clearing search restores the exact previous view.
    val unassignedStudents = remember(state.students, state.blocks) {
        val validBlockIds = state.blocks.map { it.id }.toSet()
        state.students
            .filter { it.blockId.isBlank() || it.blockId !in validBlockIds }
            .sortedWith(
                compareBy<Student> { it.effectivePosition }
                    .thenBy { it.sortOrder }
                    .thenBy { it.studentNumber }
            )
    }

    val visibleBlocks = remember(state.blocks, state.hiddenBlockIds) {
        state.blocks.filter { it.id !in state.hiddenBlockIds }
    }

    fun isBlockCollapsed(blockId: String): Boolean {
        return localCollapsedOverrides[blockId] ?: (blockId in state.collapsedBlockIds)
    }

    val listState = rememberLazyListState()
    val elevatedRowSurfaceColor = KamSemanticColors.elevatedSurface
    val onOpenMoveSheetRemembered: (Student) -> Unit = remember { { studentForMoveSheet = it } }
    val onOpenDetailsSheetRemembered: (Student) -> Unit = remember { { studentForDetailsSheet = it } }
    val onOpenPhotoSheetRemembered: (Student) -> Unit = remember { { studentForPhotoDialog = it } }
    val onOpenOwnServantPhotoRemembered: () -> Unit = remember { { showOwnServantPhotoDialog = true } }
    val currentCollapsedBlockIds by rememberUpdatedState(state.collapsedBlockIds)
    val currentOnToggleBlockCollapsed by rememberUpdatedState(onToggleBlockCollapsed)
    val onToggleCollapseByIdRemembered: (String) -> Unit = remember {
        { blockId ->
            val next = !(localCollapsedOverrides[blockId] ?: (blockId in currentCollapsedBlockIds))
            localCollapsedOverrides[blockId] = next
            currentOnToggleBlockCollapsed(blockId)
        }
    }
    val onToggleUnassignedCollapseRemembered: () -> Unit = remember {
        { onToggleCollapseByIdRemembered("UNASSIGNED") }
    }
    val immutableBlocksHolder = remember(state.blocks) { ImmutableBlocksHolder(state.blocks) }

    LaunchedEffect(state.students.isNotEmpty()) {
        if (state.students.isNotEmpty()) {
            KamPerformanceTracker.markFirstStudentListRendered(state.students.size)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        StudentAvatarImageHelper.setScrollActive(listState.isScrollInProgress)
        if (KamPerformanceTracker.isLiveDiagnosticsEnabled) {
            if (listState.isScrollInProgress) {
                KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_HOME_SCROLL, scrolling = true)
            } else {
                KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_HOME_IDLE, scrolling = false)
            }
        }
    }

    val onOpenQuickCommentRemembered: (Student) -> Unit = remember { { studentForQuickComment = it } }
    val onOpenMemorizationRemembered: (Student) -> Unit = remember { { studentForMemorizationSheet = it } }
    val onHomeCardTapRemembered: (Student, CardType) -> Unit = remember(onAwardCard) {
        { stu, cardType ->
            if (cardType == CardType.MEMORIZATION) {
                studentForMemorizationSheet = stu
            } else {
                onAwardCard(stu, cardType)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSwipeCardsScreen) {
            SwipeCardsScreen(
                state = state,
                currentUserId = currentUserId,
                onAwardCard = onAwardCard,
                onUndoCardEvent = onUndoCardEvent,
                onAddQuickComment = onAddChildComment,
                onPostNotReceivedReport = { stu, card, note ->
                    val noteSuffix = if (note.isNotBlank()) " — $note" else ""
                    onPostChildGroupNotification(
                        stu,
                        NotificationCategory.ABSENT_ISSUE,
                        "لم يستلم كارت ${card.arabicTitle}$noteSuffix"
                    )
                },
                onOpenMemorizationSheet = onOpenMemorizationRemembered,
                onOpenSmartAttendance = {
                    showSwipeCardsScreen = false
                    showSmartAttendanceScreen = true
                },
                onClose = { showSwipeCardsScreen = false }
            )
        } else if (showSmartAttendanceScreen) {
            SmartAttendanceScreen(
                state = state,
                currentUserId = currentUserId,
                onOpenSwipeCards = {
                    showSmartAttendanceScreen = false
                    showSwipeCardsScreen = true
                },
                onClose = { showSmartAttendanceScreen = false }
            )
        } else if (showLeaderboardExpanded) {
            FullClassLeaderboardScreen(
                state = state,
                onBackToHome = { showLeaderboardExpanded = false },
                onOpenStudentDetails = { studentForDetailsSheet = it }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_lazy_column"),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Compact Header Bar + Attendance & Swipe Cards quick launchers
                item(key = "header_controls", contentType = "header_controls") {
                    HomeTopControlCard(
                        state = state,
                        showLeaderboardExpanded = showLeaderboardExpanded,
                        onToggleLeaderboard = { showLeaderboardExpanded = true },
                        onOpenSmartAttendance = { showSmartAttendanceScreen = true },
                        onOpenSwipeCards = { showSwipeCardsScreen = true },
                        onMonthOffset = onMonthOffset,
                        onOpenMonthWeekTree = { showMonthWeekTreeDialog = true },
                        onOpenDuplicateAlerts = { showDuplicateAlertsDialog = true },
                        onSearchQueryChange = onSearchQueryChange
                    )
                }

                // 1b. Duplicate Student Name Comparison Banner (when duplicate names are found during migration or sync)
                if (state.duplicateStudentsByName.isNotEmpty()) {
                    item(key = "duplicate_student_names_banner", contentType = "duplicate_banner") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDuplicateNamesDialog = true }
                                .testTag("duplicate_student_names_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "تم اكتشاف ${state.duplicateStudentsByName.size} اسم متطابق في المجموعة",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "اضغط هنا لعرض الأسماء المتطابقة واختيار السجل المراد الاحتفاظ به يدوياً",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = "مقارنة",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. Search Results Section ABOVE all Servant Blocks (even if child's block is collapsed!)
                if (query.isNotBlank()) {
                    item(key = "search_results_above_blocks", contentType = "search_results") {
                        SearchResultsWithBlockOwnerSection(
                            query = query,
                            matchingStudents = searchMatchedStudents,
                            blocks = state.blocks,
                            enabledCards = state.enabledCardTypes,
                            state = state,
                            onClearSearch = { onSearchQueryChange("") },
                            onAwardCard = onHomeCardTapRemembered,
                            onOpenMoveDialog = { studentForMoveSheet = it },
                            onOpenDetailsDialog = { studentForDetailsSheet = it },
                            onOpenPhotoPicker = { studentForPhotoDialog = it }
                        )
                    }
                }

                // 4. Empty State when no students and no blocks exist yet
                if (state.students.isEmpty()) {
                    item(key = "empty_students_prompt", contentType = "empty_state") {
                        EmptyClassroomPromptCard(
                            isAdmin = state.isAdmin,
                            onOpenBulkImport = { showBulkImportDialog = true },
                            onOpenAddStudent = { showAddStudentDialog = true },
                            onOpenAddBlock = { showAddBlockDialog = true }
                        )
                    }
                }

                // 5. Servant Blocks ("بلوك لكل خادم وتحته أطفاله" — flattened into LazyColumn items for 60fps scrolling)
                visibleBlocks.forEach { block ->
                    val blockStudents = studentsByBlockId[block.id].orEmpty()
                    val isCollapsed = isBlockCollapsed(block.id)
                    val blockAccent = KamSemanticColors.blockColor(block.colorHex)
                    val resolvedOwnerName = state.resolvedServantName(block.servantId, block.servantName)
                    val blockTintSurface = blockAccent.copy(alpha = 0.12f).compositeOver(elevatedRowSurfaceColor)

                    item(
                        key = "block_header_${block.id}",
                        contentType = "servant_block_header"
                    ) {
                        val isOwnBlock = block.servantId.isNotBlank() && block.servantId == currentUserId
                        val servantProfile = state.memberProfileForServant(block.servantId)
                        ServantBlockHeaderCard(
                            block = block,
                            resolvedOwnerName = resolvedOwnerName,
                            groupId = state.group?.id.orEmpty(),
                            groupName = state.group?.name.orEmpty(),
                            studentsCount = blockStudents.size,
                            blockAccent = blockAccent,
                            isCollapsed = isCollapsed,
                            servantProfile = servantProfile,
                            isOwnBlock = isOwnBlock,
                            onToggleCollapseById = onToggleCollapseByIdRemembered,
                            onAvatarClick = if (isOwnBlock) onOpenOwnServantPhotoRemembered else null,
                            onAddOwnPhoneClick = onOpenPhoneWizard
                        )
                    }

                    if (!isCollapsed) {
                        if (blockStudents.isEmpty()) {
                            item(
                                key = "block_empty_${block.id}",
                                contentType = "servant_block_empty"
                            ) {
                                ServantBlockEmptyCard(blockAccent = blockAccent)
                            }
                        } else {
                            val blockBorderColor = blockAccent.copy(alpha = 0.62f)
                            itemsIndexed(
                                items = blockStudents,
                                key = { _, student -> "blk_${block.id}_stu_${student.id}" },
                                contentType = { _, _ -> "block_student_row" }
                            ) { idx, student ->
                                StudentFastCardRow(
                                    student = student,
                                    blockAccent = blockAccent,
                                    rowSurfaceColor = blockTintSurface,
                                    blockLabel = "بلوك $resolvedOwnerName",
                                    monthPoints = state.pointsForStudentInDisplayedScope(student.id),
                                    enabledCardsMask = state.enabledCardsMask,
                                    packedCardCounts = state.packedCardCountsForStudent(student.id),
                                    outerBorderColor = blockBorderColor,
                                    canMoveUp = idx > 0,
                                    canMoveDown = idx < blockStudents.lastIndex,
                                    onAwardCard = onHomeCardTapRemembered,
                                    onOpenQuickComment = onOpenQuickCommentRemembered,
                                    onOpenMoveDialog = onOpenMoveSheetRemembered,
                                    onOpenDetailsDialog = onOpenDetailsSheetRemembered,
                                    onOpenPhotoDialog = onOpenPhotoSheetRemembered,
                                    onReorder = onReorderStudent
                                )
                            }
                        }
                    }
                }

                // 6. Unassigned Students ("قسم بدون بلوك في الآخر" — flattened into LazyColumn items for instant rendering)
                val isUnassignedCollapsed = isBlockCollapsed("UNASSIGNED")
                item(
                    key = "unassigned_section_header",
                    contentType = "unassigned_header"
                ) {
                    UnassignedStudentsHeaderCard(
                        studentsCount = unassignedStudents.size,
                        isCollapsed = isUnassignedCollapsed,
                        onToggleCollapse = onToggleUnassignedCollapseRemembered
                    )
                }

                if (!isUnassignedCollapsed) {
                    if (unassignedStudents.isEmpty()) {
                        item(
                            key = "unassigned_section_empty",
                            contentType = "unassigned_empty"
                        ) {
                            UnassignedStudentsEmptyCard()
                        }
                    } else {
                        itemsIndexed(
                            items = unassignedStudents,
                            key = { _, student -> "unassigned_stu_${student.id}" },
                            contentType = { _, _ -> "unassigned_student_row" }
                        ) { idx, student ->
                            UnassignedStudentLazyRow(
                                student = student,
                                blocksHolder = immutableBlocksHolder,
                                rowSurfaceColor = elevatedRowSurfaceColor,
                                enabledCardsMask = state.enabledCardsMask,
                                monthPoints = state.pointsForStudentInDisplayedScope(student.id),
                                packedCardCounts = state.packedCardCountsForStudent(student.id),
                                canMoveUp = idx > 0,
                                canMoveDown = idx < unassignedStudents.lastIndex,
                                onAwardCard = onHomeCardTapRemembered,
                                onOpenQuickComment = onOpenQuickCommentRemembered,
                                onMoveStudentToBlock = onMoveStudentToBlock,
                                onOpenMoveDialog = onOpenMoveSheetRemembered,
                                onOpenDetailsDialog = onOpenDetailsSheetRemembered,
                                onOpenPhotoDialog = onOpenPhotoSheetRemembered,
                                onReorder = onReorderStudent
                            )
                        }
                    }
                }
            }

            // Compact Auto-Shrinking Floating Action Button isolated in its own composable so scrolling never recomposes HomeScreen
            val showFloatingAddButton = LocalAppAppearanceController.current.showFloatingAddButton
            if (showFloatingAddButton) {
                HomeAutoShrinkingFab(
                    listState = listState,
                    onClick = { showAddMenuDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
            }
        }
    }

    // --- Dialogs & Action Modals ---

    if (showAddMenuDialog) {
        QuickAddActionDialog(
            isAdmin = state.isAdmin,
            onDismiss = { showAddMenuDialog = false },
            onSelectAddSingleStudent = {
                showAddMenuDialog = false
                showAddStudentDialog = true
            },
            onSelectBulkImport = {
                showAddMenuDialog = false
                showBulkImportDialog = true
            },
            onSelectAddBlock = {
                showAddMenuDialog = false
                if (state.isAdmin) showAddBlockDialog = true
            }
        )
    }

    if (showAddStudentDialog) {
        AddSingleStudentDialog(
            blocks = state.blocks,
            autoNumber = state.students.size + 1,
            remainingCapacity = state.remainingStudentCapacity,
            allowCustomNumber = state.group?.autoStudentNumber == false,
            onDismiss = { showAddStudentDialog = false },
            onConfirm = { name, customNum, blockId ->
                onAddSingleStudent(name, customNum, blockId)
                showAddStudentDialog = false
            }
        )
    }

    if (showBulkImportDialog) {
        BulkImportStudentsDialog(
            remainingCapacity = state.remainingStudentCapacity,
            currentStudentsCount = state.students.size,
            onAnalyze = onAnalyzeBulkImport,
            onDismiss = { showBulkImportDialog = false },
            onConfirmImport = { names ->
                onConfirmBulkImport(names)
                showBulkImportDialog = false
            }
        )
    }

    if (showAddBlockDialog && state.isAdmin) {
        AddServantBlockDialog(
            memberProfiles = state.memberProfiles,
            existingBlocks = state.blocks,
            resolvedMemberName = { profile ->
                state.resolvedServantName(profile.effectiveUid, profile.displayName)
            },
            onDismiss = { showAddBlockDialog = false },
            onConfirm = { servantName, colorHex, servantUid ->
                onCreateBlock(servantName, colorHex, servantUid)
                showAddBlockDialog = false
            }
        )
    }

    studentForMoveSheet?.let { targetStudent ->
        MoveStudentBlockDialog(
            student = targetStudent,
            blocks = state.blocks,
            onDismiss = { studentForMoveSheet = null },
            onSelectBlock = { targetBlockId ->
                onMoveStudentToBlock(targetStudent, targetBlockId)
                studentForMoveSheet = null
            }
        )
    }

    studentForDetailsSheet?.let { initialStudent ->
        val targetStudent = state.students.firstOrNull { it.id == initialStudent.id } ?: initialStudent
        val canUndoEvents = state.isAdmin || (state.group?.allowServantUndo == true)
        val leaderboardEntries = remember(state.selectedMonthKey, state.students.size, state.cardEvents.size) {
            state.computeClassLeaderboardEntries(selectedMonths = setOf(state.selectedMonthKey))
        }
        val studentRank = leaderboardEntries.firstOrNull { it.student.id == targetStudent.id }?.rank
        ChildProfileFullSheet(
            student = targetStudent,
            blocks = state.blocks,
            resolvedServantName = { blk -> state.resolvedServantName(blk.servantId, blk.servantName) },
            monthKey = state.selectedMonthKey,
            monthPoints = state.pointsForStudentInSelectedMonth(targetStudent.id),
            leaderboardRank = studentRank,
            totalStudentsCount = state.students.size,
            events = state.eventsForStudent(targetStudent.id),
            comments = state.notesForStudent(targetStudent.id, ChildNoteType.COMMENT),
            isAdmin = state.isAdmin,
            currentUserId = currentUserId,
            canServantUndoOwnEvents = canUndoEvents,
            onDismiss = { studentForDetailsSheet = null },
            onSaveEdits = { newName, newNumber, newBlockId ->
                onUpdateStudent(targetStudent, newName, newNumber, newBlockId)
            },
            onDeleteStudent = {
                onDeleteStudent(targetStudent)
                studentForDetailsSheet = null
            },
            onUndoCardEvent = onUndoCardEvent,
            onOpenPhotoPickerDialog = {
                studentForPhotoDialog = targetStudent
            },
            onUpdateStudentAvatar = { updatedAvatarField ->
                onUpdateStudentAvatar(targetStudent, updatedAvatarField)
                studentForDetailsSheet = targetStudent.copy(avatarBase64 = updatedAvatarField)
            },
            onAddChildComment = { text ->
                onAddChildComment(targetStudent, text)
            },
            onDeleteNote = onDeleteChildNote,
            onOpenMemorizationSheet = {
                studentForMemorizationSheet = targetStudent
            }
        )
    }

    studentForQuickComment?.let { initialStudent ->
        val targetStudent = state.students.firstOrNull { it.id == initialStudent.id } ?: initialStudent
        QuickHomeCommentDialog(
            studentName = targetStudent.name,
            blockName = state.blockNameForStudent(targetStudent),
            onConfirm = { text ->
                onAddChildComment(targetStudent, text)
                studentForQuickComment = null
            },
            onDismiss = { studentForQuickComment = null }
        )
    }

    studentForMemorizationSheet?.let { initialStudent ->
        val targetStudent = state.students.firstOrNull { it.id == initialStudent.id } ?: initialStudent
        val ownerBlock = state.blocks.firstOrNull { it.id == targetStudent.blockId }
        val blockAccent = if (ownerBlock != null) KamSemanticColors.blockColor(ownerBlock.colorHex) else KamSemanticColors.unassignedBlock
        val activeWeekKey = state.selectedWeekKey?.takeIf { it.isNotBlank() } ?: state.currentWeekKey
        val alreadyHasMemCard = state.effectiveAllCardEvents.any {
            it.studentId == targetStudent.id &&
                it.cardType == CardType.MEMORIZATION.id &&
                it.weekKey == activeWeekKey
        }
        MemorizationNotesSheet(
            student = targetStudent,
            blockName = state.blockNameForStudent(targetStudent),
            blockAccent = blockAccent,
            memorizationNotes = state.notesForStudent(targetStudent.id, ChildNoteType.MEMORIZATION),
            alreadyHasMemorizationCardThisWeek = alreadyHasMemCard,
            isAdmin = state.isAdmin,
            currentUserId = currentUserId,
            onAddMemorizationNote = { text, awardCard ->
                onAddMemorizationNote(targetStudent, text, awardCard)
            },
            onToggleMemorizationCardOnly = {
                onAwardCard(targetStudent, CardType.MEMORIZATION)
            },
            onDeleteNote = onDeleteChildNote,
            onDismiss = { studentForMemorizationSheet = null }
        )
    }

    if (showMonthWeekTreeDialog) {
        YearMonthWeekTreeDialog(
            selectedMonthKey = state.selectedMonthKey,
            selectedWeekKey = state.selectedWeekKey,
            onDismiss = { showMonthWeekTreeDialog = false },
            onSelectMonthAndWeek = onSelectMonthAndWeek
        )
    }

    if (showDuplicateAlertsDialog) {
        val canUndoEvents = state.isAdmin || (state.group?.allowServantUndo == true)
        DuplicateCardAlertsDialog(
            alerts = state.duplicateAlerts,
            isAdmin = state.isAdmin,
            currentUserId = currentUserId,
            canServantUndoOwnEvents = canUndoEvents,
            onDismiss = { showDuplicateAlertsDialog = false },
            onUndoCardEvent = onUndoCardEvent
        )
    }

    if (showDuplicateNamesDialog) {
        DuplicateStudentComparisonDialog(
            duplicateGroups = state.duplicateStudentsByName,
            blocks = state.blocks,
            state = state,
            onKeepStudentAndMerge = { keepStu, removeStu ->
                onMergeDuplicateStudents(keepStu, removeStu)
            },
            onKeepBothAsSeparate = { normName ->
                onKeepBothDuplicateStudents(normName)
            },
            onDismiss = { showDuplicateNamesDialog = false }
        )
    }

    studentForPhotoDialog?.let { initialStudent ->
        val targetStudent = state.students.firstOrNull { it.id == initialStudent.id } ?: initialStudent
        val ownerBlock = state.blocks.firstOrNull { it.id == targetStudent.blockId }
        val blockAccent = if (ownerBlock != null) {
            KamSemanticColors.blockColor(ownerBlock.colorHex)
        } else {
            KamSemanticColors.unassignedBlock
        }
        StudentPhotoPickerDialog(
            student = targetStudent,
            blockAccent = blockAccent,
            isAdmin = state.isAdmin,
            onDismiss = { studentForPhotoDialog = null },
            onSaveAvatarBase64 = { newBase64 ->
                onUpdateStudentAvatar(targetStudent, newBase64)
            }
        )
    }

    if (showOwnServantPhotoDialog) {
        val ownBlock = state.blocks.firstOrNull { it.servantId == currentUserId }
        val ownAccent = if (ownBlock != null) {
            KamSemanticColors.blockColor(ownBlock.colorHex)
        } else {
            MaterialTheme.colorScheme.primary
        }
        val ownProfile = state.memberProfileForServant(currentUserId)
        val ownName = ownBlock?.servantName?.ifBlank { null }
            ?: state.userProfile?.displayName?.ifBlank { null }
            ?: "خادم"
        ServantPhotoPickerDialog(
            servantUid = currentUserId,
            servantName = ownName,
            groupId = state.group?.id.orEmpty(),
            memberProfile = ownProfile,
            accentColor = ownAccent,
            onDismiss = { showOwnServantPhotoDialog = false },
            onSaveBase64 = { newBase64 ->
                onUpdateOwnServantAvatar(newBase64)
                showOwnServantPhotoDialog = false
            }
        )
    }
}

/**
 * Compact 56dp single-bar header on the Home screen:
 * - Group Name
 * - Compact Month/Week Chip ("أكتوبر ٢٦ • أسبوع ٥ ▾") opening the Month/Week Tree dialog
 * - Small Search Icon Button (expands search field on tap; close '✕' collapses it back to icon)
 * - Small Leaderboard Icon Button ('الأوائل')
 * - Duplicate Card Alerts Bell + Badge ('الجرس')
 * Secondary group metadata (student count, block count, invite code) lives in the Group/Settings screen.
 */
@Composable
private fun HomeTopControlCard(
    state: KamScreenState,
    showLeaderboardExpanded: Boolean,
    onToggleLeaderboard: () -> Unit,
    onOpenSmartAttendance: () -> Unit = {},
    onOpenSwipeCards: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onMonthOffset: (Int) -> Unit,
    onOpenMonthWeekTree: () -> Unit,
    onOpenDuplicateAlerts: () -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val duplicateCount = state.duplicateAlerts.size
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    val showSearchField = isSearchExpanded || state.searchQuery.isNotEmpty()

    val compactPeriodLabel = remember(state.selectedMonthKey, state.selectedWeekKey) {
        SaturdayCalendarHelper.formatCompactHeaderMonthWeek(state.selectedMonthKey, state.selectedWeekKey)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("compact_home_header_bar"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
            // Single 56dp Compact Row: Group Name + Month/Week Chip + Search Icon + Leaderboard Icon + Bell
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Group Name + App Name & Version (compact header)
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = state.group?.name ?: "الفصل",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "نجم الكارت v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 2. Compact Month & Week Selector Chip ("أكتوبر ٢٦ • أسبوع ٥ ▾")
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .clickable(onClick = onOpenMonthWeekTree)
                        .testTag("open_month_week_tree_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = compactPeriodLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "فتح شجرة الشهور والأسابيع",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 3. Action Icons Row: Search Icon + Leaderboard Icon + Bell + Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Search Icon Button (expands/collapses search bar)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (showSearchField) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clickable {
                                if (showSearchField) {
                                    onSearchQueryChange("")
                                    isSearchExpanded = false
                                } else {
                                    isSearchExpanded = true
                                }
                            }
                            .testTag("toggle_search_icon_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (showSearchField) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearchField) "إغلاق البحث" else "بحث عن طفل",
                                tint = if (showSearchField) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Leaderboard Icon Button ("صفحة ترتيب ونقاط الفصل بالكامل" — always accessible)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (showLeaderboardExpanded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clickable(onClick = onToggleLeaderboard)
                            .testTag("toggle_leaderboard_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "ترتيب ونقاط الفصل بالكامل",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Duplicate Card Bell + Badge Button ("جرس + شارة عدد")
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (duplicateCount > 0) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .height(38.dp)
                            .clickable(onClick = onOpenDuplicateAlerts)
                            .testTag("duplicate_alerts_bell_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (duplicateCount > 0) {
                                    Icons.Default.NotificationsActive
                                } else {
                                    Icons.Default.Notifications
                                },
                                contentDescription = "تنبيهات الكروت المكررة",
                                tint = if (duplicateCount > 0) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(19.dp)
                            )
                            if (duplicateCount > 0) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.error
                                ) {
                                    Text(
                                        text = "$duplicateCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onError,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                            .testTag("duplicate_alerts_badge_count")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Mode Launchers: Smart Attendance ("تحضير") + Swipe Cards ("كروت")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onOpenSmartAttendance)
                        .testTag("open_smart_attendance_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "📋", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "التحضير بالسحب",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clickable(onClick = onOpenSwipeCards)
                        .testTag("open_swipe_cards_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "⭐", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الكروت بالسحب",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Expandable Search Field when Search Icon is tapped
            AnimatedVisibility(visible = showSearchField) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("ابحث باسم الطفل أو رقمه...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "بحث")
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onSearchQueryChange("")
                                isSearchExpanded = false
                            },
                            modifier = Modifier.testTag("close_search_field_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق البحث")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_search_input")
                )
            }

            // Inline Duplicate Card Alert Strip when any duplicate is detected after sync
            AnimatedVisibility(visible = state.duplicateAlerts.isNotEmpty()) {
                val firstAlert = state.duplicateAlerts.firstOrNull()
                if (firstAlert != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenDuplicateAlerts)
                            .testTag("inline_duplicate_alert_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = firstAlert.formattedMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (state.duplicateAlerts.size > 1) {
                                Text(
                                    text = "+${state.duplicateAlerts.size - 1} أخرى",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
}



@Composable
private fun EmptyClassroomPromptCard(
    isAdmin: Boolean,
    onOpenBulkImport: () -> Unit,
    onOpenAddStudent: () -> Unit,
    onOpenAddBlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "قائمة الأطفال فارغة حالياً",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "يمكنك استيراد قائمة أسماء الفصل (حتى ١٥٠ اسم للمجموعة الواحدة) أو إضافة طفل جديد، ثم توزيعهم على بلوكات الخدام.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenBulkImport,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("empty_state_bulk_import_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("استيراد قائمة أسماء")
            }
            OutlinedButton(
                onClick = onOpenAddStudent,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("empty_state_add_single_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة طفل")
            }
        }
        if (isAdmin) {
            TextButton(onClick = onOpenAddBlock) {
                Icon(Icons.Default.ViewModule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("إضافة بلوك خادم جديد")
            }
        }
    }
}

@Composable
private fun ServantBlockHeaderCard(
    block: ServantBlock,
    resolvedOwnerName: String = block.servantName,
    groupId: String,
    groupName: String,
    studentsCount: Int,
    blockAccent: Color,
    isCollapsed: Boolean,
    servantProfile: ServantMemberProfile? = null,
    isOwnBlock: Boolean = false,
    onToggleCollapseById: (String) -> Unit,
    onAvatarClick: (() -> Unit)? = null,
    onAddOwnPhoneClick: () -> Unit = {}
) {
    val currentBlockId by rememberUpdatedState(block.id)
    val currentOnToggle by rememberUpdatedState(onToggleCollapseById)
    val onRowClick = remember { { currentOnToggle(currentBlockId) } }

    val cardShape = RoundedCornerShape(18.dp)
    val headerAccentShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val tintedContainerColor = blockAccent.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tintedContainerColor, cardShape)
            .border(
                width = 1.8.dp,
                color = blockAccent.copy(alpha = 0.75f),
                shape = cardShape
            )
            .testTag("servant_block_header_card_${block.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(blockAccent.copy(alpha = 0.28f), headerAccentShape)
                .clickable(onClick = onRowClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ServantAvatarCircle(
                    servantUid = block.servantId.ifBlank { block.id },
                    servantName = resolvedOwnerName,
                    groupId = groupId,
                    memberProfile = servantProfile,
                    accentColor = blockAccent,
                    size = 42.dp,
                    isEditable = isOwnBlock,
                    onClick = onAvatarClick
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "بلوك $resolvedOwnerName",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$groupName • $studentsCount طفل",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(blockAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$studentsCount",
                        style = MaterialTheme.typography.labelLarge,
                        color = blockAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isCollapsed) "عرض الأطفال" else "طي البلوك",
                        tint = blockAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        ServantBlockContactRows(
            blockId = block.id,
            servantProfile = servantProfile,
            isOwnBlock = isOwnBlock,
            blockAccent = blockAccent,
            onAddOwnPhoneClick = onAddOwnPhoneClick
        )
    }
}

@Composable
private fun ServantBlockEmptyCard(blockAccent: Color) {
    val shape = RoundedCornerShape(14.dp)
    val tintedBg = blockAccent.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(tintedBg, shape)
            .border(
                width = 1.2.dp,
                color = blockAccent.copy(alpha = 0.55f),
                shape = shape
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "لا يوجد أطفال في هذا البلوك حالياً — انقل أي طفل من قسم 'بدون بلوك' بضغطة واحدة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnassignedStudentsHeaderCard(
    studentsCount: Int,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit
) {
    val unassignedColor = KamSemanticColors.unassignedBlock
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .background(unassignedColor.copy(alpha = 0.28f), shape)
            .border(
                width = 1.5.dp,
                color = unassignedColor,
                shape = shape
            )
            .clickable(onClick = onToggleCollapse)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "بدون بلوك (الأسماء الجديدة)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$studentsCount طفل في انتظار التسكين في بلوك",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = "طي أو فتح",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UnassignedStudentsEmptyCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "كل الأطفال موزعين على البلوكات بنجاح!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeAutoShrinkingFab(
    listState: LazyListState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 80
        }
    }
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.testTag("home_add_fab")
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isFabExpanded) 12.dp else 10.dp,
                vertical = 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "إضافة",
                modifier = Modifier.size(20.dp)
            )
            AnimatedVisibility(visible = isFabExpanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "إضافة / استيراد",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Immutable
private data class ImmutableBlocksHolder(val blocks: List<ServantBlock>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnassignedStudentLazyRow(
    student: Student,
    blocksHolder: ImmutableBlocksHolder,
    rowSurfaceColor: Color,
    enabledCardsMask: Int,
    monthPoints: Long,
    packedCardCounts: Long,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAwardCard: (Student, CardType) -> Unit,
    onOpenQuickComment: (Student) -> Unit = {},
    onMoveStudentToBlock: (Student, String) -> Unit,
    onOpenMoveDialog: (Student) -> Unit,
    onOpenDetailsDialog: (Student) -> Unit,
    onOpenPhotoDialog: (Student) -> Unit,
    onReorder: (Student, Boolean) -> Unit
) {
    val blocks = blocksHolder.blocks
    val currentStudent by rememberUpdatedState(student)
    val currentOnMoveToBlock by rememberUpdatedState(onMoveStudentToBlock)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowSurfaceColor, StudentRowCornerShape)
            .border(
                width = 1.dp,
                color = KamSemanticColors.unassignedBlock.copy(alpha = 0.55f),
                shape = StudentRowCornerShape
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StudentFastCardRow(
            student = student,
            blockAccent = MaterialTheme.colorScheme.primary,
            rowSurfaceColor = rowSurfaceColor,
            blockLabel = "بدون بلوك",
            monthPoints = monthPoints,
            enabledCardsMask = enabledCardsMask,
            packedCardCounts = packedCardCounts,
            outerBorderColor = null,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onAwardCard = onAwardCard,
            onOpenQuickComment = onOpenQuickComment,
            onOpenMoveDialog = onOpenMoveDialog,
            onOpenDetailsDialog = onOpenDetailsDialog,
            onOpenPhotoDialog = onOpenPhotoDialog,
            onReorder = onReorder
        )

        // One-tap quick transfer chips to any Servant Block
        if (blocks.isNotEmpty()) {
            Text(
                text = "نقل سريع بضغطة واحدة إلى بلوك:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                blocks.forEach { blk ->
                    val blkColor = KamSemanticColors.blockColor(blk.colorHex)
                    val chipClick = remember(blk.id) { { currentOnMoveToBlock(currentStudent, blk.id) } }
                    Box(
                        modifier = Modifier
                            .background(blkColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .border(1.dp, blkColor, RoundedCornerShape(10.dp))
                            .clickable(onClick = chipClick)
                            .testTag("quick_move_${student.id}_to_${blk.id}")
                    ) {
                        Text(
                            text = "↗ ${blk.servantName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

private val StudentRowCornerShape = RoundedCornerShape(14.dp)
private val PointsPillCornerShape = RoundedCornerShape(8.dp)

@Composable
private fun StudentFastCardRow(
    student: Student,
    blockAccent: Color,
    rowSurfaceColor: Color,
    blockLabel: String = "بدون بلوك",
    monthPoints: Long,
    enabledCardsMask: Int,
    packedCardCounts: Long,
    outerBorderColor: Color? = null,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onAwardCard: (Student, CardType) -> Unit,
    onOpenQuickComment: (Student) -> Unit = {},
    onOpenMoveDialog: (Student) -> Unit,
    onOpenDetailsDialog: (Student) -> Unit,
    onOpenPhotoDialog: (Student) -> Unit,
    onReorder: (Student, Boolean) -> Unit
) {
    KamPerformanceTracker.recordStudentRowRecomposition()
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val currentStudent by rememberUpdatedState(student)
    val currentOnAwardCard by rememberUpdatedState(onAwardCard)
    val currentOnOpenQuickComment by rememberUpdatedState(onOpenQuickComment)
    val currentOnOpenMoveDialog by rememberUpdatedState(onOpenMoveDialog)
    val currentOnOpenDetailsDialog by rememberUpdatedState(onOpenDetailsDialog)
    val currentOnOpenPhotoDialog by rememberUpdatedState(onOpenPhotoDialog)
    val currentOnReorder by rememberUpdatedState(onReorder)

    val clickPhotoDialog = remember { { currentOnOpenPhotoDialog(currentStudent) } }
    val clickDetailsDialog = remember { { currentOnOpenDetailsDialog(currentStudent) } }
    val clickMoveDialog = remember { { currentOnOpenMoveDialog(currentStudent) } }
    val clickMoveUp = remember { { currentOnReorder(currentStudent, true) } }
    val clickMoveDown = remember { { currentOnReorder(currentStudent, false) } }
    val clickAwardCard = remember { { cardType: CardType -> currentOnAwardCard(currentStudent, cardType) } }

    var dragOffsetX by remember(student.id) { mutableFloatStateOf(0f) }

    val baseModifier = if (outerBorderColor != null) {
        Modifier
            .fillMaxWidth()
            .background(rowSurfaceColor, StudentRowCornerShape)
            .border(1.2.dp, outerBorderColor, StudentRowCornerShape)
    } else {
        Modifier
            .fillMaxWidth()
            .background(rowSurfaceColor, StudentRowCornerShape)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (dragOffsetX > 12f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        KamSemanticColors.NotificationChildComment.copy(alpha = 0.22f),
                        StudentRowCornerShape
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = Icons.Default.Comment,
                    contentDescription = "تعليق سريع",
                    tint = KamSemanticColors.NotificationChildComment,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "💬 اكتب تعليقاً",
                    style = typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = KamSemanticColors.NotificationChildComment
                )
            }
        }

        Column(
            modifier = baseModifier
                .graphicsLayer { translationX = dragOffsetX }
                .pointerInput(student.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetX >= 75f) {
                                currentOnOpenQuickComment(currentStudent)
                            }
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            dragOffsetX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || dragOffsetX > 0f) {
                                change.consume()
                                dragOffsetX = (dragOffsetX + dragAmount).coerceIn(0f, 110f)
                            }
                        }
                    )
                }
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("student_row_${student.id}"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        // Line 1: Dedicated Full-Width Row for Avatar + Full Student Name (prevents name truncation on mobile)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudentAvatarCircle(
                student = student,
                blockAccent = blockAccent,
                size = 38.dp,
                onClick = clickPhotoDialog,
                testTag = "student_avatar_${student.id}",
                blockLabel = blockLabel
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = student.name,
                style = typography.titleMedium,
                color = colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = clickDetailsDialog)
            )
        }

        // Line 2: Organized Row Below Name for Student Number + Non-Wrapping Points Pill + Compact Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "رقم #${student.studentNumber}",
                    style = typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
                Box(
                    modifier = Modifier
                        .background(colorScheme.primaryContainer, PointsPillCornerShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$monthPoints نقطة",
                        style = typography.labelMedium,
                        color = colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag("student_points_${student.id}")
                    )
                }
            }

            // Reorder & Block Transfer & History Actions (lightweight Box buttons without clip or IconButton CompositionLocal overhead)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (canMoveUp) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = clickMoveUp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "تحريك لأعلى",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                if (canMoveDown) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = clickMoveDown)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "تحريك لأسفل",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = clickMoveDialog)
                        .testTag("move_student_button_${student.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                        contentDescription = "نقل لبلوك آخر",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = clickDetailsDialog)
                        .testTag("details_student_button_${student.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "سجل وتفاصيل الطفل",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Line 3: Compact Card Buttons (primitive enabledCardsMask + packedCardCounts -> skips 57 rows on single card tap!)
        CompactCardButtonsRow(
            studentId = student.id,
            enabledCardsMask = enabledCardsMask,
            packedCardCounts = packedCardCounts,
            tagPrefix = "card_btn",
            onCardClick = clickAwardCard
        )
        }
    }
}

@Composable
private fun QuickAddActionDialog(
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSelectAddSingleStudent: () -> Unit,
    onSelectBulkImport: () -> Unit,
    onSelectAddBlock: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إضافة للفصل",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSelectAddSingleStudent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("menu_add_single_student"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إضافة طفل واحد")
                }

                Button(
                    onClick = onSelectBulkImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("menu_bulk_import_students"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استيراد قائمة أسماء (حتى ١٥٠ للمجموعة)")
                }

                if (isAdmin) {
                    OutlinedButton(
                        onClick = onSelectAddBlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("menu_add_servant_block"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.ViewModule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إضافة بلوك خادم جديد")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSingleStudentDialog(
    blocks: List<ServantBlock>,
    autoNumber: Int,
    remainingCapacity: Int,
    allowCustomNumber: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, customNumber: Int?, blockId: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var numberText by rememberSaveable { mutableStateOf(autoNumber.toString()) }
    var selectedBlockId by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة طفل جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "الحد الأقصى للمجموعة: ١٥٠ اسم • المتاح للإضافة حالياً: $remainingCapacity اسم",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remainingCapacity > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الطفل ثلاثي أو ثنائي") },
                    enabled = remainingCapacity > 0,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_student_name_input")
                )

                if (allowCustomNumber) {
                    OutlinedTextField(
                        value = numberText,
                        onValueChange = { numberText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رقم الطفل") },
                        enabled = remainingCapacity > 0,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "الرقم المتسلسل الموحد في المجموعة: #$autoNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "البلوك (افتراضياً ينزل في 'بدون بلوك'):",
                    style = MaterialTheme.typography.labelMedium
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedBlockId.isEmpty()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.clickable { selectedBlockId = "" }
                    ) {
                        Text(
                            text = "بدون بلوك",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    blocks.forEach { blk ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedBlockId == blk.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.clickable { selectedBlockId = blk.id }
                        ) {
                            Text(
                                text = blk.servantName,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val num = numberText.toIntOrNull() ?: autoNumber
                    onConfirm(name, num, selectedBlockId)
                },
                enabled = name.trim().isNotBlank() && remainingCapacity > 0,
                modifier = Modifier.testTag("confirm_add_student_button")
            ) {
                Text("حفظ الطفل")
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
private fun BulkImportStudentsDialog(
    remainingCapacity: Int,
    currentStudentsCount: Int,
    onAnalyze: (String) -> List<BulkImportLinePreview>,
    onDismiss: () -> Unit,
    onConfirmImport: (List<String>) -> Unit
) {
    var rawText by rememberSaveable { mutableStateOf("") }
    var isPreviewMode by rememberSaveable { mutableStateOf(false) }
    var skipDuplicatesInPaste by rememberSaveable { mutableStateOf(true) }

    val previews = remember(rawText, isPreviewMode) {
        if (rawText.isBlank()) emptyList() else onAnalyze(rawText)
    }

    val warningsCount = previews.count { it.duplicateInPaste || it.existingSimilarStudentName != null }
    val finalNamesToImport = remember(previews, skipDuplicatesInPaste, remainingCapacity) {
        previews
            .filter { !skipDuplicatesInPaste || !it.duplicateInPaste }
            .map { it.rawName }
            .take(remainingCapacity.coerceAtLeast(0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (!isPreviewMode) "استيراد قائمة أسماء" else "معاينة الأسماء قبل الحفظ (${finalNamesToImport.size})",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (remainingCapacity > 0) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "العدد الحالي: $currentStudentsCount / 150 • المتاح للإضافة: $remainingCapacity اسم",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remainingCapacity > 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                if (!isPreviewMode) {
                    Text(
                        text = "الصق القائمة بحيث يكون كل اسم في سطر مستقل (الحد الأقصى ١٥٠ اسم للمجموعة الواحدة). ستنزل الأسماء في قسم 'بدون بلوك' بترقيم متسلسل موحد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("قائمة الأسماء (اسم في كل سطر)") },
                        enabled = remainingCapacity > 0,
                        minLines = 7,
                        maxLines = 12,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bulk_import_textarea")
                    )

                    val enteredLinesCount = rawText.lines().count { it.isNotBlank() }
                    Text(
                        text = if (enteredLinesCount > remainingCapacity) {
                            "عدد الأسطر المدخلة: $enteredLinesCount (سيتم استيراد أول $remainingCapacity اسم للوصول للحد الأقصى ١٥٠)"
                        } else {
                            "عدد الأسطر المدخلة: $enteredLinesCount"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enteredLinesCount > remainingCapacity) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                } else {
                    if (warningsCount > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "تنبيه تشابه",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تنبيه: تم اكتشاف $warningsCount اسم مكرر أو مشابه لأسماء موجودة بالفعل!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    previews.take(150).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${item.rawName}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (item.duplicateInPaste) {
                                Text(
                                    text = "مكرر بالقائمة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (item.existingSimilarStudentName != null) {
                                Text(
                                    text = "يشبه: ${item.existingSimilarStudentName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isPreviewMode) {
                Button(
                    onClick = { isPreviewMode = true },
                    enabled = remainingCapacity > 0 && rawText.lines().any { it.isNotBlank() },
                    modifier = Modifier.testTag("preview_bulk_import_button")
                ) {
                    Text("معاينة قبل الحفظ")
                }
            } else {
                Button(
                    onClick = { onConfirmImport(finalNamesToImport) },
                    enabled = finalNamesToImport.isNotEmpty() && remainingCapacity > 0,
                    modifier = Modifier.testTag("confirm_bulk_import_button")
                ) {
                    Text("تأكيد استيراد ${finalNamesToImport.size} اسم")
                }
            }
        },
        dismissButton = {
            if (isPreviewMode) {
                TextButton(onClick = { isPreviewMode = false }) {
                    Text("تعديل النص")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("إلغاء")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddServantBlockDialog(
    memberProfiles: List<ServantMemberProfile>,
    existingBlocks: List<ServantBlock>,
    resolvedMemberName: (ServantMemberProfile) -> String,
    onDismiss: () -> Unit,
    onConfirm: (servantName: String, colorHex: String, servantUid: String) -> Unit
) {
    var selectedMemberUid by rememberSaveable { mutableStateOf("") }
    var servantName by rememberSaveable { mutableStateOf("") }
    var selectedPreset by rememberSaveable { mutableStateOf(BlockColorPreset.BLUE) }
    var showMultiBlockConfirmWarning by remember { mutableStateOf(false) }

    val existingBlockOwnerUids = remember(existingBlocks) {
        existingBlocks.mapNotNull { it.servantId.takeIf { uid -> uid.isNotBlank() } }.toSet()
    }
    val selectedAlreadyHasBlock = selectedMemberUid.isNotBlank() && selectedMemberUid in existingBlockOwnerUids

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء بلوك خادم جديد (الأمين)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (memberProfiles.isNotEmpty()) {
                    Text(
                        text = "اختر الخادم من أعضاء المجموعة:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        memberProfiles.forEach { member ->
                            val uid = member.effectiveUid
                            val displayName = resolvedMemberName(member)
                            val isSelected = selectedMemberUid == uid
                            val alreadyOwns = uid in existingBlockOwnerUids
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMemberUid = uid
                                        servantName = displayName
                                        showMultiBlockConfirmWarning = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (alreadyOwns) {
                                        Text(
                                            text = "لديه بلوك",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = servantName,
                    onValueChange = {
                        servantName = it.take(80)
                        showMultiBlockConfirmWarning = false
                    },
                    label = { Text("اسم الخادم المسؤول عن البلوك") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_block_servant_name_input")
                )

                if (selectedAlreadyHasBlock || showMultiBlockConfirmWarning) {
                    Surface(
                        color = KamSemanticColors.NotificationCardConflict.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, KamSemanticColors.NotificationCardConflict)
                    ) {
                        Text(
                            text = "تنبيه: هذا الخادم لديه بلوك بالفعل. يمكنك استثناءه كأمين ومنحه بلوكاً إضافياً بالضغط على إنشاء.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Text(
                    text = "اختر لون البلوك:",
                    style = MaterialTheme.typography.labelLarge
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BlockColorPreset.entries.forEach { preset ->
                        val color = KamSemanticColors.blockColor(preset.hex)
                        val isSelected = selectedPreset == preset
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = color.copy(alpha = if (isSelected) 0.35f else 0.15f),
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, color),
                            modifier = Modifier.clickable { selectedPreset = preset }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = preset.arabicName,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedAlreadyHasBlock && !showMultiBlockConfirmWarning) {
                        showMultiBlockConfirmWarning = true
                    } else {
                        onConfirm(servantName.trim(), selectedPreset.hex, selectedMemberUid)
                    }
                },
                enabled = servantName.trim().isNotBlank(),
                modifier = Modifier.testTag("confirm_add_block_button")
            ) {
                Text(if (showMultiBlockConfirmWarning) "تأكيد ومنح بلوك إضافي" else "إنشاء البلوك")
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
private fun MoveStudentBlockDialog(
    student: Student,
    blocks: List<ServantBlock>,
    onDismiss: () -> Unit,
    onSelectBlock: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نقل '${student.name}' إلى بلوك") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                blocks.forEach { blk ->
                    val blkColor = KamSemanticColors.blockColor(blk.colorHex)
                    val isCurrent = student.blockId == blk.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) blkColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, blkColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectBlock(blk.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "بلوك ${blk.servantName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCurrent) {
                                Text(
                                    text = "الحالي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = blkColor
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (student.blockId.isBlank()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectBlock("") }
                ) {
                    Text(
                        text = "بدون بلوك (إلغاء التسكين)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(14.dp)
                    )
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
private fun QuickHomeCommentDialog(
    studentName: String,
    blockName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var commentText by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Comment,
                    contentDescription = null,
                    tint = KamSemanticColors.NotificationChildComment
                )
                Column {
                    Text(
                        text = "تعليق سريع — $studentName",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = blockName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "يُحفظ التعليق في ملف الطفل ويُرسل تلقائياً لجروب الخدّام بصورة الطفل واسمه وبلوكه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it.take(ChildNote.MAX_TEXT_LENGTH) },
                    placeholder = { Text("اكتب تعليقك عن الطفل...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_swipe_comment_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(commentText.trim()) },
                enabled = commentText.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KamSemanticColors.NotificationChildComment,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_home_swipe_comment_button")
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
