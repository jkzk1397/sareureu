package com.capstone.pause.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.capstone.pause.core.database.entity.AiCallEntity
import com.capstone.pause.core.database.entity.CopyPackEntity
import com.capstone.pause.core.database.entity.FriendCacheEntity
import com.capstone.pause.core.database.entity.HypothesisEntity
import com.capstone.pause.core.database.entity.WeeklyReportEntity
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Verdict
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** AI 감사 로그. */
@Dao
interface AiCallDao {
    /** 넣기. */
    @Insert
    suspend fun insert(call: AiCallEntity): Long

    /** 최근 호출. */
    @Query("SELECT * FROM ai_calls ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AiCallEntity>>

    /** 신고 표시. */
    @Query("UPDATE ai_calls SET reported = 1 WHERE id = :id")
    suspend fun markReported(id: Long)

    /** 30일 지난 기록 지우기. */
    @Query("DELETE FROM ai_calls WHERE at < :before")
    suspend fun deleteOlderThan(before: Instant)
}

/** 주간 리포트·가설. */
@Dao
interface ReportDao {
    /** 리포트 저장. */
    @Upsert
    suspend fun upsertReport(report: WeeklyReportEntity)

    /** 주 시작일로 리포트 읽기. */
    @Query("SELECT * FROM weekly_reports WHERE weekStart = :weekStart AND source = :source")
    fun observeReport(weekStart: String, source: DataSource): Flow<WeeklyReportEntity?>

    /** 가설 저장. */
    @Upsert
    suspend fun upsertHypotheses(items: List<HypothesisEntity>)

    /** 그 주의 가설. */
    @Query("SELECT * FROM hypotheses WHERE weekStart = :weekStart AND source = :source")
    fun observeHypotheses(weekStart: String, source: DataSource): Flow<List<HypothesisEntity>>

    /** 모든 가설(내보내기용). */
    @Query("SELECT * FROM hypotheses ORDER BY weekStart")
    suspend fun getAllHypotheses(): List<HypothesisEntity>

    /** 판정 저장. */
    @Query("UPDATE hypotheses SET verdict = :verdict WHERE id = :id")
    suspend fun setVerdict(id: String, verdict: Verdict?)

    /** 출처별 리포트 지우기. */
    @Query("DELETE FROM weekly_reports WHERE source = :source")
    suspend fun deleteReportsBySource(source: DataSource)

    /** 출처별 가설 지우기. */
    @Query("DELETE FROM hypotheses WHERE source = :source")
    suspend fun deleteHypothesesBySource(source: DataSource)
}

/** 문구 팩. */
@Dao
interface CopyPackDao {
    /** 저장. */
    @Upsert
    suspend fun upsert(pack: CopyPackEntity)

    /** 활성 팩. */
    @Query("SELECT * FROM copy_pack WHERE active = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActive(): Flow<CopyPackEntity?>

    /** 모두 비활성. */
    @Query("UPDATE copy_pack SET active = 0")
    suspend fun deactivateAll()
}

/** 친구 캐시. */
@Dao
interface FriendDao {
    /** 저장. */
    @Upsert
    suspend fun upsert(friends: List<FriendCacheEntity>)

    /** 출처별 친구. */
    @Query("SELECT * FROM friends_cache WHERE source = :source ORDER BY nickname")
    fun observe(source: DataSource): Flow<List<FriendCacheEntity>>

    /** 출처별 지우기. */
    @Query("DELETE FROM friends_cache WHERE source = :source")
    suspend fun deleteBySource(source: DataSource)
}
