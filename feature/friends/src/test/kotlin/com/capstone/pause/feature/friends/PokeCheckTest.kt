package com.capstone.pause.feature.friends

import com.capstone.pause.core.ai.engine.validate.BannedWords
import com.capstone.pause.core.social.EntrustResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PokeCheckTest {
    @Test
    fun acceptsShortFriendlyTextWithNumbers() {
        assertNull(checkPoke("내일 1교시야. 쇼츠 그만 보고 자라"))
    }

    @Test
    fun rejectsEmptyAndTooLong() {
        assertEquals(EntrustResult.Empty, checkPoke("   "))
        assertEquals(EntrustResult.TooLong, checkPoke("가".repeat(41)))
        assertNull(checkPoke("가".repeat(40)))
    }

    @Test
    fun rejectsBannedWords() {
        val banned = BannedWords.SHAMING.first()
        assertTrue(BannedWords.contains("너 $banned 같아"))
        assertEquals(EntrustResult.Banned, checkPoke("너 $banned 같아"))
    }

    @Test
    fun fillsFriendPlaceholder() {
        assertEquals("준호! 얼른 자", fillFriend("{friend}! 얼른 자", "준호"))
    }

    @Test
    fun josa() {
        assertTrue(hasFinalConsonant("하은"))
        assertFalse(hasFinalConsonant("준호"))
        assertFalse(hasFinalConsonant("abc"))
    }
}
