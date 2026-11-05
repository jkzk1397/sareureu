package com.capstone.pause.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.capstone.pause.core.database.entity.MessageEntity
import com.capstone.pause.core.database.entity.NudgeEventEntity
import com.capstone.pause.core.database.entity.PlanEntity
import com.capstone.pause.core.database.entity.PokeInboxEntity
import com.capstone.pause.core.model.DataSource
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 개입 기록. */
@Dao
interface NudgeEventDao {
    /** 넣기. */
    @Insert
    suspend fun insert(events: List<NudgeEventEntity>)

    /** 모든 개입 기록(내보내기용). */
    @Query("SELECT * FROM nudge_events ORDER BY at")
    suspend fun getAll(): List<NudgeEventEntity>

    /** 구간 안의 개입 기록. */
    @Query("SELECT * FROM nudge_events WHERE source = :source AND at >= :from AND at < :to ORDER BY at")
    fun observeRange(source: DataSource, from: Instant, to: Instant): Flow<List<NudgeEventEntity>>

    /** 출처별 지우기. */
    @Query("DELETE FROM nudge_events WHERE source = :source")
    suspend fun deleteBySource(source: DataSource)
}

/** 르르 메시지·채팅. */
@Dao
interface MessageDao {
    /** 넣기. */
    @Insert
    suspend fun insert(message: MessageEntity): Long

    /** 모든 메시지(내보내기용). */
    @Query("SELECT * FROM messages ORDER BY at")
    suspend fun getAll(): List<MessageEntity>

    /** 최근 메시지. */
    @Query("SELECT * FROM messages WHERE source = :source ORDER BY at DESC LIMIT :limit")
    fun observeRecent(source: DataSource, limit: Int): Flow<List<MessageEntity>>

    /** 출처별 지우기. */
    @Query("DELETE FROM messages WHERE source = :source")
    suspend fun deleteBySource(source: DataSource)
}

/** 만약~하면 계획. */
@Dao
interface PlanDao {
    /** 넣기. */
    @Insert
    suspend fun insert(plan: PlanEntity): Long

    /** 모든 계획(내보내기용). */
    @Query("SELECT * FROM plans ORDER BY createdAt")
    suspend fun getAll(): List<PlanEntity>

    /** 켜 둔 계획(최신순). */
    @Query("SELECT * FROM plans WHERE source = :source AND active = 1 ORDER BY createdAt DESC")
    fun observeActive(source: DataSource): Flow<List<PlanEntity>>

    /** 출처별 지우기. */
    @Query("DELETE FROM plans WHERE source = :source")
    suspend fun deleteBySource(source: DataSource)
}

/** 받은 잔소리. */
@Dao
interface PokeDao {
    /** 넣거나 바꾸기. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pokes: List<PokeInboxEntity>)

    /** 아직 안 쓴, 만료 전 잔소리(오래된 순). */
    @Query("SELECT * FROM pokes_inbox WHERE source = :source AND used = 0 AND expiresAt > :now ORDER BY createdAt")
    fun observeUnused(source: DataSource, now: Instant): Flow<List<PokeInboxEntity>>

    /** 사용 표시. */
    @Query("UPDATE pokes_inbox SET used = 1 WHERE id = :id")
    suspend fun markUsed(id: String)

    /** 출처별 지우기. */
    @Query("DELETE FROM pokes_inbox WHERE source = :source")
    suspend fun deleteBySource(source: DataSource)

    /** 업서트(동기화용). */
    @Upsert
    suspend fun upsert(poke: PokeInboxEntity)
}
