package com.capstone.pause.core.intervention.nudge

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.capstone.pause.core.model.ContextSlot
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 감지를 껐다 켜거나(서비스 재시작) 앱이 강제 종료된 뒤에도 오늘의 단계·연장·오늘은 그만이 풀리지 않는지.
 * 엔진을 새로 만드는 것이 곧 '서비스가 다시 뜬 것'이다.
 */
class NudgeEngineRestoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private val start = LocalDateTime.of(2026, 11, 3, 22, 30).atZone(zone).toInstant()

    private fun at(minutes: Double, from: Instant = start): Instant = from.plusMillis((minutes * 60_000).toLong())

    private fun live(s: Instant = start) = LiveInput(s, ContextSlot.PRE_SLEEP, "유튜브", 15)

    private val writer = object : NudgeWriter {
        override fun card(level: Int, live: LiveInput, elapsedMinutes: Int) = CardContent(
            level = level,
            context = live.context,
            appLabel = live.appLabel,
            elapsedMinutes = elapsedMinutes,
            text = "card$level",
            copyId = "c$level",
            extendMinutes = if (level >= 4) 3 else 5,
        )

        override fun message(live: LiveInput, elapsedMinutes: Int) = OutgoingMessage("르르", false, "msg", "m")

        override fun praise(live: LiveInput) = "잘했어"
    }

    private fun engine() = NudgeEngine(zone, writer)

    private fun List<NudgeCommand>.card() = filterIsInstance<NudgeCommand.ShowCard>().singleOrNull()?.card

    /** 3단계 카드를 연장한 직후(25.3분)까지 간 엔진. */
    private fun engineAfterCard3(): NudgeEngine {
        val e = engine()
        e.tick(at(15.0), live())
        e.onAction(at(15.1), NudgeUserAction.Extend(1), live())
        e.tick(at(20.1), live())
        e.onAction(at(20.2), NudgeUserAction.Extend(2), live())
        assertEquals(3, e.tick(at(25.2), live()).card()?.level)
        e.onAction(at(25.3), NudgeUserAction.Extend(3), live())
        return e
    }

    @Test
    fun withoutRestoreANewEngineStartsOverAtCard1() {
        // 고치기 전 동작(재현): 감지를 껐다 켜면 새 엔진이 15분을 처음부터 다시 세고 1단계부터 시작했다.
        engineAfterCard3()
        val fresh = engine()
        val again = at(26.0)
        assertNull(fresh.tick(at(1.0, again), live(again)).card())
        assertEquals(1, fresh.tick(at(15.0, again), live(again)).card()?.level)
    }

    @Test
    fun restoreContinuesTheLadderWhenMonitoringIsToggledOffAndOn() {
        val old = engineAfterCard3()
        val snapshot = old.snapshot(at(26.0))
        // 30초 뒤 다시 켰다: 새 세션(시작 시각이 다름)이지만 같은 세션으로 이어 간다.
        val again = at(26.5)
        val e = engine().apply { restore(snapshot, again) }
        assertEquals(3, e.todayExtensions)
        // 3단계 연장(5분)은 25.3분에 시작했고 26.0분에 끊겼다 → 4.3분 남음.
        assertNull(e.tick(at(4.2, again), live(again)).card())
        assertEquals(4, e.tick(at(4.4, again), live(again)).card()?.level)
    }

    @Test
    fun cardOnScreenWhenStoppedComesBackAtTheSameLevel() {
        val old = engine()
        old.tick(at(15.0), live())
        old.onAction(at(15.1), NudgeUserAction.Extend(1), live())
        assertEquals(2, old.tick(at(20.1), live()).card()?.level)
        val snapshot = old.snapshot(at(20.5))
        val again = at(21.0)
        val e = engine().apply { restore(snapshot, again) }
        val out = e.tick(at(0.1, again), live(again))
        assertEquals(2, out.card()?.level)
        assertFalse(out.any { it is NudgeCommand.PostBuddyMessage }, "message was already sent before the restart")
    }

    @Test
    fun snoozeTodaySurvivesRestartAndEndsAtFourAm() {
        val old = engine()
        old.tick(at(15.0), live())
        old.onAction(at(15.1), NudgeUserAction.Extend(1), live())
        old.tick(at(20.1), live())
        old.onAction(at(20.2), NudgeUserAction.SnoozeToday(2), live())
        val snapshot = old.snapshot(at(21.0))

        val again = at(40.0)
        val e = engine().apply { restore(snapshot, again) }
        assertTrue(e.isSnoozed(again))
        assertNull(e.tick(at(30.0, again), live(again)).card(), "still snoozed after restart")

        // 새벽 4시가 지나면 오늘은 그만과 연장 횟수가 초기화된다.
        val nextDay = LocalDateTime.of(2026, 11, 4, 9, 0).atZone(zone).toInstant()
        val tomorrow = engine().apply { restore(snapshot, nextDay) }
        assertFalse(tomorrow.isSnoozed(nextDay))
        assertEquals(0, tomorrow.todayExtensions)
        assertEquals(1, tomorrow.tick(at(15.0, nextDay), live(nextDay)).card()?.level)
    }

    @Test
    fun afterFiveMinutesItIsANewSessionButTodayCountsStay() {
        val old = engineAfterCard3()
        val snapshot = old.snapshot(at(26.0))
        val again = at(26.0 + 5.5)
        val e = engine().apply { restore(snapshot, again) }
        assertEquals(3, e.todayExtensions)
        assertNull(e.tick(at(14.9, again), live(again)).card())
        assertEquals(1, e.tick(at(15.0, again), live(again)).card()?.level)
    }

    @Test
    fun stoppedSessionIsNotCarried() {
        val old = engine()
        old.tick(at(15.0), live())
        old.onAction(at(15.1), NudgeUserAction.Stop(1), live())
        assertNull(old.snapshot(at(15.2)).session)
    }

    @Test
    fun closeFinishesTheSessionSoItsNudgesAreNotLost() {
        val e = engineAfterCard3()
        e.close(at(26.0))
        assertEquals(3, e.drainFinished().single().events.size)
    }

    @Test
    fun storeRoundTripsTheSnapshot() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = NudgeStateStore(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "monitoring.preferences_pb") },
        )
        assertNull(store.load())
        val withCard = engine().apply {
            tick(at(15.0), live())
            onAction(at(15.1), NudgeUserAction.Extend(1), live())
            tick(at(20.1), live())
        }.snapshot(at(20.5))
        store.save(withCard)
        assertEquals(withCard, store.load())

        val snoozed = engine().apply {
            tick(at(15.0), live())
            onAction(at(15.1), NudgeUserAction.Extend(1), live())
            tick(at(20.1), live())
            onAction(at(20.2), NudgeUserAction.SnoozeToday(2), live())
            tick(at(21.0), null)
            tick(at(23.0), null)
        }.snapshot(at(23.0))
        store.save(snoozed)
        assertEquals(snoozed, store.load())
        assertNull(store.load()?.session)

        store.clear()
        assertNull(store.load())
        scope.cancel()
    }
}
