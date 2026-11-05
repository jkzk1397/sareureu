package com.capstone.pause.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Mood
import com.capstone.pause.core.model.Surface
import java.time.Instant

/** 앱 전환 원시 이벤트(7일 보관). 패키지와 이벤트 종류만, 화면 글자는 없다. */
@Entity(tableName = "raw_events", indices = [Index("at")])
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Instant,
    val packageName: String?,
    val type: String,
    val source: DataSource,
)

/**
 * 숏폼 세션. 진입·이탈은 종류([entryType])와 값([entryValue]: 앱 종류 이름이나 패키지)으로 나눠 저장한다.
 * 정밀 모드 통계는 있을 때만 채운다.
 */
@Entity(tableName = "sessions", indices = [Index("start"), Index("source")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Instant,
    val end: Instant?,
    val context: ContextSlot,
    val contextConfidence: Confidence,
    val contextConfirmed: Boolean,
    val entryType: String,
    val entryValue: String?,
    val exitType: String?,
    val exitValue: String?,
    val swipes: Int?,
    val medianSwipeIntervalSec: Double?,
    val likes: Int?,
    val otherClicks: Int?,
    val maxFrictionLevel: Int,
    val extensions: Int,
    val stoppedByNudge: Boolean,
    val checkIn: Mood?,
    val source: DataSource,
)

/** 세션 안 앱별 구간. */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val surface: Surface,
    val start: Instant,
    val end: Instant,
)

/** 세션 + 구간. */
data class SessionWithSegments(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val segments: List<SegmentEntity>,
)
