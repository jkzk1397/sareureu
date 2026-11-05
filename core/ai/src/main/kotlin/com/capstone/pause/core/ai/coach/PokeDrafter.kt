package com.capstone.pause.core.ai.coach

import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.AiRouter
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.run.AiRunner
import com.capstone.pause.core.ai.engine.schema.PokeDraftInput
import com.capstone.pause.core.model.Tone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 친구에게 맡길 잔소리 초안(친구 탭, 5단계). 40자 이하·금지 표현 없는 검증 통과본만 돌려준다.
 * 메모에 위기 표현이 있으면 실행기가 메모를 빼고 부른다 — 메모 입력 화면이 도움 카드를 직접 띄워야 한다.
 */
@Singleton
class PokeDrafter @Inject constructor(
    private val runner: AiRunner,
    private val router: AiRouter,
    private val logger: AiCallLogger,
) {
    /** 초안 몇 개. [goalHint]는 친구 목표 범주(SLEEP 등, 알면), [notes]는 보내는 사람 메모. */
    suspend fun draft(tone: Tone, goalHint: String? = null, notes: String = ""): List<String> {
        val outcome = runner.pokeDraft(
            router.clientFor(AiFeature.POKE_DRAFT),
            PokeDraftInput(tone = tone, goalHint = goalHint, userNotes = notes),
        )
        logger.log(outcome.record)
        return outcome.value.options
    }
}
