package com.capstone.pause.core.intervention.nudge

import com.capstone.pause.core.model.CardCopy
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyLine
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.Tone
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class CopyPickerTest {
    private fun line(id: String, text: String, vararg tones: Tone) = CopyLine(id, text, tones.toSet())

    private val card = CardCopy(
        l1 = listOf(line("p1", "{app} {min}분째!", Tone.PLAYFUL), line("w1", "{buddy}가 걱정해", Tone.WARM)),
        l2 = listOf(line("l2", "이유는?", Tone.PLAIN)),
        l3 = listOf(line("l3", "숨", Tone.PLAIN)),
        l4Question = listOf(line("q", "다음 할 일은?", Tone.PLAIN)),
    )
    private val pack = CopyPack(
        version = "t",
        card = mapOf(ContextSlot.OTHER to card),
        chipReply = mapOf("BORED" to listOf(line("b", "산책 어때", Tone.WARM))),
        buddyMessage = listOf(line("m", "{app} 보고 있어?", Tone.PLAYFUL)),
        praise = listOf(line("pr", "잘했어", Tone.PLAYFUL)),
        predictive = emptyMap(),
    )
    private val texts = FixedTexts(
        buddyName = "르르",
        buddySender = "르르 · 사르르",
        friendSenderFormat = "%1\$s · 맡겨 둔 잔소리",
        contextLabels = mapOf(ContextSlot.PRE_SLEEP to "자기 전"),
        reasonChips = listOf(ReasonChip("BORED", "심심해서")),
        typeToExtend = "5분만 더 볼게",
    )
    private val live = LiveInput(Instant.ofEpochSecond(1000), ContextSlot.PRE_SLEEP, "유튜브", 15)

    @Test
    fun picksToneAndFallsBackToOtherContext() {
        val writer = CopyNudgeWriter(pack, Tone.PLAYFUL, texts, null) { null }
        val c = writer.card(1, live, 15)
        assertEquals("p1", c.copyId)
        assertEquals("유튜브 15분째!", c.text)
    }

    @Test
    fun noToneMatchFallsBackToAnyLine() {
        assertEquals("l2", CopyPicker.pick(card.l2, Tone.WARM, 0)?.id)
        assertNull(CopyPicker.pick(emptyList(), Tone.WARM, 0))
    }

    @Test
    fun fillRemovesUnknownAndNonWhitelistedPlaceholders() {
        assertEquals("안녕 르르", CopyPicker.fill("안녕 {buddy} {secret}", mapOf("buddy" to "르르", "secret" to "x")))
    }

    @Test
    fun levelSpecificFields() {
        val writer = CopyNudgeWriter(pack, Tone.PLAIN, texts, null) { null }
        assertEquals(listOf("BORED"), writer.card(2, live, 20).reasonChips.map { it.key })
        val l3 = writer.card(3, live, 25)
        assertEquals(10, l3.breathSeconds)
        assertEquals("5분만 더 볼게", l3.typeToExtend)
        val l4 = writer.card(4, live, 30)
        assertEquals("다음 할 일은?", l4.question)
        assertEquals(3, l4.extendMinutes)
        assertTrue(l4.canSnoozeToday)
    }

    @Test
    fun friendPokeComesFirstThenBuddyMessage() {
        var poke: PendingPoke? = PendingPoke("p", "민지", "또 쇼츠야?")
        val writer = CopyNudgeWriter(pack, Tone.PLAYFUL, texts, null) { poke }
        val fromFriend = writer.message(live, 20)
        assertTrue(fromFriend.fromFriend)
        assertEquals("민지 · 맡겨 둔 잔소리", fromFriend.senderName)
        assertEquals("p", fromFriend.pokeId)
        poke = null
        val buddy = writer.message(live, 20)
        assertEquals("르르 · 사르르", buddy.senderName)
        assertEquals("유튜브 보고 있어?", buddy.text)
    }

    @Test
    fun sameSessionSameLine() {
        val writer = CopyNudgeWriter(pack, Tone.PLAYFUL, texts, null) { null }
        assertEquals(writer.card(1, live, 15).copyId, writer.card(1, live, 16).copyId)
        assertEquals("산책 어때", writer.chipReply("BORED", live))
    }
}
