package com.capstone.pause.core.ai.engine

import com.capstone.pause.core.ai.engine.safety.SafetyFilter
import com.capstone.pause.core.ai.engine.safety.SafetyResult
import com.capstone.pause.core.ai.engine.validate.BannedWords
import com.capstone.pause.core.ai.engine.validate.TextRules
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TextRulesTest {
    @Test
    fun placeholdersAreFineButDigitsOutsideFail() {
        assertTrue(TextRules.problems("{app} {min}분째야 🍬", 60).isEmpty())
        assertTrue(TextRules.problems("37분째야", 60).any { "숫자" in it })
        assertTrue(TextRules.problems("{realMin}분째", 60).any { "자리표시자" in it })
    }

    @Test
    fun koreanNumeralTimesFailButCountsAndWordsPass() {
        assertTrue(TextRules.problems("벌써 한 시간 넘었어", 60).any { "수사" in it })
        assertTrue(TextRules.problems("열 분만 더?", 60).any { "수사" in it })
        assertTrue(TextRules.problems("기지개 한 번 켜고 와", 60).isEmpty())
        assertTrue(TextRules.problems("일하는 시간 구분하기", 60).isEmpty())
    }

    @Test
    fun lengthAndEmojiLimits() {
        assertTrue(TextRules.problems("가".repeat(61), 60).any { "넘음" in it })
        assertTrue(TextRules.problems("좋아 😀😀", 60).any { "이모지" in it })
        assertEquals(1, TextRules.emojiCount("멈췄다 ✨"))
    }
}

class BannedWordsTest {
    @Test
    fun findsListedWordsIgnoringSpaces() {
        listOf("중독", "장애", "진단", "환자", "한심", "정신 차려", "의지가 약", "쓰레기", "루저").forEach {
            assertTrue(BannedWords.contains("너 $it 같아"), it)
        }
        assertTrue(BannedWords.contains("정신차려"))
        assertTrue(BannedWords.contains("병신아"))
    }

    @Test
    fun ordinaryCopyPasses() {
        assertNull(BannedWords.find("오늘은 침대 가기 전에 답장 마무리해 볼까?"))
        assertFalse(BannedWords.contains("{app} 여기까지! 잘 멈췄어"))
    }
}

class SafetyFilterTest {
    @Test
    fun crisisStopsWithHelpLines() {
        val r = SafetyFilter.check("요즘 그냥 죽고싶어")
        assertIs<SafetyResult.Crisis>(r)
        assertEquals(listOf("109", "1577-0199"), r.helpLines.map { it.number })
        assertTrue(SafetyFilter.isCrisis("살기 싫다"))
        assertTrue(SafetyFilter.isCrisis("다 끝내고 싶어"))
    }

    @Test
    fun masksPhoneEmailAndName() {
        val r = SafetyFilter.check("내 이름은 김서연이고 010-1234-5678, seo@example.com 이야")
        assertIs<SafetyResult.Safe>(r)
        assertFalse("010-1234-5678" in r.maskedText)
        assertFalse("seo@example.com" in r.maskedText)
        assertFalse("김서연" in r.maskedText)
        assertEquals(3, r.maskedCount)
    }

    @Test
    fun normalQuestionPassesUnchanged() {
        val r = SafetyFilter.check("나 언제 제일 많이 봐?")
        assertIs<SafetyResult.Safe>(r)
        assertEquals("나 언제 제일 많이 봐?", r.maskedText)
    }
}
