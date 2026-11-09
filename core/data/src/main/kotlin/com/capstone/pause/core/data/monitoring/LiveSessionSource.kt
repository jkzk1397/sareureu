package com.capstone.pause.core.data.monitoring

import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.ShortformSession
import kotlinx.coroutines.flow.Flow

/**
 * 아직 저장되지 않은 진행 중 세션. 세션은 끝나야 저장되므로, 오늘 탭이 지금 보는 시간을 바로 세려면 이것을 더한다.
 * 구현은 `core:sensing`의 `SensingPipeline`이고 그 모듈 `di/`에서 묶는다.
 */
interface LiveSessionSource {
    /**
     * 진행 중 세션. 숏폼 화면에 있으면 `end = null`, 잠깐 다른 앱에 가 있으면(60초 안) `end` = 떠난 시각. 없으면 null.
     */
    val current: Flow<ShortformSession?>
}

/**
 * 저장된 세션 [saved]에 진행 중 세션 [live]를 더한다(같은 출처일 때만, 이미 저장된 것은 빼고). 시간순을 지킨다.
 */
fun mergeLiveSession(
    saved: List<ShortformSession>,
    live: ShortformSession?,
    activeSource: DataSource,
): List<ShortformSession> {
    if (live == null || live.source != activeSource) return saved
    if (saved.any { it.start.toEpochMilli() == live.start.toEpochMilli() }) return saved
    return (saved + live).sortedBy { it.start }
}
