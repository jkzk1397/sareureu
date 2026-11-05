package com.capstone.pause.core.social

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.insights.Baseline
import com.capstone.pause.core.insights.DayStat
import com.capstone.pause.core.insights.ReductionRate
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.ShortformSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 공정한 감소율 랭킹 규칙(ADR-0005). 순수 함수. */
object Ranking {
    /** 랭킹에 넣는 최소 유효 날. */
    const val MIN_VALID_DAYS = 5

    /** 상위 몇 명을 보이나. */
    const val TOP = 3

    /** 유효한 날 5일 미만, 기록률 80% 미만, 감소율 없음 → 측정 중. */
    fun isMeasuring(s: Standing): Boolean = s.reductionRate == null ||
        s.validDays < MIN_VALID_DAYS ||
        (s.coverage ?: 0.0) < Baseline.MIN_COVERAGE

    /**
     * 측정 중이 아닌 사람을 감소율 내림차순으로 매기고, 상위 3명 + 나만 보인다.
     * 4명 이상이면 꼴찌는 상위 3에 들 수 없어 저절로 숨는다. 3명 이하 작은 모임은 모두 보인다(목업 08).
     * 같은 감소율이면 같은 순위.
     */
    fun build(people: List<Standing>): RankingView {
        val (measuring, ranked) = people.partition { it.measuring }
        val sorted = ranked.sortedWith(compareByDescending<Standing> { it.reductionRate }.thenBy { it.nickname })
        var lastRate: Double? = null
        var lastRank = 0
        val rows = sorted.mapIndexed { i, s ->
            val rank = if (s.reductionRate == lastRate) lastRank else i + 1
            lastRate = s.reductionRate
            lastRank = rank
            RankedRow(rank, s)
        }
        val shown = rows.filterIndexed { i, r -> i < TOP || r.standing.isMe }
        return RankingView(shown, measuring)
    }
}

/** 내 감소율·유효 날 계산(순수). 기록률은 아직 기록하지 않아 기록이 있는 날을 100%로 본다(BUILD_REPORT TODO#3). */
object MyStanding {
    /** 이번 주 = 오늘-7 ~ 어제(새벽 4시 경계), 기준선 = 그 전 28일 중 처음 기록된 7일(리포트와 같은 규칙). */
    fun compute(
        sessions: List<ShortformSession>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
        nickname: String,
    ): Standing {
        val history = (HISTORY_DAYS downTo 1).map { back ->
            val date = today.minusDays(back.toLong())
            DayStat(date, ShortformMinutes.onDay(sessions, date, zone, now), coverage = 1.0)
        }
        val firstRecorded = sessions.minOfOrNull { it.start }?.let { DayBoundary.logicalDate(it, zone) }
        val valid = history.filter { firstRecorded != null && !it.date.isBefore(firstRecorded) }
        val week = valid.filter { !it.date.isBefore(today.minusDays(WEEK.toLong())) }
        val baselineDays = valid.filter { it.date.isBefore(today.minusDays(WEEK.toLong())) }
        val baseline = Baseline.compute(baselineDays)
        val rate = ReductionRate.averageOfCoveredDays(week)?.let { ReductionRate.compute(it, baseline) }
        return Standing(
            id = ME_ID,
            nickname = nickname,
            reductionRate = rate,
            coverage = if (week.isEmpty()) null else 1.0,
            validDays = week.size,
            isMe = true,
        )
    }

    /** 이번 주(월~일) '한밤중 0분' 달성 여부. 오늘 이후는 null. */
    fun lateNightZeroDays(
        sessions: List<ShortformSession>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
    ): List<Boolean?> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val night = sessions.filter { it.context == ContextSlot.LATE_NIGHT }
        val firstRecorded = sessions.minOfOrNull { it.start }?.let { DayBoundary.logicalDate(it, zone) }
        return (0 until WEEK).map { i ->
            val date = monday.plusDays(i.toLong())
            when {
                date.isAfter(today) -> null
                firstRecorded == null || date.isBefore(firstRecorded) -> null
                else -> ShortformMinutes.onDay(night, date, zone, now) == 0
            }
        }
    }

    /** 나를 가리키는 ID. */
    const val ME_ID = "me"

    private const val WEEK = 7
    private const val HISTORY_DAYS = 35
}
