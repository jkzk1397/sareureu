package com.capstone.pause.core.designsystem.theme

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class ReducedMotionTest {
    @Test
    fun anyZeroScaleTurnsMotionOff() {
        assertTrue(ReducedMotion.isReduced(0f, 1f))
        assertTrue(ReducedMotion.isReduced(1f, 0f))
        assertFalse(ReducedMotion.isReduced(1f, 1f))
        assertFalse(ReducedMotion.isReduced(0.5f, 1f))
    }
}
