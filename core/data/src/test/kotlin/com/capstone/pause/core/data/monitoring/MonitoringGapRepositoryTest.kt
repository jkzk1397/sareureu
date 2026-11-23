package com.capstone.pause.core.data.monitoring

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MonitoringGapRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val t0 = Instant.parse("2026-11-23T03:00:00Z")

    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60)

    @Test
    fun offThenOnMakesOneClosedGap() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = DataStoreMonitoringGapRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") },
        )
        // 꺼 둔 적이 없으면 켜도 구간이 생기지 않는다.
        repo.recordOn(at(0))
        assertTrue(repo.gaps.first().isEmpty())

        repo.recordOff(at(10))
        // 이미 꺼져 있으면 다시 꺼도 처음 시각을 지킨다.
        repo.recordOff(at(20))
        assertEquals(listOf(MonitoringGap(at(10), null)), repo.gaps.first())
        // 아직 꺼져 있는 구간은 지금까지로 센다.
        assertEquals(20 * 60L, repo.gaps.first().toSpans(at(30)).single().seconds)

        repo.recordOn(at(40))
        repo.recordOff(at(100))
        repo.recordOn(at(130))
        assertEquals(
            listOf(MonitoringGap(at(10), at(40)), MonitoringGap(at(100), at(130))),
            repo.gaps.first(),
        )

        repo.clear()
        assertTrue(repo.gaps.first().isEmpty())
        scope.cancel()
    }

    @Test
    fun codecDropsOldGapsAndBrokenText() {
        val now = at(0)
        val old =
            MonitoringGap(now.minus(MonitoringGapCodec.KEEP).minusSeconds(120), now.minus(MonitoringGapCodec.KEEP))
        val recent = MonitoringGap(now.minusSeconds(600), now.minusSeconds(60))
        assertEquals(listOf(recent), MonitoringGapCodec.trim(listOf(old, recent), now))
        assertEquals(listOf(recent), MonitoringGapCodec.decode(MonitoringGapCodec.encode(listOf(recent))))
        assertEquals(emptyList(), MonitoringGapCodec.decode("이상한 값,:,"))
        assertEquals(emptyList(), MonitoringGapCodec.decode(null))
    }
}
