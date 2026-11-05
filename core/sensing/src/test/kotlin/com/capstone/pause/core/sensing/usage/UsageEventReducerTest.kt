package com.capstone.pause.core.sensing.usage

import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.usage.UsageEventTypes.ACTIVITY_PAUSED
import com.capstone.pause.core.sensing.usage.UsageEventTypes.ACTIVITY_RESUMED
import com.capstone.pause.core.sensing.usage.UsageEventTypes.KEYGUARD_HIDDEN
import com.capstone.pause.core.sensing.usage.UsageEventTypes.SCREEN_INTERACTIVE
import com.capstone.pause.core.sensing.usage.UsageEventTypes.SCREEN_NON_INTERACTIVE
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class UsageEventReducerTest {
    private fun ev(pkg: String, type: Int, t: Long) = RawUsageEvent(pkg, type, t)

    private fun reducer() = UsageEventReducer(isIgnored = { it == "com.android.systemui" }, startAtMs = 1_000)

    @Test
    fun firstPollOnlyReportsCurrentForeground() {
        val r = reducer()
        val out = r.reduce(listOf(ev("a", ACTIVITY_RESUMED, 100), ev("b", ACTIVITY_RESUMED, 500)))
        assertEquals(listOf<ForegroundEvent>(ForegroundEvent.AppForeground("b", Instant.ofEpochMilli(1_000))), out)
    }

    @Test
    fun rePollingSameWindowDoesNotDuplicate() {
        val r = reducer()
        r.reduce(listOf(ev("a", ACTIVITY_RESUMED, 500)))
        val window = listOf(
            ev("a", ACTIVITY_RESUMED, 500),
            ev("a", ACTIVITY_PAUSED, 2_000),
            ev("com.android.systemui", ACTIVITY_RESUMED, 2_001),
            ev("b", ACTIVITY_RESUMED, 2_010),
        )
        val first = r.reduce(window)
        assertEquals(listOf<ForegroundEvent>(ForegroundEvent.AppForeground("b", Instant.ofEpochMilli(2_010))), first)
        assertTrue(r.reduce(window).isEmpty())
    }

    @Test
    fun screenAndKeyguardEventsInOrder() {
        val r = reducer()
        r.reduce(listOf(ev("a", ACTIVITY_RESUMED, 500)))
        val out = r.reduce(
            listOf(
                ev("", SCREEN_NON_INTERACTIVE, 3_000),
                ev("", SCREEN_INTERACTIVE, 9_000),
                ev("", KEYGUARD_HIDDEN, 9_500),
                ev("a", ACTIVITY_RESUMED, 9_600),
            ),
        )
        assertEquals(
            listOf(
                ForegroundEvent.ScreenOff(Instant.ofEpochMilli(3_000)),
                ForegroundEvent.ScreenOn(Instant.ofEpochMilli(9_000)),
                ForegroundEvent.Unlock(Instant.ofEpochMilli(9_500)),
                ForegroundEvent.AppForeground("a", Instant.ofEpochMilli(9_600)),
            ),
            out,
        )
    }

    @Test
    fun lateEventCorrectsForegroundAtLastEmittedTime() {
        val r = reducer()
        r.reduce(listOf(ev("a", ACTIVITY_RESUMED, 500)))
        r.reduce(listOf(ev("a", ACTIVITY_RESUMED, 500), ev("", KEYGUARD_HIDDEN, 5_000)))
        // b가 4_000에 앞에 왔다는 이벤트가 늦게 도착
        val out = r.reduce(
            listOf(ev("a", ACTIVITY_RESUMED, 500), ev("b", ACTIVITY_RESUMED, 4_000), ev("", KEYGUARD_HIDDEN, 5_000)),
        )
        assertEquals(listOf<ForegroundEvent>(ForegroundEvent.AppForeground("b", Instant.ofEpochMilli(5_000))), out)
    }
}
