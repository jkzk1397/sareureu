package com.capstone.pause.core.insights

import com.capstone.pause.core.model.MeltStage

/** 르르가 녹은 정도(06 문서 8절): 오늘 숏폼 ÷ 기준 → 50% 미만 0, 100% 미만 1, 150% 미만 2, 그 이상 3. */
object MeltStageCalculator {
    /** [referenceMinutes]는 기준선 하루 평균(없으면 프로필의 하루 목표). */
    fun compute(todayMinutes: Int, referenceMinutes: Double): MeltStage {
        if (referenceMinutes <= 0.0 || todayMinutes <= 0) return MeltStage.NONE
        val ratio = todayMinutes / referenceMinutes
        return when {
            ratio < SOFT -> MeltStage.NONE
            ratio < PUDDLE -> MeltStage.SOFT
            ratio < MELTED -> MeltStage.PUDDLE
            else -> MeltStage.MELTED
        }
    }

    private const val SOFT = 0.5
    private const val PUDDLE = 1.0
    private const val MELTED = 1.5
}
