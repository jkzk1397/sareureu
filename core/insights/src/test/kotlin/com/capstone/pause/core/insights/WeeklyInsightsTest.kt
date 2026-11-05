package com.capstone.pause.core.insights

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.AppSegment
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.Surface
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

private val now = at("2026-11-10T12:00:00")

private fun s(
    start: String,
    minutes: Long,
    slot: ContextSlot,
    entry: EntryPoint = EntryPoint.Unknown,
    exit: ExitPoint? = ExitPoint.Home,
    id: Long = 0,
    stopped: Boolean = false,
) = session(start, minutes, slot, id).let {
    it.copy(
        entry = entry,
        exit = exit,
        stoppedByNudge = stopped,
        segments = listOf(AppSegment("com.google.android.youtube", Surface.SHORTS, it.start, it.end ?: it.start)),
    )
}

class CoverageTest {
    @Test
    fun aliveInsideScreenOnCounts() {
        val on = listOf(TimeSpan(at("2026-11-03T10:00:00"), at("2026-11-03T11:00:00")))
        val alive = listOf(TimeSpan(at("2026-11-03T10:30:00"), at("2026-11-03T12:00:00")))
        assertEquals(0.5, Coverage.compute(on, alive))
    }

    @Test
    fun overlappingSpansAreMergedAndNoScreenIsNull() {
        val on = listOf(
            TimeSpan(at("2026-11-03T10:00:00"), at("2026-11-03T10:40:00")),
            TimeSpan(at("2026-11-03T10:20:00"), at("2026-11-03T11:00:00")),
        )
        assertEquals(1.0, Coverage.compute(on, on))
        assertNull(Coverage.compute(emptyList(), on))
    }
}

class SessionStatsTest {
    @Test
    fun lengthBucketsBoundaries() {
        assertEquals(LengthBucket.UNDER_5, LengthBucket.of(4.99))
        assertEquals(LengthBucket.FROM_5_TO_10, LengthBucket.of(5.0))
        assertEquals(LengthBucket.FROM_20_TO_40, LengthBucket.of(20.0))
        assertEquals(LengthBucket.OVER_40, LengthBucket.of(40.0))
    }

    @Test
    fun medianEvenAndOdd() {
        assertEquals(2.0, SessionStats.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, SessionStats.median(listOf(1.0, 2.0, 3.0, 4.0)))
        assertEquals(0.0, SessionStats.median(emptyList()))
    }

    @Test
    fun stopRateCountsSessionsWithCards() {
        val sessions = listOf(
            s("2026-11-03T22:00:00", 20, ContextSlot.PRE_SLEEP, id = 1, stopped = true),
            s("2026-11-04T22:00:00", 20, ContextSlot.PRE_SLEEP, id = 2),
            s("2026-11-05T22:00:00", 5, ContextSlot.PRE_SLEEP, id = 3),
        )
        val nudges = listOf(
            NudgeEvent(1, now, 1, null, NudgeResponse.STOP, null),
            NudgeEvent(2, now, 1, null, NudgeResponse.EXTEND, null),
            NudgeEvent(2, now, 2, null, NudgeResponse.EXTEND, null),
        )
        val out = StopRate.compute(sessions, nudges)
        assertEquals(2, out.shownSessions)
        assertEquals(1, out.stoppedSessions)
        assertEquals(2, out.extensions)
        assertEquals(0.5, out.stopRate)
    }
}

class ViewerTypeClassifierTest {
    private fun week(vararg pairs: Pair<ContextSlot, Long>, entry: EntryPoint = EntryPoint.Home) =
        pairs.mapIndexed { i, (slot, min) -> s("2026-11-0${i % 7 + 1}T12:00:00", min, slot, entry = entry) }

    @Test
    fun sharesAreComparedAsDisplayedWholePercent() {
        // 시연 밤형 주: 자기 전 208 + 한밤중 60 = 268 / 672 = 39.88% → 화면 40% → 밤 산책형.
        val sessions = week(ContextSlot.PRE_SLEEP to 208, ContextSlot.LATE_NIGHT to 60, ContextSlot.COMMUTE to 404)
        assertEquals(ViewerType.NIGHT_OWL, ViewerTypeClassifier.classify(sessions, 7, now))
        // 39.4%는 39%로 보이므로 아니다.
        val below = week(ContextSlot.PRE_SLEEP to 197, ContextSlot.OTHER to 303)
        assertEquals(ViewerType.BALANCED, ViewerTypeClassifier.classify(below, 7, now))
    }

    @Test
    fun priorityOrder() {
        assertEquals(
            ViewerType.NIGHT_OWL,
            ViewerTypeClassifier.classify(week(ContextSlot.PRE_SLEEP to 200, ContextSlot.COMMUTE to 200), 7, now),
        )
        assertEquals(
            ViewerType.COMMUTER,
            ViewerTypeClassifier.classify(week(ContextSlot.COMMUTE to 300, ContextSlot.OTHER to 400), 7, now),
        )
        assertEquals(
            ViewerType.MEAL_BUDDY,
            ViewerTypeClassifier.classify(
                week(
                    ContextSlot.MEAL to 300,
                    ContextSlot.OTHER to 400,
                ),
                7,
                now,
            ),
        )
        assertEquals(
            ViewerType.DOMINO,
            ViewerTypeClassifier.classify(
                week(ContextSlot.OTHER to 400, entry = EntryPoint.FromApp(AppKind.MESSENGER)),
                7,
                now,
            ),
        )
        assertEquals(
            ViewerType.MORNING,
            ViewerTypeClassifier.classify(
                week(
                    ContextSlot.WAKE_UP to 200,
                    ContextSlot.OTHER to 400,
                ),
                7,
                now,
            ),
        )
    }

    @Test
    fun gapFillerNeedsShortMedian() {
        val short = (1..60).map { s("2026-11-0${it % 7 + 1}T10:0${it % 6}:00", 5, ContextSlot.BREAK) }
        assertEquals(ViewerType.GAP_FILLER, ViewerTypeClassifier.classify(short, 7, now))
        val long = (1..20).map { s("2026-11-0${it % 7 + 1}T10:00:00", 15, ContextSlot.BREAK) }
        assertEquals(ViewerType.BALANCED, ViewerTypeClassifier.classify(long, 7, now))
    }

    @Test
    fun lowDailyAverageIsBalanced() {
        assertEquals(ViewerType.BALANCED, ViewerTypeClassifier.classify(week(ContextSlot.PRE_SLEEP to 100), 7, now))
    }
}

class WeeklyObservationsTest {
    private val messenger = EntryPoint.FromApp(AppKind.MESSENGER)

    private val sessions = listOf(
        s("2026-11-03T22:30:00", 30, ContextSlot.PRE_SLEEP, messenger, ExitPoint.ScreenOff, id = 1),
        s("2026-11-04T22:30:00", 20, ContextSlot.PRE_SLEEP, messenger, ExitPoint.ScreenOff, id = 2, stopped = true),
        s("2026-11-05T22:30:00", 10, ContextSlot.PRE_SLEEP, EntryPoint.Unlock, ExitPoint.ScreenOff, id = 3),
        s("2026-11-05T08:10:00", 12, ContextSlot.COMMUTE, EntryPoint.Unlock, ExitPoint.Home, id = 4),
    )

    private fun compute(coverage: Double? = 0.9) = WeeklyObservations.compute(
        WeekInput(
            sessions = sessions,
            nudges = listOf(
                NudgeEvent(2, now, 1, null, NudgeResponse.STOP, null),
                NudgeEvent(1, now, 1, null, NudgeResponse.EXTEND, null),
            ),
            days = 7,
            now = now,
            baselineMinPerDay = 20.0,
            coverage = coverage,
            riskWindows = emptyList(),
        ),
    )

    @Test
    fun idsAreStableAndNumbersComeFromCode() {
        val w = compute()
        assertEquals(72, w.totalMinutes)
        assertEquals("PRE_SLEEP", w["o3"]?.labels?.get("context"))
        assertEquals(60.0, w["o3"]?.numbers?.get("minutes"))
        assertEquals("COMMUTE", w["o4"]?.labels?.get("context"))
        assertEquals(1.0 - (72.0 / 7) / 20.0, w["o2"]?.numbers?.get("reductionRate"))
        assertEquals("MESSENGER", w["o12"]?.labels?.get("entry"))
        assertEquals(2.0, w["o12"]?.numbers?.get("count"))
        assertEquals(0.5, w["o10"]?.numbers?.get("stopRate"))
        assertNull(w["o6"], "위험 시간대가 없으면 o6도 없다")
        assertTrue(w.observations.map { it.id }.zipWithNext().all { (a, b) -> a.drop(1).toInt() < b.drop(1).toInt() })
        assertTrue(w.enoughForAi)
    }

    @Test
    fun lowCoverageBlocksAi() {
        assertTrue(!compute(coverage = 0.4).enoughForAi)
    }

    @Test
    fun emptyWeekHasNoObservations() {
        val w = WeeklyObservations.compute(WeekInput(emptyList(), emptyList(), 7, now, null, null, emptyList()))
        assertTrue(w.observations.isEmpty())
        assertEquals(ViewerType.BALANCED, w.viewerType)
        assertTrue(!w.enoughForAi)
    }
}
