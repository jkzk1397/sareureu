package com.capstone.pause.core.sensing.session

import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.model.AppSegment
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.model.Surface
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.context.ContextResult
import com.capstone.pause.core.sensing.path.AppKindLookup
import com.capstone.pause.core.sensing.path.PathResolver
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 이벤트 → 숏폼 세션 상태 기계(순수, 시계는 이벤트 시각과 [onTick]으로만 받는다).
 * - 숏폼 앱끼리 바로 넘어가거나 다른 앱에 다녀와도 60초 안에 돌아오면 같은 세션(앱별 [AppSegment]).
 * - 다른 앱에 60초 이상 있거나 화면이 꺼지면 세션 끝. 끝 시각은 숏폼을 떠난 시각.
 * 진행 중 세션은 [liveSession](숏폼 화면에 있는 동안만), 끝난 세션은 [onEvent]·[onTick]의 반환값.
 */
class SessionTracker(
    private val isShortform: (String) -> Boolean,
    private val kinds: AppKindLookup,
    private val classify: (start: Instant, firstUnlockToday: LocalDateTime?) -> ContextResult,
    private val zone: ZoneId,
    private val source: DataSource = DataSource.REAL,
    private val surfaceOf: (String) -> Surface = { Surface.APP_UNKNOWN },
) {
    private class Active(
        val start: Instant,
        val entry: EntryPoint,
        val context: ContextResult,
        val segments: MutableList<AppSegment> = mutableListOf(),
        var openPackage: String?,
        var openSince: Instant?,
        var leftAt: Instant? = null,
        var leftTo: String? = null,
    )

    private val live = MutableStateFlow<ShortformSession?>(null)

    /** 지금 숏폼 화면에 있는 진행 중 세션(`end = null`). 떠나 있는 동안은 null. */
    val liveSession: StateFlow<ShortformSession?> = live.asStateFlow()

    private var foreground: String? = null
    private var lastUnlockAt: Instant? = null
    private var firstUnlockAt: Instant? = null
    private var lastNudgeStopAt: Instant? = null
    private var active: Active? = null

    /** '그만'을 눌렀다(이탈을 [ExitPoint.AfterNudgeStop]으로 셀 수 있게). */
    fun onNudgeStop(at: Instant) {
        lastNudgeStopAt = at
    }

    /** 이벤트 하나 처리. 끝난 세션들을 돌려준다. */
    fun onEvent(event: ForegroundEvent): List<ShortformSession> {
        val finished = mutableListOf<ShortformSession>()
        when (event) {
            is ForegroundEvent.Unlock -> {
                lastUnlockAt = event.at
                val first = firstUnlockAt
                if (first == null || DayBoundary.logicalDate(first, zone) != DayBoundary.logicalDate(event.at, zone)) {
                    firstUnlockAt = event.at
                }
            }

            is ForegroundEvent.ScreenOn -> Unit

            is ForegroundEvent.ScreenOff -> {
                active?.let { a ->
                    closeOpenSegment(a, event.at)
                    if (a.leftAt == null) a.leftAt = event.at
                    finished += finish(a, screenOffAt = event.at)
                }
                foreground = null
            }

            is ForegroundEvent.AppForeground -> finished += onForeground(event.packageName, event.at)
        }
        publishLive(event.at)
        return finished
    }

    /** 시간이 흘렀다: 다른 앱에 60초 이상 있었으면 세션을 끝낸다. */
    fun onTick(now: Instant): List<ShortformSession> {
        val a = active ?: return emptyList()
        val left = a.leftAt ?: return emptyList<ShortformSession>().also { publishLive(now) }
        if (Duration.between(left, now) < MERGE_GAP) return emptyList()
        return listOf(finish(a, screenOffAt = null)).also { publishLive(now) }
    }

    private fun onForeground(pkg: String, at: Instant): List<ShortformSession> {
        val previous = foreground
        if (pkg == previous) return emptyList()
        foreground = pkg
        val finished = mutableListOf<ShortformSession>()
        val a = active
        if (isShortform(pkg)) {
            val left = a?.leftAt
            if (a != null && left != null && Duration.between(left, at) >= MERGE_GAP) {
                finished += finish(a, screenOffAt = null)
            }
            val current = active
            if (current == null) {
                startSession(pkg, at, previous)
            } else {
                closeOpenSegment(current, at)
                current.leftAt = null
                current.leftTo = null
                current.openPackage = pkg
                current.openSince = at
            }
        } else if (a != null && a.openPackage != null) {
            closeOpenSegment(a, at)
            a.leftAt = at
            a.leftTo = pkg
        }
        return finished
    }

    private fun startSession(pkg: String, at: Instant, previous: String?) {
        val entry = PathResolver.entry(at, pkg, lastUnlockAt, previous, isShortform, kinds)
        val firstUnlock = firstUnlockAt
            ?.takeIf { DayBoundary.logicalDate(it, zone) == DayBoundary.logicalDate(at, zone) }
            ?.let { LocalDateTime.ofInstant(it, zone) }
        active = Active(at, entry, classify(at, firstUnlock), openPackage = pkg, openSince = at)
    }

    private fun closeOpenSegment(a: Active, at: Instant) {
        val pkg = a.openPackage ?: return
        val since = a.openSince ?: return
        if (at.isAfter(since)) a.segments += AppSegment(pkg, surfaceOf(pkg), since, at)
        a.openPackage = null
        a.openSince = null
    }

    private fun finish(a: Active, screenOffAt: Instant?): ShortformSession {
        active = null
        val end = a.leftAt ?: screenOffAt ?: a.segments.lastOrNull()?.end ?: a.start
        val exit = PathResolver.exit(end, a.leftTo, screenOffAt, lastNudgeStopAt, kinds)
        return build(a, end, exit)
    }

    private fun build(a: Active, end: Instant?, exit: ExitPoint?) = ShortformSession(
        id = 0,
        start = a.start,
        end = end,
        segments = a.segments.toList(),
        context = a.context.slot,
        contextConfidence = a.context.confidence,
        contextConfirmed = false,
        entry = a.entry,
        exit = exit,
        interaction = null,
        maxFrictionLevel = 0,
        extensions = 0,
        stoppedByNudge = exit == ExitPoint.AfterNudgeStop,
        checkIn = null,
        source = source,
    )

    private fun publishLive(now: Instant) {
        val a = active
        val pkg = a?.openPackage
        val since = a?.openSince
        live.value = if (a == null || pkg == null || since == null) {
            null
        } else {
            val open = AppSegment(pkg, surfaceOf(pkg), since, maxOf(since, now))
            build(a, end = null, exit = null).copy(segments = a.segments + open)
        }
    }

    companion object {
        /** 같은 세션으로 잇는 최대 공백. */
        val MERGE_GAP: Duration = Duration.ofSeconds(60)
    }
}
