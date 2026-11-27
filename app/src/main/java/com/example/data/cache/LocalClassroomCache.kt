package com.example.data.cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.data.model.CardEvent
import com.example.data.model.CardType
import com.example.data.model.Group
import com.example.data.model.MonthlyBalance
import com.example.data.model.ServantBlock
import com.example.data.model.ServantMemberProfile
import com.example.data.model.Student
import android.os.Looper
import android.os.SystemClock
import com.example.data.model.UserProfile
import com.example.ui.components.StudentAvatarImageHelper
import com.example.util.KamPerformanceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.Timestamp
import java.io.File
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class CachedClassroomSnapshot(
    val userProfile: UserProfile,
    val group: Group?,
    val userGroups: List<Group> = emptyList(),
    val blocks: List<ServantBlock> = emptyList(),
    val students: List<Student> = emptyList(),
    val cardEvents: List<CardEvent> = emptyList(),
    val monthlyBalances: List<MonthlyBalance> = emptyList(),
    val memberProfiles: List<ServantMemberProfile> = emptyList(),
    val savedAtMillis: Long = System.currentTimeMillis()
)

@Immutable
data class LocalUserPreferences(
    val enabledCards: List<String> = CardType.defaultEnabledIds,
    val collapsedBlockIds: List<String> = emptyList(),
    val hiddenBlockIds: List<String> = emptyList(),
    val themeId: String = "DARK",
    val uiScaleMode: String = "AUTO",
    val uiScaleFactor: Float = 0.85f,
    val showFloatingAddButton: Boolean = true,
    val activeGroupId: String = "",
    val phoneOnboardingDone: Boolean = false,
    val primaryPhone: String = "",
    val secondaryPhone: String = "",
    val phoneHidden: Boolean = false,
    val servantAvatarHash: String = "",
    val updatedAtMillis: Long = 0L
)

/**
 * Fast local cache for instant 0ms startup and offline-first personal preferences persistence.
 * All disk & SharedPreferences operations run strictly on [Dispatchers.IO] so the Main Thread
 * never blocks on DiskReadViolation / DiskWriteViolation.
 */
object LocalClassroomCache {
    private const val TAG = "LocalClassroomCache"
    private const val PREFS_NAME = "kam_fast_local_cache_v16"
    private const val SNAPSHOT_DIR = "classroom_snapshots"
    private val SAFE_USER_ID_REGEX = Regex("[^a-zA-Z0-9_-]")

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingDiskWriteJobs = ConcurrentHashMap<String, Job>()

    @Volatile
    private var appContext: Context? = null

    private val memorySnapshots = ConcurrentHashMap<String, CachedClassroomSnapshot>()
    private val memoryPrefs = ConcurrentHashMap<String, LocalUserPreferences>()
    private val migratedGroupIds = ConcurrentHashMap.newKeySet<String>()
    private val historicalRangeMemoryCache = ConcurrentHashMap<String, List<CardEvent>>()

    private fun isOnMainThread(): Boolean {
        return runCatching { Looper.myLooper() == Looper.getMainLooper() }.getOrDefault(false)
    }

    fun init(context: Context?) {
        val ctx = context?.applicationContext ?: return
        appContext = ctx
    }

    /**
     * Preloads the user's classroom snapshot and preferences on [Dispatchers.IO] without blocking the Main Thread.
     * Returns immediately if already in memory.
     */
    fun preloadForUser(context: Context?, userId: String): CachedClassroomSnapshot? {
        init(context)
        if (userId.isBlank()) return null
        memorySnapshots[userId]?.let { return it }
        if (isOnMainThread()) {
            ioScope.launch {
                loadUserPreferencesFromDiskInternal(userId)
                readSnapshotFromDisk(userId)?.let { snap ->
                    memorySnapshots.putIfAbsent(userId, snap)
                }
            }
            return memorySnapshots[userId]
        }
        loadUserPreferencesFromDiskInternal(userId)
        return readSnapshotFromDisk(userId)?.also {
            memorySnapshots[userId] = it
        }
    }

    fun getMemorySnapshot(userId: String): CachedClassroomSnapshot? {
        if (userId.isBlank()) return null
        return memorySnapshots[userId]
    }

    fun getMemoryUserPreferences(userId: String): LocalUserPreferences? {
        if (userId.isBlank()) return null
        return memoryPrefs[userId]
    }

    suspend fun loadSnapshotFromDiskAsync(userId: String): CachedClassroomSnapshot? {
        if (userId.isBlank()) return null
        memorySnapshots[userId]?.let { return it }
        return withContext(Dispatchers.IO) {
            memorySnapshots[userId] ?: readSnapshotFromDisk(userId)?.also {
                memorySnapshots[userId] = it
            }
        }
    }

    suspend fun loadUserPreferencesAsync(userId: String): LocalUserPreferences {
        if (userId.isBlank()) return LocalUserPreferences()
        memoryPrefs[userId]?.let { return it }
        return withContext(Dispatchers.IO) {
            loadUserPreferencesFromDiskInternal(userId)
        }
    }

    fun getCachedSnapshot(userId: String): CachedClassroomSnapshot? {
        if (userId.isBlank()) return null
        memorySnapshots[userId]?.let { return it }
        if (isOnMainThread()) {
            ioScope.launch {
                readSnapshotFromDisk(userId)?.let { snap ->
                    memorySnapshots.putIfAbsent(userId, snap)
                }
            }
            return null
        }
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
        monthlyBalances: List<MonthlyBalance>,
        memberProfiles: List<ServantMemberProfile> = emptyList()
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
            memberProfiles = memberProfiles,
            savedAtMillis = System.currentTimeMillis()
        )
        memorySnapshots[userId] = snapshot
        scheduleDebouncedDiskWrite(userId, snapshot)
    }

    private fun scheduleDebouncedDiskWrite(userId: String, snapshot: CachedClassroomSnapshot) {
        pendingDiskWriteJobs[userId]?.cancel()
        pendingDiskWriteJobs[userId] = ioScope.launch {
            // Wait 3.0 seconds after activity stops before writing lightweight metadata snapshot to disk
            delay(3000L)
            val latest = memorySnapshots[userId] ?: snapshot
            writeSnapshotToDisk(userId, latest)
        }
    }

    fun loadUserPreferences(userId: String): LocalUserPreferences {
        if (userId.isBlank()) return LocalUserPreferences()
        memoryPrefs[userId]?.let { return it }
        if (isOnMainThread()) {
            ioScope.launch {
                loadUserPreferencesFromDiskInternal(userId)
            }
            return LocalUserPreferences()
        }
        return loadUserPreferencesFromDiskInternal(userId)
    }

    private fun loadUserPreferencesFromDiskInternal(userId: String): LocalUserPreferences {
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
                phoneOnboardingDone = obj.optBoolean("phoneOnboardingDone", false),
                primaryPhone = obj.optString("primaryPhone", ""),
                secondaryPhone = obj.optString("secondaryPhone", ""),
                phoneHidden = false,
                servantAvatarHash = obj.optString("servantAvatarHash", ""),
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
        ioScope.launch {
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
                    put("phoneOnboardingDone", stamped.phoneOnboardingDone)
                    put("primaryPhone", stamped.primaryPhone)
                    put("secondaryPhone", stamped.secondaryPhone)
                    put("phoneHidden", stamped.phoneHidden)
                    put("servantAvatarHash", stamped.servantAvatarHash)
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
    }

    fun isGroupMigrationCompleted(groupId: String): Boolean {
        if (groupId.isBlank()) return false
        if (migratedGroupIds.contains(groupId)) return true
        if (isOnMainThread()) return false
        val ctx = appContext ?: return false
        val done = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("migrated_v16_$groupId", false)
        if (done) {
            migratedGroupIds.add(groupId)
        }
        return done
    }

    fun markGroupMigrationCompleted(groupId: String) {
        if (groupId.isBlank()) return
        migratedGroupIds.add(groupId)
        val ctx = appContext ?: return
        ioScope.launch {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("migrated_v16_$groupId", true)
                .apply()
        }
    }

    private fun snapshotFileForUser(userId: String): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, SNAPSHOT_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val safeId = userId.replace(SAFE_USER_ID_REGEX, "_")
        return File(dir, "snapshot_$safeId.json")
    }

    private fun writeSnapshotToDisk(userId: String, snapshot: CachedClassroomSnapshot) {
        val file = snapshotFileForUser(userId) ?: return
        val startNs = SystemClock.elapsedRealtimeNanos()
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
                    // Keep only 250 recent events in fast startup metadata; Firestore SQLite persistence holds full history
                    snapshot.cardEvents.take(250).forEach { put(serializeCardEvent(it)) }
                })
                put("monthlyBalances", JSONArray().apply {
                    snapshot.monthlyBalances.take(150).forEach { put(serializeMonthlyBalance(it)) }
                })
                put("memberProfiles", JSONArray().apply {
                    snapshot.memberProfiles.forEach { put(serializeMemberProfile(it)) }
                })
            }
            val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
            file.writeBytes(jsonBytes)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
            KamPerformanceTracker.recordSnapshotDiskWrite(elapsedMs, jsonBytes.size)
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
            val memberProfiles = mutableListOf<ServantMemberProfile>()
            root.optJSONArray("memberProfiles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { memberProfiles.add(deserializeMemberProfile(it)) }
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
                memberProfiles = memberProfiles,
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
        put("servantAliases", JSONObject().apply {
            p.servantAliases.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) put(k, v)
            }
        })
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
        showFloatingAddButton = obj.optBoolean("showFloatingAddButton", true),
        servantAliases = jsonObjectToStringMap(obj.optJSONObject("servantAliases"))
    )

    private fun serializeGroup(g: Group): JSONObject {
        if (g.avatarBase64.isNotBlank() && g.inviteCode.isNotBlank()) {
            StudentAvatarImageHelper.writeLocalAlbumRaw(appContext, "group_${g.inviteCode}", g.avatarBase64)
        }
        return JSONObject().apply {
            put("id", g.id.ifBlank { g.inviteCode })
            put("inviteCode", g.inviteCode)
            put("name", g.name)
            put("adminId", g.adminId)
            put("adminName", g.adminName)
            put("assistantAdminIds", JSONArray(g.assistantAdminIds))
            put("memberUids", JSONArray(g.memberUids))
            put("allowServantUndo", g.allowServantUndo)
            put("enforceWeeklyCardLimit", g.enforceWeeklyCardLimit)
            put("maxMonthlyPoints", g.maxMonthlyPoints)
            put("attendanceIsCardOnly", g.attendanceIsCardOnly)
            put("showLeaderboard", g.showLeaderboard)
            put("autoStudentNumber", g.autoStudentNumber)
            put("lockStudentTransfer", g.lockStudentTransfer)
            put("bannedUids", JSONArray(g.bannedUids))
            put("bannedServantEntries", JSONArray(g.bannedServantEntries))
            put("dailyInviteToken", g.dailyInviteToken)
            put("inviteCodeUpdatedAtMillis", g.inviteCodeUpdatedAtMillis)
            put("avatarHash", g.avatarHash)
            put("customCardEnabled", g.customCardEnabled)
            put("customCardName", g.customCardName)
            put("customCardPoints", g.customCardPoints)
            put("customCardIcon", g.customCardIcon)
            put("customCardColorKey", g.customCardColorKey)
            put("customCardFrequency", g.customCardFrequency)
        }
    }

    private fun deserializeGroup(obj: JSONObject): Group {
        val code = obj.optString("inviteCode", "")
        val localAvatar = if (code.isNotBlank()) {
            StudentAvatarImageHelper.peekCachedLocalAlbumRaw("group_$code")
        } else {
            ""
        }
        return Group(
            id = obj.optString("id", code),
            inviteCode = code,
            name = obj.optString("name", ""),
            adminId = obj.optString("adminId", ""),
            adminName = obj.optString("adminName", ""),
            assistantAdminIds = jsonArrayToStringList(obj.optJSONArray("assistantAdminIds")),
            memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids")),
            allowServantUndo = obj.optBoolean("allowServantUndo", true),
            enforceWeeklyCardLimit = obj.optBoolean("enforceWeeklyCardLimit", false),
            maxMonthlyPoints = obj.optLong("maxMonthlyPoints", 0L),
            attendanceIsCardOnly = obj.optBoolean("attendanceIsCardOnly", true),
            showLeaderboard = obj.optBoolean("showLeaderboard", true),
            autoStudentNumber = obj.optBoolean("autoStudentNumber", true),
            lockStudentTransfer = obj.optBoolean("lockStudentTransfer", false),
            bannedUids = jsonArrayToStringList(obj.optJSONArray("bannedUids")),
            bannedServantEntries = jsonArrayToStringList(obj.optJSONArray("bannedServantEntries")),
            dailyInviteToken = obj.optString("dailyInviteToken", ""),
            inviteCodeUpdatedAtMillis = obj.optLong("inviteCodeUpdatedAtMillis", 0L),
            avatarBase64 = localAvatar,
            avatarHash = obj.optString("avatarHash", ""),
            customCardEnabled = obj.optBoolean("customCardEnabled", false),
            customCardName = obj.optString("customCardName", ""),
            customCardPoints = obj.optLong("customCardPoints", 15L),
            customCardIcon = obj.optString("customCardIcon", "🏅"),
            customCardColorKey = obj.optString("customCardColorKey", "GOLD"),
            customCardFrequency = obj.optString("customCardFrequency", "WEEKLY")
        )
    }

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
        // Persist any inline avatar into standalone local file first, then strip Base64 from snapshot JSON
        if (s.avatarBase64.isNotBlank() && s.id.isNotBlank()) {
            val existingLocal = StudentAvatarImageHelper.readLocalAlbumRaw(appContext, s.id)
            if (existingLocal.isBlank()) {
                StudentAvatarImageHelper.writeLocalAlbumRaw(appContext, s.id, s.avatarBase64)
            }
        }
        put("id", s.id)
        put("groupId", s.groupId)
        put("name", s.name)
        put("studentNumber", s.studentNumber)
        put("blockId", s.blockId)
        put("sortOrder", s.sortOrder)
        put("position", s.position)
        put("avatarInitials", s.avatarInitials)
        put("avatarBase64", "")
        put("hasAvatar", s.effectiveHasAvatar)
        put("avatarHash", s.effectiveAvatarKey)
        put("createdByUid", s.createdByUid)
        put("memberUids", JSONArray(s.memberUids))
        put("lastMemorizedText", s.lastMemorizedText)
        put("lastMemorizedBy", s.lastMemorizedBy)
        put("lastMemorizedAtMillis", s.lastMemorizedAtMillis)
    }

    private fun deserializeStudent(obj: JSONObject): Student {
        val id = obj.optString("id", "")
        val legacyInlineAvatar = obj.optString("avatarBase64", "").trim()
        if (legacyInlineAvatar.isNotBlank() && id.isNotBlank()) {
            val existingLocal = StudentAvatarImageHelper.readLocalAlbumRaw(appContext, id)
            if (existingLocal.isBlank()) {
                StudentAvatarImageHelper.writeLocalAlbumRaw(appContext, id, legacyInlineAvatar)
            }
        }
        val hasAvatarFlag = obj.optBoolean("hasAvatar", false) || legacyInlineAvatar.isNotBlank()
        val hashFlag = obj.optString("avatarHash", "").ifBlank {
            if (legacyInlineAvatar.isNotBlank()) Student.computeAvatarHash(legacyInlineAvatar) else ""
        }
        return Student(
            id = id,
            groupId = obj.optString("groupId", ""),
            name = obj.optString("name", ""),
            studentNumber = obj.optLong("studentNumber", 1L),
            blockId = obj.optString("blockId", ""),
            sortOrder = obj.optLong("sortOrder", 0L),
            position = obj.optDouble("position", 0.0),
            avatarInitials = obj.optString("avatarInitials", ""),
            avatarBase64 = "",
            hasAvatar = hasAvatarFlag,
            avatarHash = hashFlag,
            createdByUid = obj.optString("createdByUid", ""),
            memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids")),
            lastMemorizedText = obj.optString("lastMemorizedText", ""),
            lastMemorizedBy = obj.optString("lastMemorizedBy", ""),
            lastMemorizedAtMillis = obj.optLong("lastMemorizedAtMillis", 0L)
        )
    }

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
        val tsMillis = e.createdAt?.toDate()?.time ?: 0L
        if (tsMillis > 0L) {
            put("createdAtMillis", tsMillis)
        }
    }

    private fun deserializeCardEvent(obj: JSONObject): CardEvent {
        val createdAtMs = obj.optLong("createdAtMillis", 0L)
        val parsedCreatedAt = if (createdAtMs > 0L) Timestamp(Date(createdAtMs)) else null
        return CardEvent(
            id = obj.optString("id", ""),
            groupId = obj.optString("groupId", ""),
            studentId = obj.optString("studentId", ""),
            studentName = obj.optString("studentName", ""),
            cardType = obj.optString("cardType", "ATTENDANCE"),
            points = obj.optLong("points", 3L),
            servantId = obj.optString("servantId", ""),
            servantName = obj.optString("servantName", ""),
            monthKey = obj.optString("monthKey", ""),
            weekKey = obj.optString("weekKey", ""),
            createdAt = parsedCreatedAt
        )
    }

    /**
     * Returns cached historical `cardEvents` for an out-of-month date range `[startDayMillis, endDayMillis]`
     * from memory or local disk so past periods never repeat network `get()` calls.
     */
    fun getCachedHistoricalRangeEvents(
        groupId: String,
        startDayMillis: Long,
        endDayMillis: Long
    ): List<CardEvent>? {
        if (groupId.isBlank()) return null
        val key = "${groupId}_${startDayMillis / 86_400_000L}_${endDayMillis / 86_400_000L}"
        historicalRangeMemoryCache[key]?.let { return it }
        if (isOnMainThread()) {
            ioScope.launch {
                readHistoricalRangeFromDiskInternal(key)
            }
            return null
        }
        return readHistoricalRangeFromDiskInternal(key)
    }

    private fun readHistoricalRangeFromDiskInternal(key: String): List<CardEvent>? {
        historicalRangeMemoryCache[key]?.let { return it }
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "historical_range_cache")
        val file = File(dir, "range_${key.replace(SAFE_USER_ID_REGEX, "_")}.json")
        if (!file.exists()) return null
        return runCatching {
            val arr = JSONArray(file.readText())
            val list = ArrayList<CardEvent>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list.add(deserializeCardEvent(item))
            }
            historicalRangeMemoryCache[key] = list
            list
        }.getOrNull()
    }

    fun saveCachedHistoricalRangeEvents(
        groupId: String,
        startDayMillis: Long,
        endDayMillis: Long,
        events: List<CardEvent>
    ) {
        if (groupId.isBlank()) return
        val key = "${groupId}_${startDayMillis / 86_400_000L}_${endDayMillis / 86_400_000L}"
        historicalRangeMemoryCache[key] = events
        val ctx = appContext ?: return
        ioScope.launch {
            runCatching {
                val dir = File(ctx.filesDir, "historical_range_cache")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "range_${key.replace(SAFE_USER_ID_REGEX, "_")}.json")
                val arr = JSONArray()
                events.forEach { arr.put(serializeCardEvent(it)) }
                file.writeText(arr.toString())
            }
        }
    }

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

    private fun serializeMemberProfile(m: ServantMemberProfile): JSONObject {
        if (m.effectiveAvatarBase64.isNotBlank()) {
            StudentAvatarImageHelper.writeLocalAlbumRaw(appContext, m.localAvatarKey, m.effectiveAvatarBase64)
        }
        val effectiveHasAvatar = m.effectiveHasAvatar
        val effectiveHash = m.avatarHash.ifBlank {
            if (m.effectiveAvatarBase64.isNotBlank()) Student.computeAvatarHash(m.effectiveAvatarBase64) else ""
        }
        val primary = m.effectivePrimaryPhone
        val hidden = !m.effectiveShowPhone
        return JSONObject().apply {
            put("id", m.id.ifBlank { m.uid })
            put("uid", m.uid.ifBlank { m.id })
            put("groupId", m.groupId)
            put("displayName", m.displayName)
            put("phone", primary)
            put("primaryPhone", primary)
            put("secondaryPhone", m.secondaryPhone)
            put("phoneVerified", false)
            put("showPhone", !hidden)
            put("phoneHidden", hidden)
            put("hasAvatar", effectiveHasAvatar)
            put("avatarHash", effectiveHash)
            put("memberUids", JSONArray(m.memberUids))
            m.lastSeenAt?.toDate()?.time?.let { put("lastSeenAtMillis", it) }
        }
    }

    private fun deserializeMemberProfile(obj: JSONObject): ServantMemberProfile {
        val uid = obj.optString("uid", obj.optString("id", ""))
        val legacyBase64 = obj.optString("avatarBase64", obj.optString("avatar", ""))
        if (legacyBase64.isNotBlank() && uid.isNotBlank()) {
            StudentAvatarImageHelper.writeLocalAlbumRaw(appContext, "servant_$uid", legacyBase64)
        }
        val hasAvatar = obj.optBoolean("hasAvatar", legacyBase64.isNotBlank())
        val avatarHash = obj.optString(
            "avatarHash",
            if (legacyBase64.isNotBlank()) Student.computeAvatarHash(legacyBase64) else ""
        )
        val primary = obj.optString("primaryPhone", obj.optString("phone", ""))
        val lastSeenMillis = obj.optLong("lastSeenAtMillis", 0L)
        val parsedLastSeen = if (lastSeenMillis > 0L) Timestamp(Date(lastSeenMillis)) else null
        return ServantMemberProfile(
            id = obj.optString("id", uid),
            uid = uid,
            groupId = obj.optString("groupId", ""),
            displayName = obj.optString("displayName", ""),
            phone = primary,
            primaryPhone = primary,
            secondaryPhone = obj.optString("secondaryPhone", ""),
            phoneVerified = false,
            showPhone = true,
            phoneHidden = false,
            hasAvatar = hasAvatar,
            avatarHash = avatarHash,
            avatarBase64 = "",
            memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids")),
            lastSeenAt = parsedLastSeen
        )
    }

    private val memoryChatReadCounts = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    suspend fun loadChatReadCountsAsync(userId: String): Map<String, Long> {
        if (userId.isBlank()) return emptyMap()
        memoryChatReadCounts[userId]?.let { return it.toMap() }
        return withContext(Dispatchers.IO) {
            loadChatReadCountsFromDiskInternal(userId)
        }
    }

    fun getMemoryChatReadCounts(userId: String): Map<String, Long> {
        if (userId.isBlank()) return emptyMap()
        return memoryChatReadCounts[userId]?.toMap() ?: emptyMap()
    }

    private fun loadChatReadCountsFromDiskInternal(userId: String): Map<String, Long> {
        if (userId.isBlank()) return emptyMap()
        memoryChatReadCounts[userId]?.let { return it.toMap() }
        val ctx = appContext ?: return emptyMap()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString("chat_reads_$userId", null)
        val map = ConcurrentHashMap<String, Long>()
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val obj = JSONObject(rawJson)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val count = obj.optLong(key, 0L)
                    if (key.isNotBlank() && count >= 0L) {
                        map[key] = count
                    }
                }
            }
        }
        memoryChatReadCounts[userId] = map
        return map.toMap()
    }

    fun saveChatLastReadCount(userId: String, roomKey: String, totalReadCount: Long): Map<String, Long> {
        if (userId.isBlank() || roomKey.isBlank() || totalReadCount < 0L) {
            return getMemoryChatReadCounts(userId)
        }
        val userMap = memoryChatReadCounts.getOrPut(userId) { ConcurrentHashMap() }
        val current = userMap[roomKey] ?: 0L
        if (totalReadCount <= current) {
            return userMap.toMap()
        }
        userMap[roomKey] = totalReadCount
        val snapshotMap = userMap.toMap()
        val ctx = appContext
        if (ctx != null) {
            ioScope.launch {
                runCatching {
                    val obj = JSONObject()
                    snapshotMap.forEach { (k, v) -> obj.put(k, v) }
                    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString("chat_reads_$userId", obj.toString())
                        .apply()
                }
            }
        }
        return snapshotMap
    }

    private val memoryWeeklyAttendance = ConcurrentHashMap<String, List<com.example.data.model.AttendanceRecord>>()

    fun getMemoryWeeklyAttendance(groupId: String, weekKey: String): List<com.example.data.model.AttendanceRecord> {
        if (groupId.isBlank() || weekKey.isBlank()) return emptyList()
        return memoryWeeklyAttendance["${groupId}_$weekKey"].orEmpty()
    }

    suspend fun loadWeeklyAttendanceAsync(
        groupId: String,
        weekKey: String
    ): List<com.example.data.model.AttendanceRecord> {
        if (groupId.isBlank() || weekKey.isBlank()) return emptyList()
        val cacheKey = "${groupId}_$weekKey"
        memoryWeeklyAttendance[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            val ctx = appContext ?: return@withContext emptyList()
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString("attendance_$cacheKey", null)
            if (raw.isNullOrBlank()) return@withContext emptyList()
            runCatching {
                val arr = JSONArray(raw)
                val list = ArrayList<com.example.data.model.AttendanceRecord>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val createdSec = obj.optLong("createdAtSec", 0L)
                    val updatedSec = obj.optLong("updatedAtSec", createdSec)
                    list.add(
                        com.example.data.model.AttendanceRecord(
                            id = obj.optString("id", ""),
                            groupId = obj.optString("groupId", groupId),
                            studentId = obj.optString("studentId", ""),
                            studentName = obj.optString("studentName", ""),
                            status = obj.optString("status", "PRESENT"),
                            weekKey = obj.optString("weekKey", weekKey),
                            servantId = obj.optString("servantId", ""),
                            servantName = obj.optString("servantName", ""),
                            note = obj.optString("note", ""),
                            memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids")),
                            createdAt = if (createdSec > 0L) Timestamp(createdSec, 0) else null,
                            updatedAt = if (updatedSec > 0L) Timestamp(updatedSec, 0) else null
                        )
                    )
                }
                memoryWeeklyAttendance[cacheKey] = list
                list
            }.getOrDefault(emptyList())
        }
    }

    fun saveWeeklyAttendance(
        groupId: String,
        weekKey: String,
        records: List<com.example.data.model.AttendanceRecord>
    ) {
        if (groupId.isBlank() || weekKey.isBlank()) return
        val cacheKey = "${groupId}_$weekKey"
        memoryWeeklyAttendance[cacheKey] = records
        val ctx = appContext ?: return
        ioScope.launch {
            runCatching {
                val arr = JSONArray()
                for (rec in records) {
                    val obj = JSONObject()
                    obj.put("id", rec.effectiveId)
                    obj.put("groupId", rec.groupId.ifBlank { groupId })
                    obj.put("studentId", rec.studentId)
                    obj.put("studentName", rec.studentName)
                    obj.put("status", rec.status)
                    obj.put("weekKey", rec.weekKey.ifBlank { weekKey })
                    obj.put("servantId", rec.servantId)
                    obj.put("servantName", rec.servantName)
                    obj.put("note", rec.note)
                    obj.put("memberUids", JSONArray(rec.memberUids))
                    obj.put("createdAtSec", rec.createdAt?.seconds ?: 0L)
                    obj.put("updatedAtSec", rec.updatedAt?.seconds ?: 0L)
                    arr.put(obj)
                }
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("attendance_$cacheKey", arr.toString())
                    .apply()
            }
        }
    }

    private val memoryChildNotes = ConcurrentHashMap<String, List<com.example.data.model.ChildNote>>()
    private val reportedConflictKeys = ConcurrentHashMap.newKeySet<String>()

    fun getMemoryChildNotes(groupId: String): List<com.example.data.model.ChildNote> {
        if (groupId.isBlank()) return emptyList()
        return memoryChildNotes[groupId].orEmpty()
    }

    suspend fun loadChildNotesAsync(groupId: String): List<com.example.data.model.ChildNote> {
        if (groupId.isBlank()) return emptyList()
        memoryChildNotes[groupId]?.let { return it }
        return withContext(Dispatchers.IO) {
            val ctx = appContext ?: return@withContext emptyList()
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString("child_notes_$groupId", null) ?: return@withContext emptyList()
            runCatching {
                val arr = JSONArray(raw)
                val list = ArrayList<com.example.data.model.ChildNote>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val createdSec = obj.optLong("createdAtSec", 0L)
                    list.add(
                        com.example.data.model.ChildNote(
                            id = obj.optString("id", ""),
                            groupId = obj.optString("groupId", groupId),
                            studentId = obj.optString("studentId", ""),
                            studentName = obj.optString("studentName", ""),
                            type = obj.optString("type", "COMMENT"),
                            text = obj.optString("text", ""),
                            status = obj.optString("status", ""),
                            authorUid = obj.optString("authorUid", ""),
                            authorName = obj.optString("authorName", ""),
                            clientCreatedAtMillis = obj.optLong("clientCreatedAtMillis", 0L),
                            memberUids = jsonArrayToStringList(obj.optJSONArray("memberUids")),
                            createdAt = if (createdSec > 0L) Timestamp(createdSec, 0) else null
                        )
                    )
                }
                memoryChildNotes[groupId] = list
                list
            }.getOrDefault(emptyList())
        }
    }

    fun saveChildNotes(groupId: String, notes: List<com.example.data.model.ChildNote>) {
        if (groupId.isBlank()) return
        val capped = notes.take(300)
        memoryChildNotes[groupId] = capped
        val ctx = appContext ?: return
        ioScope.launch {
            runCatching {
                val arr = JSONArray()
                for (n in capped) {
                    val obj = JSONObject()
                    obj.put("id", n.id)
                    obj.put("groupId", n.groupId.ifBlank { groupId })
                    obj.put("studentId", n.studentId)
                    obj.put("studentName", n.studentName)
                    obj.put("type", n.type)
                    obj.put("text", n.text)
                    obj.put("status", n.status)
                    obj.put("authorUid", n.authorUid)
                    obj.put("authorName", n.authorName)
                    obj.put("clientCreatedAtMillis", n.effectiveTimeMillis)
                    obj.put("memberUids", JSONArray(n.memberUids))
                    obj.put("createdAtSec", n.createdAt?.seconds ?: 0L)
                    arr.put(obj)
                }
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("child_notes_$groupId", arr.toString())
                    .apply()
            }
        }
    }

    fun isConflictNotificationReported(groupId: String, conflictKey: String): Boolean {
        val fullKey = "${groupId}_$conflictKey"
        if (reportedConflictKeys.contains(fullKey)) return true
        val ctx = appContext ?: return false
        val saved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("conflict_rep_$fullKey", false)
        if (saved) reportedConflictKeys.add(fullKey)
        return saved
    }

    fun markConflictNotificationReported(groupId: String, conflictKey: String) {
        val fullKey = "${groupId}_$conflictKey"
        reportedConflictKeys.add(fullKey)
        val ctx = appContext ?: return
        ioScope.launch {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("conflict_rep_$fullKey", true)
                .apply()
        }
    }

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

    private fun jsonObjectToStringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val map = LinkedHashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k, "").trim()
            if (k.isNotBlank() && v.isNotBlank()) {
                map[k] = v
            }
        }
        return map
    }
}
