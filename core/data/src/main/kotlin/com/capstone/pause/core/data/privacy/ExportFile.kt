package com.capstone.pause.core.data.privacy

import com.capstone.pause.core.data.profile.ConsentRecord
import com.capstone.pause.core.data.profile.ProfileDto
import com.capstone.pause.core.data.profile.toDto
import com.capstone.pause.core.data.settings.AppSettings
import com.capstone.pause.core.database.entity.HypothesisEntity
import com.capstone.pause.core.database.entity.MessageEntity
import com.capstone.pause.core.database.entity.NudgeEventEntity
import com.capstone.pause.core.database.entity.PlanEntity
import com.capstone.pause.core.database.entity.SessionWithSegments
import com.capstone.pause.core.model.Profile
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * '내 기록 내보내기' JSON 모양(04 문서 1절 4번). 이 폰에 저장된 것 전부를 사람이 읽을 수 있게 담는다.
 * AI 원본 입력은 저장하지 않으므로 없다(감사 로그는 해시·길이만이라 넣지 않음).
 */
@Serializable
internal data class ExportFile(
    val formatVersion: Int = FORMAT_VERSION,
    val exportedAt: String,
    val profile: ProfileDto?,
    val consents: List<ExportConsent>,
    val settings: ExportSettings,
    val sessions: List<ExportSession>,
    val nudges: List<ExportNudge>,
    val messages: List<ExportMessage>,
    val plans: List<ExportPlan>,
    val hypotheses: List<ExportHypothesis>,
) {
    companion object {
        /** 형식 버전. 필드를 바꾸면 올린다. */
        const val FORMAT_VERSION = 1
    }
}

/** 동의 기록. */
@Serializable
internal data class ExportConsent(val item: String, val version: Int, val granted: Boolean, val at: String)

/** 설정. */
@Serializable
internal data class ExportSettings(
    val demoMode: Boolean,
    val demoPattern: String,
    val themeMode: String,
    val showMelt: Boolean,
    val aiMode: String,
    val thresholdsMinutes: Map<String, Int>,
)

/** 세션 구간. */
@Serializable
internal data class ExportSegment(val packageName: String, val surface: String, val start: String, val end: String)

/** 숏폼 세션. */
@Serializable
internal data class ExportSession(
    val id: Long,
    val source: String,
    val start: String,
    val end: String?,
    val context: String,
    val contextConfidence: String,
    val entry: String,
    val exit: String?,
    val maxFrictionLevel: Int,
    val extensions: Int,
    val stoppedByNudge: Boolean,
    val segments: List<ExportSegment>,
)

/** 개입 기록. */
@Serializable
internal data class ExportNudge(
    val sessionId: Long,
    val source: String,
    val at: String,
    val level: Int,
    val response: String,
    val reasonChip: String?,
)

/** 메시지(르르·친구 잔소리·내 채팅). */
@Serializable
internal data class ExportMessage(val source: String, val at: String, val sender: String, val text: String)

/** 계획. */
@Serializable
internal data class ExportPlan(
    val source: String,
    val context: String?,
    val ifText: String,
    val thenText: String,
    val createdAt: String,
    val active: Boolean,
)

/** AI 가설과 내 판정. */
@Serializable
internal data class ExportHypothesis(
    val source: String,
    val weekStart: String,
    val text: String,
    val confidence: String,
    val verdict: String?,
)

/** 저장소에서 읽은 원본 묶음. [ExportMapper.toFile]이 순수하게 바꾼다. */
internal data class ExportSnapshot(
    val profile: Profile?,
    val consents: Collection<ConsentRecord>,
    val settings: AppSettings,
    val sessions: List<SessionWithSegments>,
    val nudges: List<NudgeEventEntity>,
    val messages: List<MessageEntity>,
    val plans: List<PlanEntity>,
    val hypotheses: List<HypothesisEntity>,
)

/** 원본 → 내보내기 JSON(순수 함수). */
internal object ExportMapper {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** JSON 문자열. */
    fun encode(snapshot: ExportSnapshot, exportedAt: Instant): String =
        json.encodeToString(ExportFile.serializer(), toFile(snapshot, exportedAt))

    /** 내보내기 파일 모양으로. */
    fun toFile(s: ExportSnapshot, exportedAt: Instant): ExportFile = ExportFile(
        exportedAt = exportedAt.toString(),
        profile = s.profile?.toDto(),
        consents = s.consents.sortedBy { it.item.name }.map {
            ExportConsent(it.item.name, it.version, it.granted, it.at.toString())
        },
        settings = ExportSettings(
            demoMode = s.settings.demoMode,
            demoPattern = s.settings.demoPattern.name,
            themeMode = s.settings.themeMode.name,
            showMelt = s.settings.showMelt,
            aiMode = s.settings.aiMode.name,
            thresholdsMinutes = s.settings.thresholds.mapKeys { it.key.name },
        ),
        sessions = s.sessions.map { row ->
            val e = row.session
            ExportSession(
                id = e.id,
                source = e.source.name,
                start = e.start.toString(),
                end = e.end?.toString(),
                context = e.context.name,
                contextConfidence = e.contextConfidence.name,
                entry = listOfNotNull(e.entryType, e.entryValue).joinToString(":"),
                exit = e.exitType?.let { listOfNotNull(it, e.exitValue).joinToString(":") },
                maxFrictionLevel = e.maxFrictionLevel,
                extensions = e.extensions,
                stoppedByNudge = e.stoppedByNudge,
                segments = row.segments.sortedBy { it.start }.map {
                    ExportSegment(it.packageName, it.surface.name, it.start.toString(), it.end.toString())
                },
            )
        },
        nudges = s.nudges.map {
            ExportNudge(it.sessionId, it.source.name, it.at.toString(), it.level, it.response.name, it.reasonChip)
        },
        messages = s.messages.map {
            ExportMessage(it.source.name, it.at.toString(), it.friendNickname ?: it.senderType, it.text)
        },
        plans = s.plans.map {
            ExportPlan(it.source.name, it.context?.name, it.ifText, it.thenText, it.createdAt.toString(), it.active)
        },
        hypotheses = s.hypotheses.map {
            ExportHypothesis(it.source.name, it.weekStart, it.text, it.confidence.name, it.verdict?.name)
        },
    )
}
