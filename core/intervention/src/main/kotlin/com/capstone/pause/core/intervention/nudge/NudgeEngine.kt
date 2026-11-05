package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.MeltStage
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.NudgeResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 엔진이 보는 진행 중 세션 한 장면. [sessionStart]가 세션을 구분한다. */
data class LiveInput(
    val sessionStart: Instant,
    val context: ContextSlot,
    val appLabel: String,
    /** 이 맥락의 임계값 T(분). */
    val thresholdMin: Int,
    val melt: MeltStage = MeltStage.NONE,
)

/** 끝난 세션의 개입 기록(세션 ID는 저장소가 시작 시각으로 찾아 붙인다). */
data class FinishedNudges(val sessionStart: Instant, val events: List<NudgeEvent>)

/** 카드·메시지·칭찬 문장을 만드는 쪽. 엔진은 문장을 모른다(테스트에서는 가짜를 넣는다). */
interface NudgeWriter {
    /** [level] 단계 카드. */
    fun card(level: Int, live: LiveInput, elapsedMinutes: Int): CardContent

    /** 2단계와 함께 가는 메시지 알림(친구 잔소리가 있으면 그것 먼저). */
    fun message(live: LiveInput, elapsedMinutes: Int): OutgoingMessage

    /** '그만' 뒤 칭찬. */
    fun praise(live: LiveInput): String
}

/**
 * 개입 사다리(PRD 7.2, 03 문서 3.2)를 도는 순수 상태 기계. 안드로이드·AI에 의존하지 않는다.
 * - T에서 1단계, 연장(5분) 뒤 2단계 + 메시지 알림, 다시 연장 뒤 3단계(숨 고르기·입력), 그다음부터 4단계(연장 3분, 반복).
 * - 하루(새벽 4시 경계) 연장이 3번을 넘으면 다음 세션은 2단계부터.
 * - '오늘은 그만 알려 줘'는 그날 새벽 4시까지 카드를 띄우지 않는다(버블은 남긴다).
 * - 숏폼을 떠나도 60초 안에 돌아오면 같은 세션으로 이어 간다(떠나는 순간 화면만 내림).
 * - [speed]는 시연용 배속(타이머에만 적용): 60이면 실제 1초가 1분.
 */
class NudgeEngine(private val zone: ZoneId, private val writer: NudgeWriter, private val speed: Double = 1.0) {
    private class SessionState(val start: Instant, val thresholdSec: Long) {
        var level = 0
        var dueSec = thresholdSec
        var card: CardContent? = null
        var stopped = false
        var messaged = false
        var absentSince: Instant? = null
        val events = mutableListOf<NudgeEvent>()
    }

    private var current: SessionState? = null
    private var day: LocalDate? = null
    private var extensionsToday = 0
    private var snoozeUntil: Instant? = null
    private val finished = mutableListOf<FinishedNudges>()

    /** 오늘 이미 있었던 연장 수(서비스가 다시 시작될 때 DB에서 읽어 넣는다). */
    fun seedToday(now: Instant, extensions: Int) {
        rollDay(now)
        extensionsToday = extensions
    }

    /** 오늘 연장 횟수. */
    val todayExtensions: Int get() = extensionsToday

    /** 매 틱. [live]가 null이면 숏폼 화면이 아니다(세션이 끝났거나 잠깐 떠남). */
    fun tick(now: Instant, live: LiveInput?): List<NudgeCommand> {
        rollDay(now)
        val out = mutableListOf<NudgeCommand>()
        val state = current
        if (state != null && live != null && live.sessionStart != state.start) {
            finish(state, now)
            current = null
            out += NudgeCommand.Hide
        }
        if (live == null) {
            // 숏폼을 잠깐 떠났다: 60초 안에 돌아오면 같은 세션(세션 추적과 같은 규칙). 화면만 먼저 내린다.
            val s = current ?: return out
            val since = s.absentSince
            if (s.stopped) {
                // '그만'을 누르고 떠났다: 기다릴 것 없이 바로 끝낸다.
                finish(s, now)
                current = null
                if (since == null) out += NudgeCommand.Hide
            } else if (since == null) {
                s.absentSince = now
                out += NudgeCommand.Hide
            } else if (Duration.between(since, now) >= MERGE_GAP) {
                finish(s, now)
                current = null
            }
            return out
        }
        val s = current ?: SessionState(live.sessionStart, live.thresholdMin * SEC_PER_MIN).also { current = it }
        if (s.absentSince != null) {
            s.absentSince = null
            s.card?.let { out += NudgeCommand.ShowCard(it) }
        }
        if (s.stopped || s.card != null) return out
        val elapsed = elapsedSec(s.start, now)
        if (elapsed >= s.dueSec && !isSnoozed(now)) {
            s.level = when {
                s.level == 0 && extensionsToday > MAX_EXTENSIONS_BEFORE_L2 -> 2
                s.level == 0 -> 1
                else -> minOf(s.level + 1, MAX_LEVEL)
            }
            val minutes = (elapsed / SEC_PER_MIN).toInt()
            val card = writer.card(s.level, live, minutes)
            s.card = card
            out += NudgeCommand.ShowCard(card)
            if (s.level >= 2 && !s.messaged) {
                s.messaged = true
                out += NudgeCommand.PostBuddyMessage(writer.message(live, minutes))
            }
        } else {
            out += NudgeCommand.ShowBubble(elapsed, s.thresholdSec, live.melt)
        }
        return out
    }

    /** 사용자 동작. 카드가 없을 때 온 동작(늦게 온 탭 등)은 무시한다. */
    fun onAction(now: Instant, action: NudgeUserAction, live: LiveInput?): List<NudgeCommand> {
        val s = current ?: return emptyList()
        if (action is NudgeUserAction.BubbleTapped) return emptyList()
        val card = s.card ?: return emptyList()
        val elapsed = elapsedSec(s.start, now)
        fun record(response: NudgeResponse, chip: String? = null) {
            s.events += NudgeEvent(0, now, card.level, card.copyId, response, chip)
        }
        s.card = null
        return when (action) {
            is NudgeUserAction.Stop -> {
                record(NudgeResponse.STOP)
                s.stopped = true
                val praise = live?.let(writer::praise)
                listOfNotNull(NudgeCommand.Hide, praise?.let { NudgeCommand.ShowPraise(it) })
            }

            is NudgeUserAction.Extend -> {
                record(NudgeResponse.EXTEND, action.reasonChip)
                extensionsToday += 1
                s.dueSec = elapsed + card.extendMinutes * SEC_PER_MIN
                listOf(NudgeCommand.ShowBubble(elapsed, s.thresholdSec, live?.melt ?: MeltStage.NONE))
            }

            is NudgeUserAction.SnoozeToday -> {
                record(NudgeResponse.SNOOZE_TODAY)
                snoozeUntil = DayBoundary.endOf(DayBoundary.logicalDate(now, zone), zone)
                listOf(NudgeCommand.ShowBubble(elapsed, s.thresholdSec, live?.melt ?: MeltStage.NONE))
            }

            is NudgeUserAction.Dismiss -> {
                record(NudgeResponse.DISMISSED)
                s.dueSec = elapsed + DEFAULT_EXTEND_MIN * SEC_PER_MIN
                listOf(NudgeCommand.ShowBubble(elapsed, s.thresholdSec, live?.melt ?: MeltStage.NONE))
            }

            NudgeUserAction.BubbleTapped -> emptyList()
        }
    }

    /** 끝난 세션들의 개입 기록을 꺼낸다(꺼내면 비워진다). */
    fun drainFinished(): List<FinishedNudges> = finished.toList().also { finished.clear() }

    /** 지금 오늘은 그만 알려 줘 상태인지. */
    fun isSnoozed(now: Instant): Boolean = snoozeUntil?.let { now.isBefore(it) } ?: false

    private fun finish(state: SessionState, now: Instant) {
        state.card?.let { state.events += NudgeEvent(0, now, it.level, it.copyId, NudgeResponse.IGNORED, null) }
        if (state.events.isNotEmpty()) finished += FinishedNudges(state.start, state.events.toList())
    }

    private fun rollDay(now: Instant) {
        val today = DayBoundary.logicalDate(now, zone)
        if (day != today) {
            day = today
            extensionsToday = 0
            if (!isSnoozed(now)) snoozeUntil = null
        }
    }

    private fun elapsedSec(start: Instant, now: Instant): Long =
        (Duration.between(start, now).toMillis().coerceAtLeast(0) * speed / MILLIS_PER_SEC).toLong()

    /** 엔진 규칙 상수. */
    companion object {
        /** 하루 연장이 이 횟수를 넘으면 다음 세션은 2단계부터. */
        const val MAX_EXTENSIONS_BEFORE_L2 = 3

        /** 가장 높은 단계. */
        const val MAX_LEVEL = 4

        /** 1~3단계 연장(분). */
        const val DEFAULT_EXTEND_MIN = 5

        /** 4단계 연장(분). */
        const val L4_EXTEND_MIN = 3

        /** 3단계 숨 고르기(초). */
        const val BREATH_SECONDS = 10

        private const val SEC_PER_MIN = 60L
        private val MERGE_GAP: Duration = Duration.ofSeconds(60)
        private const val MILLIS_PER_SEC = 1000.0
    }
}
