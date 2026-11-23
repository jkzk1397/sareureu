package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.BuddyExpression
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

/**
 * 서비스가 다시 떠도(감지 껐다 켜기·강제 종료·재시작) 이어 가려고 저장하는 엔진 상태. 하루(새벽 4시 경계) 단위다.
 * 날이 바뀌면 버린다.
 */
data class NudgeSnapshot(
    val day: LocalDate,
    val extensionsToday: Int,
    /** '오늘은 그만 알려 줘'가 끝나는 시각. 없으면 null. */
    val snoozeUntil: Instant?,
    /** 저장할 때 진행 중이던 세션. 없으면 null. */
    val session: SessionSnapshot?,
)

/** 진행 중이던 세션의 사다리 위치. 초 단위 값은 세션 경과 기준(배속 적용 뒤)이다. */
data class SessionSnapshot(
    val start: Instant,
    val elapsedSec: Long,
    val level: Int,
    val dueSec: Long,
    val windowStartSec: Long,
    /** 카드가 떠 있었으면 그 단계(다시 뜨면 같은 단계 카드를 바로 보인다). */
    val cardLevel: Int?,
    val messaged: Boolean,
    /** 이 값을 잰 시각. 새 세션이 이 시각에서 [NudgeEngine.RESUME_GAP] 안에 시작하면 같은 세션으로 잇는다. */
    val savedAt: Instant,
)

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
 * - 엔진이 새로 만들어져도([restore]) 오늘의 연장 횟수·오늘은 그만은 그대로이고, 끊긴 지 5분 안에 다시 시작한
 *   세션은 단계와 남은 시간을 이어 간다(감지를 껐다 켜서 1단계로 되돌리지 못하게).
 * - [speed]는 시연용 배속(타이머에만 적용): 60이면 실제 1초가 1분.
 */
class NudgeEngine(private val zone: ZoneId, private val writer: NudgeWriter, private val speed: Double = 1.0) {
    private class SessionState(val start: Instant, val thresholdSec: Long) {
        /** 이어 받은 세션이 끊기기 전까지 이미 지난 시간(초). 새 세션이면 0. */
        var carriedSec = 0L
        var level = 0
        var dueSec = thresholdSec

        /** 이번 구간 시작(초). 연장·닫기·오늘은 그만 뒤에는 그 순간부터 다시 센다(버블 링이 0부터). */
        var windowStartSec = 0L
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

    /** [restore]로 받은, 아직 이어 붙이지 않은 세션. */
    private var carry: SessionSnapshot? = null

    /** 오늘 이미 있었던 연장 수(서비스가 다시 시작될 때 DB에서 읽어 넣는다). */
    fun seedToday(now: Instant, extensions: Int) {
        rollDay(now)
        extensionsToday = extensions
    }

    /** 지금 상태를 저장용으로 꺼낸다. '그만'으로 끝낸 세션은 이어 갈 것이 없어 담지 않는다. */
    fun snapshot(now: Instant): NudgeSnapshot {
        rollDay(now)
        val s = current?.takeIf { !it.stopped }
        val session = if (s != null) {
            SessionSnapshot(
                start = s.start,
                elapsedSec = elapsedSec(s, s.absentSince ?: now),
                level = s.level,
                dueSec = s.dueSec,
                windowStartSec = s.windowStartSec,
                cardLevel = s.card?.level,
                messaged = s.messaged,
                savedAt = s.absentSince ?: now,
            )
        } else {
            carry.takeIf { current == null }
        }
        return NudgeSnapshot(
            DayBoundary.logicalDate(now, zone),
            extensionsToday,
            snoozeUntil?.takeIf { now.isBefore(it) },
            session,
        )
    }

    /** 저장해 둔 상태를 이어 받는다. 다른 날(새벽 4시 경계) 것이면 버린다. */
    fun restore(snapshot: NudgeSnapshot, now: Instant) {
        rollDay(now)
        if (snapshot.day != day) return
        extensionsToday = maxOf(extensionsToday, snapshot.extensionsToday)
        snapshot.snoozeUntil?.takeIf { now.isBefore(it) }?.let { snoozeUntil = it }
        carry = snapshot.session
    }

    /** 멈출 때 부른다: 진행 중 세션의 개입 기록을 마무리한다([drainFinished]로 꺼낸다). 상태는 [snapshot]으로 먼저 꺼내 둘 것. */
    fun close(now: Instant) {
        current?.let { finish(it, now) }
        current = null
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
        val s = current ?: newSession(live).also { current = it }
        if (s.absentSince != null) {
            s.absentSince = null
            s.card?.let { out += NudgeCommand.ShowCard(it) }
        }
        if (s.stopped || s.card != null) return out
        val elapsed = elapsedSec(s, now)
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
            out += bubble(s, elapsed, live, now)
        }
        return out
    }

    /** 사용자 동작. 카드가 없을 때 온 동작(늦게 온 탭 등)은 무시한다. */
    fun onAction(now: Instant, action: NudgeUserAction, live: LiveInput?): List<NudgeCommand> {
        val s = current ?: return emptyList()
        if (action is NudgeUserAction.BubbleTapped || action is NudgeUserAction.BubbleDismissed) return emptyList()
        if (action is NudgeUserAction.StopNow && s.card == null) return stopFromBubble(s, now, live)
        val card = s.card ?: return emptyList()
        val elapsed = elapsedSec(s, now)
        fun record(response: NudgeResponse, chip: String? = null) {
            s.events += NudgeEvent(0, now, card.level, card.copyId, response, chip)
        }
        s.card = null
        return when (action) {
            is NudgeUserAction.Stop, NudgeUserAction.StopNow -> {
                record(NudgeResponse.STOP)
                s.stopped = true
                val praise = live?.let(writer::praise)
                listOfNotNull(NudgeCommand.Hide, praise?.let { NudgeCommand.ShowPraise(it) })
            }

            is NudgeUserAction.Extend -> {
                record(NudgeResponse.EXTEND, action.reasonChip)
                extensionsToday += 1
                s.windowStartSec = elapsed
                s.dueSec = elapsed + card.extendMinutes * SEC_PER_MIN
                listOf(bubble(s, elapsed, live, now))
            }

            is NudgeUserAction.SnoozeToday -> {
                record(NudgeResponse.SNOOZE_TODAY)
                snoozeUntil = DayBoundary.endOf(DayBoundary.logicalDate(now, zone), zone)
                s.windowStartSec = elapsed
                listOf(bubble(s, elapsed, live, now))
            }

            is NudgeUserAction.Dismiss -> {
                record(NudgeResponse.DISMISSED)
                s.windowStartSec = elapsed
                s.dueSec = elapsed + DEFAULT_EXTEND_MIN * SEC_PER_MIN
                listOf(bubble(s, elapsed, live, now))
            }

            NudgeUserAction.BubbleTapped, NudgeUserAction.BubbleDismissed -> emptyList()
        }
    }

    /** 카드가 오기 전 버블 패널의 '그만 볼래'. 0단계(스스로 멈춤)로 기록하고 칭찬한다. */
    private fun stopFromBubble(s: SessionState, now: Instant, live: LiveInput?): List<NudgeCommand> {
        if (s.stopped) return emptyList()
        s.events += NudgeEvent(0, now, SELF_STOP_LEVEL, null, NudgeResponse.STOP, null)
        s.stopped = true
        val praise = live?.let(writer::praise)
        return listOfNotNull(NudgeCommand.Hide, praise?.let { NudgeCommand.ShowPraise(it) })
    }

    /** 버블: 이번 구간 진행과 표정(곧 말 걸 때는 부르는 얼굴, 오늘은 그만이면 조는 얼굴). */
    private fun bubble(s: SessionState, elapsed: Long, live: LiveInput?, now: Instant): NudgeCommand.ShowBubble {
        val snoozed = isSnoozed(now)
        val windowSec = (s.dueSec - s.windowStartSec).coerceAtLeast(1)
        val windowElapsed = (elapsed - s.windowStartSec).coerceIn(0, windowSec)
        val expression = when {
            snoozed -> BuddyExpression.SLEEPY
            windowElapsed >= windowSec * CALLING_PERCENT / PERCENT -> BuddyExpression.CALLING
            else -> BuddyExpression.WATCHING
        }
        return NudgeCommand.ShowBubble(
            elapsedSec = elapsed,
            windowElapsedSec = if (snoozed) 0 else windowElapsed,
            windowSec = windowSec,
            melt = live?.melt ?: MeltStage.NONE,
            expression = expression,
            appLabel = live?.appLabel.orEmpty(),
            snoozed = snoozed,
            sessionStartMs = s.start.toEpochMilli(),
            realMsToNext = if (snoozed) null else ((windowSec - windowElapsed) * MILLIS_PER_SEC / speed).toLong(),
        )
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

    /** 새 세션. 끊긴 지 [RESUME_GAP] 안이면 저장해 둔 단계·남은 시간을 이어 받는다(끊긴 동안은 본 시간으로 세지 않는다). */
    private fun newSession(live: LiveInput): SessionState {
        val state = SessionState(live.sessionStart, live.thresholdMin * SEC_PER_MIN)
        val c = carry ?: return state
        carry = null
        val gap = Duration.between(c.savedAt, live.sessionStart)
        if (gap > RESUME_GAP || live.sessionStart == c.start) return state
        state.carriedSec = c.elapsedSec
        state.windowStartSec = c.windowStartSec
        state.messaged = c.messaged
        val cardLevel = c.cardLevel
        if (cardLevel != null) {
            // 카드가 떠 있을 때 끊겼다: 같은 단계 카드를 바로 다시 보인다.
            state.level = cardLevel - 1
            state.dueSec = c.elapsedSec
        } else {
            state.level = c.level
            state.dueSec = c.dueSec
        }
        return state
    }

    private fun elapsedSec(s: SessionState, now: Instant): Long =
        s.carriedSec + (Duration.between(s.start, now).toMillis().coerceAtLeast(0) * speed / MILLIS_PER_SEC).toLong()

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

        /** 버블 패널에서 카드 전에 스스로 멈춘 기록의 단계. */
        const val SELF_STOP_LEVEL = 0

        /** 이번 구간이 이만큼(%) 지나면 버블이 부르는 얼굴이 된다. */
        private const val CALLING_PERCENT = 80L
        private const val PERCENT = 100L

        private const val SEC_PER_MIN = 60L
        private val MERGE_GAP: Duration = Duration.ofSeconds(60)

        /** 엔진이 끊긴 뒤(감지 끄기·강제 종료) 이 시간 안에 다시 시작한 세션은 같은 세션으로 잇는다. */
        val RESUME_GAP: Duration = Duration.ofMinutes(5)
        private const val MILLIS_PER_SEC = 1000.0
    }
}
