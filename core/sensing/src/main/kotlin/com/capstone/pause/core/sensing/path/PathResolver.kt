package com.capstone.pause.core.sensing.path

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import java.time.Duration
import java.time.Instant

/** 패키지 → 앱 종류. 안드로이드 구현은 `AndroidAppKindResolver`, 테스트는 맵. */
fun interface AppKindLookup {
    /** [packageName]의 앱 종류. 모르면 [AppKind.OTHER]. */
    fun kindOf(packageName: String): AppKind
}

/**
 * 진입·이탈 경로 계산(순수 함수, 03 문서 3.1절).
 * 진입: 잠금 해제 10초 이내 → 직전 앱이 다른 숏폼(도미노) → 런처(홈) → 직전 앱 종류.
 * 이탈: '그만' 뒤 60초 이내 → 떠난 뒤 10초 안에 화면 끔 → 홈 → 다음 앱 종류.
 */
object PathResolver {
    /** 잠금 해제 직후로 보는 시간. */
    val UNLOCK_WINDOW: Duration = Duration.ofSeconds(10)

    /** 떠난 뒤 화면을 끈 것으로 보는 시간. */
    val SCREEN_OFF_WINDOW: Duration = Duration.ofSeconds(10)

    /** '그만' 뒤 떠난 것으로 보는 시간. */
    val AFTER_STOP_WINDOW: Duration = Duration.ofSeconds(60)

    /** 세션이 [start]에 [currentPackage]로 시작할 때의 진입 경로. */
    fun entry(
        start: Instant,
        currentPackage: String,
        lastUnlockAt: Instant?,
        previousPackage: String?,
        isShortform: (String) -> Boolean,
        kinds: AppKindLookup,
    ): EntryPoint {
        if (lastUnlockAt != null && within(lastUnlockAt, start, UNLOCK_WINDOW)) return EntryPoint.Unlock
        if (previousPackage == null) return EntryPoint.Unknown
        if (previousPackage != currentPackage && isShortform(previousPackage)) {
            return EntryPoint.FromShortform(previousPackage)
        }
        return when (val kind = kinds.kindOf(previousPackage)) {
            AppKind.LAUNCHER -> EntryPoint.Home
            else -> EntryPoint.FromApp(kind)
        }
    }

    /** [leftAt]에 숏폼을 떠나 [nextPackage]로 갔을 때의 이탈 경로. */
    fun exit(
        leftAt: Instant,
        nextPackage: String?,
        screenOffAt: Instant?,
        lastNudgeStopAt: Instant?,
        kinds: AppKindLookup,
    ): ExitPoint {
        if (lastNudgeStopAt != null &&
            within(lastNudgeStopAt, leftAt, AFTER_STOP_WINDOW)
        ) {
            return ExitPoint.AfterNudgeStop
        }
        if (screenOffAt != null && within(leftAt, screenOffAt, SCREEN_OFF_WINDOW)) return ExitPoint.ScreenOff
        if (nextPackage == null) return ExitPoint.Unknown
        return when (val kind = kinds.kindOf(nextPackage)) {
            AppKind.LAUNCHER -> ExitPoint.Home
            else -> ExitPoint.ToApp(kind)
        }
    }

    /** [later]가 [earlier]부터 [window] 안(같은 시각 포함)이면 true. */
    private fun within(earlier: Instant, later: Instant, window: Duration): Boolean =
        !later.isBefore(earlier) && Duration.between(earlier, later) <= window
}
