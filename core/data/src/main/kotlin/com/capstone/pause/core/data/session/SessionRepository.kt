package com.capstone.pause.core.data.session

import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.database.dao.NudgeEventDao
import com.capstone.pause.core.database.dao.SessionDao
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.ShortformSession
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** 숏폼 세션 저장소. 읽기는 지금 쓰는 출처(시연 모드면 DEMO, 아니면 REAL)만 돌려준다. */
interface SessionRepository {
    /** [from]~[to]에 걸친 세션. */
    fun observeSessions(from: Instant, to: Instant): Flow<List<ShortformSession>>

    /** [from]~[to]의 개입 기록. */
    fun observeNudges(from: Instant, to: Instant): Flow<List<NudgeEvent>>

    /** 세션과 개입 기록을 저장하고 세션 ID를 돌려준다. 출처는 [ShortformSession.source]. */
    suspend fun insert(session: ShortformSession, nudges: List<NudgeEvent>): Long

    /** 출처별 세션·개입 기록 모두 지우기. */
    suspend fun deleteAll(source: DataSource)

    /**
     * 진행 중에 모아 둔 개입 기록을 저장된 세션에 붙인다. 세션은 끝나야 저장되고 ID가 생기므로,
     * 시작 시각([sessionStart])과 출처로 찾는다. 아직 저장되지 않았으면 false.
     */
    suspend fun attachNudges(sessionStart: Instant, source: DataSource, nudges: List<NudgeEvent>): Boolean = false

    /** [since] 이후 세션 중 최근 [limit]개(최신순). 설정 > 개발자 메뉴용. */
    fun observeRecent(since: Instant, limit: Int): Flow<List<ShortformSession>> =
        observeSessions(since, since.plus(Duration.ofDays(FAR_FUTURE_DAYS))).map { it.takeLast(limit).reversed() }
}

private const val FAR_FUTURE_DAYS = 3650L

/** 설정의 시연 모드로 읽을 출처를 정한다. */
internal fun SettingsRepository.activeSource(): Flow<DataSource> =
    settings.map { if (it.demoMode) DataSource.DEMO else DataSource.REAL }.distinctUntilChanged()

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomSessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val nudgeEventDao: NudgeEventDao,
    private val settingsRepository: SettingsRepository,
) : SessionRepository {

    override fun observeSessions(from: Instant, to: Instant): Flow<List<ShortformSession>> =
        settingsRepository.activeSource().flatMapLatest { source ->
            sessionDao.observeRange(source, from, to).map { rows -> rows.map { it.toModel() } }
        }

    override fun observeNudges(from: Instant, to: Instant): Flow<List<NudgeEvent>> =
        settingsRepository.activeSource().flatMapLatest { source ->
            nudgeEventDao.observeRange(source, from, to).map { rows -> rows.map { it.toModel() } }
        }

    override suspend fun insert(session: ShortformSession, nudges: List<NudgeEvent>): Long {
        val id = sessionDao.insertWithSegments(session.toEntity(), session.segmentEntities())
        if (nudges.isNotEmpty()) nudgeEventDao.insert(nudges.map { it.toEntity(id, session.source) })
        return id
    }

    override suspend fun attachNudges(sessionStart: Instant, source: DataSource, nudges: List<NudgeEvent>): Boolean {
        // DB는 밀리초까지만 저장하므로 밀리초로 맞춰 비교한다.
        val millis = sessionStart.toEpochMilli()
        val from = Instant.ofEpochMilli(millis)
        val row = sessionDao.getRange(source, from, from.plusSeconds(1))
            .firstOrNull { it.session.start.toEpochMilli() == millis } ?: return false
        if (nudges.isEmpty()) return true
        nudgeEventDao.insert(nudges.map { it.toEntity(row.session.id, source) })
        sessionDao.updateSession(
            row.session.copy(
                maxFrictionLevel = maxOf(row.session.maxFrictionLevel, nudges.maxOf { it.level }),
                extensions = row.session.extensions + nudges.count { it.response == NudgeResponse.EXTEND },
                stoppedByNudge = row.session.stoppedByNudge || nudges.any { it.response == NudgeResponse.STOP },
            ),
        )
        return true
    }

    override suspend fun deleteAll(source: DataSource) {
        nudgeEventDao.deleteBySource(source)
        sessionDao.deleteBySource(source)
    }
}
