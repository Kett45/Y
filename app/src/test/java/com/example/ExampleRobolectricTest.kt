package com.example

import android.content.Context
import android.os.StrictMode
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.cache.LocalClassroomCache
import com.example.data.cache.LocalUserPreferences
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.Group
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.data.model.UserProfile
import com.example.data.sync.GitHubSmartSyncEngine
import com.example.ui.components.StudentAvatarImageHelper
import com.example.ui.components.StudentHoldQuickPeekRootOverlay
import com.example.ui.components.StudentQuickPeekRequest
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppAppearanceController
import com.example.ui.theme.KamLiteTheme
import com.example.ui.theme.UiScaleController
import com.example.ui.viewmodel.KamScreenState
import com.example.ui.viewmodel.PrecomputedClassroomIndices
import com.example.util.KamPerformanceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun `verify zero StrictMode violations and measure 58-student scroll and 5 card taps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KamPerformanceTracker.resetAllMetrics()

        // 1. Enable StrictMode on Main Thread and verify all startup / cache / avatar helpers produce 0 violations
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyListener(context.mainExecutor) { violation ->
                    KamPerformanceTracker.recordStrictModeViolation(violation)
                }
                .build()
        )

        val coldStartBeginNs = System.nanoTime()
        KamPerformanceTracker.markActivityOnCreate()
        LocalClassroomCache.init(context)
        AppAppearanceController.preloadAsync(context)
        UiScaleController.preloadAsync(context)
        LocalClassroomCache.preloadForUser(context, "servant_1")
        LocalClassroomCache.loadUserPreferences("servant_1")
        LocalClassroomCache.saveUserPreferences("servant_1", LocalUserPreferences())
        AppAppearanceController.loadFromContext(context)
        UiScaleController.loadFromContext(context)
        GitHubSmartSyncEngine.initializeFromLocalCache(context)
        StudentAvatarImageHelper.readLocalAlbumRaw(context, "stu_1")
        StudentAvatarImageHelper.writeLocalAlbumRaw(context, "stu_1", "")

        // Build 58 students across 4 blocks
        val blocks = (1..4).map { bIdx ->
            ServantBlock(
                id = "blk_$bIdx",
                groupId = "GRP1",
                servantId = "srv_$bIdx",
                servantName = "خادم $bIdx",
                colorHex = "#3B82F6",
                sortOrder = bIdx.toLong()
            )
        }
        val students = (1..58).map { sIdx ->
            val blkId = "blk_${((sIdx - 1) % 4) + 1}"
            Student(
                id = "stu_$sIdx",
                groupId = "GRP1",
                name = "طفل رقم $sIdx",
                studentNumber = sIdx.toLong(),
                blockId = blkId,
                sortOrder = sIdx.toLong(),
                position = sIdx.toDouble()
            )
        }
        val profile = UserProfile(
            id = "servant_1",
            userId = "servant_1",
            displayName = "أمين الخدمة",
            groupId = "GRP1"
        )
        val group = Group(
            id = "GRP1",
            inviteCode = "GRP1",
            name = "فصل القديس أبانوب",
            adminId = "servant_1",
            adminName = "أمين الخدمة",
            memberUids = listOf("servant_1")
        )

        fun buildScreenState(events: List<CardEvent>): KamScreenState {
            val precomputed = PrecomputedClassroomIndices(
                userProfile = profile,
                students = students,
                cardEvents = events,
                monthlyBalances = emptyList(),
                selectedWeekKey = "2026-11-W2",
                dismissedDuplicateNames = emptySet()
            ).also { it.prewarmAll() }
            return KamScreenState(
                isLoading = false,
                userProfile = profile,
                group = group,
                isAdmin = true,
                blocks = blocks,
                students = students,
                cardEvents = events,
                selectedMonthKey = "2026-11",
                selectedWeekKey = "2026-11-W2",
                currentWeekKey = "2026-11-W2",
                precomputedIndices = precomputed
            )
        }

        var currentEvents by mutableStateOf<List<CardEvent>>(emptyList())
        var uiState by mutableStateOf(buildScreenState(emptyList()))

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            KamLiteTheme {
                HomeScreen(
                    state = uiState,
                    currentUserId = "servant_1",
                    onSearchQueryChange = {},
                    onMonthOffset = {},
                    onAwardCard = { stu, cardType ->
                        val startTapNs = System.nanoTime()
                        val newEv = CardEvent(
                            id = "ev_${currentEvents.size + 1}",
                            groupId = "GRP1",
                            studentId = stu.id,
                            studentName = stu.name,
                            cardType = cardType.id,
                            points = cardType.points.toLong(),
                            servantId = "servant_1",
                            servantName = "أمين الخدمة",
                            monthKey = "2026-11",
                            weekKey = "2026-11-W2"
                        )
                        val updated = currentEvents + newEv
                        currentEvents = updated
                        uiState = buildScreenState(updated)
                        val tapElapsedMs = (System.nanoTime() - startTapNs) / 1_000_000.0
                        KamPerformanceTracker.recordOptimisticCardTapLatency(stu.name, cardType.arabicTitle, tapElapsedMs)
                    },
                    onUndoCardEvent = {},
                    onAddSingleStudent = { _, _, _ -> },
                    onAnalyzeBulkImport = { emptyList() },
                    onConfirmBulkImport = {},
                    onCreateBlock = { _, _ -> },
                    onMoveStudentToBlock = { _, _ -> },
                    onReorderStudent = { _, _ -> },
                    onUpdateStudent = { _, _, _, _ -> },
                    onDeleteStudent = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val coldStartTotalMs = (System.nanoTime() - coldStartBeginNs) / 1_000_000.0

        // 2. Measure scrolling across all 58 students in HomeScreen LazyColumn
        KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_HOME_SCROLL, scrolling = true)
        val scrollFrameTimesMs = mutableListOf<Double>()
        val scrollIndices = listOf(5, 12, 20, 28, 36, 44, 52, 60, 45, 30, 15, 0)
        for (targetIndex in scrollIndices) {
            val frameStartNs = System.nanoTime()
            composeTestRule.onNodeWithTag("home_lazy_column").performScrollToIndex(targetIndex)
            composeTestRule.waitForIdle()
            val frameDurationNs = System.nanoTime() - frameStartNs
            KamPerformanceTracker.recordFrameDuration(frameDurationNs)
            scrollFrameTimesMs.add(frameDurationNs / 1_000_000.0)
        }
        KamPerformanceTracker.setInteractionContext(KamPerformanceTracker.CTX_HOME_IDLE, scrolling = false)

        // 3. Measure 5 card taps and count exact student row recompositions per tap
        val recompositionsBeforeTaps = KamPerformanceTracker.metrics.value.let {
            KamPerformanceTracker.publishFrameMetricsToState()
            KamPerformanceTracker.metrics.value.studentRowRecompositionCount
        }

        val c0 = CardType.entries[0].id
        val c1 = CardType.entries[1].id
        val c2 = CardType.entries[2].id
        val tappedTags = listOf(
            "card_btn_stu_1_$c0",
            "card_btn_stu_1_$c1",
            "card_btn_stu_1_$c2",
            "card_btn_stu_5_$c0",
            "card_btn_stu_5_$c1"
        )
        val tapFrameTimesMs = mutableListOf<Double>()
        for (tag in tappedTags) {
            val tapStartNs = System.nanoTime()
            composeTestRule.onNodeWithTag(tag).performClick()
            composeTestRule.waitForIdle()
            val tapDurationNs = System.nanoTime() - tapStartNs
            KamPerformanceTracker.recordFrameDuration(tapDurationNs)
            tapFrameTimesMs.add(tapDurationNs / 1_000_000.0)
        }

        KamPerformanceTracker.publishFrameMetricsToState()
        val finalMetrics = KamPerformanceTracker.metrics.value
        val recompositionsDuring5Taps = finalMetrics.studentRowRecompositionCount - recompositionsBeforeTaps
        val avgScrollMs = scrollFrameTimesMs.average()
        val worstScrollMs = scrollFrameTimesMs.maxOrNull() ?: 0.0
        val avgTapFrameMs = tapFrameTimesMs.average()

        println(
            "KAM_BENCHMARK_RESULT: " +
                "strictModeViolations=${finalMetrics.strictModeViolationsCount}, " +
                "coldStartMs=${String.format(java.util.Locale.US, "%.1f", coldStartTotalMs)}, " +
                "scrollSteps=${scrollFrameTimesMs.size}, " +
                "avgScrollStepMs=${String.format(java.util.Locale.US, "%.2f", avgScrollMs)}, " +
                "worstScrollStepMs=${String.format(java.util.Locale.US, "%.2f", worstScrollMs)}, " +
                "cardTapAvgMs=${String.format(java.util.Locale.US, "%.2f", finalMetrics.avgCardTapMs)}, " +
                "avgTapFrameMs=${String.format(java.util.Locale.US, "%.2f", avgTapFrameMs)}, " +
                "recompositionsDuring5Taps=$recompositionsDuring5Taps " +
                "(perTap=${recompositionsDuring5Taps / 5.0})"
        )

        assertEquals("Expected 0 StrictMode violations on Main Thread", 0, finalMetrics.strictModeViolationsCount)
        // Each single-student card tap should recompose ONLY that 1 student row (5 taps = 5 row recompositions, not 5 * 58 = 290!)
        assertTrue(
            "Expected <= 5 student row recompositions for 5 card taps, but got $recompositionsDuring5Taps",
            recompositionsDuring5Taps <= 5L
        )
    }

    @Test
    fun `root quick peek overlay renders centered metadata and compact hint without duplicate circle`() {
        val sampleStudent = Student(
            id = "stu_peek",
            groupId = "GRP1",
            name = "مينا سامح كامل",
            studentNumber = 14L,
            blockId = "blk_1"
        )
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            KamLiteTheme {
                StudentHoldQuickPeekRootOverlay(
                    request = StudentQuickPeekRequest(
                        student = sampleStudent,
                        blockLabel = "بلوك مينا",
                        blockAccent = Color(0xFF3B82F6),
                        fullPhotoBitmap = null,
                        isLoadingFullPhoto = false
                    )
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("student_quick_peek_popup_stu_peek").assertIsDisplayed()
        composeTestRule.onNodeWithTag("student_quick_peek_name_stu_peek").assertIsDisplayed()
        composeTestRule.onNodeWithTag("student_quick_peek_block_stu_peek").assertIsDisplayed()
        composeTestRule.onNodeWithTag("student_quick_peek_number_stu_peek").assertIsDisplayed()
        composeTestRule.onNodeWithText("مينا سامح كامل").assertIsDisplayed()
        composeTestRule.onNodeWithText("بلوك مينا").assertIsDisplayed()
        composeTestRule.onNodeWithText("رقم #14").assertIsDisplayed()
        composeTestRule.onNodeWithText("ارفع إصبعك لإغلاق المعاينة").assertIsDisplayed()
    }

    @Test
    fun `admin toggling enforceWeeklyCardLimit switch saves immediately without reverting`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalClassroomCache.init(context)
        AppAppearanceController.loadFromContext(context)
        UiScaleController.loadFromContext(context)

        val adminProfile = UserProfile(
            id = "admin_1",
            userId = "admin_1",
            displayName = "الأمين",
            groupId = "GRP1"
        )
        val initialGroup = Group(
            id = "GRP1",
            inviteCode = "GRP1",
            name = "فصل الاختبار",
            adminId = "admin_1",
            adminName = "الأمين",
            memberUids = listOf("admin_1"),
            enforceWeeklyCardLimit = false
        )
        var savedWeeklyLimit: Boolean? = null
        var uiState by mutableStateOf(
            KamScreenState(
                isLoading = false,
                userProfile = adminProfile,
                group = initialGroup,
                isAdmin = true
            )
        )

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            KamLiteTheme {
                SettingsScreen(
                    state = uiState,
                    onToggleCardVisibility = { _, _ -> },
                    onSaveGroupPolicies = { allowUndo, enforceWeekly, maxPts, attOnly, showLb, autoNum ->
                        savedWeeklyLimit = enforceWeekly
                        uiState = uiState.copy(
                            group = uiState.group?.copy(
                                allowServantUndo = allowUndo,
                                enforceWeeklyCardLimit = enforceWeekly,
                                maxMonthlyPoints = maxPts,
                                attendanceIsCardOnly = attOnly,
                                showLeaderboard = showLb,
                                autoStudentNumber = autoNum
                            )
                        )
                    },
                    onLeaveGroup = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("switch_policy_enforce_weekly_limit"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_policy_enforce_weekly_limit")
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, savedWeeklyLimit)
        assertEquals(true, uiState.group?.enforceWeeklyCardLimit)
    }

    @Test
    fun `performance optimizations default diagnostics off reuse duplicate check and preclip circular bitmap`() {
        val prevMode = KamPerformanceTracker.isLiveDiagnosticsEnabled
        try {
            KamPerformanceTracker.setLiveDiagnosticsEnabled(false)
            assertEquals(false, KamPerformanceTracker.isLiveDiagnosticsEnabled)

            var hookCalls = 0
            KamPerformanceTracker.onDiagnosticsModeChanged = { hookCalls++ }
            KamPerformanceTracker.setLiveDiagnosticsEnabled(true)
            assertEquals(true, KamPerformanceTracker.isLiveDiagnosticsEnabled)
            assertEquals(1, hookCalls)
            KamPerformanceTracker.setLiveDiagnosticsEnabled(false)
            assertEquals(2, hookCalls)
        } finally {
            KamPerformanceTracker.onDiagnosticsModeChanged = null
            KamPerformanceTracker.setLiveDiagnosticsEnabled(prevMode)
        }

        // Verify PrecomputedClassroomIndices reuses duplicate map when students and dismissed set are unchanged
        val sampleStudents = listOf(
            Student(id = "s1", name = "مينا سامح", studentNumber = 1L),
            Student(id = "s2", name = "مينا سامح", studentNumber = 2L)
        )
        val first = PrecomputedClassroomIndices(
            userProfile = null,
            students = sampleStudents,
            cardEvents = emptyList(),
            monthlyBalances = emptyList(),
            selectedWeekKey = "2026-11-W2",
            dismissedDuplicateNames = emptySet()
        )
        val reused = PrecomputedClassroomIndices(
            userProfile = null,
            students = sampleStudents,
            cardEvents = emptyList(),
            monthlyBalances = emptyList(),
            selectedWeekKey = "2026-11-W2",
            dismissedDuplicateNames = emptySet(),
            reusedDuplicatesByName = first.duplicateStudentsByName
        )
        assertTrue(first.duplicateStudentsByName === reused.duplicateStudentsByName)
        assertEquals(1, reused.duplicateStudentsByName.size)

        // Verify renderFramedAvatarToBitmap(clipCircle = true) produces 100x100 ARGB_8888 bitmap with alpha channel
        val src = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }
        val circular = StudentAvatarImageHelper.renderFramedAvatarToBitmap(
            source = src,
            frame = com.example.ui.components.PhotoCropFrame(),
            targetSizePx = 100,
            clipCircle = true
        )
        assertEquals(100, circular.width)
        assertEquals(100, circular.height)
        assertEquals(android.graphics.Bitmap.Config.ARGB_8888, circular.config)
        assertTrue(circular.hasAlpha())
    }

    @Test
    fun `flattened settings scroll, bounded avatar helper cache, and scroll-aligned ViewConfiguration work correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalClassroomCache.init(context)
        AppAppearanceController.loadFromContext(context)
        UiScaleController.loadFromContext(context)

        // 1. Verify root KamLiteTheme preserves standard system longPressTimeoutMillis (>= 300ms, NOT global 210ms)
        var capturedLongPressMs = 0L
        val checkActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        checkActivity.setContent {
            KamLiteTheme {
                capturedLongPressMs = androidx.compose.ui.platform.LocalViewConfiguration.current.longPressTimeoutMillis
            }
        }
        composeTestRule.waitForIdle()
        assertTrue(
            "Expected root KamLiteTheme longPressTimeoutMillis >= 300ms, got $capturedLongPressMs",
            capturedLongPressMs >= 300L
        )

        // 2. Verify localPackedAvatarByStudentId LruCache(24) bounds entries and scroll gate toggles cleanly
        StudentAvatarImageHelper.setScrollActive(true)
        assertTrue(StudentAvatarImageHelper.isScrollActive)
        StudentAvatarImageHelper.setScrollActive(false)
        assertEquals(false, StudentAvatarImageHelper.isScrollActive)
        for (i in 1..30) {
            StudentAvatarImageHelper.writeLocalAlbumRaw(null, "cap_test_$i", "dummy_$i")
        }
        assertEquals("", StudentAvatarImageHelper.peekCachedLocalAlbumRaw("cap_test_1"))
        assertEquals("dummy_30", StudentAvatarImageHelper.peekCachedLocalAlbumRaw("cap_test_30"))

        // 3. Verify flattened SettingsScreen scrolls down to bottom performance & sign-out items with all tags intact
        val adminProfile = UserProfile(
            id = "admin_flat",
            userId = "admin_flat",
            displayName = "الأمين",
            groupId = "GRP_FLAT"
        )
        val group = Group(
            id = "GRP_FLAT",
            inviteCode = "GRP_FLAT",
            name = "فصل السلاسة",
            adminId = "admin_flat",
            adminName = "الأمين",
            memberUids = listOf("admin_flat")
        )
        val blocks = (1..3).map { i ->
            ServantBlock(id = "blk_$i", groupId = "GRP_FLAT", servantId = "srv_$i", servantName = "خادم $i")
        }
        val state = KamScreenState(
            isLoading = false,
            userProfile = adminProfile,
            group = group,
            isAdmin = true,
            blocks = blocks
        )
        val settingsActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        settingsActivity.setContent {
            KamLiteTheme {
                SettingsScreen(
                    state = state,
                    onToggleCardVisibility = { _, _ -> },
                    onSaveGroupPolicies = { _, _, _, _, _, _ -> },
                    onLeaveGroup = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("github_smart_sync_settings_card"))
        composeTestRule.onNodeWithTag("github_smart_sync_settings_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("performance_metrics_settings_card"))
        composeTestRule.onNodeWithTag("performance_metrics_settings_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_sign_out_button"))
        composeTestRule.onNodeWithTag("settings_sign_out_button").assertIsDisplayed()
    }
}



