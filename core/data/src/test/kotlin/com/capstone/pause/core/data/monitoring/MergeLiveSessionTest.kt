package com.capstone.pause.core.data.monitoring

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ShortformSession
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

class MergeLiveSessionTest {
    private val t0 = Instant.parse("2026-11-09T12:00:00Z")

    private fun session(startMin: Long, endMin: Long?, source: DataSource = DataSource.REAL) = ShortformSession(
        id = 0,
        start = t0.plusSeconds(startMin * 60),
        end = endMin?.let { t0.plusSeconds(it * 60) },
        segments = emptyList(),
        context = ContextSlot.PRE_SLEEP,
        contextConfidence = Confidence.HIGH,
        contextConfirmed = false,
        entry = EntryPoint.Unknown,
        exit = null,
        interaction = null,
        maxFrictionLevel = 0,
        extensions = 0,
        stoppedByNudge = false,
        checkIn = null,
        source = source,
    )

    @Test
    fun liveSessionIsAddedInTimeOrder() {
        val saved = listOf(session(0, 10), session(60, 70))
        val live = session(30, null)
        assertEquals(listOf(saved[0], live, saved[1]), mergeLiveSession(saved, live, DataSource.REAL))
    }

    @Test
    fun alreadySavedOrOtherSourceIsNotAddedTwice() {
        val saved = listOf(session(0, 10))
        assertEquals(saved, mergeLiveSession(saved, session(0, null), DataSource.REAL))
        assertEquals(saved, mergeLiveSession(saved, session(30, null, DataSource.DEMO), DataSource.REAL))
        assertEquals(saved, mergeLiveSession(saved, null, DataSource.REAL))
    }
}
