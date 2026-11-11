package com.example

import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.SaturdayCalendarHelper
import com.example.data.model.Student
import com.example.ui.viewmodel.KamScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

import com.example.data.model.Group
import com.example.data.model.ServantBlock
import com.example.ui.viewmodel.KamViewModel
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

class ExampleUnitTest {

    @Test
    fun groupDailyInviteCode_zeroCollisionAcrossGroups_and24HourExpirationAndBannedList() {
        val nowMillis = 1_762_000_000_000L
        val groupA = Group(
            id = "KAMHX3RK",
            name = "فصل مارمينا",
            inviteCode = "KAMHX3RK",
            dailyInviteToken = "482",
            inviteCodeUpdatedAtMillis = nowMillis - (2 * 60 * 60 * 1000L), // 2 hours ago
            bannedUids = listOf("uid_banned_1"),
            bannedServantEntries = listOf("uid_banned_1|أ. هاني")
        )
        // Even if groupB happens to roll the exact same daily token "482", its group prefix guarantees 0% collision
        val groupB = Group(
            id = "KAM99ZZ1",
            name = "فصل الأنبا أنطونيوس",
            inviteCode = "KAM99ZZ1",
            dailyInviteToken = "482",
            inviteCodeUpdatedAtMillis = nowMillis - (25 * 60 * 60 * 1000L) // 25 hours ago -> expired!
        )

        assertEquals("KAMHX3RK-482", groupA.fullDailyInviteCode)
        assertEquals("KAM99ZZ1-482", groupB.fullDailyInviteCode)
        assertNotEquals(groupA.fullDailyInviteCode, groupB.fullDailyInviteCode)

        // Parsing both hyphenated and non-hyphenated inputs routes to the exact unique group prefix
        assertEquals("KAMHX3RK" to "482", Group.parseInviteCodeInput(" kamhx3rk-482 "))
        assertEquals("KAMHX3RK" to "482", Group.parseInviteCodeInput("KAMHX3RK482"))
        assertEquals("KAM99ZZ1" to "482", Group.parseInviteCodeInput("KAM99ZZ1-482"))

        // 24-hour validity check
        assertFalse(groupA.isDailyCodeExpired(nowMillis))
        assertEquals(22, groupA.remainingDailyCodeHours(nowMillis))

        assertTrue(groupB.isDailyCodeExpired(nowMillis))
        assertEquals(0, groupB.remainingDailyCodeHours(nowMillis))

        // Banned servants list parsing
        assertEquals(1, groupA.bannedServantsList.size)
        assertEquals("uid_banned_1", groupA.bannedServantsList.first().uid)
        assertEquals("أ. هاني", groupA.bannedServantsList.first().name)
    }

    @Test
    fun saturdayCalendarHelper_startsFromNovember2026WithCopticMonthsWeeksAndTransitionBadge() {
        // Sunday Nov 1, 2026
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 1, 10, 0, 0)
        }
        val tree2026 = SaturdayCalendarHelper.buildYearTree(2026, nowCal)
        // Only November (11) and December (12) exist in 2026; older months 1..10 are removed
        assertEquals(2, tree2026.months.size)
        assertEquals(11, tree2026.months[0].monthNumber)
        assertEquals(12, tree2026.months[1].monthNumber)

        val november = tree2026.months[0]
        assertTrue(november.isCurrentMonth)
        assertFalse(november.isFutureMonth)
        assertTrue(november.copticMonthsLabel.contains("بابه"))
        assertTrue(november.copticMonthsLabel.contains("هاتور"))
        assertTrue(november.weeks.isNotEmpty())

        // In November 2026: Nov 1 is Sunday .. Nov 6 is Friday (Week 1), Nov 7 is Saturday .. Nov 13 is Friday (Week 2)
        assertEquals(1, november.weeks[0].startDayOfMonth)
        assertEquals(6, november.weeks[0].endDayOfMonth)
        assertNull(november.weeks[0].copticTransitionBadge)

        // Week 2 (Nov 7..13) contains Nov 10 = 1 Hathor -> has a Coptic Month Transition Badge!
        val week2 = november.weeks[1]
        assertEquals(7, week2.startDayOfMonth)
        assertEquals(13, week2.endDayOfMonth)
        assertNotNull(week2.copticTransitionBadge)
        assertTrue(week2.copticTransitionBadge!!.contains("هاتور"))

        // December 2026 is a future month relative to Nov 1, 2026 -> locked & empty weeks
        val december = tree2026.months[1]
        assertTrue(december.isFutureMonth)
        assertTrue(december.weeks.isEmpty())
    }

    @Test
    fun computeSequentiallyNumberedStudents_recomputesGlobalOneToNOrderWhenStudentMoves() {
        val blockA = ServantBlock(id = "blk_a", servantName = "أ. مينا", sortOrder = 0)
        val blockB = ServantBlock(id = "blk_b", servantName = "أ. كيرلس", sortOrder = 1)
        val blocks = listOf(blockA, blockB)

        val s1 = Student(id = "s1", name = "أبانوب", blockId = "blk_a", position = 1000.0, studentNumber = 99)
        val s2 = Student(id = "s2", name = "بيشوي", blockId = "blk_a", position = 2000.0, studentNumber = 42)
        val s3 = Student(id = "s3", name = "جرجس", blockId = "blk_b", position = 1000.0, studentNumber = 15)
        val s4 = Student(id = "s4", name = "دانيال", blockId = "", position = 1000.0, studentNumber = 7)

        val initialOrdered = KamViewModel.computeSequentiallyNumberedStudents(
            rawStudents = listOf(s4, s3, s2, s1),
            blocks = blocks
        )
        assertEquals(listOf("s1", "s2", "s3", "s4"), initialOrdered.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), initialOrdered.map { it.studentNumber })

        // Now move s2 above s1 inside blockA (s2 position = 500.0, s1 position = 1000.0)
        val reordered = KamViewModel.computeSequentiallyNumberedStudents(
            rawStudents = listOf(
                s1.copy(position = 1000.0),
                s2.copy(position = 500.0),
                s3,
                s4
            ),
            blocks = blocks
        )
        assertEquals(listOf("s2", "s1", "s3", "s4"), reordered.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), reordered.map { it.studentNumber })
    }

    @Test
    fun duplicateAlerts_detectsSameStudentSameCardTwoDifferentServantsInSameWeek() {
        val student = Student(id = "stu_1", name = "مينا جرجس", studentNumber = 1)
        val events = listOf(
            CardEvent(
                id = "evt_1",
                studentId = "stu_1",
                studentName = "مينا جرجس",
                cardType = CardType.ATTENDANCE.id,
                points = 3,
                servantId = "srv_1",
                servantName = "أ. أبانوب",
                monthKey = "2026-10",
                weekKey = "2026-10-W5"
            ),
            CardEvent(
                id = "evt_2",
                studentId = "stu_1",
                studentName = "مينا جرجس",
                cardType = CardType.ATTENDANCE.id,
                points = 3,
                servantId = "srv_2",
                servantName = "أ. مايكل",
                monthKey = "2026-10",
                weekKey = "2026-10-W5"
            ),
            // Different card type -> should NOT trigger duplicate alert
            CardEvent(
                id = "evt_3",
                studentId = "stu_1",
                studentName = "مينا جرجس",
                cardType = CardType.QUIET.id,
                points = 5,
                servantId = "srv_1",
                servantName = "أ. أبانوب",
                monthKey = "2026-10",
                weekKey = "2026-10-W5"
            )
        )

        val state = KamScreenState(
            students = listOf(student),
            cardEvents = events,
            selectedMonthKey = "2026-10",
            selectedWeekKey = "2026-10-W5"
        )

        assertEquals(1, state.duplicateAlerts.size)
        val alert = state.duplicateAlerts.first()
        assertEquals(
            "الطفل مينا جرجس أخد كارت الحضور مرتين: أ. أبانوب وأ. مايكل",
            alert.formattedMessage
        )
    }

    @Test
    fun formatCompactHeaderMonthWeek_formatsShortMonthYearAndWeekFor56dpHeader() {
        val compactWeekLabel = SaturdayCalendarHelper.formatCompactHeaderMonthWeek(
            monthKey = "2026-10",
            selectedWeekKey = "2026-10-W5"
        )
        assertEquals("أكتوبر ٢٦ • أسبوع ٥", compactWeekLabel)

        val compactAllWeeksLabel = SaturdayCalendarHelper.formatCompactHeaderMonthWeek(
            monthKey = "2026-10",
            selectedWeekKey = null
        )
        assertEquals("أكتوبر ٢٦ • الكل", compactAllWeeksLabel)
    }

    @Test
    fun cardCountForStudent_drivesSymbolToPointsTransformationOnCardButton() {
        val student = Student(
            id = "stu_10",
            name = "كيرلس سامح",
            studentNumber = 10L,
            avatarBase64 = "base64_sample_thumb"
        )
        val initialState = KamScreenState(
            students = listOf(student),
            cardEvents = emptyList(),
            selectedMonthKey = "2026-10",
            selectedWeekKey = "2026-10-W5"
        )
        // Before tapping: count is 0 -> button displays symbol only
        assertEquals(0, initialState.cardCountForStudent("stu_10", CardType.ATTENDANCE))

        // After tapping Attendance (3 pts): count is 1 -> button transforms to points number (3)
        val recordedEvent = CardEvent(
            id = "evt_stu_10_ATTENDANCE_2026-10-W5_srv_1",
            studentId = "stu_10",
            studentName = "كيرلس سامح",
            cardType = CardType.ATTENDANCE.id,
            points = CardType.ATTENDANCE.points.toLong(),
            servantId = "srv_1",
            servantName = "أ. أبانوب",
            monthKey = "2026-10",
            weekKey = "2026-10-W5"
        )
        val awardedState = initialState.copy(cardEvents = listOf(recordedEvent))
        assertEquals(1, awardedState.cardCountForStudent("stu_10", CardType.ATTENDANCE))
        assertEquals(3, CardType.ATTENDANCE.points * awardedState.cardCountForStudent("stu_10", CardType.ATTENDANCE))
    }

    @Test
    fun appearancePresets_providesTwoThemesDarkAndSunlightLightAndSingleStarIcon() {
        val allThemes = com.example.ui.theme.KamAppThemePreset.entries
        assertEquals(2, allThemes.size)
        assertEquals(
            listOf(
                com.example.ui.theme.KamAppThemePreset.DARK,
                com.example.ui.theme.KamAppThemePreset.LIGHT
            ),
            allThemes
        )
        assertTrue(com.example.ui.theme.KamAppThemePreset.DARK.isDark)
        assertFalse(com.example.ui.theme.KamAppThemePreset.LIGHT.isDark)
        // Legacy theme IDs safely resolve to DARK or LIGHT
        assertEquals(
            com.example.ui.theme.KamAppThemePreset.LIGHT,
            com.example.ui.theme.KamAppThemePreset.fromId("MORNING_PEARL")
        )
        assertEquals(
            com.example.ui.theme.KamAppThemePreset.DARK,
            com.example.ui.theme.KamAppThemePreset.fromId("ROYAL_DARK")
        )

        val allIcons = com.example.ui.theme.KamLauncherIconPreset.entries
        assertEquals(1, allIcons.size)
        assertEquals(com.example.ui.theme.KamLauncherIconPreset.BETHLEHEM_STAR, allIcons.first())
    }

    @Test
    fun studentPhotoAlbum_preservesOldPhotosWhenAddingNewPhotoAndPromotesToMain() {
        val helper = com.example.ui.components.StudentAvatarImageHelper
        val oldPhoto = "base64_old_photo_1"
        val newPhoto = "base64_new_photo_2"
        val extraAlbumPhoto = "base64_album_photo_3"

        // 1. Adding a new main photo on top of an old photo keeps BOTH photos (old photo moves to album)
        val afterSecondMain = helper.addPhotoPreservingOldAlbum(
            context = null,
            studentId = "stu_album_1",
            currentRawAvatarField = oldPhoto,
            newPhotoBase64 = newPhoto,
            setAsMain = true
        )
        assertEquals(newPhoto, helper.extractMainPhotoBase64(afterSecondMain))
        assertEquals(listOf(newPhoto, oldPhoto), helper.extractAllPhotosFromField(afterSecondMain))

        // 2. Adding an extra photo directly to the album keeps current main photo first and appends to album
        val afterExtraAlbum = helper.addPhotoPreservingOldAlbum(
            context = null,
            studentId = "stu_album_1",
            currentRawAvatarField = afterSecondMain,
            newPhotoBase64 = extraAlbumPhoto,
            setAsMain = false
        )
        assertEquals(newPhoto, helper.extractMainPhotoBase64(afterExtraAlbum))
        assertEquals(listOf(newPhoto, extraAlbumPhoto, oldPhoto), helper.extractAllPhotosFromField(afterExtraAlbum))

        // 3. Promoting the old photo back to main keeps all 3 photos in the album
        val afterPromoteOld = helper.promoteAlbumPhotoToMain(
            context = null,
            studentId = "stu_album_1",
            currentRawAvatarField = afterExtraAlbum,
            targetPhotoBase64 = oldPhoto
        )
        assertEquals(oldPhoto, helper.extractMainPhotoBase64(afterPromoteOld))
        assertEquals(listOf(oldPhoto, newPhoto, extraAlbumPhoto), helper.extractAllPhotosFromField(afterPromoteOld))

        // 4. Updating Facebook-style circular framing (#crop=zoom,panX,panY,rot) preserves the full uncropped photo
        val newFrame = com.example.ui.components.PhotoCropFrame(
            zoom = 1.85f,
            panXFraction = -0.25f,
            panYFraction = 0.40f,
            rotationDegrees = 90
        )
        val afterReframe = helper.updatePhotoFramingInAlbum(
            context = null,
            studentId = "stu_album_1",
            currentRawAvatarField = afterPromoteOld,
            targetPhotoEntry = oldPhoto,
            newFrame = newFrame,
            promoteToMain = true
        )
        val (parsedRaw, parsedFrame) = helper.parsePhotoEntry(helper.extractMainPhotoBase64(afterReframe))
        assertEquals(oldPhoto, parsedRaw)
        assertEquals(1.85f, parsedFrame.zoom, 0.01f)
        assertEquals(-0.25f, parsedFrame.panXFraction, 0.01f)
        assertEquals(0.40f, parsedFrame.panYFraction, 0.01f)
        assertEquals(90, parsedFrame.rotationDegrees)
        // Still keeps all 3 distinct underlying photos without duplicating oldPhoto
        assertEquals(3, helper.extractAllPhotosFromField(afterReframe).size)

        // 5. Fit-full-photo zoom scales rectangular photo inside the circular window (< 1.0f)
        val fitZoom = helper.computeFitFullPhotoZoom(width = 1200, height = 600, rotationDegrees = 0)
        assertTrue(fitZoom < 0.6f && fitZoom >= 0.35f)
    }

    @Test
    fun fullClassLeaderboard_ranksAllStudentsWithPerCardBreakdownAcrossSingleMonthMultiMonthAndWeek() {
        // Create 7 students (> 5) including weak and absent (0-point) students
        val students = (1..7).map { i ->
            Student(id = "stu_$i", name = "طالب $i", studentNumber = i.toLong())
        }

        val allEvents = listOf(
            // Month 10 (2026-10), Week 1: stu_1 gets Attendance (3) + Quiet (5) = 8 pts
            CardEvent(id = "e1", studentId = "stu_1", cardType = CardType.ATTENDANCE.id, points = 3, monthKey = "2026-10", weekKey = "2026-10-W1"),
            CardEvent(id = "e2", studentId = "stu_1", cardType = CardType.QUIET.id, points = 5, monthKey = "2026-10", weekKey = "2026-10-W1"),
            // Month 10 (2026-10), Week 2: stu_2 gets Praise (10) + Attendance (3) = 13 pts
            CardEvent(id = "e3", studentId = "stu_2", cardType = CardType.PRAISE.id, points = 10, monthKey = "2026-10", weekKey = "2026-10-W2"),
            CardEvent(id = "e4", studentId = "stu_2", cardType = CardType.ATTENDANCE.id, points = 3, monthKey = "2026-10", weekKey = "2026-10-W2"),
            // Month 11 (2026-11), Week 1: stu_1 gets Gold (20) + Attendance (3) = 23 pts
            CardEvent(id = "e5", studentId = "stu_1", cardType = CardType.GOLD.id, points = 20, monthKey = "2026-11", weekKey = "2026-11-W1"),
            CardEvent(id = "e6", studentId = "stu_1", cardType = CardType.ATTENDANCE.id, points = 3, monthKey = "2026-11", weekKey = "2026-11-W1"),
            // Month 12 (2026-12), Week 1: stu_3 gets Participation (2) but NO attendance = 2 pts
            CardEvent(id = "e7", studentId = "stu_3", cardType = CardType.PARTICIPATION.id, points = 2, monthKey = "2026-12", weekKey = "2026-12-W1")
        )

        val state = KamScreenState(
            students = students,
            allGroupCardEvents = allEvents,
            selectedMonthKey = "2026-10"
        )

        // 1. Default Single Month (Month 10 only): includes all 7 students, sums ONLY 2026-10
        val month10Entries = state.computeClassLeaderboardEntries(selectedMonths = setOf("2026-10"))
        assertEquals(7, month10Entries.size) // Not limited to Top 5!
        assertEquals("stu_2", month10Entries[0].student.id)
        assertEquals(13L, month10Entries[0].totalPoints)
        assertEquals("stu_1", month10Entries[1].student.id)
        assertEquals(8L, month10Entries[1].totalPoints)
        // Students 3..7 have 0 points in Month 10 and are included at the bottom so servants spot weak/absent students
        assertEquals(0L, month10Entries.last().totalPoints)
        assertEquals(0, month10Entries.last().attendanceCount)

        // 2. Single Month 11 (2026-11 only): stu_1 is #1 with 23 pts, stu_2 has 0 pts in Month 11
        val month11Entries = state.computeClassLeaderboardEntries(selectedMonths = setOf("2026-11"))
        assertEquals("stu_1", month11Entries[0].student.id)
        assertEquals(23L, month11Entries[0].totalPoints)
        assertEquals(0L, month11Entries.first { it.student.id == "stu_2" }.totalPoints)

        // 3. Multi-Month Aggregation (Months 10 + 11 + 12 combined): stu_1 has 8 + 23 = 31 pts (2 Attendance = 6 pts)
        val multiMonthEntries = state.computeClassLeaderboardEntries(
            selectedMonths = setOf("2026-10", "2026-11", "2026-12")
        )
        val stu1Multi = multiMonthEntries.first { it.student.id == "stu_1" }
        assertEquals(1, stu1Multi.rank)
        assertEquals(31L, stu1Multi.totalPoints)
        assertEquals(2, stu1Multi.attendanceCount)
        assertEquals(6L, stu1Multi.attendancePoints)
        val stu1Quiet = stu1Multi.breakdownByCard.first { it.cardType == CardType.QUIET }
        assertEquals(1, stu1Quiet.count)
        assertEquals(5L, stu1Quiet.points)

        // 4. Single-Week Mode (2026-10-W1 only): stu_1 has 8 pts, stu_2 has 0 pts in W1
        val week1Entries = state.computeClassLeaderboardEntries(
            selectedMonths = setOf("2026-10"),
            selectedWeekOverride = "2026-10-W1"
        )
        assertEquals("stu_1", week1Entries[0].student.id)
        assertEquals(8L, week1Entries[0].totalPoints)
        assertEquals(0L, week1Entries.first { it.student.id == "stu_2" }.totalPoints)
    }

    @Test
    fun githubSmartSyncEngine_appliesDeltaOnlyWhenGapIsSmall_andSwitchesToFullInstallWhenVersionGapIsLarge() {
        val engine = com.example.data.sync.GitHubSmartSyncEngine

        // 1. Small/incremental update (installed = 2, remote = 4, gap = 2 <= maxDeltaGap 3) -> DeltaOnlySync (no full APK download!)
        val smallGapManifest = com.example.data.sync.GitHubVersionManifest(
            versionCode = 4,
            versionName = "1.2.4",
            minDeltaBaseVersionCode = 1,
            maxDeltaGap = 3,
            requiresFullInstall = false,
            commitSha = "abc1234"
        )
        val deltaDecision = engine.evaluateSyncStrategy(
            installedBinaryVersionCode = 2,
            lastSyncedDeltaVersionCode = 2,
            lastSyncedCommitSha = "old_sha",
            remoteManifest = smallGapManifest
        )
        assertTrue(deltaDecision is com.example.data.sync.GitHubSyncDecision.DeltaOnlySync)

        // 2. Already synced with remote version and commit -> UpToDate
        val upToDateDecision = engine.evaluateSyncStrategy(
            installedBinaryVersionCode = 2,
            lastSyncedDeltaVersionCode = 4,
            lastSyncedCommitSha = "abc1234",
            remoteManifest = smallGapManifest
        )
        assertTrue(upToDateDecision is com.example.data.sync.GitHubSyncDecision.UpToDate)

        // 3. Large Version Gap Scenario (installed = 2, remote = 9, gap = 7 > maxDeltaGap 3) -> FullPackageUpgradeRequired
        val largeGapManifest = com.example.data.sync.GitHubVersionManifest(
            versionCode = 9,
            versionName = "1.2.9",
            minDeltaBaseVersionCode = 6,
            maxDeltaGap = 3,
            requiresFullInstall = false,
            commitSha = "def5678"
        )
        val fullFallbackDecision = engine.evaluateSyncStrategy(
            installedBinaryVersionCode = 2,
            lastSyncedDeltaVersionCode = 4,
            lastSyncedCommitSha = "abc1234",
            remoteManifest = largeGapManifest
        )
        assertTrue(fullFallbackDecision is com.example.data.sync.GitHubSyncDecision.FullPackageUpgradeRequired)
        val fullReq = fullFallbackDecision as com.example.data.sync.GitHubSyncDecision.FullPackageUpgradeRequired
        assertEquals(7, fullReq.versionGap)
        assertTrue(fullReq.reasonArabic.isNotBlank())

        // 4. Normalization to the single permanent auto-updating GitHub APK link
        val refreshedDirect = engine.sanitizeAndRefreshApkUrl(
            url = "https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk?v=1.5.0",
            fallbackUrl = engine.defaultDirectApkUrl("Kett45/Y"),
            activeVersionName = "1.6"
        )
        assertEquals("https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk", refreshedDirect)

        // 5. Hidden cache-buster appends ?cb=timestamp for network calls while keeping visible link clean
        val cacheBusted = engine.appendHiddenCacheBuster(refreshedDirect, timestampMillis = 1762800000123L)
        assertEquals("https://github.com/Kett45/Y/raw/main/releases/kAM-Lite.apk?cb=1762800000123", cacheBusted)

        // 6. Dual check (versionCode + apkSha256): detects modified APK even when versionCode is unchanged (6 == 6)
        val sameVersionModifiedHashManifest = com.example.data.sync.GitHubVersionManifest(
            versionCode = 6,
            versionName = "1.6",
            requiresFullInstall = true,
            commitSha = "998877665544",
            apkSha256 = "99887766554433221100ffeeddccbbaa99887766554433221100ffeeddccbbaa"
        )
        assertTrue(
            engine.hasNewerOrModifiedApkBinary(
                installedVersionCode = 6,
                installedApkSha256 = "5ab9b8ab54841f9ff32c269e8dc1f64cba091a5dcc1ed3152e08bf35026a5e7f",
                lastSyncedApkSha256 = "5ab9b8ab54841f9ff32c269e8dc1f64cba091a5dcc1ed3152e08bf35026a5e7f",
                remoteManifest = sameVersionModifiedHashManifest
            )
        )
        assertFalse(
            engine.hasNewerOrModifiedApkBinary(
                installedVersionCode = 6,
                installedApkSha256 = "99887766554433221100ffeeddccbbaa99887766554433221100ffeeddccbbaa",
                lastSyncedApkSha256 = "99887766554433221100ffeeddccbbaa99887766554433221100ffeeddccbbaa",
                remoteManifest = sameVersionModifiedHashManifest
            )
        )
    }
}
