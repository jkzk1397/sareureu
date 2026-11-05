package com.capstone.pause.core.intervention.overlay

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FrictionGateTest {
    private val target = "5분만 더 볼게"

    @Test
    fun typedTextNeedsBreathToFinish() {
        assertFalse(FrictionGate.canExtendTyped(breathSecondsLeft = 3, typed = target, target = target))
        assertFalse(FrictionGate.inputEnabled(1))
        assertTrue(FrictionGate.inputEnabled(0))
    }

    @Test
    fun typedTextMustMatchIgnoringSpacing() {
        assertTrue(FrictionGate.canExtendTyped(0, "  5분만  더 볼게 ", target))
        assertFalse(FrictionGate.canExtendTyped(0, "5분만 더", target))
        assertFalse(FrictionGate.canExtendTyped(0, "", target))
        assertFalse(FrictionGate.canExtendTyped(0, target, null))
    }

    @Test
    fun answerNeedsFiveNonBlankCharsAndFinishedBreath() {
        assertFalse(FrictionGate.canExtendAnswered("응 볼래"))
        assertTrue(FrictionGate.canExtendAnswered("심심해서 그냥 봐"))
        assertFalse(FrictionGate.canExtendAnswered("심심해서 그냥 봐", breathSecondsLeft = 4))
    }

    @Test
    fun ladderGetsHarderEachLevel() {
        val breaths = (1..4).map { com.capstone.pause.core.intervention.nudge.FrictionLadder.breathSeconds(it) }
        assertTrue(breaths == breaths.sorted() && breaths.first() == 0 && breaths.last() > breaths[2], "$breaths")
    }
}
