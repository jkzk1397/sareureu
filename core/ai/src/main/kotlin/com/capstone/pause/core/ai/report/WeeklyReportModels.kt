package com.capstone.pause.core.ai.report

import com.capstone.pause.core.ai.engine.schema.WeeklyReportOut
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Verdict
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** 관찰(코드가 센 것). 화면이 [kind]·[numbers]·[labels]로 문장을 만든다. 숫자는 여기서만 나온다. */
data class ReportObservation(
    val id: String,
    val kind: String,
    val numbers: Map<String, Double>,
    val labels: Map<String, String>,
)

/** AI 가설 한 개(추측) + 사용자 판정. [evidence]는 관찰 ID. */
data class AiHypothesis(
    val id: String,
    val text: String,
    val confidence: Confidence,
    val evidence: List<String>,
    val confirmQuestion: String,
    val verdict: Verdict?,
)

/** 고를 수 있는 전략(라이브러리 원문 + AI가 내 말로 고친 '하면'). */
data class AiStrategy(
    val id: String,
    val context: ContextSlot?,
    val ifText: String,
    val thenText: String,
    val why: String,
)

/** 임계값 제안("자기 전은 10분으로 낮출까?"). 수락할 때만 바뀐다. */
data class AiThreshold(val slot: ContextSlot, val minutes: Int, val currentMinutes: Int, val why: String)

/** AI 주간 분석(검증 통과본). [isMock]이면 규칙 기반 모의 결과. */
data class AiReport(
    val summaryLine: String,
    val hypotheses: List<AiHypothesis>,
    val strategies: List<AiStrategy>,
    val viewerTypeLine: String,
    val thresholds: List<AiThreshold>,
    val callId: Long,
    val isMock: Boolean,
    val createdAt: Instant,
)

/** 만들기 상태. */
enum class GenerationStatus { IDLE, RUNNING, FAILED }

/**
 * 리포트 화면 상태. 관찰은 코드, [ai]는 AI(없을 수 있음). 화면은 둘을 나눠 그린다.
 * 주는 '지난 7일'(오늘-7 ~ 어제, 새벽 4시 경계)이다.
 */
data class WeeklyReportState(
    val weekStart: LocalDate,
    val weekEndInclusive: LocalDate,
    val isDemo: Boolean,
    val observations: List<ReportObservation>,
    val totalMinutes: Int,
    val avgMinPerDay: Double,
    val reductionRate: Double?,
    val stopRate: Double?,
    val sessionCount: Int,
    /** 코드가 정한 유형(`ViewerType` 이름). */
    val viewerType: String,
    val enoughForAi: Boolean,
    val ai: AiReport?,
    val status: GenerationStatus,
    val regenerationsLeft: Int,
    val usingRealAi: Boolean,
)

/** [WeeklyReportGenerator.generate] 결과. */
enum class GenerateResult { CREATED, CACHED, NOT_ENOUGH_DATA, QUOTA_REACHED, FAILED }

/** Room에 저장하는 리포트 JSON 모양. */
@Serializable
internal data class SavedWeeklyReport(
    val out: WeeklyReportOut,
    val callId: Long,
    val provider: String,
    val outcome: String,
    val createdAtMs: Long,
)
