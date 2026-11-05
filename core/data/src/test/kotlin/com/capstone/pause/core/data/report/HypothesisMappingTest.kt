package com.capstone.pause.core.data.report

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Verdict
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

class HypothesisMappingTest {
    @Test
    fun verdictAndEvidenceSurviveRoundTrip() {
        val stored =
            StoredHypothesis(
                "2026-10-27#1#0",
                "자기 전엔 메신저에서 넘어와",
                Confidence.MEDIUM,
                listOf("o3", "o12"),
                Verdict.PARTLY,
            )
        val entity = stored.toEntity(LocalDate.of(2026, 10, 27), DataSource.DEMO)
        assertEquals("2026-10-27", entity.weekStart)
        assertEquals(DataSource.DEMO, entity.source)
        assertEquals(stored, entity.toStored())
    }

    @Test
    fun brokenEvidenceJsonBecomesEmpty() {
        assertEquals(emptyList(), EvidenceJson.decode("not json"))
        assertEquals(listOf("o1"), EvidenceJson.decode(EvidenceJson.encode(listOf("o1"))))
    }
}
