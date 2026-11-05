package com.capstone.pause.core.sensing.demo

import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.Surface
import kotlin.random.Random

/** 시연용 숏폼 앱. */
internal data class DemoApp(val packageName: String, val surface: Surface, val weight: Int)

/** 오늘(지금까지) 세션 하나. */
internal data class TodaySession(
    val slot: ContextSlot,
    val hour: Int,
    val minute: Int,
    val minutes: Long,
    val entry: EntryPoint,
    val appIndex: Int,
    val nudge: NudgeResponse? = null,
)

/**
 * 패턴 하나의 재료. [lengths]는 리포트 주의 맥락별 세션 길이(분), [dayTargets]는 오늘-7 ~ 오늘-1의 하루 목표.
 * 진입·이탈은 비율대로 개수를 만든다(가장 큰 나머지 방식).
 */
internal data class DemoProfile(
    val lengths: Map<ContextSlot, List<Int>>,
    val dayTargets: List<Int>,
    val baselineScale: Double,
    val stops: Int,
    val entryWeights: List<Pair<EntryPoint, Int>>,
    val exitWeights: List<Pair<ExitPoint, Int>>,
    val today: List<TodaySession>,
    val forcedFirstDay: Int = 0,
) {
    private val sessionCount: Int get() = lengths.values.sumOf { it.size }

    /** 진입 목록(앞쪽이 밤 세션에 먼저 붙는다). */
    fun entryPool(): List<EntryPoint> = expand(entryWeights, sessionCount)

    /** '그만'으로 끝나지 않은 세션의 이탈 목록. */
    fun exitPool(stopCount: Int): List<ExitPoint> = expand(exitWeights, sessionCount - stopCount)

    private fun <T> expand(weights: List<Pair<T, Int>>, n: Int): List<T> {
        val total = weights.sumOf { it.second }.toDouble()
        val raw = weights.map { it.second * n / total }
        val counts = raw.map { it.toInt() }.toMutableList()
        raw.withIndex().sortedByDescending { it.value - it.value.toInt() }
            .take(n - counts.sum())
            .forEach { counts[it.index] += 1 }
        return weights.flatMapIndexed { i, (value, _) -> List(counts[i]) { value } }
    }
}

/** 패턴별 재료와 공통 표. */
internal object DemoProfiles {
    /** 유튜브 쇼츠 · 인스타 릴스 · 틱톡(한국 스토어). */
    val apps = listOf(
        DemoApp("com.google.android.youtube", Surface.SHORTS, 50),
        DemoApp("com.instagram.android", Surface.REELS, 30),
        DemoApp("com.ss.android.ugc.trill", Surface.TIKTOK_FEED, 20),
    )

    /** 가중치대로 앱 고르기. */
    fun pickApp(random: Random): DemoApp {
        var r = random.nextInt(apps.sumOf { it.weight })
        for (app in apps) {
            if (r < app.weight) return app
            r -= app.weight
        }
        return apps.first()
    }

    /** 맥락별 세션 시작 창(시, 분). 한밤중은 새벽(다음 달력 날짜, 같은 논리적 날짜). */
    val windows: Map<ContextSlot, List<Pair<Int, Int>>> = mapOf(
        ContextSlot.WAKE_UP to listOf(7 to 10),
        ContextSlot.COMMUTE to listOf(8 to 0, 18 to 10),
        ContextSlot.CLASS_WORK to listOf(10 to 30, 14 to 20),
        ContextSlot.MEAL to listOf(12 to 20, 18 to 50),
        ContextSlot.BREAK to listOf(15 to 30, 20 to 0),
        ContextSlot.PRE_SLEEP to listOf(22 to 15, 23 to 10),
        ContextSlot.LATE_NIGHT to listOf(0 to 40, 1 to 30),
        ContextSlot.OTHER to listOf(17 to 0),
    )

    private val messenger = EntryPoint.FromApp(AppKind.MESSENGER)

    /** 목업 01(화요일 밤 9:40, 오늘 72분: 이동 22·수업 9·식사 15·쉬는 시간 26, 알림 3번 중 2번 멈춤). */
    private val todayDefault = listOf(
        TodaySession(ContextSlot.COMMUTE, 8, 5, 14, EntryPoint.Unlock, 0, NudgeResponse.STOP),
        TodaySession(ContextSlot.CLASS_WORK, 10, 40, 9, messenger, 1),
        TodaySession(ContextSlot.MEAL, 12, 35, 15, EntryPoint.Home, 0, NudgeResponse.EXTEND),
        TodaySession(ContextSlot.BREAK, 15, 10, 18, messenger, 2, NudgeResponse.STOP),
        TodaySession(ContextSlot.COMMUTE, 18, 20, 8, EntryPoint.Unlock, 0),
        TodaySession(ContextSlot.BREAK, 20, 15, 8, EntryPoint.FromShortform("com.instagram.android"), 1),
    )

    private val nightOwl = DemoProfile(
        lengths = mapOf(
            ContextSlot.WAKE_UP to listOf(10, 4),
            ContextSlot.COMMUTE to listOf(62, 30, 24, 16, 12, 7, 4, 3, 3),
            ContextSlot.CLASS_WORK to listOf(12, 6, 4, 3, 3, 2, 2, 2),
            ContextSlot.MEAL to listOf(26, 15, 9, 8, 7, 4, 4, 4, 4),
            ContextSlot.BREAK to listOf(30, 18, 12, 9, 8, 8, 7, 6, 5, 4, 4, 3),
            ContextSlot.PRE_SLEEP to listOf(70, 36, 32, 18, 14, 9, 8, 7, 4, 4, 3, 3),
            ContextSlot.LATE_NIGHT to listOf(28, 16, 6, 4, 3, 3),
        ),
        dayTargets = listOf(118, 88, 92, 90, 96, 93, 95),
        baselineScale = 872.0 / 672.0,
        stops = 6,
        entryWeights = listOf(
            messenger to 22,
            EntryPoint.Unlock to 14,
            EntryPoint.Home to 12,
            EntryPoint.FromShortform("com.instagram.android") to 6,
            EntryPoint.FromApp(AppKind.BROWSER) to 4,
        ),
        exitWeights = listOf(
            ExitPoint.ScreenOff to 24,
            ExitPoint.ToApp(AppKind.MESSENGER) to 13,
            ExitPoint.Home to 11,
            ExitPoint.ToApp(AppKind.OTHER) to 4,
        ),
        today = todayDefault,
    )

    private val commuter = DemoProfile(
        lengths = mapOf(
            ContextSlot.WAKE_UP to listOf(8, 6, 4),
            ContextSlot.COMMUTE to listOf(38, 32, 28, 25, 22, 20, 18, 16, 14, 12, 10, 8, 6),
            ContextSlot.CLASS_WORK to listOf(9, 6, 4, 3),
            ContextSlot.MEAL to listOf(14, 9, 7, 5),
            ContextSlot.BREAK to listOf(16, 10, 7, 5, 4),
            ContextSlot.PRE_SLEEP to listOf(20, 12, 8, 5),
            ContextSlot.LATE_NIGHT to listOf(6),
        ),
        dayTargets = listOf(66, 60, 62, 58, 61, 60, 59),
        baselineScale = 1.25,
        stops = 5,
        entryWeights = listOf(
            EntryPoint.Unlock to 40,
            messenger to 25,
            EntryPoint.Home to 25,
            EntryPoint.Unknown to 10,
        ),
        exitWeights = listOf(ExitPoint.ScreenOff to 45, ExitPoint.Home to 25, ExitPoint.ToApp(AppKind.MESSENGER) to 30),
        today = todayDefault,
    )

    private val domino = DemoProfile(
        lengths = mapOf(
            ContextSlot.WAKE_UP to listOf(6, 4, 3),
            ContextSlot.COMMUTE to listOf(16, 9, 6, 4, 3),
            ContextSlot.CLASS_WORK to listOf(8, 6, 5, 4, 3, 3, 2, 2),
            ContextSlot.MEAL to listOf(18, 12, 6, 5, 4),
            ContextSlot.BREAK to listOf(24, 18, 12, 9, 7, 6, 5, 4, 4, 3, 3, 2, 2),
            ContextSlot.PRE_SLEEP to listOf(32, 22, 16, 9, 6, 4),
            ContextSlot.LATE_NIGHT to listOf(12, 5),
        ),
        dayTargets = listOf(60, 52, 54, 50, 55, 52, 53),
        baselineScale = 1.2,
        stops = 3,
        entryWeights = listOf(
            messenger to 45,
            EntryPoint.FromShortform("com.instagram.android") to 30,
            EntryPoint.Unlock to 15,
            EntryPoint.Home to 10,
        ),
        exitWeights = listOf(
            ExitPoint.ToApp(AppKind.MESSENGER) to 45,
            ExitPoint.ScreenOff to 30,
            ExitPoint.Home to 25,
        ),
        today = todayDefault,
    )

    /** 패턴의 재료. */
    fun of(pattern: DemoPattern): DemoProfile = when (pattern) {
        DemoPattern.NIGHT_OWL -> nightOwl
        DemoPattern.COMMUTER -> commuter
        DemoPattern.DOMINO -> domino
    }
}
