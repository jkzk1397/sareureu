package com.capstone.pause.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.capstone.pause.core.database.entity.RawEventEntity
import com.capstone.pause.core.database.entity.SegmentEntity
import com.capstone.pause.core.database.entity.SessionEntity
import com.capstone.pause.core.database.entity.SessionWithSegments
import com.capstone.pause.core.model.DataSource
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 세션·구간·원시 이벤트. */
@Dao
interface SessionDao {
    /** [from] 이후 시작했거나 아직 진행 중인 세션(출처별). */
    @Transaction
    @Query(
        "SELECT * FROM sessions WHERE source = :source AND (start >= :from OR `end` IS NULL OR `end` > :from) " +
            "AND start < :to ORDER BY start",
    )
    fun observeRange(source: DataSource, from: Instant, to: Instant): Flow<List<SessionWithSegments>>

    /** 한 번 읽기. */
    @Transaction
    @Query(
        "SELECT * FROM sessions WHERE source = :source AND (`end` IS NULL OR `end` > :from) AND start < :to ORDER BY start",
    )
    suspend fun getRange(source: DataSource, from: Instant, to: Instant): List<SessionWithSegments>

    /** 세션 하나 넣기. */
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    /** 세션 고치기. */
    @Update
    suspend fun updateSession(session: SessionEntity)

    /** 구간 넣기. */
    @Insert
    suspend fun insertSegments(segments: List<SegmentEntity>)

    /** 세션과 구간을 함께 넣고 새 ID를 돌려준다. */
    @Transaction
    suspend fun insertWithSegments(session: SessionEntity, segments: List<SegmentEntity>): Long {
        val id = insertSession(session)
        insertSegments(segments.map { it.copy(sessionId = id) })
        return id
    }

    /** 모든 세션(내보내기용, 출처 모두). */
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY start")
    suspend fun getAll(): List<SessionWithSegments>

    /** 출처별 세션 수. */
    @Query("SELECT COUNT(*) FROM sessions WHERE source = :source")
    suspend fun count(source: DataSource): Int

    /** 출처별 전부 지우기(구간은 CASCADE). */
    @Query("DELETE FROM sessions WHERE source = :source")
    suspend fun deleteBySource(source: DataSource)

    /** 보관 기간 지난 세션 지우기. */
    @Query("DELETE FROM sessions WHERE start < :before")
    suspend fun deleteOlderThan(before: Instant)

    /** 원시 이벤트 넣기. */
    @Insert
    suspend fun insertRawEvents(events: List<RawEventEntity>)

    /** 보관 기간 지난 원시 이벤트 지우기(7일). */
    @Query("DELETE FROM raw_events WHERE at < :before")
    suspend fun deleteRawEventsOlderThan(before: Instant)
}
