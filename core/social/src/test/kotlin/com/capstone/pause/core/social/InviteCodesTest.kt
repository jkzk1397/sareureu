package com.capstone.pause.core.social

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class InviteCodesTest {
    @Test
    fun cleansSpacesHyphensAndCase() {
        assertEquals("K7P2QXM4", InviteCodes.clean(" k7p2-qxm4\n"))
        assertEquals(CodeCheck.Ok("K7P2QXM4"), InviteCodes.check("k7p2 qxm4"))
        assertEquals(CodeCheck.Ok("K7P2QXM4"), InviteCodes.check("K7P-2QX-M4"))
    }

    @Test
    fun tellsConfusingCharactersBeforeAskingTheServer() {
        // 글자표에 없는 0·O·1·I·L은 서버에 보내기 전에 알려 준다(틀린 코드 한도를 아낀다).
        assertEquals(CodeCheck.BadCharacters(listOf('0', 'O')), InviteCodes.check("K0P2OXM4"))
        assertEquals(CodeCheck.BadCharacters(listOf('1', 'I', 'L')), InviteCodes.check("1ILK7P2Q"))
        assertEquals(CodeCheck.BadCharacters(listOf('가')), InviteCodes.check("K7P2QXM가"))
    }

    @Test
    fun lengthAndEmpty() {
        assertEquals(CodeCheck.Empty, InviteCodes.check(" - "))
        assertEquals(CodeCheck.WrongLength(6), InviteCodes.check("K7P2QX"))
        assertEquals(CodeCheck.WrongLength(9), InviteCodes.check("K7P2QXM4A"))
    }

    @Test
    fun alphabetMatchesTheServer() {
        assertEquals(31, SocialLimits.CODE_ALPHABET.length)
        for (confusing in "0O1IL") assertFalse(confusing in SocialLimits.CODE_ALPHABET)
        assertEquals(SocialLimits.CODE_ALPHABET.toSet().size, SocialLimits.CODE_ALPHABET.length)
    }
}
