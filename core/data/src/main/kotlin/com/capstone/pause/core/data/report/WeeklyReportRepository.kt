package com.capstone.pause.core.data.report

import com.capstone.pause.core.data.session.activeSource
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.database.dao.CopyPackDao
import com.capstone.pause.core.database.dao.ReportDao
import com.capstone.pause.core.database.entity.CopyPackEntity
import com.capstone.pause.core.database.entity.HypothesisEntity
import com.capstone.pause.core.database.entity.WeeklyReportEntity
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Verdict
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 저장된 주간 리포트(검증 통과본 JSON 그대로). */
data class StoredWeeklyReport(val weekStart: LocalDate, val json: String, val createdAt: Instant)

/** 저장된 가설 한 개와 사용자 판정. [evidence]는 관찰 ID(o1..). */
data class StoredHypothesis(
    val id: String,
    val text: String,
    val confidence: Confidence,
    val evidence: List<String>,
    val verdict: Verdict?,
)

/**
 * 주간 리포트·가설·판정 저장소(Room `weekly_reports`, `hypotheses`). 읽기는 지금 쓰는 출처(시연/실제)만.
 * 리포트 JSON의 모양은 쓰는 쪽(`core:ai`)이 정한다. 여기서는 문자열로만 다룬다.
 */
interface WeeklyReportRepository {
    /** [weekStart] 주의 리포트. 없으면 null. */
    fun observeReport(weekStart: LocalDate): Flow<StoredWeeklyReport?>

    /** [weekStart] 주의 가설과 판정. */
    fun observeHypotheses(weekStart: LocalDate): Flow<List<StoredHypothesis>>

    /** 리포트와 가설을 저장한다(지금 출처로). 같은 주를 다시 만들면 덮어쓴다. */
    suspend fun save(report: StoredWeeklyReport, hypotheses: List<StoredHypothesis>)

    /** 맞아/반쯤/아니야 저장. */
    suspend fun setVerdict(hypothesisId: String, verdict: Verdict?)

    /** 지금 출처(시연 모드면 DEMO). */
    suspend fun currentSource(): DataSource
}

/** AI가 만든 문구 팩을 활성 팩으로 저장한다(Room `copy_pack`). 읽기는 `CopyPackRepository`. */
interface CopyPackWriter {
    /** [json](검증 통과본 `CopyPack` JSON)을 [version]으로 저장하고 활성으로 바꾼다. */
    suspend fun activate(version: String, json: String, at: Instant)
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RoomWeeklyReportRepository @Inject constructor(
    private val reportDao: ReportDao,
    private val settingsRepository: SettingsRepository,
) : WeeklyReportRepository {

    override fun observeReport(weekStart: LocalDate): Flow<StoredWeeklyReport?> =
        settingsRepository.activeSource().flatMapLatest { source ->
            reportDao.observeReport(weekStart.toString(), source).map { row ->
                row?.let { StoredWeeklyReport(LocalDate.parse(it.weekStart), it.json, it.createdAt) }
            }
        }

    override fun observeHypotheses(weekStart: LocalDate): Flow<List<StoredHypothesis>> =
        settingsRepository.activeSource().flatMapLatest { source ->
            reportDao.observeHypotheses(weekStart.toString(), source).map { rows -> rows.map { it.toStored() } }
        }

    override suspend fun save(report: StoredWeeklyReport, hypotheses: List<StoredHypothesis>) {
        val source = currentSource()
        reportDao.upsertReport(WeeklyReportEntity(report.weekStart.toString(), report.json, report.createdAt, source))
        reportDao.upsertHypotheses(hypotheses.map { it.toEntity(report.weekStart, source) })
    }

    override suspend fun setVerdict(hypothesisId: String, verdict: Verdict?) =
        reportDao.setVerdict(hypothesisId, verdict)

    override suspend fun currentSource(): DataSource = settingsRepository.activeSource().first()
}

@Singleton
internal class RoomCopyPackWriter @Inject constructor(private val copyPackDao: CopyPackDao) : CopyPackWriter {
    override suspend fun activate(version: String, json: String, at: Instant) {
        copyPackDao.deactivateAll()
        copyPackDao.upsert(CopyPackEntity(version = version, json = json, createdAt = at, active = true))
    }
}

private val evidenceSerializer = ListSerializer(String.serializer())

/** 근거 목록 JSON 변환(테스트에서도 쓴다). */
internal object EvidenceJson {
    fun encode(ids: List<String>): String = Json.encodeToString(evidenceSerializer, ids)

    fun decode(text: String): List<String> = runCatching { Json.decodeFromString(evidenceSerializer, text) }
        .getOrDefault(emptyList())
}

internal fun HypothesisEntity.toStored() = StoredHypothesis(
    id = id,
    text = text,
    confidence = confidence,
    evidence = EvidenceJson.decode(evidenceJson),
    verdict = verdict,
)

internal fun StoredHypothesis.toEntity(weekStart: LocalDate, source: DataSource) = HypothesisEntity(
    id = id,
    weekStart = weekStart.toString(),
    text = text,
    confidence = confidence,
    evidenceJson = EvidenceJson.encode(evidence),
    verdict = verdict,
    source = source,
)
