package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.IfThenPlan

/**
 * 알림 카드에 보여 줄 '내가 정한 계획' 고르기(순수).
 * 켜진 계획 중 지금 맥락과 같은 것 → 맥락 없는 것 순서. 같은 순위에서는 가장 최근에 저장한 것.
 * 둘 다 없으면 null(카드는 지금과 똑같다). 계획 문장은 사용자가 고른 그대로 쓴다(AI를 부르지 않는다).
 *
 * [anyContext]는 시연 모드에서만 true: 시연 세션의 맥락은 발표하는 시각으로 정해져서(낮이면 '쉬는 시간')
 * 리포트에서 저장한 '자기 전' 계획이 맞지 않는다. 그때는 맥락이 달라도 가장 최근 계획을 보여 준다.
 */
object PlanPicker {
    /** [context]에서 보여 줄 계획. */
    fun pick(plans: List<IfThenPlan>, context: ContextSlot, anyContext: Boolean = false): IfThenPlan? {
        val usable = plans.filter { it.active && it.thenText.isNotBlank() }
        return usable.filter { it.context == context }.maxByOrNull { it.createdAt }
            ?: usable.filter { it.context == null }.maxByOrNull { it.createdAt }
            ?: if (anyContext) usable.maxByOrNull { it.createdAt } else null
    }
}
