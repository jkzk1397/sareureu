package com.capstone.pause.core.model

import java.time.Instant

/** 진입 경로: 어디서 넘어왔나. */
sealed interface EntryPoint {
    /** 잠금 해제 10초 이내. */
    data object Unlock : EntryPoint

    /** 홈 화면(런처)에서. */
    data object Home : EntryPoint

    /** 직전 앱 종류. */
    data class FromApp(val kind: AppKind) : EntryPoint

    /** 다른 숏폼에서(도미노). */
    data class FromShortform(val packageName: String) : EntryPoint

    /** 알 수 없음. */
    data object Unknown : EntryPoint
}

/** 이탈 경로: 어디로 나갔나. */
sealed interface ExitPoint {
    /** 화면 끔. */
    data object ScreenOff : ExitPoint

    /** 홈 화면으로. */
    data object Home : ExitPoint

    /** 다른 앱 종류로. */
    data class ToApp(val kind: AppKind) : ExitPoint

    /** '그만' 뒤 60초 이내. */
    data object AfterNudgeStop : ExitPoint

    /** 알 수 없음. */
    data object Unknown : ExitPoint
}

/** 한 세션 안의 앱별 구간. */
data class AppSegment(val packageName: String, val surface: Surface, val start: Instant, val end: Instant)

/** 넘김·클릭 통계(정밀 모드에서만). */
data class InteractionStats(val swipes: Int, val medianSwipeIntervalSec: Double, val likes: Int, val otherClicks: Int)

/** 숏폼 세션. 숏폼 앱끼리 60초 이내 전환은 한 세션이다. [end]가 null이면 진행 중. */
data class ShortformSession(
    val id: Long,
    val start: Instant,
    val end: Instant?,
    val segments: List<AppSegment>,
    val context: ContextSlot,
    val contextConfidence: Confidence,
    val contextConfirmed: Boolean,
    val entry: EntryPoint,
    val exit: ExitPoint?,
    val interaction: InteractionStats?,
    val maxFrictionLevel: Int,
    val extensions: Int,
    val stoppedByNudge: Boolean,
    val checkIn: Mood?,
    val source: DataSource,
)
