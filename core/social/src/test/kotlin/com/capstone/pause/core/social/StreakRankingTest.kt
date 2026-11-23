package com.capstone.pause.core.social

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.social.remote.PresenceRules
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class StreakRankingTest {
    private fun s(id: String, days: Int, me: Boolean = false) =
        Standing(id, id, reductionRate = null, coverage = null, validDays = 0, isMe = me, streakDays = days)

    @Test
    fun everyoneIsShownLongestStreakFirst() {
        val view = StreakRanking.build(listOf(s("민지", 3), s("하은", 12), s("me", 5, me = true), s("준호", 0)))
        assertEquals(listOf("하은", "me", "민지", "준호"), view.rows.map { it.standing.id })
        assertEquals(listOf(1, 2, 3, 4), view.rows.map { it.rank })
        assertTrue(view.measuring.isEmpty())
    }

    @Test
    fun tiesShareRankAndMeComesFirstAmongTies() {
        val view = StreakRanking.build(listOf(s("하은", 4), s("가람", 4), s("me", 4, me = true), s("준호", 1)))
        assertEquals(listOf("me", "가람", "하은", "준호"), view.rows.map { it.standing.id })
        assertEquals(listOf(1, 1, 1, 4), view.rows.map { it.rank })
    }

    @Test
    fun watchingOnlyWhileOnRealShortformScreen() {
        val start = Instant.parse("2026-11-23T12:00:00Z")
        val live = ShortformSession(
            id = 0, start = start, end = null, segments = emptyList(), context = ContextSlot.OTHER,
            contextConfidence = Confidence.HIGH, contextConfirmed = false, entry = EntryPoint.Unknown, exit = null,
            interaction = null, maxFrictionLevel = 0, extensions = 0, stoppedByNudge = false, checkIn = null,
            source = DataSource.REAL,
        )
        assertTrue(PresenceRules.isWatching(live))
        // 잠깐 다른 앱에 가 있음(60초 안) = end가 있음.
        assertFalse(PresenceRules.isWatching(live.copy(end = start.plusSeconds(30))))
        assertFalse(PresenceRules.isWatching(live.copy(source = DataSource.DEMO)))
        assertFalse(PresenceRules.isWatching(null))
        // 서버의 40초 판정 안에 신호가 두 번은 가게(한 번 놓쳐도 시청 중이 이어진다).
        assertTrue(PresenceRules.BEAT_MS * 2 < 40_000)
    }
}
