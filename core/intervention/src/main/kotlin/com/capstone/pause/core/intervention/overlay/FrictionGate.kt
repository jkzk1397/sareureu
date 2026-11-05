package com.capstone.pause.core.intervention.overlay

import com.capstone.pause.core.intervention.nudge.FrictionLadder

/** 카드 연장 조건(순수 함수). '그만'은 언제나 열려 있다. */
object FrictionGate {
    /** 4단계 답의 최소 글자 수(공백 제외). */
    const val MIN_ANSWER_CHARS = FrictionLadder.MIN_ANSWER_CHARS

    /** 1~3단계: 숨 고르기가 끝났고, 입력이 [target]과 같을 때(앞뒤·연속 공백 무시)만 연장. */
    fun canExtendTyped(breathSecondsLeft: Int, typed: String, target: String?): Boolean {
        if (breathSecondsLeft > 0 || target == null) return false
        return normalize(typed) == normalize(target)
    }

    /** 4단계: 숨 고르기가 끝났고 답이 [MIN_ANSWER_CHARS]자 이상일 때만 연장. */
    fun canExtendAnswered(answer: String, breathSecondsLeft: Int = 0): Boolean =
        breathSecondsLeft <= 0 && answer.filterNot { it.isWhitespace() }.length >= MIN_ANSWER_CHARS

    /** 입력 칸은 숨 고르기가 끝나야 열린다. */
    fun inputEnabled(breathSecondsLeft: Int): Boolean = breathSecondsLeft <= 0

    private fun normalize(text: String): String = text.trim().split(Regex("\\s+")).joinToString(" ")
}
