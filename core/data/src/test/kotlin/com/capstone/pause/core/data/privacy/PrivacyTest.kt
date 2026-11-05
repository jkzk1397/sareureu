package com.capstone.pause.core.data.privacy

import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRecord
import com.capstone.pause.core.data.settings.AppSettings
import com.capstone.pause.core.database.entity.MessageEntity
import com.capstone.pause.core.database.entity.NudgeEventEntity
import com.capstone.pause.core.database.entity.PlanEntity
import com.capstone.pause.core.database.entity.SegmentEntity
import com.capstone.pause.core.database.entity.SessionEntity
import com.capstone.pause.core.database.entity.SessionWithSegments
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.Surface
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ExportMapperTest {
    private val t0 = Instant.parse("2026-11-03T13:40:00Z")

    private fun snapshot() = ExportSnapshot(
        profile = null,
        consents = listOf(ConsentRecord(ConsentItem.AI_USE, 1, true, t0)),
        settings = AppSettings(),
        sessions = listOf(
            SessionWithSegments(
                session = SessionEntity(
                    id = 7, start = t0, end = t0.plusSeconds(600), context = ContextSlot.PRE_SLEEP,
                    contextConfidence = Confidence.HIGH, contextConfirmed = false,
                    entryType = "FROM_APP", entryValue = "MESSENGER", exitType = "SCREEN_OFF", exitValue = null,
                    swipes = null, medianSwipeIntervalSec = null, likes = null, otherClicks = null,
                    maxFrictionLevel = 2, extensions = 1, stoppedByNudge = true, checkIn = null,
                    source = DataSource.REAL,
                ),
                segments = listOf(
                    SegmentEntity(1, 7, "com.google.android.youtube", Surface.APP_UNKNOWN, t0, t0.plusSeconds(600)),
                ),
            ),
        ),
        nudges = listOf(NudgeEventEntity(1, 7, t0, 1, "c1", NudgeResponse.STOP, null, DataSource.REAL)),
        messages = listOf(MessageEntity(1, t0, "FRIEND", "minji", "민지", "얼른 자", null, 7, DataSource.DEMO)),
        plans = listOf(PlanEntity(1, ContextSlot.PRE_SLEEP, "누우면", "폰은 책상에", t0, true, DataSource.REAL)),
        hypotheses = emptyList(),
    )

    @Test
    fun mapsEveryStoreIntoReadableJson() {
        val text = ExportMapper.encode(snapshot(), t0)
        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals(ExportFile.FORMAT_VERSION.toString(), root.getValue("formatVersion").jsonPrimitive.content)
        val session = root.getValue("sessions").jsonArray.single().jsonObject
        assertEquals("FROM_APP:MESSENGER", session.getValue("entry").jsonPrimitive.content)
        assertEquals("SCREEN_OFF", session.getValue("exit").jsonPrimitive.content)
        assertEquals(1, session.getValue("segments").jsonArray.size)
        assertEquals(
            "민지",
            root.getValue("messages").jsonArray.single().jsonObject.getValue("sender").jsonPrimitive.content,
        )
        assertEquals(1, root.getValue("consents").jsonArray.size)
        assertEquals(
            "10",
            root.getValue("settings").jsonObject.getValue("thresholdsMinutes").jsonObject
                .getValue("LATE_NIGHT").jsonPrimitive.content,
        )
    }

    @Test
    fun emptyProfileIsNullAndNoAiInputsAreExported() {
        val file = ExportMapper.toFile(snapshot(), t0)
        assertNull(file.profile)
        assertTrue("aiCalls" !in ExportMapper.encode(snapshot(), t0))
    }
}

class EraseOrchestratorTest {
    @Test
    fun runsEveryStepAndReportsFailures() = runTest {
        val ran = mutableListOf<String>()
        val orchestrator = EraseOrchestrator(
            listOf(
                EraseStep("room") { ran += "room" },
                EraseStep("settings") { error("disk") },
                EraseStep("profile") { ran += "profile" },
            ),
        )
        val failed = orchestrator.run()
        assertEquals(listOf("room", "profile"), ran)
        assertEquals(listOf("settings"), failed)
    }

    @Test
    fun allSucceedReturnsEmpty() = runTest {
        assertTrue(EraseOrchestrator(listOf(EraseStep("cache") {})).run().isEmpty())
    }
}
