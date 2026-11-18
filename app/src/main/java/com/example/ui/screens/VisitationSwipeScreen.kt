package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChildNote
import com.example.data.model.ChildNoteType
import com.example.data.model.Student
import com.example.data.model.VisitationStatus
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.theme.KamBackgroundDark
import com.example.ui.theme.KamPrimaryContainerDark
import com.example.ui.theme.KamSemanticColors
import com.example.ui.theme.KamSurfaceDark
import com.example.ui.theme.LocalKamExtendedColors
import com.example.ui.viewmodel.KamScreenState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class VisitationSwipeDecision(
    val student: Student,
    val status: VisitationStatus,
    val noteText: String
)

@Composable
fun VisitationSwipeScreen(
    state: KamScreenState,
    currentUserId: String,
    onRecordVisitation: (student: Student, status: VisitationStatus, noteText: String, notifyGroup: Boolean) -> Unit,
    onDeleteNote: (ChildNote) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Filter to children belonging to the current servant's block(s),
    // with an optional block selector for the Admin so the Admin can inspect any block or all blocks.
    val ownBlocks = remember(state.blocks, currentUserId) {
        state.blocks.filter { it.servantId == currentUserId }
    }
    var selectedAdminBlockFilter by rememberSaveable(state.isAdmin) {
        mutableStateOf("OWN") // "OWN", "ALL", or specific block.id
    }

    val activeBlocks = remember(state.blocks, ownBlocks, state.isAdmin, selectedAdminBlockFilter) {
        if (!state.isAdmin) {
            ownBlocks
        } else {
            when (selectedAdminBlockFilter) {
                "ALL" -> state.blocks
                "OWN" -> if (ownBlocks.isNotEmpty()) ownBlocks else state.blocks
                else -> state.blocks.filter { it.id == selectedAdminBlockFilter }.ifEmpty {
                    if (ownBlocks.isNotEmpty()) ownBlocks else state.blocks
                }
            }
        }
    }

    val activeBlockIds = remember(activeBlocks) {
        activeBlocks.map { it.id }.toSet()
    }
    val blockStudents = remember(state.students, activeBlockIds) {
        state.students
            .filter { it.blockId.isNotBlank() && it.blockId in activeBlockIds }
            .sortedBy { it.studentNumber }
    }

    var currentIndex by rememberSaveable(activeBlockIds.toString(), blockStudents.size) {
        mutableIntStateOf(0)
    }
    val sessionDecisions = remember { mutableStateListOf<VisitationSwipeDecision>() }

    // Preload ONLY visible child + next 1 child (Smart Local Images Rule)
    val upcomingStudents = remember(blockStudents, currentIndex) {
        if (currentIndex in blockStudents.indices) {
            blockStudents.subList(currentIndex, minOf(currentIndex + 2, blockStudents.size))
        } else {
            emptyList()
        }
    }
    LaunchedEffect(upcomingStudents) {
        if (upcomingStudents.isNotEmpty()) {
            StudentAvatarImageHelper.preloadNextAttendancePhotosAsync(
                context = context,
                upcomingStudents = upcomingStudents,
                count = 2
            )
        }
    }

    var noteDialogStudent by remember { mutableStateOf<Student?>(null) }
    var noteDraftText by remember { mutableStateOf("") }
    var noteSelectedStatus by remember { mutableStateOf(VisitationStatus.VISITED) }

    val visitationNotes = remember(state.childNotes, blockStudents) {
        val ownStudentIds = blockStudents.map { it.id }.toSet()
        state.childNotes.filter { note ->
            note.noteType == ChildNoteType.VISITATION && note.studentId in ownStudentIds
        }
    }

    val visitedCount = sessionDecisions.count { it.status == VisitationStatus.VISITED }
    val followupCount = sessionDecisions.count { it.status == VisitationStatus.FOLLOWUP }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    fun handleVisitationDecision(
        student: Student,
        status: VisitationStatus,
        noteText: String
    ) {
        val defaultText = when (status) {
            VisitationStatus.VISITED -> "تم افتقاده والاطمئنان عليه ✓"
            VisitationStatus.FOLLOWUP -> "يحتاج متابعة وافتقاد خلال الأسبوع 🔔"
        }
        val finalNote = noteText.trim().ifBlank { defaultText }
        sessionDecisions.add(VisitationSwipeDecision(student, status, finalNote))
        onRecordVisitation(student, status, finalNote, true)
        currentIndex = (currentIndex + 1).coerceAtMost(blockStudents.size)
    }

    val extColors = LocalKamExtendedColors.current
    val visitationAccent = KamSemanticColors.NotificationVisitation

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("visitation_swipe_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Compact Top Header + Live Counters Row combined to give maximum vertical screen space to the photo card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extColors.hairline),
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(visitationAccent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = visitationAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "الافتقاد الذكي بالسحب",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                val blockTitles = activeBlocks.joinToString(" • ") { it.servantName }
                                Text(
                                    text = if (blockTitles.isNotBlank()) "البلوك: $blockTitles" else "خاص بأطفال بلوكك فقط",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Compact inline counters
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = visitationAccent.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${blockStudents.size} طفل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = visitationAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = KamSemanticColors.onlineIndicator.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "✓ $visitedCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KamSemanticColors.onlineIndicator,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = KamSemanticColors.offlineIndicator.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "🔔 $followupCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KamSemanticColors.offlineIndicator,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            if (sessionDecisions.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        if (sessionDecisions.isNotEmpty() && currentIndex > 0) {
                                            sessionDecisions.removeAt(sessionDecisions.lastIndex)
                                            currentIndex = (currentIndex - 1).coerceAtLeast(0)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("visitation_undo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "تراجع",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Admin quick block filter strip so the Admin can inspect any servant's block
                    if (state.isAdmin && state.blocks.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (ownBlocks.isNotEmpty()) {
                                val isOwnSelected = selectedAdminBlockFilter == "OWN"
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isOwnSelected) visitationAccent else visitationAccent.copy(alpha = 0.10f),
                                    modifier = Modifier.clickable {
                                        selectedAdminBlockFilter = "OWN"
                                        currentIndex = 0
                                    }
                                ) {
                                    Text(
                                        text = "بلوكي",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOwnSelected) Color.White else visitationAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            val isAllSelected = selectedAdminBlockFilter == "ALL" || (selectedAdminBlockFilter == "OWN" && ownBlocks.isEmpty())
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isAllSelected) visitationAccent else visitationAccent.copy(alpha = 0.10f),
                                modifier = Modifier.clickable {
                                    selectedAdminBlockFilter = "ALL"
                                    currentIndex = 0
                                }
                            ) {
                                Text(
                                    text = "كل البلوكات",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAllSelected) Color.White else visitationAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            state.blocks.forEach { blk ->
                                val isSelected = selectedAdminBlockFilter == blk.id
                                val blkColor = KamSemanticColors.blockColor(blk.colorHex)
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isSelected) blkColor else blkColor.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable {
                                        selectedAdminBlockFilter = blk.id
                                        currentIndex = 0
                                    }
                                ) {
                                    Text(
                                        text = blk.servantName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else blkColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (activeBlocks.isEmpty()) {
                // No block assigned to current servant
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("visitation_empty_no_block"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, extColors.hairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = visitationAccent,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا يوجد بلوك مربوط بحسابك حالياً",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "شاشة الافتقاد تعرض أطفال بلوكك فقط لسرعة المتابعة والتركيز. اطلب من الأمين ربط حسابك بالبلوك الخاص بك من تبويب «المجموعة».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (blockStudents.isEmpty()) {
                // Servant has block(s), but no children assigned yet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("visitation_empty_no_students"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, extColors.hairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = visitationAccent,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا يوجد أطفال مسجلين في هذا البلوك بعد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "أضف أطفالاً إلى البلوك من الشاشة الرئيسية ليظهروا هنا فوراً في قائمة الافتقاد الذكي.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (currentIndex < blockStudents.size) {
                val currentStudent = blockStudents[currentIndex]
                val studentBlock = state.blocks.firstOrNull { it.id == currentStudent.blockId }
                val studentBlockColor = KamSemanticColors.blockColor(studentBlock?.colorHex ?: "#8B5CF6")
                val progress = ((currentIndex + 1).toFloat() / blockStudents.size.toFloat()).coerceIn(0f, 1f)

                var largePhoto by remember(currentStudent.id, currentStudent.effectiveAvatarKey) {
                    mutableStateOf(StudentAvatarImageHelper.getCachedLargeAttendancePhoto(currentStudent))
                }
                LaunchedEffect(currentStudent.id, currentStudent.effectiveAvatarKey) {
                    if (largePhoto == null && (currentStudent.effectiveHasAvatar || currentStudent.avatarBase64.isNotBlank())) {
                        largePhoto = StudentAvatarImageHelper.loadLargeAttendancePhotoBitmap(context, currentStudent)
                    }
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = visitationAccent
                )

                val tintOverlay by animateColorAsState(
                    targetValue = when {
                        offsetX.value > 90f -> KamSemanticColors.onlineIndicator.copy(alpha = 0.28f)
                        offsetX.value < -90f -> KamSemanticColors.offlineIndicator.copy(alpha = 0.28f)
                        offsetY.value > 90f -> visitationAccent.copy(alpha = 0.28f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(120),
                    label = "visitationSwipeTint"
                )

                // Full-Bleed Edge-to-Edge Photo Swipe Card filling the screen
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .offset {
                            IntOffset(
                                offsetX.value.roundToInt(),
                                offsetY.value.roundToInt()
                            )
                        }
                        .graphicsLayer {
                            rotationZ = (offsetX.value / 28f).coerceIn(-12f, 12f)
                        }
                        .pointerInput(currentStudent.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val dx = offsetX.value
                                    val dy = offsetY.value
                                    scope.launch {
                                        when {
                                            dx > 160f -> {
                                                offsetX.animateTo(900f, tween(160))
                                                offsetX.snapTo(0f)
                                                offsetY.snapTo(0f)
                                                handleVisitationDecision(
                                                    student = currentStudent,
                                                    status = VisitationStatus.VISITED,
                                                    noteText = ""
                                                )
                                            }
                                            dx < -160f -> {
                                                offsetX.animateTo(-900f, tween(160))
                                                offsetX.snapTo(0f)
                                                offsetY.snapTo(0f)
                                                handleVisitationDecision(
                                                    student = currentStudent,
                                                    status = VisitationStatus.FOLLOWUP,
                                                    noteText = ""
                                                )
                                            }
                                            dy > 140f && abs(dy) > abs(dx) -> {
                                                offsetX.animateTo(0f, tween(140))
                                                offsetY.animateTo(0f, tween(140))
                                                noteDialogStudent = currentStudent
                                                noteDraftText = ""
                                                noteSelectedStatus = VisitationStatus.VISITED
                                            }
                                            else -> {
                                                offsetX.animateTo(0f, tween(150))
                                                offsetY.animateTo(0f, tween(150))
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch {
                                        offsetX.animateTo(0f, tween(150))
                                        offsetY.animateTo(0f, tween(150))
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
                        .testTag("visitation_active_student_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KamSurfaceDark
                    ),
                    border = BorderStroke(1.dp, extColors.hairline),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Full-screen edge-to-edge child photo filling 100% of the card
                        val bmp = largePhoto
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = currentStudent.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Full-bleed artistic gradient backdrop when child has no photo yet
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                studentBlockColor.copy(alpha = 0.85f),
                                                KamPrimaryContainerDark,
                                                KamBackgroundDark
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentStudent.avatarInitials,
                                        fontSize = 64.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // 2. Top gradient scrim for crisp badge readability over bright photos
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.68f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // 3. Bottom gradient scrim for child name, memorization, and last visitation note
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.65f),
                                            Color.Black.copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )

                        // 4. Live Swipe Tint Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(tintOverlay)
                        )

                        // 5. Live Swipe Direction Stamp in Center
                        val swipeStamp = when {
                            offsetX.value > 95f -> Triple("تم افتقاده ✓", KamSemanticColors.onlineIndicator, true)
                            offsetX.value < -95f -> Triple("محتاج متابعة 🔔", KamSemanticColors.offlineIndicator, true)
                            offsetY.value > 95f -> Triple("ملاحظة افتقاد 📝", visitationAccent, true)
                            else -> Triple("", Color.Transparent, false)
                        }
                        if (swipeStamp.third) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = swipeStamp.second.copy(alpha = 0.92f),
                                border = BorderStroke(2.dp, Color.White),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = swipeStamp.first,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                                )
                            }
                        }

                        // 6. Top Overlay Bar (Block name, Child index, Points)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "بلوك ${studentBlock?.servantName ?: "بلوكي"} • ${currentIndex + 1}/${blockStudents.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 6.dp
                                    )
                                )
                            }

                            val studentPts = state.pointsForStudentInSelectedMonth(currentStudent.id)
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Black.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "#${currentStudent.studentNumber} • $studentPts نقطة",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 6.dp
                                    )
                                )
                            }
                        }

                        // 7. Bottom Overlay Info (Child Name, Last Memorization, Last Visitation Note, Swipe Hint)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentStudent.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (currentStudent.lastMemorizedText.isNotBlank()) {
                                Text(
                                    text = "📖 آخر تسميع: ${currentStudent.lastMemorizedText}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.92f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val lastChildVisitation = remember(visitationNotes, currentStudent.id) {
                                visitationNotes.firstOrNull { it.studentId == currentStudent.id }
                            }
                            if (lastChildVisitation != null) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "آخر افتقاد مسجل:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extColors.notifyVisitation,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = lastChildVisitation.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "اسحب يميناً (تم افتقاده ✓) • لأسفل (ملاحظة 📝) • يساراً (متابعة 🔔)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // 1-Handed Bottom Action Bar (Right = Visited, Center = Note, Left = Needs Follow-up)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            handleVisitationDecision(
                                student = currentStudent,
                                status = VisitationStatus.VISITED,
                                noteText = ""
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KamSemanticColors.onlineIndicator,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("visitation_btn_visited")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تم افتقاده ✓", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            noteDialogStudent = currentStudent
                            noteDraftText = ""
                            noteSelectedStatus = VisitationStatus.VISITED
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("visitation_btn_note")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ملاحظة 📝", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            handleVisitationDecision(
                                student = currentStudent,
                                status = VisitationStatus.FOLLOWUP,
                                noteText = ""
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KamSemanticColors.offlineIndicator,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("visitation_btn_followup")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("متابعة 🔔", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Completed Visitation Session Summary + History
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("visitation_session_summary_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, extColors.hairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "اكتملت جولة الافتقاد لأطفال بلوكك!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "تم إرسال إشعارات الافتقاد (اللون البنفسجي) لجروب الخدام تلقائياً.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    sessionDecisions.clear()
                                    currentIndex = 0
                                },
                                modifier = Modifier.testTag("visitation_restart_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("بدء جولة جديدة")
                            }
                        }

                        HorizontalDivider()

                        Text(
                            text = "سجل الافتقاد الأخير لأطفال بلوكك (${visitationNotes.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (visitationNotes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "لا توجد ملاحظات افتقاد سابقة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val dateFormatter = remember {
                                SimpleDateFormat("dd/MM hh:mm a", Locale("ar"))
                            }
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(visitationNotes, key = { it.id }) { note ->
                                    val visStatus = VisitationStatus.fromId(note.status)
                                    val statusColor = if (visStatus == VisitationStatus.FOLLOWUP) {
                                        KamSemanticColors.offlineIndicator
                                    } else {
                                        KamSemanticColors.onlineIndicator
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = 1.dp,
                                                color = statusColor.copy(alpha = 0.45f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = note.studentName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(999.dp),
                                                        color = statusColor.copy(alpha = 0.16f)
                                                    ) {
                                                        Text(
                                                            text = if (visStatus == VisitationStatus.FOLLOWUP) {
                                                                "محتاج متابعة 🔔"
                                                            } else {
                                                                "تم افتقاده ✓"
                                                            },
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = statusColor,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(
                                                                horizontal = 8.dp,
                                                                vertical = 2.dp
                                                            )
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = note.text,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Text(
                                                    text = "${note.authorName} • ${dateFormatter.format(Date(note.effectiveTimeMillis))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (state.isAdmin || note.authorUid == currentUserId) {
                                                IconButton(
                                                    onClick = { onDeleteNote(note) },
                                                    modifier = Modifier.testTag("delete_visitation_note_${note.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "حذف الملاحظة",
                                                        tint = MaterialTheme.colorScheme.error
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
    }

    // Visitation Custom Note Dialog (Down Swipe / Note Button)
    val targetStudent = noteDialogStudent
    if (targetStudent != null) {
        AlertDialog(
            onDismissRequest = { noteDialogStudent = null },
            title = {
                Text(
                    text = "ملاحظة افتقاد — ${targetStudent.name}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isVisited = noteSelectedStatus == VisitationStatus.VISITED
                        FilterStatusButton(
                            text = "تم افتقاده ✓",
                            selected = isVisited,
                            color = KamSemanticColors.onlineIndicator,
                            onClick = { noteSelectedStatus = VisitationStatus.VISITED },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_visitation_status_visited")
                        )
                        FilterStatusButton(
                            text = "محتاج متابعة 🔔",
                            selected = !isVisited,
                            color = KamSemanticColors.offlineIndicator,
                            onClick = { noteSelectedStatus = VisitationStatus.FOLLOWUP },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dialog_visitation_status_followup")
                        )
                    }
                    OutlinedTextField(
                        value = noteDraftText,
                        onValueChange = { noteDraftText = it },
                        label = { Text("اكتب تفاصيل الافتقاد أو سبب المتابعة") },
                        singleLine = false,
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_visitation_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        handleVisitationDecision(
                            student = targetStudent,
                            status = noteSelectedStatus,
                            noteText = noteDraftText
                        )
                        noteDialogStudent = null
                    },
                    modifier = Modifier.testTag("dialog_visitation_save_button")
                ) {
                    Text("حفظ وإرسال للجروب")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogStudent = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun VisitationCounterChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
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
private fun FilterStatusButton(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
        )
    }
}
