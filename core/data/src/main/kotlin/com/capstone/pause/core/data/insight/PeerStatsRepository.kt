package com.capstone.pause.core.data.insight

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * 또래 비교 통계(`PeerBaseline`) 캐시. 친구 서버가 하루 한 번 동의한 사용자의 하루 합계로 계산한
 * 연령대·신분별 **그룹 집계**만 담는다. 10명 미만 그룹과 개인별 값은 없다.
 * 값이 없으면(서버 없음·아직 안 받음) 실제 모드의 또래 비교 카드는 숨는다.
 */
interface PeerStatsRepository {
    /** 마지막으로 받은 통계. 없으면 null. */
    val stats: Flow<PeerStats?>

    /** 서버에서 받은 통계 저장(core:social이 부른다). */
    suspend fun save(stats: PeerStats)

    /** 지우기(공유 철회·모든 기록 삭제). */
    suspend fun clear()
}

/** 서버가 내려 준 또래 통계 묶음. */
data class PeerStats(val groups: List<PeerGroupStat>, val fetchedAt: Instant)

/** 연령대·신분 한 그룹: 사람 수와 하루 평균 시청 분의 중앙값·상위 25%. [ageBand]·[occupation]은 enum 이름. */
data class PeerGroupStat(val ageBand: String, val occupation: String, val n: Int, val p50: Int, val p75: Int)
