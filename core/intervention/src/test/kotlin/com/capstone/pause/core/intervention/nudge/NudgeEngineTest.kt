package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.NudgeResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class NudgeEngineTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val start = LocalDateTime.of(2026, 11, 3, 22, 30).atZone(zone).toInstant()

    private fun at(minutes: Double, from: Instant = start): Instant = from.plusMillis((minutes * 60_000).toLong())

    private fun live(s: Instant = start, threshold: Int = 15) = LiveInput(s, ContextSlot.PRE_SLEEP, "유튜브", threshold)

    private val writer = object : NudgeWriter {
        override fun card(level: Int, live: LiveInput, elapsedMinutes: Int) = CardContent(
            level = level,
            context = live.context,
            appLabel = live.appLabel,
            elapsedMinutes = elapsedMinutes,
            text = "card$level",
            copyId = "c$level",
            extendMinutes = if (level >= 4) 3 else 5,
        )

        override fun message(live: LiveInput, elapsedMinutes: Int) = OutgoingMessage("르르", false, "msg", "m")

        override fun praise(live: LiveInput) = "잘했어"
    }

    private fun engine(speed: Double = 1.0) = NudgeEngine(zone, writer, speed)

    private fun List<NudgeCommand>.card() = filterIsInstance<NudgeCommand.ShowCard>().singleOrNull()?.card

    @Test
    fun bubbleBeforeThresholdAndCard1AtThreshold() {
        val e = engine()
        val before = e.tick(at(14.9), live())
        assertTrue(before.single() is NudgeCommand.ShowBubble)
        assertEquals(1, e.tick(at(15.0), live()).card()?.level)
    }

    @Test
    fun ladderGoesOneTwoThreeFourThenStaysAtFourWithThreeMinuteExtensions() {
        val e = engine()
        assertEquals(1, e.tick(at(15.0), live()).card()?.level)
        e.onAction(at(15.1), NudgeUserAction.Extend(1), live())
        assertTrue(e.tick(at(20.0), live()).card() == null)
        val l2 = e.tick(at(20.1), live())
        assertEquals(2, l2.card()?.level)
        assertTrue(l2.any { it is NudgeCommand.PostBuddyMessage }, "card 2 comes with a message")
        e.onAction(at(20.2), NudgeUserAction.Extend(2, reasonChip = "BORED"), live())
        assertEquals(3, e.tick(at(25.2), live()).card()?.level)
        e.onAction(at(25.3), NudgeUserAction.Extend(3), live())
        assertEquals(4, e.tick(at(30.3), live()).card()?.level)
        e.onAction(at(30.4), NudgeUserAction.Extend(4, answer = "잠"), live())
        assertTrue(e.tick(at(33.3), live()).card() == null, "card 4 extension is 3 minutes")
        val again = e.tick(at(33.4), live())
        assertEquals(4, again.card()?.level)
        assertFalse(again.any { it is NudgeCommand.PostBuddyMessage }, "one message per session")
    }

    @Test
    fun stopFromEveryLevelHidesPraisesAndRecordsStop() {
        for (level in 1..4) {
            val e = engine()
            var t = 15.0
            e.tick(at(t), live())
            repeat(level - 1) {
                e.onAction(at(t), NudgeUserAction.Extend(it + 1), live())
                t += 5.1
                e.tick(at(t), live())
            }
            val out = e.onAction(at(t + 0.1), NudgeUserAction.Stop(level), live())
            assertEquals(NudgeCommand.Hide, out.first())
            assertTrue(out.any { it is NudgeCommand.ShowPraise })
            assertTrue(e.tick(at(t + 30), live()).isEmpty(), "no more cards after stop (level $level)")
            e.tick(at(t + 31), null)
            val events = e.drainFinished().single().events
            assertEquals(NudgeResponse.STOP, events.last().response)
            assertEquals(level, events.last().level)
        }
    }

    @Test
    fun moreThanThreeExtensionsTodayMakesNextSessionStartAtCard2() {
        val e = engine()
        var t = 15.0
        e.tick(at(t), live())
        repeat(4) {
            e.onAction(at(t), NudgeUserAction.Extend(it + 1), live())
            t += 5.1
            e.tick(at(t), live())
        }
        assertEquals(4, e.todayExtensions)
        e.tick(at(t + 1), null)
        e.tick(at(t + 3), null)
        val next = at(60.0)
        val out = e.tick(at(15.0, next), live(next))
        assertEquals(2, out.card()?.level)
        assertTrue(out.any { it is NudgeCommand.PostBuddyMessage })
    }

    @Test
    fun exactlyThreeExtensionsStillStartsAtCard1() {
        val e = engine()
        e.seedToday(start, extensions = 3)
        assertEquals(1, e.tick(at(15.0), live()).card()?.level)
    }

    @Test
    fun extensionCountResetsAtFourAm() {
        val e = engine()
        e.seedToday(start, extensions = 5)
        val nextDay = LocalDateTime.of(2026, 11, 4, 22, 0).atZone(zone).toInstant()
        assertEquals(1, e.tick(at(15.0, nextDay), live(nextDay)).card()?.level)
    }

    @Test
    fun snoozeTodaySuppressesCardsUntilFourAm() {
        val e = engine()
        e.tick(at(15.0), live())
        e.onAction(at(15.1), NudgeUserAction.Extend(1), live())
        e.tick(at(20.1), live())
        e.onAction(at(20.2), NudgeUserAction.SnoozeToday(2), live())
        assertTrue(e.tick(at(40.0), live()).single() is NudgeCommand.ShowBubble)
        e.tick(at(41.0), null)
        e.tick(at(43.0), null)
        val lateSession = LocalDateTime.of(2026, 11, 4, 1, 0).atZone(zone).toInstant()
        assertTrue(e.tick(at(20.0, lateSession), live(lateSession)).card() == null, "still the same logical day")
        e.tick(at(21.0, lateSession), null)
        e.tick(at(23.0, lateSession), null)
        val afterFour = LocalDateTime.of(2026, 11, 4, 4, 10).atZone(zone).toInstant()
        assertEquals(1, e.tick(at(15.0, afterFour), live(afterFour)).card()?.level)
    }

    @Test
    fun sessionEndHidesAndRecordsIgnoredOpenCard() {
        val e = engine()
        e.tick(at(15.0), live())
        val out = e.tick(at(16.0), null)
        assertEquals(listOf<NudgeCommand>(NudgeCommand.Hide), out)
        assertTrue(e.drainFinished().isEmpty(), "not finished until 60 seconds away")
        e.tick(at(17.1), null)
        val finished = e.drainFinished().single()
        assertEquals(start, finished.sessionStart)
        assertEquals(NudgeResponse.IGNORED, finished.events.single().response)
        assertTrue(e.drainFinished().isEmpty())
    }

    @Test
    fun shortAbsenceKeepsSessionAndReshowsOpenCard() {
        val e = engine()
        val card = e.tick(at(15.0), live()).card()
        assertEquals(listOf<NudgeCommand>(NudgeCommand.Hide), e.tick(at(15.2), null))
        val back = e.tick(at(15.5), live())
        assertEquals(card, back.card())
        e.onAction(at(15.6), NudgeUserAction.Extend(1), live())
        assertEquals(2, e.tick(at(20.7), live()).card()?.level)
    }

    @Test
    fun newSessionStartReplacesOldOne() {
        val e = engine()
        e.tick(at(5.0), live())
        val other = at(10.0)
        val out = e.tick(at(1.0, other), live(other))
        assertEquals(NudgeCommand.Hide, out.first())
        assertTrue(out.last() is NudgeCommand.ShowBubble)
    }

    @Test
    fun dismissMovesToNextLevelAfterFiveMinutesWithoutCountingExtension() {
        val e = engine()
        e.tick(at(15.0), live())
        e.onAction(at(15.0), NudgeUserAction.Dismiss(1), live())
        assertEquals(0, e.todayExtensions)
        assertEquals(2, e.tick(at(20.1), live()).card()?.level)
    }

    @Test
    fun actionsWithoutCardAreIgnored() {
        val e = engine()
        e.tick(at(1.0), live())
        assertTrue(e.onAction(at(1.0), NudgeUserAction.Stop(1), live()).isEmpty())
        assertTrue(e.onAction(at(1.0), NudgeUserAction.BubbleTapped, live()).isEmpty())
    }

    @Test
    fun bubbleRingRestartsFromZeroAfterExtension() {
        val e = engine()
        val before = e.tick(at(12.0), live()).single() as NudgeCommand.ShowBubble
        assertEquals(0.8f, before.progress, 0.001f)
        assertEquals(BuddyExpression.CALLING, before.expression, "close to the card the face calls")
        e.tick(at(15.0), live())
        val afterExtend = e.onAction(at(15.0), NudgeUserAction.Extend(1), live()).single() as NudgeCommand.ShowBubble
        assertEquals(0f, afterExtend.progress, "ring restarts for the 5-minute extension")
        assertEquals(5, afterExtend.minutesToNext)
        val mid = e.tick(at(17.5), live()).single() as NudgeCommand.ShowBubble
        assertEquals(0.5f, mid.progress, 0.001f)
        assertEquals(17, (mid.elapsedSec / 60).toInt(), "bubble still knows the whole session length")
        assertEquals(BuddyExpression.WATCHING, mid.expression)
    }

    @Test
    fun snoozedBubbleSleepsWithEmptyRing() {
        val e = engine()
        e.tick(at(15.0), live())
        e.onAction(at(15.1), NudgeUserAction.Extend(1), live())
        e.tick(at(20.1), live())
        val out = e.onAction(at(20.2), NudgeUserAction.SnoozeToday(2), live()).single() as NudgeCommand.ShowBubble
        assertTrue(out.snoozed)
        assertEquals(BuddyExpression.SLEEPY, out.expression)
        assertEquals(0f, out.progress)
    }

    @Test
    fun stopNowFromBubbleBeforeCardEndsSessionWithSelfStop() {
        val e = engine()
        e.tick(at(6.0), live())
        val out = e.onAction(at(6.1), NudgeUserAction.StopNow, live())
        assertEquals(NudgeCommand.Hide, out.first())
        assertTrue(out.any { it is NudgeCommand.ShowPraise })
        assertTrue(e.tick(at(20.0), live()).isEmpty(), "no card after stopping")
        assertTrue(e.onAction(at(20.1), NudgeUserAction.StopNow, live()).isEmpty(), "second stop is ignored")
        e.tick(at(21.0), null)
        val event = e.drainFinished().single().events.single()
        assertEquals(NudgeResponse.STOP, event.response)
        assertEquals(NudgeEngine.SELF_STOP_LEVEL, event.level)
    }

    @Test
    fun demoSpeedSixtyReachesFifteenMinutesInFifteenSeconds() {
        val e = engine(speed = 60.0)
        assertTrue(e.tick(start.plusSeconds(14), live()).card() == null)
        assertEquals(1, e.tick(start.plusSeconds(15), live()).card()?.level)
    }
}
