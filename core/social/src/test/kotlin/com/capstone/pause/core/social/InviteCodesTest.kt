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
    fun pollingSlowsDownWhileTheServerRefusesAndNeverBursts() {
        val base = SocialLimits.FRIENDS_POLL
        assertEquals(java.time.Duration.ofSeconds(15), PollBackoff.next(base, 0))
        assertEquals(java.time.Duration.ofSeconds(30), PollBackoff.next(base, 1))
        assertEquals(java.time.Duration.ofSeconds(60), PollBackoff.next(base, 2))
        // 5분에서 멈춘다(계속 실패해도 그 이상 늘리지 않고, 그보다 자주 부르지도 않는다).
        assertEquals(SocialLimits.POLL_BACKOFF_MAX, PollBackoff.next(base, 6))
        assertEquals(SocialLimits.POLL_BACKOFF_MAX, PollBackoff.next(base, 99))
        // 신호 간격은 서버 판정 창(40초)의 절반보다 짧다: 한 번 놓쳐도 시청 중이 이어진다.
        assertEquals(15L, SocialLimits.PRESENCE_BEAT.seconds)
    }

    @Test
    fun alphabetMatchesTheServer() {
        assertEquals(31, SocialLimits.CODE_ALPHABET.length)
        for (confusing in "0O1IL") assertFalse(confusing in SocialLimits.CODE_ALPHABET)
        assertEquals(SocialLimits.CODE_ALPHABET.toSet().size, SocialLimits.CODE_ALPHABET.length)
    }
}
