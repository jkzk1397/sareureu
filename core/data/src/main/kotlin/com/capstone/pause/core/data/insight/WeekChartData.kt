package com.capstone.pause.core.data.insight

import com.capstone.pause.core.model.ContextSlot
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 리포트 차트 값(목업 05). 모두 `core:insights`가 계산했다. 화면은 그리기만 한다.
 * 주는 AI 주간 분석과 같은 지난 7일(오늘-7 ~ 어제, 새벽 4시 경계).
 */
data class WeekChartData(
    val weekStart: LocalDate,
    val weekEndInclusive: LocalDate,
    val isDemo: Boolean,
    val totalMinutes: Int,
    val sessionCount: Int,
    /** 일주일 시계: 시간(0~23)별 맥락별 분. */
    val minutesByHour: List<Map<ContextSlot, Int>>,
    /** 위험 시간대(시 범위) — 시계 테두리 점선. */
    val riskHours: List<IntRange>,
    /** 표 보기: 요일(월~일) × 2시간 칸. */
    val heatmapDays: List<DayOfWeek>,
    val heatmap: List<List<Int>>,
    /** 위험 칸(행, 열). */
    val riskCells: Set<Pair<Int, Int>>,
    /** 가장 길었던 이어진 2시간. */
    val peak: PeakSummary?,
    /** 맥락별 합계(많은 순). [ContextShare.share]는 0~1. */
    val contexts: List<ContextShare>,
    /** 흐름도(상위 5 + 기타). 키는 화면이 문구로 바꾼다. */
    val flow: FlowSummary,
    /** 세션 수가 3개 이상인 진입 중 길이 중앙값이 가장 긴 것. */
    val longestEntry: EntryLengthSummary?,
    /** 세션 길이 분포: 5개 칸(0–5, 5–10, 10–20, 20–40, 40+분)의 비율 0~1. */
    val lengthShares: List<Double>,
    /** 또래 비교. 그룹이 없거나 10명 미만이면 null. */
    val peer: PeerSummary?,
)

/** 이어진 2시간 [startHour]부터, 그 주 합계 [minutes]. */
data class PeakSummary(val startHour: Int, val minutes: Int)

/** 맥락 하나의 합계와 비중. [barFraction]은 1위를 1로 둔 막대 길이. */
data class ContextShare(val slot: ContextSlot, val minutes: Int, val share: Double, val barFraction: Float)

/** 흐름도 값. 키 규칙: 진입·이탈은 UNLOCK·HOME·SHORTFORM·SCREEN_OFF·NUDGE_STOP·UNKNOWN·앱 종류 이름, 앱은 YOUTUBE 등, 기타는 [OTHER_GROUP]. */
data class FlowSummary(
    val entries: List<Pair<String, Int>>,
    val apps: List<Pair<String, Int>>,
    val exits: List<Pair<String, Int>>,
    val entryToApp: List<Triple<String, String, Int>>,
    val appToExit: List<Triple<String, String, Int>>,
) {
    companion object {
        /** 접은 항목 키. */
        const val OTHER_GROUP = "OTHER_GROUP"
    }
}

/** 진입 경로 하나의 길이 중앙값. */
data class EntryLengthSummary(val key: String, val sessions: Int, val medianMinutes: Int)

/** 또래 비교: 내 하루 평균, 그룹 중앙값·상위 25%, 내 기준선. [isDemo]면 '시연용 가상 데이터'. */
data class PeerSummary(
    val myMinutesPerDay: Int,
    val peerMedian: Int,
    val peerP75: Int,
    val myBaseline: Int?,
    val groupSize: Int,
    val isDemo: Boolean,
    /** 막대 오른쪽 끝(분). 한 시간 단위로 올림, 최소 3시간. */
    val scaleMaxMinutes: Int,
) {
    /** [minutes]의 막대 위 위치 0~1. */
    fun position(minutes: Int): Float = (minutes.toFloat() / scaleMaxMinutes).coerceIn(0f, 1f)
}
