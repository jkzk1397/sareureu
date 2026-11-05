package com.capstone.pause.core.intervention.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyBitmap
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.nudge.OutgoingMessage
import com.capstone.pause.core.model.BuddyExpression
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 알림 담당(03 문서 4절). 채널 4개, 메시지 알림은 `MessagingStyle` + `Person`만 쓰고
 * 대화 단축키(`setShortcutId`)는 붙이지 않는다(ADR-0003, ONE_SHOT 8절 6번). 탭하면 코치 탭.
 */
@Singleton
class PauseNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: AppClock,
) {

    private val manager = NotificationManagerCompat.from(context)

    private val buddyName: String get() = context.getString(DesignR.string.buddy_name)

    /** 알림을 보낼 수 있는지(권한·사용자 설정). */
    fun canNotify(): Boolean = manager.areNotificationsEnabled()

    /** 채널 만들기(여러 번 불러도 된다). */
    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(
                CHANNEL_BUDDY,
                context.getString(R.string.intervention_channel_buddy, buddyName),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.intervention_channel_buddy_desc) },
            NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.intervention_channel_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.intervention_channel_reminder_desc) },
            NotificationChannel(
                CHANNEL_FRIENDS,
                context.getString(R.string.intervention_channel_friends),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.intervention_channel_friends_desc) },
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.intervention_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.intervention_channel_status_desc) },
        ).forEach(system::createNotificationChannel)
    }

    /**
     * 메시지 알림(헤드업). 같은 세션은 [MESSAGE_ID] 하나를 갱신한다(연달아 쌓지 않음). 실제로 보냈으면 true.
     * 친구 잔소리는 friends 채널 그룹, 르르는 buddy_message 채널.
     */
    fun postBuddyMessage(message: OutgoingMessage): Boolean {
        if (!canNotify()) return false
        ensureChannels()
        val sender = Person.Builder()
            .setName(message.senderName)
            .setIcon(
                if (message.fromFriend) {
                    null
                } else {
                    IconCompat.createWithBitmap(BuddyBitmap.render(context, BuddyExpression.CALLING, LARGE_ICON_PX))
                },
            )
            .setBot(!message.fromFriend)
            .build()
        val me = Person.Builder().setName(context.getString(R.string.intervention_me)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(message.text, clock.now().toEpochMilli(), sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_BUDDY)
            .setSmallIcon(DesignR.drawable.designsystem_ic_buddy_silhouette)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(if (message.fromFriend) GROUP_FRIENDS else GROUP_BUDDY)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(openTab(TAB_COACH, REQUEST_MESSAGE))
            .build()
        return notifySafely(MESSAGE_ID, notification)
    }

    /** 예약·미리·주간 알림(reminder 채널). */
    fun postReminder(id: Int, title: String, body: String, tab: String) {
        if (!canNotify()) return
        ensureChannels()
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(DesignR.drawable.designsystem_ic_buddy_silhouette)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openTab(tab, id))
            .build()
        notifySafely(id, notification)
    }

    /** 감지 서비스 상태 알림(낮은 중요도). */
    fun statusNotification(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(DesignR.drawable.designsystem_ic_buddy_silhouette)
            .setContentTitle(context.getString(R.string.intervention_status_title))
            .setContentText(context.getString(R.string.intervention_status_body))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openTab(TAB_TODAY, REQUEST_STATUS))
            .build()
    }

    /** 메시지 알림 지우기(세션이 끝났을 때). */
    fun clearBuddyMessage() = manager.cancel(MESSAGE_ID)

    private fun openTab(tab: String, requestCode: Int): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(EXTRA_OPEN_TAB, tab)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notifySafely(id: Int, notification: Notification): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        return try {
            manager.notify(id, notification)
            true
        } catch (_: SecurityException) {
            // 사용자가 그 사이 권한을 거둔 경우: 오버레이 카드만으로 계속 돈다.
            false
        }
    }

    /** 채널·딥링크 상수. */
    companion object {
        /** 메시지 알림 채널(헤드업). */
        const val CHANNEL_BUDDY = "buddy_message"

        /** 예약·미리 알림 채널. */
        const val CHANNEL_REMINDER = "reminder"

        /** 친구 채널. */
        const val CHANNEL_FRIENDS = "friends"

        /** 감지 상태 채널. */
        const val CHANNEL_STATUS = "service_status"

        /** MainActivity가 읽는 탭 엑스트라. */
        const val EXTRA_OPEN_TAB = "open_tab"

        /** 코치 탭. */
        const val TAB_COACH = "coach"

        /** 오늘 탭. */
        const val TAB_TODAY = "today"

        /** 리포트 탭. */
        const val TAB_REPORT = "report"

        /** 서비스 상태 알림 ID. */
        const val STATUS_ID = 1001

        /** 메시지 알림 ID(세션마다 갱신). */
        const val MESSAGE_ID = 1002

        private const val GROUP_BUDDY = "buddy"
        private const val GROUP_FRIENDS = "friends"
        private const val LARGE_ICON_PX = 144
        private const val REQUEST_MESSAGE = 11
        private const val REQUEST_STATUS = 12
    }
}
