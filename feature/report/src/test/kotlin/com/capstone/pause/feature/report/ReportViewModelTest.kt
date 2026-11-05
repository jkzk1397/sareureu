package com.capstone.pause.feature.report

import com.capstone.pause.core.ai.AiCallEntry
import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.engine.run.AiCallRecord
import com.capstone.pause.core.ai.report.GenerateResult
import com.capstone.pause.core.ai.report.WeeklyReportGenerator
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.plan.PlanRepository
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.data.settings.AppSettings
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.IfThenPlan
import com.capstone.pause.core.model.Verdict
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    private val settings = MutableStateFlow(AppSettings(demoMode = true))
    private val plans = mutableListOf<Pair<IfThenPlan, DataSource>>()
    private val reported = mutableListOf<Long>()
    private val verdicts = mutableListOf<Pair<String, Verdict?>>()
    private var nextResult = GenerateResult.CACHED

    private val generator = object : WeeklyReportGenerator {
        override fun observe(): Flow<WeeklyReportState> = flowOf(previewReport(withAi = true))
        override suspend fun generate(force: Boolean): GenerateResult = nextResult
        override suspend fun setVerdict(hypothesisId: String, verdict: Verdict?) {
            verdicts += hypothesisId to verdict
        }
    }
    private val planRepository = object : PlanRepository {
        override fun observeActivePlans(): Flow<List<IfThenPlan>> = flowOf(emptyList())
        override suspend fun add(plan: IfThenPlan, source: DataSource): Long {
            plans += plan to source
            return plans.size.toLong()
        }
        override suspend fun deleteAll(source: DataSource) = Unit
    }
    private val settingsRepository = object : SettingsRepository {
        override val settings: Flow<AppSettings> = this@ReportViewModelTest.settings
        override suspend fun setDemoMode(enabled: Boolean) = Unit
        override suspend fun setDemoPattern(pattern: DemoPattern) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setShowMelt(enabled: Boolean) = Unit
        override suspend fun setAiMode(mode: AiMode) = Unit
        override suspend fun setThreshold(slot: ContextSlot, minutes: Int) {
            this@ReportViewModelTest.settings.value =
                this@ReportViewModelTest.settings.value.copy(thresholds = mapOf(slot to minutes))
        }
        override suspend fun reset() = Unit
    }
    private val logger = object : AiCallLogger {
        override suspend fun log(record: AiCallRecord): Long = 1
        override fun observeRecent(limit: Int): Flow<List<AiCallEntry>> = flowOf(emptyList())
        override suspend fun report(id: Long) {
            reported += id
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = ReportViewModel(
        generator,
        planRepository,
        settingsRepository,
        logger,
        AppClock(Clock.fixed(Instant.EPOCH, ZoneId.of("Asia/Seoul"))),
    )

    @Test
    fun plansAreSavedOnlyAfterButtonAndWithDemoSource() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(ReportAction.ToggleStrategy("PRE_SLEEP_1"))
        assertEquals(0, plans.size)
        vm.onAction(ReportAction.SavePlans)
        assertEquals(1, plans.size)
        assertEquals(DataSource.DEMO, plans.single().second)
        assertEquals("누우면", plans.single().first.ifText)
        val ready = vm.uiState.value as ReportUiState.Ready
        assertEquals(true, ready.local.savedPlan)
    }

    @Test
    fun reportHidesSentenceAndMarksCall() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(ReportAction.ReportSentence(callId = 1, key = "h1"))
        assertEquals(listOf(1L), reported)
        val ready = vm.uiState.value as ReportUiState.Ready
        assertEquals(setOf("h1"), ready.local.reportedKeys)
        assertEquals(ReportNotice.REPORTED, ready.local.notice)
    }

    @Test
    fun quotaResultShowsNotice() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        nextResult = GenerateResult.QUOTA_REACHED
        vm.onAction(ReportAction.Generate(force = true))
        assertEquals(ReportNotice.QUOTA, (vm.uiState.value as ReportUiState.Ready).local.notice)
    }

    @Test
    fun verdictAndThresholdGoThrough() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(ReportAction.SetVerdict("h1", Verdict.YES))
        vm.onAction(ReportAction.AcceptThreshold(ContextSlot.PRE_SLEEP, 10))
        assertEquals(listOf<Pair<String, Verdict?>>("h1" to Verdict.YES), verdicts)
        assertEquals(10, settings.value.thresholds[ContextSlot.PRE_SLEEP])
    }
}
