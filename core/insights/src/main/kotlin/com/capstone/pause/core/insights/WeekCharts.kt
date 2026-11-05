package com.capstone.pause.core.insights

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.ShortformSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 요일 × 2시간 칸 히트맵(06 문서 6절 Heatmap). [cells] 행 = [days] 순서(월~일), 열 = 0~11(0시부터 2시간씩). */
data class HeatmapGrid(val days: List<DayOfWeek>, val cells: List<List<Int>>)

/** 2시간 연속 구간 중 가장 긴 곳. [startHour]부터 2시간, [minutes]는 그 주 합계. */
data class PeakSpan(val startHour: Int, val minutes: Int)

/** 흐름도용 집계: 상위 5개 + 기타로 접은 진입·앱·이탈과 그 사이 띠. 키는 [FlowKeys]·[AppKeys] 규칙. */
data class FlowChart(
    val entries: List<Pair<String, Int>>,
    val apps: List<Pair<String, Int>>,
    val exits: List<Pair<String, Int>>,
    /** 진입 → 앱 띠. */
    val entryToApp: List<Triple<String, String, Int>>,
    /** 앱 → 이탈 띠. */
    val appToExit: List<Triple<String, String, Int>>,
)

/** 진입 경로 하나의 세션 수와 길이 중앙값. */
data class EntryLength(val key: String, val sessions: Int, val medianMinutes: Double)

/** 패키지 → 앱 키(화면 이름은 리소스에서). */
object AppKeys {
    const val YOUTUBE = "YOUTUBE"
    const val INSTAGRAM = "INSTAGRAM"
    const val TIKTOK = "TIKTOK"
    const val OTHER = "OTHER"

    /** 패키지 이름으로 앱 키. */
    fun of(packageName: String?): String = when {
        packageName == null -> OTHER
        packageName.startsWith("com.google.android.youtube") -> YOUTUBE
        packageName.startsWith("com.instagram") -> INSTAGRAM
        packageName.startsWith("com.ss.android.ugc") || packageName.startsWith("com.zhiliaoapp") -> TIKTOK
        else -> OTHER
    }
}

/** 리포트 차트 계산(순수 함수). 화면은 결과를 그리기만 한다. */
object WeekCharts {
    /** 접은 항목의 키. */
    const val OTHER_KEY = "OTHER_GROUP"

    /** 흐름도에 보이는 최대 항목 수(나머지는 기타). */
    const val TOP = 5

    private const val HOURS_PER_CELL = 2
    private const val CELLS = 12
    private const val HOURS = 24

    /** [dates]의 시간별·맥락별 합계(일주일 시계). */
    fun weekClock(sessions: List<ShortformSession>, dates: List<LocalDate>, zone: ZoneId, now: Instant): ClockBins {
        val perDay = dates.map {
            ClockBins.compute(sessions, DayBoundary.startOf(it, zone), DayBoundary.endOf(it, zone), now, zone)
        }
        return ClockBins(
            List(HOURS) { h ->
                perDay.flatMap { it.minutesByHour[h].entries }
                    .groupBy({ it.key }, { it.value })
                    .mapValues { (_, v) -> v.sum() }
                    .filterValues { it > 0 }
            },
        )
    }

    /** 요일(월~일) × 2시간 칸 히트맵. 같은 요일이 여러 번이면 합한다. */
    fun heatmap(sessions: List<ShortformSession>, dates: List<LocalDate>, zone: ZoneId, now: Instant): HeatmapGrid {
        val days = DayOfWeek.entries.toList()
        val grid = Array(days.size) { IntArray(CELLS) }
        dates.forEach { date ->
            val bins = ClockBins.compute(
                sessions,
                DayBoundary.startOf(date, zone),
                DayBoundary.endOf(date, zone),
                now,
                zone,
            )
            bins.minutesByHour.forEachIndexed { h, byContext ->
                grid[date.dayOfWeek.ordinal][h / HOURS_PER_CELL] += byContext.values.sum()
            }
        }
        return HeatmapGrid(days, grid.map { it.toList() })
    }

    /** 위험 시간대(요일, 시)를 히트맵 칸(행, 열)으로. */
    fun riskCells(windows: List<RiskWindow>): Set<Pair<Int, Int>> =
        windows.map { it.dayOfWeek.ordinal to it.hour / HOURS_PER_CELL }.toSet()

    /** 이어지는 2시간 중 가장 많이 본 구간(자정을 넘어도 된다). 전부 0이면 null. */
    fun peakSpan(clock: ClockBins): PeakSpan? {
        val totals = clock.minutesByHour.map { it.values.sum() }
        val best = (0 until HOURS).maxByOrNull { totals[it] + totals[(it + 1) % HOURS] } ?: return null
        val minutes = totals[best] + totals[(best + 1) % HOURS]
        return if (minutes == 0) null else PeakSpan(best, minutes)
    }

    /** 진입 → 앱 → 이탈 흐름. 진입·이탈은 상위 [TOP]개 + 기타로 접는다. */
    fun flowChart(sessions: List<ShortformSession>): FlowChart {
        // 세션은 같은 값이 여럿일 수 있어(데이터 클래스) 맵 키로 쓰지 않고 순서대로 목록을 만든다.
        val rawEntries = sessions.map { FlowKeys.entry(it.entry) }
        val rawExits = sessions.map { FlowKeys.exit(it.exit) }
        val apps = sessions.map { AppKeys.of(it.segments.firstOrNull()?.packageName) }
        val entryKeep = topKeys(rawEntries)
        val exitKeep = topKeys(rawExits)
        val entries = rawEntries.map { if (it in entryKeep) it else OTHER_KEY }
        val exits = rawExits.map { if (it in exitKeep) it else OTHER_KEY }
        return FlowChart(
            entries = counted(entries),
            apps = counted(apps),
            exits = counted(exits),
            entryToApp = links(entries.zip(apps)),
            appToExit = links(apps.zip(exits)),
        )
    }

    /** 세션이 [minSessions]개 이상인 진입 경로 중 길이 중앙값이 가장 긴 것. */
    fun longestEntry(sessions: List<ShortformSession>, now: Instant, minSessions: Int = 3): EntryLength? = sessions
        .groupBy { FlowKeys.entry(it.entry) }
        .filterValues { it.size >= minSessions }
        .map { (key, list) -> EntryLength(key, list.size, SessionStats.median(SessionStats.lengths(list, now))) }
        .maxWithOrNull(compareBy<EntryLength> { it.medianMinutes }.thenBy { it.sessions })

    private fun topKeys(keys: Collection<String>): Set<String> {
        val ranked = counted(keys.toList())
        // 6개 이하면 접지 않는다(기타 한 칸을 따로 둘 이유가 없다).
        return if (ranked.size <=
            TOP + 1
        ) {
            ranked.map { it.first }.toSet()
        } else {
            ranked.take(TOP).map { it.first }.toSet()
        }
    }

    /** 많은 순, 같으면 키 순. 기타는 맨 뒤. */
    private fun counted(keys: List<String>): List<Pair<String, Int>> = keys.groupingBy { it }.eachCount().entries
        .sortedWith(
            compareBy<Map.Entry<String, Int>> {
                it.key == OTHER_KEY
            }.thenByDescending { it.value }.thenBy { it.key },
        )
        .map { it.key to it.value }

    private fun links(pairs: List<Pair<String, String>>): List<Triple<String, String, Int>> =
        pairs.groupingBy { it }.eachCount().map { (k, v) -> Triple(k.first, k.second, v) }
            .sortedWith(
                compareByDescending<Triple<String, String, Int>> {
                    it.third
                }.thenBy { it.first }.thenBy { it.second },
            )
}
