package com.capstone.pause.core.sensing.demo

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.AppSegment
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** 시연 세션 하나와 그 세션의 개입 기록(세션 ID는 저장할 때 정해진다). */
data class DemoSessionPlan(val session: ShortformSession, val nudges: List<NudgeEvent>)

/**
 * 고정 시드 시연 시나리오. 같은 패턴·같은 오늘 날짜면 항상 같은 결과(결정성 테스트).
 * 날짜 구성: 기준선 주(오늘-14 ~ 오늘-8) + 리포트 주(오늘-7 ~ 오늘-1) + 오늘(지금 시각까지).
 * NIGHT_OWL 리포트 주는 목업 05와 같다: 58세션, 672분(하루 96분), 맥락별 합계, 개입 14번 중 6번 멈춤.
 */
object DemoScenario {
    /** 고정 시드. */
    const val SEED = 20_261_105L

    /** 개입 1단계가 뜨는 기준(분). 시연용 단일 값. */
    const val DEMO_THRESHOLD_MIN = 15

    /** 시나리오 만들기. */
    fun generate(pattern: DemoPattern, today: LocalDate, zone: ZoneId, now: Instant): List<DemoSessionPlan> {
        val random = Random(SEED + pattern.ordinal)
        val profile = DemoProfiles.of(pattern)
        val week = buildWeek(profile, today, zone, random, firstDayOffset = 7, scale = 1.0)
        val baseline = buildWeek(profile, today, zone, random, firstDayOffset = 14, scale = profile.baselineScale)
        val todayPlans = buildToday(profile, today, zone, now)
        return (baseline + week + todayPlans).sortedBy { it.session.start }
    }

    private fun buildWeek(
        profile: DemoProfile,
        today: LocalDate,
        zone: ZoneId,
        random: Random,
        firstDayOffset: Int,
        scale: Double,
    ): List<DemoSessionPlan> {
        val lengths = scaledLengths(profile, scale)
        val targets = profile.dayTargets.map { Math.round(it * scale).toInt() }
        val assigned = assignDays(lengths, targets, profile.forcedFirstDay)
        val drafts = assigned.mapIndexed { index, (slot, minutes, day) ->
            Draft(index, slot, minutes, today.minusDays((firstDayOffset - day).toLong()))
        }
        val placed = placeInDays(drafts, zone, random)
        return decorate(placed, profile, random)
    }

    /** 길이 목록을 [scale]배 하되 맥락별 합계는 반올림한 목표에 정확히 맞춘다. */
    private fun scaledLengths(profile: DemoProfile, scale: Double): List<Pair<ContextSlot, Int>> =
        profile.lengths.flatMap { (slot, list) ->
            if (scale == 1.0) return@flatMap list.map { slot to it }
            val target = Math.round(list.sum() * scale).toInt()
            val scaled = list.map { maxOf(1, Math.round(it * scale).toInt()) }.toMutableList()
            scaled[0] += target - scaled.sum()
            scaled.map { slot to it }
        }

    /** 큰 세션부터 '목표까지 가장 많이 남은 날'에 넣는다. 첫 세션은 [forcedDay](오늘과 같은 요일)에. */
    private fun assignDays(
        lengths: List<Pair<ContextSlot, Int>>,
        targets: List<Int>,
        forcedDay: Int,
    ): List<Triple<ContextSlot, Int, Int>> {
        val load = IntArray(targets.size)
        val order = lengths.withIndex().sortedByDescending { it.value.second }
        val result = arrayOfNulls<Triple<ContextSlot, Int, Int>>(lengths.size)
        order.forEachIndexed { rank, (index, value) ->
            val day = if (rank == 0) {
                forcedDay
            } else {
                targets.indices.maxBy { targets[it] - load[it] - it * TIE_BREAK }
            }
            load[day] += value.second
            result[index] = Triple(value.first, value.second, day)
        }
        return result.filterNotNull()
    }

    private data class Draft(val index: Int, val slot: ContextSlot, val minutes: Int, val date: LocalDate)

    private data class Placed(val draft: Draft, val start: Instant, val end: Instant)

    /**
     * 맥락마다 정해 둔 시간 창에 놓고, 하루 안에서 시작 순으로 밀어 겹치지 않게 한다(사이 7~20분).
     */
    private fun placeInDays(drafts: List<Draft>, zone: ZoneId, random: Random): List<Placed> =
        drafts.groupBy { it.date }.flatMap { (date, dayDrafts) ->
            val slotCounters = HashMap<ContextSlot, Int>()
            val wanted = dayDrafts.sortedByDescending { it.minutes }.map { d ->
                val windows = DemoProfiles.windows.getValue(d.slot)
                val n = slotCounters.merge(d.slot, 1, Int::plus) ?: 1
                val (hour, minute) = windows[(n - 1) % windows.size]
                val base = date.atTime(hour, minute).let {
                    // 새벽 4시 전 창(한밤중)은 다음 달력 날짜지만 같은 논리적 날짜다.
                    if (hour < DayBoundary.START.hour) it.plusDays(1) else it
                }.atZone(zone).toInstant()
                d to base.plusSeconds(random.nextLong(0, MAX_JITTER_SEC))
            }
            var cursor = Instant.MIN
            wanted.sortedBy { it.second }.map { (d, wantedStart) ->
                val start = maxOf(wantedStart, cursor)
                val end = start.plusSeconds(d.minutes * SEC_PER_MIN)
                cursor = end.plusSeconds(random.nextLong(MIN_GAP_SEC, MAX_GAP_SEC))
                Placed(d, start, end)
            }
        }

    /** 진입·이탈·앱·개입을 붙인다. 개입은 기준을 넘긴 세션에만, 그중 [DemoProfile.stops]개가 '그만'. */
    private fun decorate(placed: List<Placed>, profile: DemoProfile, random: Random): List<DemoSessionPlan> {
        val night = setOf(ContextSlot.PRE_SLEEP, ContextSlot.LATE_NIGHT)
        val ordered = placed.sortedWith(compareBy({ it.draft.slot !in night }, { it.draft.index }))
        val nightCount = ordered.count { it.draft.slot in night }
        val entries = profile.entryPool().let { pool ->
            pool.take(nightCount) + pool.drop(nightCount).shuffled(random)
        }
        val nudged = ordered.filter { it.draft.minutes >= DEMO_THRESHOLD_MIN }
        val stopped = nudged.shuffled(random).take(profile.stops).toSet()
        val exitPool = profile.exitPool(stopCount = stopped.size).let { pool ->
            pool.take(nightCount) + pool.drop(nightCount).shuffled(random)
        }.iterator()
        return ordered.mapIndexed { i, p ->
            val isStopped = p in stopped
            val exit = if (isStopped) ExitPoint.AfterNudgeStop else exitPool.next()
            val entry = entries[i]
            val nudges = if (p.draft.minutes >= DEMO_THRESHOLD_MIN) nudgeChain(p, isStopped) else emptyList()
            val session = ShortformSession(
                id = 0,
                start = p.start,
                end = p.end,
                segments = segments(p, entry, random),
                context = p.draft.slot,
                contextConfidence = Confidence.HIGH,
                contextConfirmed = false,
                entry = entry,
                exit = exit,
                interaction = null,
                maxFrictionLevel = nudges.maxOfOrNull { it.level } ?: 0,
                extensions = nudges.count { it.response == NudgeResponse.EXTEND },
                stoppedByNudge = isStopped,
                checkIn = null,
                source = DataSource.DEMO,
            )
            DemoSessionPlan(session, nudges)
        }
    }

    /** 1단계는 기준 시각, 연장할 때마다 5분 뒤 다음 단계(최대 4). 멈춘 세션은 마지막 카드에서 '그만'. */
    private fun nudgeChain(p: Placed, stopped: Boolean): List<NudgeEvent> {
        val events = mutableListOf<NudgeEvent>()
        var at = p.start.plusSeconds(DEMO_THRESHOLD_MIN * SEC_PER_MIN)
        var level = 1
        while (!at.isAfter(p.end)) {
            val next = at.plusSeconds(EXTEND_MIN * SEC_PER_MIN)
            val last = next.isAfter(p.end)
            val response = when {
                last && stopped -> NudgeResponse.STOP
                else -> NudgeResponse.EXTEND
            }
            events += NudgeEvent(0, at, level, "demo_l$level", response, null)
            at = next
            level = minOf(level + 1, MAX_LEVEL)
        }
        return events
    }

    private fun segments(p: Placed, entry: EntryPoint, random: Random): List<AppSegment> {
        val first = DemoProfiles.pickApp(random)
        if (entry !is EntryPoint.FromShortform || p.draft.minutes < 4) {
            return listOf(AppSegment(first.packageName, first.surface, p.start, p.end))
        }
        val split = p.start.plusSeconds(p.draft.minutes * SEC_PER_MIN / 2)
        val second = DemoProfiles.apps.first { it.packageName != first.packageName }
        return listOf(
            AppSegment(first.packageName, first.surface, p.start, split),
            AppSegment(second.packageName, second.surface, split, p.end),
        )
    }

    private fun buildToday(profile: DemoProfile, today: LocalDate, zone: ZoneId, now: Instant): List<DemoSessionPlan> =
        profile.today.mapNotNull { t ->
            val start = today.atTime(t.hour, t.minute).atZone(zone).toInstant()
            if (!start.isBefore(now)) return@mapNotNull null
            val end = minOf(start.plusSeconds(t.minutes * SEC_PER_MIN), now)
            val nudges = t.nudge?.let { response ->
                listOf(NudgeEvent(0, start.plusSeconds((t.minutes - 1) * SEC_PER_MIN), 1, "demo_l1", response, null))
            }.orEmpty().filter { !it.at.isAfter(now) }
            val app = DemoProfiles.apps[t.appIndex]
            val stopped = nudges.any { it.response == NudgeResponse.STOP }
            DemoSessionPlan(
                ShortformSession(
                    id = 0, start = start, end = end,
                    segments = listOf(AppSegment(app.packageName, app.surface, start, end)),
                    context = t.slot, contextConfidence = Confidence.HIGH, contextConfirmed = false,
                    entry = t.entry,
                    exit = if (stopped) {
                        ExitPoint.AfterNudgeStop
                    } else {
                        ExitPoint.ToApp(
                            AppKind.MESSENGER,
                        )
                    },
                    interaction = null, maxFrictionLevel = if (nudges.isEmpty()) 0 else 1,
                    extensions = nudges.count { it.response == NudgeResponse.EXTEND },
                    stoppedByNudge = stopped, checkIn = null, source = DataSource.DEMO,
                ),
                nudges,
            )
        }

    private const val TIE_BREAK = 0.001
    private const val SEC_PER_MIN = 60L
    private const val MAX_JITTER_SEC = 600L
    private const val MIN_GAP_SEC = 420L
    private const val MAX_GAP_SEC = 1200L
    private const val EXTEND_MIN = 5L
    private const val MAX_LEVEL = 4
}
