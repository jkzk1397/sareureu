package com.capstone.pause.core.sensing.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.ForegroundSource
import com.capstone.pause.core.sensing.path.AndroidAppKindResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 사용 기록 접근 권한(특별 접근). 설정 화면은 [settingsIntent]. */
object UsageAccess {
    /** 권한이 켜져 있는지. */
    fun isGranted(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 사용 기록 접근 설정 화면. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * 기본 감지 소스(ADR-0004): 화면이 켜져 있을 때만 `queryEvents`를 폴링한다. 꺼지면 켜질 때까지 멈춘다.
 * 숏폼 앱이 앞에 있으면 2초, 아니면 5초 간격. 권한이 없으면 이벤트를 내지 않고 기다린다.
 */
@Singleton
class UsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val kinds: AndroidAppKindResolver,
    private val clock: AppClock,
    private val log: AppLog,
) : ForegroundSource {

    /** 지금 숏폼 앱이 앞에 있는지 알려 주면 폴링을 빠르게 한다(파이프라인이 설정). */
    @Volatile
    var fastPolling: Boolean = false

    override val events: Flow<ForegroundEvent> = channelFlow {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val interactive = MutableStateFlow(power?.isInteractive ?: true)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> interactive.value = true
                    Intent.ACTION_SCREEN_OFF -> interactive.value = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val reducer = UsageEventReducer(kinds::isIgnored, clock.now().toEpochMilli())
        launch {
            pollWhileScreenOn(
                canPoll = { usage != null && UsageAccess.isGranted(context) },
                interactive = interactive,
                intervalMs = { if (fastPolling) FAST_POLL_MS else SLOW_POLL_MS },
                noPermissionDelayMs = NO_PERMISSION_DELAY_MS,
            ) { usage?.let { reducer.reduce(query(it)).forEach { e -> send(e) } } }
        }
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun query(usage: UsageStatsManager): List<RawUsageEvent> {
        val end = clock.now().toEpochMilli()
        val begin = end - WINDOW_MS
        val result: UsageEvents? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                usage.queryEvents(
                    UsageEventsQuery.Builder(begin, end).setEventTypes(*UsageEventTypes.QUERIED).build(),
                )
            } else {
                usage.queryEvents(begin, end)
            }
        }.onFailure { log.w(TAG, "queryEvents failed", it) }.getOrNull()
        // 잠금 상태 등에서는 null이 올 수 있다.
        if (result == null) return emptyList()
        val out = ArrayList<RawUsageEvent>()
        val event = UsageEvents.Event()
        while (result.hasNextEvent()) {
            result.getNextEvent(event)
            if (event.eventType in UsageEventTypes.QUERIED) {
                out += RawUsageEvent(event.packageName.orEmpty(), event.eventType, event.timeStamp)
            }
        }
        return out
    }

    private companion object {
        const val TAG = "UsageStatsSource"
        const val WINDOW_MS = 3 * 60_000L
        const val FAST_POLL_MS = 2_000L
        const val SLOW_POLL_MS = 5_000L
        const val NO_PERMISSION_DELAY_MS = 30_000L
    }
}

/**
 * 화면이 켜져 있을 때만 [poll]을 되풀이한다. 화면이 꺼지면 마지막으로 한 번 읽고(꺼짐 이벤트를 받으려고)
 * 다시 켜질 때까지 멈춘다(배터리, 03 문서 9절). 권한이 없으면 [noPermissionDelayMs]마다 다시 확인만 한다.
 */
internal suspend fun pollWhileScreenOn(
    canPoll: () -> Boolean,
    interactive: kotlinx.coroutines.flow.StateFlow<Boolean>,
    intervalMs: () -> Long,
    noPermissionDelayMs: Long,
    poll: suspend () -> Unit,
) {
    while (true) {
        if (!canPoll()) {
            delay(noPermissionDelayMs)
            continue
        }
        val wasInteractive = interactive.value
        poll()
        if (!wasInteractive) {
            interactive.first { it }
            continue
        }
        delay(intervalMs())
    }
}
