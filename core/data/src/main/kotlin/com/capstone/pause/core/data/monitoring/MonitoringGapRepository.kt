package com.capstone.pause.core.data.monitoring

import com.capstone.pause.core.insights.TimeSpan
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * 사용자가 감지를 꺼 둔 구간 기록(`MonitoringGap`). 리포트의 기록된 비율(`coverage`)과 연속 일수(`GoalStreak`)가
 * 이 구간을 빼고 센다. 감지를 끄면 0분으로 보이는 것을 유리하게 쓰지 못하게 하려는 것이다.
 * 실제 기록에만 쓴다(시연 모드 계산에는 넣지 않는다). 처음 감지를 켜기 전은 구간이 아니다.
 * `core:intervention`의 `MonitoringController`가 끄고 켤 때 적는다.
 */
interface MonitoringGapRepository {
    /** 꺼 둔 구간들(오래된 것부터). 아직 꺼져 있으면 마지막 구간의 [MonitoringGap.onAt]이 null. */
    val gaps: Flow<List<MonitoringGap>>

    /** 감지를 껐다. 이미 열린 구간이 있으면 그대로 둔다. */
    suspend fun recordOff(at: Instant)

    /** 감지를 다시 켰다. 열린 구간이 없으면 아무것도 하지 않는다. */
    suspend fun recordOn(at: Instant)

    /** 모두 지우기(모든 기록 삭제). */
    suspend fun clear()
}

/** 감지를 꺼 둔 구간 하나. [onAt]이 null이면 아직 꺼져 있다. */
data class MonitoringGap(val offAt: Instant, val onAt: Instant?)

/** 계산용 구간으로 바꾼다. 아직 꺼져 있는 구간은 [now]까지로 본다. */
fun List<MonitoringGap>.toSpans(now: Instant): List<TimeSpan> = map { TimeSpan(it.offAt, it.onAt ?: now) }
