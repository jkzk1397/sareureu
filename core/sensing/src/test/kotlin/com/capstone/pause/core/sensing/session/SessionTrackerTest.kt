package com.capstone.pause.core.sensing.session

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.ForegroundEvent.AppForeground
import com.capstone.pause.core.sensing.ForegroundEvent.ScreenOff
import com.capstone.pause.core.sensing.ForegroundEvent.Unlock
import com.capstone.pause.core.sensing.context.ContextResult
import com.capstone.pause.core.sensing.path.INSTAGRAM
import com.capstone.pause.core.sensing.path.KAKAO
import com.capstone.pause.core.sensing.path.LAUNCHER
import com.capstone.pause.core.sensing.path.YOUTUBE
import com.capstone.pause.core.sensing.path.isShortformTest
import com.capstone.pause.core.sensing.path.testKinds
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SessionTrackerTest {
    private val t0 = Instant.parse("2026-11-03T13:00:00Z")

    private fun at(seconds: Long) = t0.plusSeconds(seconds)

    private fun tracker() = SessionTracker(
        isShortform = isShortformTest,
        kinds = testKinds,
        classify = { _, _ -> ContextResult(ContextSlot.PRE_SLEEP, Confidence.HIGH) },
        zone = ZoneId.of("Asia/Seoul"),
    )

    /** 이벤트를 차례로 넣고 [tickAt]에 한 번 틱. 끝난 세션 전부. */
    private fun run(events: List<ForegroundEvent>, tickAt: Long? = null): Pair<SessionTracker, List<ShortformSession>> {
        val t = tracker()
        val done = events.flatMap { t.onEvent(it) } + (tickAt?.let { t.onTick(at(it)) } ?: emptyList())
        return t to done
    }

    private fun ShortformSession.minutes() = Duration.between(start, end).toMinutes()

    @Test
    fun returningWithin60sIsSameSession() {
        val (_, done) = run(
            listOf(
                AppForeground(KAKAO, at(0)),
                AppForeground(YOUTUBE, at(10)),
                AppForeground(KAKAO, at(610)),
                AppForeground(YOUTUBE, at(650)),
                AppForeground(LAUNCHER, at(1210)),
            ),
            tickAt = 1271,
        )
        val s = done.single()
        assertEquals(at(10), s.start)
        assertEquals(at(1210), s.end)
        assertEquals(2, s.segments.size)
        assertEquals(EntryPoint.FromApp(AppKind.MESSENGER), s.entry)
        assertEquals(ExitPoint.Home, s.exit)
    }

    @Test
    fun leavingFor60sOrMoreSplitsSessions() {
        val (_, done) = run(
            listOf(
                AppForeground(YOUTUBE, at(0)),
                AppForeground(KAKAO, at(300)),
                AppForeground(YOUTUBE, at(360)),
                AppForeground(KAKAO, at(600)),
            ),
            tickAt = 660,
        )
        assertEquals(2, done.size)
        assertEquals(5L, done[0].minutes())
        assertEquals(ExitPoint.ToApp(AppKind.MESSENGER), done[0].exit)
        assertEquals(EntryPoint.FromApp(AppKind.MESSENGER), done[1].entry)
    }

    @Test
    fun tickBefore60sKeepsSessionOpen() {
        val (t, done) = run(listOf(AppForeground(YOUTUBE, at(0)), AppForeground(KAKAO, at(100))), tickAt = 159)
        assertTrue(done.isEmpty())
        assertNull(t.liveSession.value, "다른 앱에 있는 동안은 보는 중이 아니다")
    }

    @Test
    fun screenOffEndsSessionImmediately() {
        val (_, done) = run(listOf(Unlock(at(0)), AppForeground(YOUTUBE, at(5)), ScreenOff(at(905))))
        val s = done.single()
        assertEquals(EntryPoint.Unlock, s.entry)
        assertEquals(ExitPoint.ScreenOff, s.exit)
        assertEquals(15L, s.minutes())
    }

    @Test
    fun leavingThenScreenOffWithin10sCountsAsScreenOff() {
        val (_, done) = run(listOf(AppForeground(YOUTUBE, at(0)), AppForeground(LAUNCHER, at(600)), ScreenOff(at(608))))
        assertEquals(ExitPoint.ScreenOff, done.single().exit)
        assertEquals(at(600), done.single().end)
    }

    @Test
    fun dominoShortformSwitchIsOneSessionWithTwoSegments() {
        val (_, done) = run(
            listOf(
                AppForeground(LAUNCHER, at(0)),
                AppForeground(YOUTUBE, at(20)),
                AppForeground(INSTAGRAM, at(320)),
                ScreenOff(at(920)),
            ),
        )
        val s = done.single()
        assertEquals(EntryPoint.Home, s.entry)
        assertEquals(listOf(YOUTUBE, INSTAGRAM), s.segments.map { it.packageName })
        assertEquals(15L, s.minutes())
    }

    @Test
    fun nudgeStopThenHomeIsAfterNudgeStop() {
        val t = tracker()
        t.onEvent(AppForeground(YOUTUBE, at(0)))
        t.onNudgeStop(at(900))
        t.onEvent(AppForeground(LAUNCHER, at(902)))
        val s = t.onTick(at(963)).single()
        assertEquals(ExitPoint.AfterNudgeStop, s.exit)
        assertTrue(s.stoppedByNudge)
    }

    @Test
    fun liveSessionWhileWatching() {
        val t = tracker()
        t.onEvent(AppForeground(YOUTUBE, at(0)))
        t.onTick(at(120))
        val live = assertNotNull(t.liveSession.value)
        assertNull(live.end)
        assertEquals(at(120), live.segments.last().end)
        t.onEvent(ScreenOff(at(200)))
        assertNull(t.liveSession.value)
    }
}
