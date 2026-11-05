package com.capstone.pause.feature.report

import kotlin.test.assertEquals
import org.junit.Test

class PlaceholderFillTest {
    @Test
    fun fillsKnownAndDropsUnknown() {
        val values = mapOf("topContext" to "자기 전", "reduction" to "23%")
        assertEquals(
            "이번 주는 자기 전 때가 제일 길었어. 그래도 기준보다 23% 줄었어!",
            fillPlaceholders("이번 주는 {topContext} 때가 제일 길었어. 그래도 기준보다 {reduction} 줄었어!", values),
        )
        assertEquals("남은 말", fillPlaceholders("{nickname} 남은 말", values))
    }

    @Test
    fun leavesBracesThatAreNotPlaceholders() {
        assertEquals("{a b} 그대로", fillPlaceholders("{a b} 그대로", emptyMap()))
    }
}
