package com.capstone.pause.core.ai.engine.schema

import com.capstone.pause.core.insights.WeeklyObservations
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Tone
import kotlinx.serialization.Serializable

/** AI 기능 입력. 클라우드로 가는 것은 이 타입을 JSON으로 만든 것뿐이다(04 문서 5절: 계산된 요약·범주만). */
sealed interface AiInput

/** 관찰 하나(코드가 센 것). [id]는 o1..o12. */
@Serializable
data class ObservationIn(
    val id: String,
    val kind: String,
    val numbers: Map<String, Double> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
)

/** 프로필 요약. 닉네임·자유 메모는 넣지 않는다. */
@Serializable
data class ProfileSummary(
    val ageBand: String? = null,
    val occupation: String? = null,
    val goals: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val tone: Tone = Tone.PLAYFUL,
) {
    companion object {
        /** 프로필에서 보낼 항목만 뽑는다. */
        fun from(profile: Profile?): ProfileSummary = if (profile == null) {
            ProfileSummary()
        } else {
            ProfileSummary(
                ageBand = profile.ageBand.name,
                occupation = profile.occupation.name,
                goals = profile.goals.map { it.name }.sorted(),
                interests = profile.interests.sorted(),
                tone = profile.tone,
            )
        }
    }
}

/** 지난주 가설과 사용자 판정. */
@Serializable
data class PastHypothesis(val text: String, val verdict: String? = null)

/** `weekly_report` 입력. */
@Serializable
data class WeeklyReportInput(
    val observations: List<ObservationIn>,
    val profile: ProfileSummary = ProfileSummary(),
    /** 코드가 정한 유형(`ViewerType` 이름). */
    val viewerType: String = "BALANCED",
    val coverage: Double? = null,
    val pastHypotheses: List<PastHypothesis> = emptyList(),
    /** 사용자가 고친 메모. 프롬프트에서 `<user_text>`로 감싼다. */
    val userNotes: String = "",
) : AiInput {
    /** 관찰 ID 집합. */
    val observationIds: Set<String> get() = observations.map { it.id }.toSet()

    companion object {
        /** `core:insights` 계산 결과를 AI 입력으로 바꾼다. */
        fun from(
            week: WeeklyObservations,
            profile: ProfileSummary,
            coverage: Double?,
            pastHypotheses: List<PastHypothesis> = emptyList(),
            userNotes: String = "",
        ) = WeeklyReportInput(
            observations = week.observations.map { ObservationIn(it.id, it.kind.name, it.numbers, it.labels) },
            profile = profile,
            viewerType = week.viewerType.name,
            coverage = coverage,
            pastHypotheses = pastHypotheses,
            userNotes = userNotes,
        )
    }
}

/** `copy_pack` 입력. */
@Serializable
data class CopyPackInput(
    val profile: ProfileSummary = ProfileSummary(),
    /** 이번 주 긴 맥락 순서. 문구를 더 신경 써서 쓸 곳. */
    val topContexts: List<ContextSlot> = emptyList(),
    val observations: List<ObservationIn> = emptyList(),
) : AiInput

/** `context_check` 입력: 확신도 낮은 세션 묶음. 시각은 `{timeRange}`로 코드가 채운다. */
@Serializable
data class ContextCheckInput(
    val candidates: List<ContextSlot>,
    val sessionCount: Int,
    val weekday: Boolean = true,
    val profile: ProfileSummary = ProfileSummary(),
) : AiInput

/** `poke_draft` 입력. 친구 이름은 보내지 않고 `{friend}`로 쓴다. */
@Serializable
data class PokeDraftInput(
    val tone: Tone = Tone.PLAYFUL,
    /** 친구의 목표 범주(알면). */
    val goalHint: String? = null,
    /** 보내는 사람이 남긴 메모. `<user_text>`로 감싼다. */
    val userNotes: String = "",
) : AiInput
