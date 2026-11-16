package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ChatMessageType(val id: String) {
    TEXT("TEXT"),
    IMAGE("IMAGE"),
    AUDIO("AUDIO"),
    SYSTEM("SYSTEM");

    companion object {
        fun fromId(raw: String?): ChatMessageType {
            return entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: TEXT
        }
    }
}

/**
 * Unified media payload produced by [com.example.data.media.ChatMediaStorageProvider]
 * so the chat repository and UI remain decoupled from whether media is stored inline (Spark free tier)
 * or in external cloud storage in the future.
 */
@Immutable
data class PreparedChatMediaPayload(
    val type: ChatMessageType,
    val textLabel: String,
    val mediaBase64: String = "",
    val mediaUrl: String = "",
    val mediaMimeType: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val audioDurationSec: Int = 0
)

/**
 * Group chat metadata stored at `groups/{groupId}/chatMeta/main`.
 * Contains last message preview and total message counter (`totalMessagesCount`)
 * so unread badges are computed locally without listening to all messages.
 */
@Immutable
@IgnoreExtraProperties
data class GroupChatMeta(
    @DocumentId val id: String = "main",
    val groupId: String = "",
    val lastMessageId: String = "",
    val lastMessageText: String = "",
    val lastMessageType: String = ChatMessageType.TEXT.id,
    val lastMessageSenderUid: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageAt: Timestamp? = null,
    val totalMessagesCount: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Private 1-to-1 chat room metadata stored at `dms/{chatId}` where
 * `chatId = dm_{uidA}_{uidB}` with `uidA` and `uidB` sorted lexicographically.
 * Contains `participants` (`[uidA, uidB]`) for zero-`get()` security rule checks.
 */
@Immutable
@IgnoreExtraProperties
data class DmChatMeta(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val groupId: String = "",
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessageId: String = "",
    val lastMessageText: String = "",
    val lastMessageType: String = ChatMessageType.TEXT.id,
    val lastMessageSenderUid: String = "",
    val lastMessageSenderName: String = "",
    val lastMessageAt: Timestamp? = null,
    val totalMessagesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveChatId: String
        get() = chatId.ifBlank { id }

    fun peerUid(currentUid: String): String {
        return participants.firstOrNull { it != currentUid }
            ?: participants.firstOrNull().orEmpty()
    }

    fun peerDisplayName(currentUid: String, fallbackMembers: List<ServantMemberProfile>): String {
        val peer = peerUid(currentUid)
        val fromMember = fallbackMembers.firstOrNull { it.effectiveUid == peer }?.displayName?.takeIf { it.isNotBlank() }
        if (fromMember != null) return fromMember
        return participantNames[peer]?.takeIf { it.isNotBlank() } ?: "خادم"
    }

    companion object {
        fun buildDmChatId(uid1: String, uid2: String): String {
            val sorted = listOf(uid1.trim(), uid2.trim()).sorted()
            return "dm_${sorted[0]}_${sorted[1]}"
        }
    }
}

/**
 * Message document stored either in:
 * - `groups/{groupId}/messages/{messageId}` (with `memberUids`)
 * - `dms/{chatId}/messages/{messageId}` (with `participants`)
 */
@Immutable
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val groupId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val type: String = ChatMessageType.TEXT.id,
    val text: String = "",
    val isDeleted: Boolean = false,
    val mediaBase64: String = "",
    val mediaUrl: String = "",
    val mediaMimeType: String = "",
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val audioDurationSec: Int = 0,
    val memberUids: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val isPendingOffline: Boolean = false,
    val clientCreatedAtMillis: Long = 0L
) {
    val messageType: ChatMessageType
        get() = ChatMessageType.fromId(type)

    val displayText: String
        get() = if (isDeleted) DELETED_MESSAGE_PLACEHOLDER else text

    val effectiveTimeMillis: Long
        get() = createdAt?.toDate()?.time ?: clientCreatedAtMillis

    companion object {
        const val MAX_TEXT_LENGTH = 1000
        const val MAX_SYSTEM_TEXT_LENGTH = 4000
        const val ATTENDANCE_REPORT_V2_PREFIX = "[ATTENDANCE_V2]"
        const val PAGE_SIZE = 30
        const val DELETED_MESSAGE_PLACEHOLDER = "تم حذف الرسالة"
        const val MAX_IMAGE_DIMENSION_PX = 1024
        const val MAX_IMAGE_BASE64_CHARS = 60_000
        const val MAX_AUDIO_BASE64_CHARS = 100_000
        const val MAX_AUDIO_DURATION_SEC = 30
        const val IMAGE_DEFAULT_LABEL = "📷 صورة"
        const val AUDIO_DEFAULT_LABEL = "🎤 رسالة صوتية"
    }
}

/**
 * Identifies which conversation room is currently opened by the user.
 */
@Immutable
sealed interface ActiveChatRoom {
    val roomKey: String

    @Immutable
    data class GroupRoom(
        val groupId: String,
        val groupName: String
    ) : ActiveChatRoom {
        override val roomKey: String
            get() = "group_$groupId"
    }

    @Immutable
    data class DirectRoom(
        val chatId: String,
        val groupId: String,
        val peerUid: String,
        val peerName: String
    ) : ActiveChatRoom {
        override val roomKey: String
            get() = chatId
    }
}

/**
 * Helper for formatting Last Seen ("نشط الآن" if < 3 minutes, else "آخر ظهور منذ..."),
 * message timestamps, and day separators in Arabic.
 */
object ChatFormatHelper {
    private const val ACTIVE_NOW_THRESHOLD_MS = 3 * 60 * 1000L // 3 minutes
    const val LAST_SEEN_WRITE_THROTTLE_MS = 5 * 60 * 1000L // 5 minutes

    private val arabicLocale = Locale("ar", "EG")

    fun isServantActiveNow(lastSeenAt: Timestamp?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val seenMillis = lastSeenAt?.toDate()?.time ?: return false
        val diff = nowMillis - seenMillis
        return diff in -60_000L..ACTIVE_NOW_THRESHOLD_MS
    }

    fun formatLastSeenStatus(lastSeenAt: Timestamp?, nowMillis: Long = System.currentTimeMillis()): String {
        val seenMillis = lastSeenAt?.toDate()?.time ?: return "آخر ظهور غير متاح"
        val diffMs = (nowMillis - seenMillis).coerceAtLeast(0L)
        if (diffMs < ACTIVE_NOW_THRESHOLD_MS) {
            return "نشط الآن"
        }
        val minutes = (diffMs / 60_000L).toInt()
        if (minutes < 60) {
            return when {
                minutes <= 1 -> "آخر ظهور منذ دقيقة"
                minutes == 2 -> "آخر ظهور منذ دقيقتين"
                minutes in 3..10 -> "آخر ظهور منذ $minutes دقائق"
                else -> "آخر ظهور منذ $minutes دقيقة"
            }
        }
        val hours = minutes / 60
        if (hours < 24) {
            return when {
                hours == 1 -> "آخر ظهور منذ ساعة"
                hours == 2 -> "آخر ظهور منذ ساعتين"
                hours in 3..10 -> "آخر ظهور منذ $hours ساعات"
                else -> "آخر ظهور منذ $hours ساعة"
            }
        }
        val days = hours / 24
        return when {
            days == 1 -> "آخر ظهور أمس"
            days == 2 -> "آخر ظهور منذ يومين"
            days in 3..10 -> "آخر ظهور منذ $days أيام"
            else -> {
                val sdf = SimpleDateFormat("d MMM", arabicLocale)
                "آخر ظهور ${sdf.format(Date(seenMillis))}"
            }
        }
    }

    fun formatMessageTime(millis: Long): String {
        if (millis <= 0L) return "الآن"
        val sdf = SimpleDateFormat("hh:mm a", arabicLocale)
        return sdf.format(Date(millis))
    }

    fun formatConversationListTime(timestamp: Timestamp?, nowMillis: Long = System.currentTimeMillis()): String {
        val millis = timestamp?.toDate()?.time ?: return ""
        if (millis <= 0L) return ""
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val calMsg = Calendar.getInstance().apply { timeInMillis = millis }

        val sameYear = calNow.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR)
        val sameDay = sameYear && calNow.get(Calendar.DAY_OF_YEAR) == calMsg.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return SimpleDateFormat("hh:mm a", arabicLocale).format(Date(millis))
        }
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterdayCal.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == calMsg.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "أمس"
        }
        return SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(millis))
    }

    fun daySeparatorKey(millis: Long): String {
        if (millis <= 0L) return "today"
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(Locale.US, y, m, d)
    }

    fun formatDaySeparatorLabel(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return "اليوم"
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val calMsg = Calendar.getInstance().apply { timeInMillis = millis }
        if (calNow.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calMsg.get(Calendar.DAY_OF_YEAR)
        ) {
            return "اليوم"
        }
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }
        if (yesterdayCal.get(Calendar.YEAR) == calMsg.get(Calendar.YEAR) &&
            yesterdayCal.get(Calendar.DAY_OF_YEAR) == calMsg.get(Calendar.DAY_OF_YEAR)
        ) {
            return "أمس"
        }
        return SimpleDateFormat("EEEE، d MMMM yyyy", arabicLocale).format(Date(millis))
    }

    fun formatUnreadBadge(count: Int): String {
        return when {
            count <= 0 -> ""
            count > 99 -> "99+"
            else -> count.toString()
        }
    }

    fun formatAudioDuration(seconds: Int): String {
        val safeSec = seconds.coerceIn(0, 3600)
        val mins = safeSec / 60
        val remSecs = safeSec % 60
        return "%d:%02d".format(Locale.US, mins, remSecs)
    }
}
