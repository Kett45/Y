package com.example.data.cache

import android.content.Context
import android.util.Log
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.Group
import com.example.data.model.MonthlyBalance
import com.example.data.model.ServantBlock
import com.example.data.model.Student
import com.example.data.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class CachedClassroomSnapshot(
    val userProfile: UserProfile,
    val group: Group?,
    val userGroups: List<Group> = emptyList(),
    val blocks: List<ServantBlock> = emptyList(),
    val students: List<Student> = emptyList(),
    val cardEvents: List<CardEvent> = emptyList(),
    val monthlyBalances: List<MonthlyBalance> = emptyList(),
    val savedAtMillis: Long = System.currentTimeMillis()
)

data class LocalUserPreferences(
    val enabledCards: List<String> = CardType.defaultEnabledIds,
    val collapsedBlockIds: List<String> = emptyList(),
    val hiddenBlockIds: List<String> = emptyList(),
    val themeId: String = "DARK",
    val uiScaleMode: String = "AUTO",
    val uiScaleFactor: Float = 0.85f,
    val showFloatingAddButton: Boolean = true,
    val activeGroupId: String = "",
    val updatedAtMillis: Long = 0L
)

/**
 * Fast local cache for instant 0ms startup and offline-first personal preferences persistence.
 * Stores classroom lists (blocks, students, card events, balances) on disk and in memory so the
 * Home screen opens immediately without waiting for cloud round-trips, then updates diffs in background.
 */
object LocalClassroomCache {
    private const val TAG = "LocalClassroomCache"
    private const val PREFS_NAME = "kam_fast_local_cache_v16"
    private const val SNAPSHOT_DIR = "classroom_snapshots"

    @Volatile
    private var appContext: Context? = null

    private val memorySnapshots = ConcurrentHashMap<String, CachedClassroomSnapshot>()
    private val memoryPrefs = ConcurrentHashMap<String, LocalUserPreferences>()

    fun init(context: Context?) {
        val ctx = context?.applicationContext ?: return
        appContext = ctx
    }

    fun preloadForUser(context: Context?, userId: String): CachedClassroomSnapshot? {
        init(context)
        if (userId.isBlank()) return null
        memorySnapshots[userId]?.let { return it }
        return readSnapshotFromDisk(userId)?.also {
            memorySnapshots[userId] = it
        }
    }

    fun getCachedSnapshot(userId: String): CachedClassroomSnapshot? {
        if (userId.isBlank()) return null
        memorySnapshots[userId]?.let { return it }
        return readSnapshotFromDisk(userId)?.also {
            memorySnapshots[userId] = it
        }
    }

    fun saveClassroomSnapshot(
        userId: String,
        userProfile: UserProfile,
        group: Group?,
        userGroups: List<Group>,
        blocks: List<ServantBlock>,
        students: List<Student>,
        cardEvents: List<CardEvent>,
        monthlyBalances: List<MonthlyBalance>
    ) {
        if (userId.isBlank()) return
        val snapshot = CachedClassroomSnapshot(
            userProfile = userProfile,
            group = group,
            userGroups = userGroups,
            blocks = blocks,
            students = students,
            cardEvents = cardEvents,
            monthlyBalances = monthlyBalances,
            savedAtMillis = System.currentTimeMillis()
        )
        memorySnapshots[userId] = snapshot
        writeSnapshotToDisk(userId, snapshot)
    }

    fun loadUserPreferences(userId: String): LocalUserPreferences {
        if (userId.isBlank()) return LocalUserPreferences()
        memoryPrefs[userId]?.let { return it }
        val ctx = appContext ?: return LocalUserPreferences()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString("user_prefs_$userId", null)
        if (rawJson.isNullOrBlank()) {
            return LocalUserPreferences()
        }
        return runCatching {
            val obj = JSONObject(rawJson)
            val parsed = LocalUserPreferences(
                enabledCards = jsonArrayToStringList(obj.optJSONArray("enabledCards"))
                    .takeIf { obj.has("enabledCards") } ?: CardType.defaultEnabledIds,
                collapsedBlockIds = jsonArrayToStringList(obj.optJSONArray("collapsedBlockIds")),
                hiddenBlockIds = jsonArrayToStringList(obj.optJSONArray("hiddenBlockIds")),
                themeId = obj.optString("themeId", "DARK").ifBlank { "DARK" },
                uiScaleMode = obj.optString("uiScaleMode", "AUTO").ifBlank { "AUTO" },
                uiScaleFactor = obj.optDouble("uiScaleFactor", 0.85).toFloat().coerceIn(0.70f, 1.30f),
                showFloatingAddButton = obj.optBoolean("showFloatingAddButton", true),
                activeGroupId = obj.optString("activeGroupId", ""),
                updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
            )
            memoryPrefs[userId] = parsed
            parsed
        }.getOrDefault(LocalUserPreferences())
    }

    fun saveUserPreferences(userId: String, preferences: LocalUserPreferences) {
        if (userId.isBlank()) return
        val stamped = preferences.copy(updatedAtMillis = System.currentTimeMillis())
        memoryPrefs[userId] = stamped

        // Also update cached snapshot profile if present in memory
        memorySnapshots[userId]?.let { existing ->
            memorySnapshots[userId] = existing.copy(
                userProfile = existing.userProfile.copy(
                    enabledCards = stamped.enabledCards,
                    collapsedBlockIds = stamped.collapsedBlockIds,
                    hiddenBlockIds = stamped.hiddenBlockIds,
                    themeId = stamped.themeId,
                    uiScaleMode = stamped.uiScaleMode,
                    uiScaleFactor = stamped.uiScaleFactor.toDouble(),
                    showFloatingAddButton = stamped.showFloatingAddButton,
                    groupId = stamped.activeGroupId.ifBlank { existing.userProfile.groupId }
                )
            )
        }

        val ctx = appContext ?: return
        runCatching {
            val obj = JSONObject().apply {
                put("enabledCards", JSONArray(stamped.enabledCards))
                put("collapsedBlockIds", JSONArray(stamped.collapsedBlockIds))
                put("hiddenBlockIds", JSONArray(stamped.hiddenBlockIds))
                put("themeId", stamped.themeId)
                put("uiScaleMode", stamped.uiScaleMode)
                put("uiScaleFactor", stamped.uiScaleFactor.toDouble())
                put("showFloatingAddButton", stamped.showFloatingAddButton)
                put("activeGroupId", stamped.activeGroupId)
                put("updatedAtMillis", stamped.updatedAtMillis)
            }
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("user_prefs_$userId", obj.toString())
                .apply()
        }.onFailure { e ->
            Log.w(TAG, "Failed to persist user preferences locally: ${e.message}")
        }
    }

    fun isGroupMigrationCompleted(groupId: String): Boolean {
        if (groupId.isBlank()) return false
        val ctx = appContext ?: return false
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("migrated_v16_$groupId", false)
    }

    fun markGroupMigrationCompleted(groupId: String) {
        if (groupId.isBlank()) return
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("migrated_v16_$groupId", true)
            .apply()
    }

    private fun snapshotFileForUser(userId: String): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, SNAPSHOT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val safeId = userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(dir, "snapshot_$safeId.json")
    }

    private fun writeSnapshotToDisk(userId: String, snapshot: CachedClassroomSnapshot) {
        val file = snapshotFileForUser(userId) ?: return
        runCatching {
            val root = JSONObject().apply {
                put("savedAtMillis", snapshot.savedAtMillis)
                put("userProfile", serializeUserProfile(snapshot.userProfile))
                if (snapshot.group != null) {
                    put("group", serializeGroup(snapshot.group))
                }
                put("userGroups", JSONArray().apply {
                    snapshot.userGroups.forEach { put(serializeGroup(it)) }
                })
                put("blocks", JSONArray().apply {
                    snapshot.blocks.forEach { put(serializeBlock(it)) }
                })
                put("students", JSONArray().apply {
                    snapshot.students.forEach { put(serializeStudent(it)) }
                })
                put("cardEvents", JSONArray().apply {
                    // Cache up to 600 recent card events for instant startup
                    snapshot.cardEvents.take(600).forEach { put(serializeCardEvent(it)) }
                })
                put("monthlyBalances", JSONArray().apply {
                    snapshot.monthlyBalances.take(400).forEach { put(serializeMonthlyBalance(it)) }
                })
            }
            file.writeText(root.toString())
        }.onFailure { e ->
            Log.w(TAG, "Failed to write classroom snapshot to disk: ${e.message}")
        }
    }

    private fun readSnapshotFromDisk(userId: String): CachedClassroomSnapshot? {
        val file = snapshotFileForUser(userId) ?: return null
        if (!file.exists()) return null
        return runCatching {
            val root = JSONObject(file.readText())
            val profileObj = root.optJSONObject("userProfile") ?: return null
            val userProfile = deserializeUserProfile(profileObj)
            val group = root.optJSONObject("group")?.let { deserializeGroup(it) }
            val userGroups = mutableListOf<Group>()
            root.optJSONArray("userGroups")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { userGroups.add(deserializeGroup(it)) }
                }
            }
            val blocks = mutableListOf<ServantBlock>()
            root.optJSONArray("blocks")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { blocks.add(deserializeBlock(it)) }
                }
            }
            val students = mutableListOf<Student>()
            root.optJSONArray("students")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { students.add(deserializeStudent(it)) }
                }
            }
            val cardEvents = mutableListOf<CardEvent>()
            root.optJSONArray("cardEvents")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { cardEvents.add(deserializeCardEvent(it)) }
                }
            }
            val monthlyBalances = mutableListOf<MonthlyBalance>()
            root.optJSONArray("monthlyBalances")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { monthlyBalances.add(deserializeMonthlyBalance(it)) }
                }
            }
            CachedClassroomSnapshot(
                userProfile = userProfile,
                group = group,
                userGroups = userGroups,
                blocks = blocks,
                students = students,
                cardEvents = cardEvents,
                monthlyBalances = monthlyBalances,
                savedAtMillis = root.optLong("savedAtMillis", 0L)
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed to read classroom snapshot from disk: ${e.message}")
        }.getOrNull()
    }

    private fun serializeUserProfile(p: UserProfile): JSONObject = JSONObject().apply {
        put("id", p.id.ifBlank { p.userId })
        put("userId", p.userId)
        put("displayName", p.displayName)
        put("email", p.email)
        put("groupId", p.groupId)
        put("joinedGroupIds", JSONArray(p.joinedGroupIds))
        put("enabledCards", JSONArray(p.enabledCards))
        put("collapsedBlockIds", JSONArray(p.collapsedBlockIds))
        put("hiddenBlockIds", JSONArray(p.hiddenBlockIds))
        put("compactViewMode", p.compactViewMode)
        put("themeId", p.themeId)
        put("uiScaleMode", p.uiScaleMode)
        put("uiScaleFactor", p.uiScaleFactor)
        put("showFloatingAddButton", p.showFloatingAddButton)
    }

    private fun deserializeUserProfile(obj: JSONObject): UserProfile = UserProfile(
        id = obj.optString("id", obj.optString("userId", "")),
        userId = obj.optString("userId", ""),
        displayName = obj.optString("displayName", ""),
        email = obj.optString("email", ""),
        groupId = obj.optString("groupId", ""),
        joinedGroupIds = jsonArrayToStringList(obj.optJSONArray("joinedGroupIds")),
        enabledCards = jsonArrayToStringList(obj.optJSONArray("enabledCards"))
            .takeIf { obj.has("enabledCards") } ?: CardType.defaultEnabledIds,
        collapsedBlockIds = jsonArrayToStringList(obj.optJSONArray("collapsedBlockIds")),
        hiddenBlockIds = jsonArrayToStringList(obj.optJSONArray("hiddenBlockIds")),
        compactViewMode = obj.optBoolean("compactViewMode", false),
        themeId = obj.optString("themeId", "DARK").ifBlank { "DARK" },
        uiScaleMode = obj.optString("uiScaleMode", "AUTO").ifBlank { "AUTO" },
        uiScaleFactor = obj.optDouble("uiScaleFactor", 0.85),
        showFloatingAddButton = obj.optBoolean("showFloatingAddButton", true)
    )

    private fun serializeGroup(g: Group): JSONObject = JSONObject().apply {
        put("id", g.id.ifBlank { g.inviteCode })
        put("inviteCode", g.inviteCode)
        put("name", g.name)
        put("adminId", g.adminId)
        put("adminName", g.adminName)
        put("memberUids", JSONArray(g.memberUids))
        put("allowServantUndo", g.allowServantUndo)
        put("enforceWeeklyCardLimit", g.enforceWeeklyCardLimit)
        put("maxMonthlyPoints", g.maxMonthlyPoints)
        put("attendanceIsCardOnly", g.attendanceIsCardOnly)
        put("showLeaderboard", g.showLeaderboard)
        put("autoStudentNumber", g.autoStudentNumber)
        put("bannedUids", JSONArray(g.bannedUids))
        put("bannedServantEntries", JSONArray(g.bannedServantEntries))
        put("dailyInviteToken", g.dailyInviteToken)
        put("inviteCodeUpdatedAtMillis", g.inviteCodeUpdatedAtMillis)
    }

    private fun deserializeGroup(obj: JSONObject): Group = Group(
        id = obj.optString("id", obj.optString("inviteCode", "")),
        inviteCode = obj.optString("inviteCode", ""),
        name = obj.optString("name", ""),
        adminId = obj.optString("adminId", ""),
        adminName = obj.optString("adminName", ""),
        memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids")),
        allowServantUndo = obj.optBoolean("allowServantUndo", true),
        enforceWeeklyCardLimit = obj.optBoolean("enforceWeeklyCardLimit", false),
        maxMonthlyPoints = obj.optLong("maxMonthlyPoints", 0L),
        attendanceIsCardOnly = obj.optBoolean("attendanceIsCardOnly", true),
        showLeaderboard = obj.optBoolean("showLeaderboard", true),
        autoStudentNumber = obj.optBoolean("autoStudentNumber", true),
        bannedUids = jsonArrayToStringList(obj.optJSONArray("bannedUids")),
        bannedServantEntries = jsonArrayToStringList(obj.optJSONArray("bannedServantEntries")),
        dailyInviteToken = obj.optString("dailyInviteToken", ""),
        inviteCodeUpdatedAtMillis = obj.optLong("inviteCodeUpdatedAtMillis", 0L)
    )

    private fun serializeBlock(b: ServantBlock): JSONObject = JSONObject().apply {
        put("id", b.id)
        put("groupId", b.groupId)
        put("servantId", b.servantId)
        put("servantName", b.servantName)
        put("colorHex", b.colorHex)
        put("sortOrder", b.sortOrder)
        put("memberUids", JSONArray(b.memberUids))
    }

    private fun deserializeBlock(obj: JSONObject): ServantBlock = ServantBlock(
        id = obj.optString("id", ""),
        groupId = obj.optString("groupId", ""),
        servantId = obj.optString("servantId", ""),
        servantName = obj.optString("servantName", ""),
        colorHex = obj.optString("colorHex", "#3B82F6"),
        sortOrder = obj.optLong("sortOrder", 0L),
        memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids"))
    )

    private fun serializeStudent(s: Student): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("groupId", s.groupId)
        put("name", s.name)
        put("studentNumber", s.studentNumber)
        put("blockId", s.blockId)
        put("sortOrder", s.sortOrder)
        put("position", s.position)
        put("avatarInitials", s.avatarInitials)
        put("avatarBase64", s.avatarBase64)
        put("createdByUid", s.createdByUid)
        put("memberUids", JSONArray(s.memberUids))
    }

    private fun deserializeStudent(obj: JSONObject): Student = Student(
        id = obj.optString("id", ""),
        groupId = obj.optString("groupId", ""),
        name = obj.optString("name", ""),
        studentNumber = obj.optLong("studentNumber", 1L),
        blockId = obj.optString("blockId", ""),
        sortOrder = obj.optLong("sortOrder", 0L),
        position = obj.optDouble("position", 0.0),
        avatarInitials = obj.optString("avatarInitials", ""),
        avatarBase64 = obj.optString("avatarBase64", ""),
        createdByUid = obj.optString("createdByUid", ""),
        memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids"))
    )

    private fun serializeCardEvent(e: CardEvent): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("groupId", e.groupId)
        put("studentId", e.studentId)
        put("studentName", e.studentName)
        put("cardType", e.cardType)
        put("points", e.points)
        put("servantId", e.servantId)
        put("servantName", e.servantName)
        put("monthKey", e.monthKey)
        put("weekKey", e.weekKey)
    }

    private fun deserializeCardEvent(obj: JSONObject): CardEvent = CardEvent(
        id = obj.optString("id", ""),
        groupId = obj.optString("groupId", ""),
        studentId = obj.optString("studentId", ""),
        studentName = obj.optString("studentName", ""),
        cardType = obj.optString("cardType", "ATTENDANCE"),
        points = obj.optLong("points", 3L),
        servantId = obj.optString("servantId", ""),
        servantName = obj.optString("servantName", ""),
        monthKey = obj.optString("monthKey", ""),
        weekKey = obj.optString("weekKey", "")
    )

    private fun serializeMonthlyBalance(m: MonthlyBalance): JSONObject = JSONObject().apply {
        put("id", m.id)
        put("groupId", m.groupId)
        put("studentId", m.studentId)
        put("studentName", m.studentName)
        put("monthKey", m.monthKey)
        put("totalPoints", m.totalPoints)
        put("eventsCount", m.eventsCount)
        put("lastUpdatedByUid", m.lastUpdatedByUid)
    }

    private fun deserializeMonthlyBalance(obj: JSONObject): MonthlyBalance = MonthlyBalance(
        id = obj.optString("id", ""),
        groupId = obj.optString("groupId", ""),
        studentId = obj.optString("studentId", ""),
        studentName = obj.optString("studentName", ""),
        monthKey = obj.optString("monthKey", ""),
        totalPoints = obj.optLong("totalPoints", 0L),
        eventsCount = obj.optLong("eventsCount", 0L),
        lastUpdatedByUid = obj.optString("lastUpdatedByUid", "")
    )

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optString(i, "")
            if (item.isNotBlank()) {
                list.add(item)
            }
        }
        return list
    }
}
