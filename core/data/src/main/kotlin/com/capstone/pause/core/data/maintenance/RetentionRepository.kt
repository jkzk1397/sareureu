package com.capstone.pause.core.data.maintenance

import com.capstone.pause.core.database.dao.AiCallDao
import com.capstone.pause.core.database.dao.SessionDao
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 보관 기간 정리(04 문서 3절): 원시 이벤트 7일, 세션 90일, AI 감사 로그 30일. */
interface RetentionRepository {
    /** [now] 기준으로 기간이 지난 기록을 지운다. */
    suspend fun cleanUp(now: Instant)
}

/** 보관 기간 값. */
object RetentionPolicy {
    /** 원시 이벤트. */
    val RAW_EVENTS: Duration = Duration.ofDays(7)

    /** 세션(설정 변경 가능 — 지금은 기본값). */
    val SESSIONS: Duration = Duration.ofDays(90)

    /** AI 감사 로그. */
    val AI_CALLS: Duration = Duration.ofDays(30)
}

@Singleton
internal class RoomRetentionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val aiCallDao: AiCallDao,
) : RetentionRepository {
    override suspend fun cleanUp(now: Instant) {
        sessionDao.deleteRawEventsOlderThan(now.minus(RetentionPolicy.RAW_EVENTS))
        sessionDao.deleteOlderThan(now.minus(RetentionPolicy.SESSIONS))
        aiCallDao.deleteOlderThan(now.minus(RetentionPolicy.AI_CALLS))
    }
}
