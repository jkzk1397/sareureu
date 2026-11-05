package com.capstone.pause.core.intervention.nudge

/**
 * '5분만 더'를 누를수록 귀찮아지는 사다리(ADR-0010). '그만'은 모든 단계에서 1탭 그대로다.
 * 1단계: 문장 따라 쓰기 · 2단계: 이유 칩 + 숨 고르기 5초 + 더 긴 문장 · 3단계: 숨 고르기 10초 + 가장 긴 문장 ·
 * 4단계: 숨 고르기 15초 + 질문에 5자 이상 답하기(연장 3분).
 */
object FrictionLadder {
    /** 단계별 숨 고르기(초). 이 시간이 지나야 입력 칸이 열린다. */
    fun breathSeconds(level: Int): Int = when (level) {
        1 -> 0
        2 -> L2_BREATH
        3 -> L3_BREATH
        else -> L4_BREATH
    }

    /** 4단계 답의 최소 글자 수(공백 제외). */
    const val MIN_ANSWER_CHARS = 5

    private const val L2_BREATH = 5
    private const val L3_BREATH = 10
    private const val L4_BREATH = 15
}
