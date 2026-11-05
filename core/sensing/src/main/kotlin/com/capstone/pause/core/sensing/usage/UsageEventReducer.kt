package com.capstone.pause.core.sensing.usage

import com.capstone.pause.core.sensing.ForegroundEvent
import java.time.Instant

/** `UsageEvents.Event`에서 필요한 값만 뽑은 것(순수 테스트용). */
internal data class RawUsageEvent(val packageName: String, val type: Int, val timeMs: Long)

/** `UsageEvents.Event` 종류 값(API 29+). 안드로이드 상수와 같다. */
internal object UsageEventTypes {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_HIDDEN = 18

    /** 조회할 종류. */
    val QUERIED =
        intArrayOf(ACTIVITY_RESUMED, ACTIVITY_PAUSED, SCREEN_INTERACTIVE, SCREEN_NON_INTERACTIVE, KEYGUARD_HIDDEN)
}

/**
 * 폴링마다 '최근 N분' 창을 통째로 다시 읽어 상태를 다시 만든다(이벤트가 늦게 올 수 있어서).
 * 액티비티 단위 이벤트를 패키지 단위로 묶는다: 맨 앞 앱 = 마지막으로 RESUMED된 패키지.
 * 이미 내보낸 시각보다 이른 전환은 다시 내보내지 않고, 늦게 와서 맨 앞 앱이 바뀌었으면 마지막 시각으로 한 번 고쳐 낸다.
 */
internal class UsageEventReducer(private val isIgnored: (String) -> Boolean, startAtMs: Long) {
    private var lastEmittedMs: Long = startAtMs
    private var lastForeground: String? = null
    private var initialized = false

    /** 창 안의 이벤트(아무 순서)를 받아 새로 내보낼 이벤트를 시간순으로 돌려준다. */
    fun reduce(events: List<RawUsageEvent>): List<ForegroundEvent> {
        val out = mutableListOf<ForegroundEvent>()
        var replayForeground: String? = null
        var replayForegroundAt = 0L
        for (e in events.sortedBy { it.timeMs }) {
            val fresh = e.timeMs > lastEmittedMs
            when (e.type) {
                UsageEventTypes.ACTIVITY_RESUMED -> {
                    if (isIgnored(e.packageName) || e.packageName == replayForeground) continue
                    replayForeground = e.packageName
                    replayForegroundAt = e.timeMs
                    if (fresh && initialized && e.packageName != lastForeground) {
                        out += ForegroundEvent.AppForeground(e.packageName, Instant.ofEpochMilli(e.timeMs))
                        lastForeground = e.packageName
                        lastEmittedMs = e.timeMs
                    }
                }

                UsageEventTypes.SCREEN_NON_INTERACTIVE -> {
                    replayForeground = null
                    if (fresh && initialized) {
                        out += ForegroundEvent.ScreenOff(Instant.ofEpochMilli(e.timeMs))
                        lastForeground = null
                        lastEmittedMs = e.timeMs
                    }
                }

                UsageEventTypes.SCREEN_INTERACTIVE -> if (fresh && initialized) {
                    out += ForegroundEvent.ScreenOn(Instant.ofEpochMilli(e.timeMs))
                    lastEmittedMs = e.timeMs
                }

                UsageEventTypes.KEYGUARD_HIDDEN -> if (fresh && initialized) {
                    out += ForegroundEvent.Unlock(Instant.ofEpochMilli(e.timeMs))
                    lastEmittedMs = e.timeMs
                }

                else -> Unit
            }
        }
        // 첫 폴링은 지금 맨 앞 앱만 알린다(과거 기록으로 세션을 만들지 않는다). 늦게 온 이벤트로 바뀐 것도 여기서 고친다.
        val finalForeground = replayForeground
        if (finalForeground != null && finalForeground != lastForeground) {
            val at = maxOf(replayForegroundAt, lastEmittedMs)
            out += ForegroundEvent.AppForeground(finalForeground, Instant.ofEpochMilli(at))
            lastForeground = finalForeground
            lastEmittedMs = at
        }
        initialized = true
        return out
    }
}
