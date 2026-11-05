package com.capstone.pause.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.capstone.pause.core.database.dao.AiCallDao
import com.capstone.pause.core.database.dao.CopyPackDao
import com.capstone.pause.core.database.dao.FriendDao
import com.capstone.pause.core.database.dao.MessageDao
import com.capstone.pause.core.database.dao.NudgeEventDao
import com.capstone.pause.core.database.dao.PlanDao
import com.capstone.pause.core.database.dao.PokeDao
import com.capstone.pause.core.database.dao.ReportDao
import com.capstone.pause.core.database.dao.SessionDao
import com.capstone.pause.core.database.entity.AiCallEntity
import com.capstone.pause.core.database.entity.CopyPackEntity
import com.capstone.pause.core.database.entity.FriendCacheEntity
import com.capstone.pause.core.database.entity.HypothesisEntity
import com.capstone.pause.core.database.entity.MessageEntity
import com.capstone.pause.core.database.entity.NudgeEventEntity
import com.capstone.pause.core.database.entity.PlanEntity
import com.capstone.pause.core.database.entity.PokeInboxEntity
import com.capstone.pause.core.database.entity.RawEventEntity
import com.capstone.pause.core.database.entity.SegmentEntity
import com.capstone.pause.core.database.entity.SessionEntity
import com.capstone.pause.core.database.entity.WeeklyReportEntity
import java.time.Instant

/** 앱 DB `pause.db`(04 문서 6절). 버전을 올리면 마이그레이션 + 테스트 + schemas/ JSON을 같은 PR에. */
@Database(
    entities = [
        RawEventEntity::class,
        SessionEntity::class,
        SegmentEntity::class,
        NudgeEventEntity::class,
        MessageEntity::class,
        PlanEntity::class,
        HypothesisEntity::class,
        WeeklyReportEntity::class,
        CopyPackEntity::class,
        AiCallEntity::class,
        FriendCacheEntity::class,
        PokeInboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(PauseConverters::class)
abstract class PauseDatabase : RoomDatabase() {
    /** 세션. */
    abstract fun sessionDao(): SessionDao

    /** 개입 기록. */
    abstract fun nudgeEventDao(): NudgeEventDao

    /** 메시지. */
    abstract fun messageDao(): MessageDao

    /** 계획. */
    abstract fun planDao(): PlanDao

    /** 잔소리. */
    abstract fun pokeDao(): PokeDao

    /** 리포트·가설. */
    abstract fun reportDao(): ReportDao

    /** 문구 팩. */
    abstract fun copyPackDao(): CopyPackDao

    /** AI 감사 로그. */
    abstract fun aiCallDao(): AiCallDao

    /** 친구 캐시. */
    abstract fun friendDao(): FriendDao

    companion object {
        /** DB 파일 이름. */
        const val NAME = "pause.db"
    }
}

/** Room 형 변환: 시각은 epoch 밀리초로 저장한다. */
class PauseConverters {
    /** Instant → Long. */
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    /** Long → Instant. */
    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
