package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CardType(
    val id: String,
    val arabicTitle: String,
    val points: Int,
    val badgeSymbol: String,
    val colorKey: String
) {
    ATTENDANCE("ATTENDANCE", "الحضور", 3, "✓", "EMERALD"),
    QUIET("QUIET", "الهدوء", 5, "🤫", "SKY"),
    PARTICIPATION("PARTICIPATION", "المشاركة", 2, "✋", "VIOLET"),
    PRAISE("PRAISE", "التسبحة", 10, "🎵", "INDIGO"),
    GOLD("GOLD", "الذهب", 20, "⭐", "GOLD"),
    MEMORIZATION("MEMORIZATION", "المحفوظات", 6, "📖", "TEAL"),
    CONFESSION("CONFESSION", "الاعتراف", 4, "🕊️", "ROSE");

    companion object {
        fun fromId(id: String): CardType? = entries.find { it.id == id }
        val defaultEnabledIds: List<String> = entries.map { it.id }
    }
}

enum class BlockColorPreset(val hex: String, val arabicName: String) {
    BLUE("#3B82F6", "أزرق سماوي"),
    EMERALD("#10B981", "أخضر زمردي"),
    VIOLET("#8B5CF6", "بنفسجي"),
    AMBER("#F59E0B", "ذهبي كهرماني"),
    ROSE("#F43F5E", "وردي ياقوتي"),
    CYAN("#06B6D4", "فيروزي"),
    ORANGE("#F97316", "برتقالي");

    companion object {
        fun fromHex(hex: String): BlockColorPreset =
            entries.find { it.hex.equals(hex, ignoreCase = true) } ?: BLUE
    }
}

@Immutable
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val groupId: String = "",
    val joinedGroupIds: List<String> = emptyList(),
    val enabledCards: List<String> = CardType.defaultEnabledIds,
    val collapsedBlockIds: List<String> = emptyList(),
    val hiddenBlockIds: List<String> = emptyList(),
    val compactViewMode: Boolean = false,
    val themeId: String = "DARK",
    val uiScaleMode: String = "AUTO",
    val uiScaleFactor: Double = 0.85,
    val showFloatingAddButton: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
data class BannedServantInfo(
    val uid: String,
    val name: String
)

@Immutable
@IgnoreExtraProperties
data class Group(
    @DocumentId val id: String = "",
    val inviteCode: String = "",
    val name: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val memberUids: List<String> = emptyList(),
    val allowServantUndo: Boolean = true,
    val enforceWeeklyCardLimit: Boolean = false,
    val maxMonthlyPoints: Long = 0L,
    val attendanceIsCardOnly: Boolean = true,
    val showLeaderboard: Boolean = true,
    val autoStudentNumber: Boolean = true,
    val bannedUids: List<String> = emptyList(),
    val bannedServantEntries: List<String> = emptyList(),
    val dailyInviteToken: String = "",
    val inviteCodeUpdatedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveDailyToken: String
        get() = dailyInviteToken.ifBlank {
            computeFallbackDailyToken(inviteCode, inviteCodeUpdatedAtMillis)
        }

    /**
     * Full collision-free daily invite code: `<GroupPrefix>-<DailyToken>` (e.g. `KAMHX3RK-482`).
     * Because `<GroupPrefix>` (`inviteCode`) is the unique Firestore document ID of the group,
     * two different groups can NEVER collide even if their daily 3-digit tokens happen to match.
     */
    val fullDailyInviteCode: String
        get() = if (inviteCode.isBlank()) "" else "$inviteCode-$effectiveDailyToken"

    fun isDailyCodeExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (dailyInviteToken.isBlank() || inviteCodeUpdatedAtMillis <= 0L) return true
        return (nowMillis - inviteCodeUpdatedAtMillis) >= DAILY_INVITE_VALIDITY_MILLIS
    }

    fun remainingDailyCodeHours(nowMillis: Long = System.currentTimeMillis()): Int {
        if (inviteCodeUpdatedAtMillis <= 0L) return 24
        val elapsed = (nowMillis - inviteCodeUpdatedAtMillis).coerceAtLeast(0L)
        val remainingMs = (DAILY_INVITE_VALIDITY_MILLIS - elapsed).coerceAtLeast(0L)
        return kotlin.math.ceil(remainingMs.toDouble() / 3_600_000.0).toInt().coerceIn(0, 24)
    }

    val bannedServantsList: List<BannedServantInfo>
        get() {
            val entriesMap = bannedServantEntries.mapNotNull { entry ->
                val parts = entry.split("|", limit = 2)
                val uid = parts.getOrNull(0)?.trim().orEmpty()
                val name = parts.getOrNull(1)?.trim()?.ifBlank { "خادم محظور" } ?: "خادم محظور"
                if (uid.isNotBlank()) uid to name else null
            }.toMap()
            return bannedUids.distinct().map { uid ->
                BannedServantInfo(
                    uid = uid,
                    name = entriesMap[uid] ?: "خادم محظور"
                )
            }
        }

    companion object {
        const val DAILY_INVITE_VALIDITY_MILLIS = 86_400_000L
        private val INVITE_CODE_CLEAN_REGEX = Regex("[^A-Z0-9\\-]")

        fun computeFallbackDailyToken(groupId: String, timestampMillis: Long): String {
            val dayBucket = if (timestampMillis > 0L) {
                timestampMillis / DAILY_INVITE_VALIDITY_MILLIS
            } else {
                System.currentTimeMillis() / DAILY_INVITE_VALIDITY_MILLIS
            }
            val seed = (groupId.uppercase().hashCode().toLong() xor dayBucket) and 0x7FFFFFFFL
            val num = ((seed % 900L) + 100L).toInt()
            return num.toString()
        }

        fun parseInviteCodeInput(rawCode: String): Pair<String, String> {
            val cleaned = rawCode.trim().uppercase().replace(INVITE_CODE_CLEAN_REGEX, "")
            if (cleaned.contains("-")) {
                val prefix = cleaned.substringBefore("-").trim()
                val suffix = cleaned.substringAfter("-").replace("-", "").trim()
                return prefix to suffix
            }
            if (cleaned.length > 8 && cleaned.startsWith("KAM")) {
                return cleaned.take(8) to cleaned.drop(8)
            }
            return cleaned to ""
        }
    }
}

@Immutable
@IgnoreExtraProperties
data class Invitation(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val adminId: String = "",
    val inviteeName: String = "",
    val inviteCode: String = "",
    val status: String = "PENDING",
    val acceptedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@IgnoreExtraProperties
data class ServantBlock(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val servantId: String = "",
    val servantName: String = "",
    val colorHex: String = BlockColorPreset.BLUE.hex,
    val sortOrder: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val studentNumber: Long = 1L,
    val blockId: String = "",
    val sortOrder: Long = 0L,
    val position: Double = 0.0,
    val avatarInitials: String = "ط",
    val avatarBase64: String = "",
    val hasAvatar: Boolean = false,
    val avatarHash: String = "",
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectivePosition: Double
        get() = if (position != 0.0) position else (sortOrder.toDouble() * 1000.0 + studentNumber.toDouble())

    val effectiveHasAvatar: Boolean
        get() = hasAvatar || avatarHash.isNotBlank() || avatarBase64.isNotBlank()

    val effectiveAvatarKey: String
        get() = when {
            avatarHash.isNotBlank() -> avatarHash
            avatarBase64.isNotBlank() -> computeAvatarHash(avatarBase64)
            else -> ""
        }

    companion object {
        fun computeAvatarHash(rawAvatarBase64: String): String {
            val trimmed = rawAvatarBase64.trim()
            if (trimmed.isEmpty()) return ""
            val primary = trimmed.substringBefore("|||")
            val h1 = primary.hashCode().toUInt().toString(16)
            val h2 = trimmed.length.toString(16)
            return "${h1}_$h2"
        }
    }
}

@Immutable
@IgnoreExtraProperties
data class StudentAvatarDoc(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val studentId: String = "",
    val avatarBase64: String = "",
    val avatarHash: String = "",
    val updatedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
@IgnoreExtraProperties
data class CardEvent(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val cardType: String = CardType.ATTENDANCE.id,
    val points: Long = 3L,
    val servantId: String = "",
    val servantName: String = "",
    val monthKey: String = "",
    val weekKey: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun hasValidCreatedAt(): Boolean = createdAt != null && createdAt.seconds > 0L

    /**
     * Returns the exact event timestamp in milliseconds from [createdAt], or falls back
     * deterministically to [weekKey] / [monthKey] if an older historical event lacks a valid [createdAt].
     */
    fun effectiveTimestampMillis(): Long {
        val ts = createdAt
        if (ts != null && ts.seconds > 0L) {
            return ts.toDate().time
        }
        return ServiceCalendarHelper.computeFallbackTimestampMillis(monthKey = monthKey, weekKey = weekKey)
    }
}

@Immutable
@IgnoreExtraProperties
data class MonthlyBalance(
    @DocumentId val id: String = "",
    val groupId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val monthKey: String = "",
    val totalPoints: Long = 0L,
    val eventsCount: Long = 0L,
    val lastUpdatedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object ArabicInitialsHelper {
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun extractInitials(fullName: String): String {
        val parts = fullName.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "ط"
        val first = parts[0].firstOrNull()?.toString() ?: "ط"
        val second = if (parts.size > 1) parts[1].firstOrNull()?.toString() ?: "" else ""
        return if (second.isNotEmpty()) "$first $second" else first
    }

    fun normalizeArabicName(raw: String): String {
        return raw.trim()
            .replace(WHITESPACE_REGEX, " ")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .lowercase()
    }
}

@Immutable
data class DuplicateStudentNameGroup(
    val normalizedName: String,
    val displayName: String,
    val records: List<Student>
)

@Immutable
data class DuplicateCardAlert(
    val id: String,
    val studentId: String,
    val studentName: String,
    val cardType: CardType,
    val weekKey: String,
    val servantNames: List<String>,
    val events: List<CardEvent>
) {
    val formattedMessage: String
        get() = "الطفل $studentName أخد كارت ${cardType.arabicTitle} مرتين: ${servantNames.joinToString(" و")}"
}

const val MAX_STUDENTS_PER_GROUP = 150

@Immutable
data class CopticDate(
    val year: Int,
    val month: Int, // 1..13
    val day: Int,   // 1..30 (or 1..5/6 in Pi Kogi Enavot)
    val monthName: String,
    val weekInMonth: Int // 1..5
)

@Immutable
data class WeekTreeNode(
    val weekKey: String,
    val monthKey: String,
    val weekNumberInMonth: Int,
    val startDayOfMonth: Int,
    val endDayOfMonth: Int,
    val arabicTitle: String,
    val dateRangeLabel: String,
    val isCurrentWeek: Boolean,
    val isFutureWeek: Boolean,
    val copticWeekLabel: String = "",
    val copticDateRangeLabel: String = "",
    val copticTransitionBadge: String? = null
)

@Immutable
data class MonthTreeNode(
    val year: Int,
    val monthNumber: Int, // 1..12
    val monthKey: String, // YYYY-MM
    val arabicMonthName: String,
    val isCurrentMonth: Boolean,
    val isFutureMonth: Boolean,
    val weeks: List<WeekTreeNode>,
    val copticMonthsLabel: String = ""
)

@Immutable
data class YearTreeNode(
    val year: Int,
    val arabicYearLabel: String,
    val months: List<MonthTreeNode>
)

/**
 * Local device calendar helper where:
 * - The first active month of the app is Month 11 (November 2026), and subsequent months/years are generated automatically.
 * - Each week strictly starts on Saturday at 12:00 AM midnight (00:00:00).
 * - Computes exact Coptic Calendar (تقويم الشهداء) dates, Coptic months, Coptic weeks, and month-transition badges 100% offline.
 */
object SaturdayCalendarHelper {

    const val APP_START_YEAR = 2026
    const val APP_START_MONTH = 11 // November

    private val arabicMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private val copticMonths = listOf(
        "توت", "بابه", "هاتور", "كيهك", "طوبة", "أمشير",
        "برمهات", "برموده", "بشنس", "بؤونة", "أبيب", "مسرى", "النسيء"
    )

    private val arabicWeekNames = listOf(
        "الأسبوع الأول",
        "الأسبوع الثاني",
        "الأسبوع الثالث",
        "الأسبوع الرابع",
        "الأسبوع الخامس",
        "الأسبوع السادس"
    )

    private val arabicOrdinals = listOf(
        "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس"
    )

    fun toArabicDigits(value: Int): String {
        val arabicChars = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return value.toString().map { ch ->
            if (ch in '0'..'9') arabicChars[ch - '0'] else ch
        }.joinToString("")
    }

    fun getArabicMonthName(monthNumber: Int): String {
        return arabicMonths.getOrElse((monthNumber - 1).coerceIn(0, 11)) { "شهر" }
    }

    fun getCopticMonthName(copticMonthNumber: Int): String {
        return copticMonths.getOrElse((copticMonthNumber - 1).coerceIn(0, 12)) { "قبطي" }
    }

    fun getArabicOrdinal(number: Int): String {
        return arabicOrdinals.getOrElse((number - 1).coerceIn(0, arabicOrdinals.lastIndex)) {
            toArabicDigits(number)
        }
    }

    /**
     * Exact astronomical conversion from Gregorian (year, month 1..12, day 1..31)
     * to Coptic Martyrs Calendar (Anno Martyrum - ش) via Julian Day Number (JDN).
     */
    fun toCopticDate(gregorianYear: Int, gregorianMonth: Int, gregorianDay: Int): CopticDate {
        val a = (14 - gregorianMonth) / 12
        val y = gregorianYear + 4800 - a
        val m = gregorianMonth + 12 * a - 3
        val jdn = gregorianDay + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045

        // Coptic Epoch (1 Thout 1 AM = August 29, 284 Julian = JDN 1825030)
        val daysSinceCopticEpoch = jdn - 1825030
        val copticYear = (4 * daysSinceCopticEpoch + 3) / 1461 + 1
        val dayOfCopticYear = ((4 * daysSinceCopticEpoch + 3) % 1461) / 4 // 0..365
        val copticMonth = (dayOfCopticYear / 30) + 1 // 1..13
        val copticDay = (dayOfCopticYear % 30) + 1   // 1..30
        val weekInCopticMonth = ((copticDay - 1) / 7) + 1 // 1..5
        return CopticDate(
            year = copticYear,
            month = copticMonth,
            day = copticDay,
            monthName = getCopticMonthName(copticMonth),
            weekInMonth = weekInCopticMonth
        )
    }

    fun currentYearMonth(nowCal: java.util.Calendar = java.util.Calendar.getInstance()): String {
        val year = nowCal.get(java.util.Calendar.YEAR)
        val month = nowCal.get(java.util.Calendar.MONTH) + 1
        if (year < APP_START_YEAR || (year == APP_START_YEAR && month < APP_START_MONTH)) {
            return String.format(java.util.Locale.US, "%04d-%02d", APP_START_YEAR, APP_START_MONTH)
        }
        return String.format(java.util.Locale.US, "%04d-%02d", year, month)
    }

    /**
     * Clamps any historical or legacy month key to strictly never precede November 2026 (Month 11).
     */
    fun clampMonthKey(monthKey: String): String {
        val parts = monthKey.trim().split("-")
        val y = parts.getOrNull(0)?.toIntOrNull() ?: APP_START_YEAR
        val m = parts.getOrNull(1)?.toIntOrNull() ?: APP_START_MONTH
        if (y < APP_START_YEAR || (y == APP_START_YEAR && m < APP_START_MONTH)) {
            return String.format(java.util.Locale.US, "%04d-%02d", APP_START_YEAR, APP_START_MONTH)
        }
        return String.format(java.util.Locale.US, "%04d-%02d", y, m.coerceIn(1, 12))
    }

    /**
     * Computes the Saturday-based week key (`YYYY-MM-W#`) for the given calendar timestamp.
     * A new week within the month begins on day 1 and on every Saturday at 12:00 AM midnight (00:00:00).
     */
    fun currentWeekKey(nowCal: java.util.Calendar = java.util.Calendar.getInstance()): String {
        val year = nowCal.get(java.util.Calendar.YEAR)
        val month = nowCal.get(java.util.Calendar.MONTH) + 1
        if (year < APP_START_YEAR || (year == APP_START_YEAR && month < APP_START_MONTH)) {
            return String.format(java.util.Locale.US, "%04d-%02d-W1", APP_START_YEAR, APP_START_MONTH)
        }
        val dayOfMonth = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
        val weeks = buildWeeksForMonth(year, month, nowCal)
        val matched = weeks.find { dayOfMonth in it.startDayOfMonth..it.endDayOfMonth }
            ?: weeks.lastOrNull()
        return matched?.weekKey ?: String.format(java.util.Locale.US, "%04d-%02d-W1", year, month)
    }

    /**
     * Starts from 2026 (where Month 11 is the first month) and automatically includes new years as time progresses.
     */
    fun availableYears(nowCal: java.util.Calendar = java.util.Calendar.getInstance()): List<Int> {
        val currentYear = nowCal.get(java.util.Calendar.YEAR).coerceAtLeast(APP_START_YEAR)
        return (currentYear downTo APP_START_YEAR).toList()
    }

    fun buildYearTree(
        year: Int,
        nowCal: java.util.Calendar = java.util.Calendar.getInstance()
    ): YearTreeNode {
        val nowYear = nowCal.get(java.util.Calendar.YEAR)
        val nowMonth = nowCal.get(java.util.Calendar.MONTH) + 1

        // For 2026, start strictly from Month 11 (November) and remove old months 1..10.
        // For subsequent years (2027+), include all months 1..12 automatically.
        val startMonthForYear = if (year <= APP_START_YEAR) APP_START_MONTH else 1

        val months = (startMonthForYear..12).map { monthNum ->
            val monthKey = String.format(java.util.Locale.US, "%04d-%02d", year, monthNum)
            val isFutureMonth = (year > nowYear) || (year == nowYear && monthNum > nowMonth)
            val isCurrentMonth = (year == nowYear && monthNum == nowMonth)
            val weeks = buildWeeksForMonth(year, monthNum, nowCal)

            // Compute Coptic months overlapping this Gregorian month
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, monthNum - 1)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val firstDayCoptic = toCopticDate(year, monthNum, 1)
            val lastDayCoptic = toCopticDate(year, monthNum, maxDay)
            val copticMonthsSummary = if (firstDayCoptic.month == lastDayCoptic.month) {
                "${firstDayCoptic.monthName} (شهر ${toArabicDigits(firstDayCoptic.month)} قبطي) • ${toArabicDigits(firstDayCoptic.year)} ش"
            } else {
                "${firstDayCoptic.monthName} (${toArabicDigits(firstDayCoptic.month)}) ➔ ${lastDayCoptic.monthName} (${toArabicDigits(lastDayCoptic.month)}) • ${toArabicDigits(lastDayCoptic.year)} ش"
            }

            MonthTreeNode(
                year = year,
                monthNumber = monthNum,
                monthKey = monthKey,
                arabicMonthName = getArabicMonthName(monthNum),
                isCurrentMonth = isCurrentMonth,
                isFutureMonth = isFutureMonth,
                weeks = weeks,
                copticMonthsLabel = copticMonthsSummary
            )
        }

        val startCopticYear = toCopticDate(year, startMonthForYear, 1).year
        val endCopticYear = toCopticDate(year, 12, 31).year
        val copticYearSuffix = if (startCopticYear == endCopticYear) {
            "${toArabicDigits(startCopticYear)} ش"
        } else {
            "${toArabicDigits(startCopticYear)} - ${toArabicDigits(endCopticYear)} ش"
        }

        return YearTreeNode(
            year = year,
            arabicYearLabel = "سنة ${toArabicDigits(year)} م ($copticYearSuffix)",
            months = months
        )
    }

    /**
     * Splits a given (year, monthNumber 1..12) into weeks where each week starts on
     * Saturday at 12:00 AM midnight (`Calendar.SATURDAY`) and ends on Friday at 11:59 PM
     * (clipped to the month's start and end days), enriched with Coptic week & transition details.
     */
    fun buildWeeksForMonth(
        year: Int,
        monthNumber: Int,
        nowCal: java.util.Calendar = java.util.Calendar.getInstance()
    ): List<WeekTreeNode> {
        val monthKey = String.format(java.util.Locale.US, "%04d-%02d", year, monthNumber)
        val arabicMonth = getArabicMonthName(monthNumber)

        val cal = java.util.Calendar.getInstance().apply {
            firstDayOfWeek = java.util.Calendar.SATURDAY
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, monthNumber - 1)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val nowYear = nowCal.get(java.util.Calendar.YEAR)
        val nowMonth = nowCal.get(java.util.Calendar.MONTH) + 1
        val nowDay = nowCal.get(java.util.Calendar.DAY_OF_MONTH)

        val rawRanges = mutableListOf<IntRange>()
        var currentStart = 1

        for (day in 1..maxDay) {
            cal.set(java.util.Calendar.DAY_OF_MONTH, day)
            val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
            // Every Saturday at 12:00 AM starts a new week (if not already day 1)
            if (day > 1 && dow == java.util.Calendar.SATURDAY) {
                rawRanges.add(currentStart..(day - 1))
                currentStart = day
            }
        }
        rawRanges.add(currentStart..maxDay)

        return rawRanges.mapIndexed { idx, range ->
            val weekNum = idx + 1
            val weekKey = "$monthKey-W$weekNum"
            val isCurrentWeek = (year == nowYear && monthNumber == nowMonth && nowDay in range)
            val isFutureWeek = (year > nowYear) ||
                (year == nowYear && monthNumber > nowMonth) ||
                (year == nowYear && monthNumber == nowMonth && range.first > nowDay)

            val title = arabicWeekNames.getOrElse(idx) { "الأسبوع ${toArabicDigits(weekNum)}" }
            val rangeLabel = if (range.first == range.last) {
                "${toArabicDigits(range.first)} $arabicMonth"
            } else {
                "${toArabicDigits(range.first)} - ${toArabicDigits(range.last)} $arabicMonth"
            }

            // Compute Coptic dates & transition inside this week
            val startCoptic = toCopticDate(year, monthNumber, range.first)
            val endCoptic = toCopticDate(year, monthNumber, range.last)

            val copticDateRangeLabel = if (range.first == range.last) {
                "${toArabicDigits(startCoptic.day)} ${startCoptic.monthName}"
            } else if (startCoptic.month == endCoptic.month) {
                "${toArabicDigits(startCoptic.day)} - ${toArabicDigits(endCoptic.day)} ${startCoptic.monthName}"
            } else {
                "${toArabicDigits(startCoptic.day)} ${startCoptic.monthName} - ${toArabicDigits(endCoptic.day)} ${endCoptic.monthName}"
            }

            // Check if a new Coptic month starts on any day inside this week
            val newCopticMonthStartDay = range.firstOrNull { gDay ->
                toCopticDate(year, monthNumber, gDay).day == 1
            }

            val copticWeekLabel: String
            val copticTransitionBadge: String?

            if (startCoptic.month == endCoptic.month) {
                val ord = getArabicOrdinal(startCoptic.weekInMonth)
                copticWeekLabel = "يقابله قبطياً: الأسبوع $ord من ${startCoptic.monthName} ($copticDateRangeLabel)"
                copticTransitionBadge = if (newCopticMonthStartDay != null) {
                    "✦ بداية شهر ${startCoptic.monthName} القبطي (شهر ${toArabicDigits(startCoptic.month)}) • يبدأ الأسبوع الأول القبطي"
                } else {
                    null
                }
            } else {
                val startOrd = getArabicOrdinal(startCoptic.weekInMonth)
                copticWeekLabel = "قبطياً: نهاية الأسبوع $startOrd من ${startCoptic.monthName} ➔ الأسبوع الأول من ${endCoptic.monthName} ($copticDateRangeLabel)"
                val transitionDayText = if (newCopticMonthStartDay != null) {
                    "من يوم ${toArabicDigits(newCopticMonthStartDay)} $arabicMonth"
                } else {
                    "في هذا الأسبوع"
                }
                copticTransitionBadge = "✦ تحوّل قبطي: يبدأ شهر ${endCoptic.monthName} (شهر ${toArabicDigits(endCoptic.month)} قبطي) $transitionDayText ويبدأ الأسبوع الأول القبطي"
            }

            WeekTreeNode(
                weekKey = weekKey,
                monthKey = monthKey,
                weekNumberInMonth = weekNum,
                startDayOfMonth = range.first,
                endDayOfMonth = range.last,
                arabicTitle = title,
                dateRangeLabel = rangeLabel,
                isCurrentWeek = isCurrentWeek,
                isFutureWeek = isFutureWeek,
                copticWeekLabel = copticWeekLabel,
                copticDateRangeLabel = copticDateRangeLabel,
                copticTransitionBadge = copticTransitionBadge
            )
        }
    }

    fun formatSelectedMonthAndWeek(monthKey: String, selectedWeekKey: String?): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        val monthLabel = if (year != null && month != null) {
            "${getArabicMonthName(month)} ${toArabicDigits(year)}"
        } else {
            monthKey
        }

        if (selectedWeekKey.isNullOrBlank()) {
            return "$monthLabel • كل الأسابيع"
        }
        val weekNum = selectedWeekKey.substringAfterLast("-W", "").toIntOrNull()
        return if (weekNum != null) {
            "$monthLabel • الأسبوع ${toArabicDigits(weekNum)}"
        } else {
            "$monthLabel • $selectedWeekKey"
        }
    }

    /**
     * Compact label for the 56dp header chip (e.g. "نوفمبر ٢٦ • أسبوع ١" or "نوفمبر ٢٦ • الكل").
     */
    fun formatCompactHeaderMonthWeek(monthKey: String, selectedWeekKey: String?): String {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        val shortYear = if (year != null) toArabicDigits(year % 100) else ""
        val monthLabel = if (month != null) {
            "${getArabicMonthName(month)} $shortYear".trim()
        } else {
            monthKey
        }

        if (selectedWeekKey.isNullOrBlank()) {
            return "$monthLabel • الكل"
        }
        val weekNum = selectedWeekKey.substringAfterLast("-W", "").toIntOrNull()
        return if (weekNum != null) {
            "$monthLabel • أسبوع ${toArabicDigits(weekNum)}"
        } else {
            monthLabel
        }
    }

    /**
     * Fallback timestamp in milliseconds for legacy [CardEvent] documents that lack a valid `createdAt`.
     * Uses `weekKey` (e.g. `2026-11-W2` or `2026-W45`) first, then `monthKey` (`2026-11`).
     */
    fun computeFallbackTimestampMillis(monthKey: String, weekKey: String): Long {
        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(utcZone).apply {
            firstDayOfWeek = java.util.Calendar.SATURDAY
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val cleanMonth = monthKey.trim()
        val mParts = cleanMonth.split("-")
        val yearFromMonth = mParts.getOrNull(0)?.toIntOrNull()
        val monthFromMonth = mParts.getOrNull(1)?.toIntOrNull()

        if (yearFromMonth != null && monthFromMonth != null && monthFromMonth in 1..12) {
            val weekNumInMonth = weekKey.substringAfterLast("-W", "").toIntOrNull()
            val approxDay = if (weekNumInMonth != null && weekNumInMonth in 1..5) {
                ((weekNumInMonth - 1) * 7 + 1).coerceIn(1, 28)
            } else {
                1
            }
            cal.set(java.util.Calendar.YEAR, yearFromMonth)
            cal.set(java.util.Calendar.MONTH, monthFromMonth - 1)
            cal.set(java.util.Calendar.DAY_OF_MONTH, approxDay)
            return cal.timeInMillis
        }

        // ISO-style weekKey fallback e.g. "2026-W52"
        if (weekKey.contains("-W")) {
            val wYear = weekKey.substringBefore("-W").toIntOrNull()
            val wNum = weekKey.substringAfter("-W").toIntOrNull()
            if (wYear != null && wNum != null && wNum in 1..53) {
                cal.set(java.util.Calendar.YEAR, wYear)
                cal.set(java.util.Calendar.WEEK_OF_YEAR, wNum)
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SATURDAY)
                return cal.timeInMillis
            }
        }
        return 0L
    }

    /**
     * Returns `(startOfMonthUtcMillis, endOfMonthUtcMillis)` at 00:00:00.000 UTC for the first and
     * last day of [monthKey] (e.g. `"2026-11"`), matching Material3 `DateRangePicker` UTC day millis.
     */
    fun computeMonthDateRangeUtcMillis(monthKey: String): Pair<Long, Long> {
        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(utcZone).apply {
            firstDayOfWeek = java.util.Calendar.SATURDAY
        }
        val parts = monthKey.trim().split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(java.util.Calendar.YEAR)
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(java.util.Calendar.MONTH) + 1)).coerceIn(1, 12)

        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, month - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val startUtcMillis = cal.timeInMillis

        val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        cal.set(java.util.Calendar.DAY_OF_MONTH, maxDay)
        val endDayStartUtcMillis = cal.timeInMillis
        return startUtcMillis to endDayStartUtcMillis
    }

    fun normalizeStartOfDayMillis(utcOrLocalMillis: Long): Long {
        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(utcZone).apply {
            timeInMillis = utcOrLocalMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun normalizeEndOfDayMillis(utcOrLocalMillis: Long): Long {
        return normalizeStartOfDayMillis(utcOrLocalMillis) + 86_399_999L
    }

    /**
     * Checks if `[startMillis, endMillis]` falls completely within [monthKey] (e.g., `"2026-11"`).
     */
    fun isDateRangeWithinMonth(startMillis: Long, endMillis: Long, monthKey: String): Boolean {
        if (monthKey.isBlank()) return false
        val (monthStart, monthEndDayStart) = computeMonthDateRangeUtcMillis(monthKey)
        val monthEndInclusive = monthEndDayStart + 86_399_999L
        return startMillis >= monthStart && endMillis <= monthEndInclusive
    }

    /**
     * Formats `[startMillis, endMillis]` as a concise Arabic button label such as
     * `"٣ نوفمبر ← ١١ نوفمبر"` or `"١ نوفمبر ← ٣٠ نوفمبر"`.
     */
    fun formatCompactArabicDateRange(startMillis: Long, endMillis: Long): String {
        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val startCal = java.util.Calendar.getInstance(utcZone).apply { timeInMillis = startMillis }
        val endCal = java.util.Calendar.getInstance(utcZone).apply { timeInMillis = endMillis }

        val sYear = startCal.get(java.util.Calendar.YEAR)
        val sMonth = startCal.get(java.util.Calendar.MONTH) + 1
        val sDay = startCal.get(java.util.Calendar.DAY_OF_MONTH)

        val eYear = endCal.get(java.util.Calendar.YEAR)
        val eMonth = endCal.get(java.util.Calendar.MONTH) + 1
        val eDay = endCal.get(java.util.Calendar.DAY_OF_MONTH)

        val sMonthName = getArabicMonthName(sMonth)
        val eMonthName = getArabicMonthName(eMonth)

        return when {
            sYear == eYear && sMonth == eMonth && sDay == eDay ->
                "${toArabicDigits(sDay)} $sMonthName"
            sYear == eYear ->
                "${toArabicDigits(sDay)} $sMonthName ← ${toArabicDigits(eDay)} $eMonthName"
            else ->
                "${toArabicDigits(sDay)} $sMonthName ${toArabicDigits(sYear)} ← ${toArabicDigits(eDay)} $eMonthName ${toArabicDigits(eYear)}"
        }
    }

    /**
     * Formats the multi-month and multi-week selection as a concise Arabic label
     * displayed on the calendar trigger button in the Leaderboard top bar.
     */
    fun formatLeaderboardSelectionLabel(
        selectedMonthKeys: Set<String>,
        selectedWeekKeys: Set<String>
    ): String {
        val validMonths = selectedMonthKeys.filter { it.isNotBlank() }.toSortedSet()
        val validWeeks = selectedWeekKeys.filter { it.isNotBlank() }.toSortedSet()

        if (validMonths.isEmpty() && validWeeks.isEmpty()) {
            return "اختر الشهور والأسابيع"
        }

        if (validWeeks.isEmpty()) {
            return if (validMonths.size == 1) {
                val mKey = validMonths.first()
                val parts = mKey.split("-")
                val y = parts.getOrNull(0)?.toIntOrNull()
                val m = parts.getOrNull(1)?.toIntOrNull()
                if (y != null && m != null) {
                    "شهر ${toArabicDigits(m)} (${getArabicMonthName(m)} ${toArabicDigits(y)})"
                } else {
                    mKey
                }
            } else if (validMonths.size <= 3) {
                validMonths.joinToString("، ") { mKey ->
                    val m = mKey.split("-").getOrNull(1)?.toIntOrNull() ?: 1
                    getArabicMonthName(m)
                }
            } else {
                "${toArabicDigits(validMonths.size)} شهور محددة"
            }
        }

        if (validMonths.isEmpty()) {
            val byMonth = validWeeks.groupBy { it.substringBefore("-W") }
            return if (byMonth.size == 1) {
                val (mKey, weeks) = byMonth.entries.first()
                val m = mKey.split("-").getOrNull(1)?.toIntOrNull() ?: 1
                val mName = getArabicMonthName(m)
                val weekNums = weeks.mapNotNull { it.substringAfter("-W", "").toIntOrNull() }.sorted()
                val weekLabels = weekNums.joinToString("، ") { toArabicDigits(it) }
                "$mName (أسبوع $weekLabels)"
            } else {
                "${toArabicDigits(validWeeks.size)} أسابيع محددة"
            }
        }

        val monthNames = validMonths.map { mKey ->
            val m = mKey.split("-").getOrNull(1)?.toIntOrNull() ?: 1
            getArabicMonthName(m)
        }
        val weekCount = validWeeks.size
        return "${monthNames.joinToString(" + ")} (+ ${toArabicDigits(weekCount)} أسابيع)"
    }

    /**
     * Computes the bounding start and end UTC timestamps in milliseconds across all selected months & weeks.
     */
    fun computeBoundsForSelection(
        monthKeys: Set<String>,
        weekKeys: Set<String>
    ): Pair<Long, Long> {
        val startTimes = mutableListOf<Long>()
        val endTimes = mutableListOf<Long>()

        monthKeys.filter { it.isNotBlank() }.forEach { mKey ->
            val (mStart, mEndDayStart) = computeMonthDateRangeUtcMillis(mKey)
            startTimes.add(mStart)
            endTimes.add(mEndDayStart + 86_399_999L)
        }

        val utcZone = java.util.TimeZone.getTimeZone("UTC")
        val cal = java.util.Calendar.getInstance(utcZone).apply {
            firstDayOfWeek = java.util.Calendar.SATURDAY
        }

        weekKeys.filter { it.isNotBlank() }.forEach { wKey ->
            val mKey = wKey.substringBefore("-W")
            val wNum = wKey.substringAfter("-W", "").toIntOrNull() ?: 1
            val parts = mKey.split("-")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: 2026
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 11
            val weeks = buildWeeksForMonth(y, m)
            val matched = weeks.find { it.weekNumberInMonth == wNum } ?: weeks.firstOrNull()
            if (matched != null) {
                cal.set(java.util.Calendar.YEAR, y)
                cal.set(java.util.Calendar.MONTH, m - 1)
                cal.set(java.util.Calendar.DAY_OF_MONTH, matched.startDayOfMonth)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                startTimes.add(cal.timeInMillis)

                cal.set(java.util.Calendar.DAY_OF_MONTH, matched.endDayOfMonth)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                cal.set(java.util.Calendar.MINUTE, 59)
                cal.set(java.util.Calendar.SECOND, 59)
                cal.set(java.util.Calendar.MILLISECOND, 999)
                endTimes.add(cal.timeInMillis)
            }
        }

        if (startTimes.isEmpty() || endTimes.isEmpty()) {
            val (defStart, defEndDayStart) = computeMonthDateRangeUtcMillis(currentYearMonth())
            return defStart to (defEndDayStart + 86_399_999L)
        }

        return startTimes.minOrNull()!! to endTimes.maxOrNull()!!
    }
}

typealias ServiceCalendarHelper = SaturdayCalendarHelper




