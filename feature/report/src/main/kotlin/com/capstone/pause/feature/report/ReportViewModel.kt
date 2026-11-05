package com.capstone.pause.feature.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.report.AiStrategy
import com.capstone.pause.core.ai.report.GenerateResult
import com.capstone.pause.core.ai.report.WeeklyReportGenerator
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.plan.PlanRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.IfThenPlan
import com.capstone.pause.core.model.Verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 리포트 화면에서만 쓰는 상태(고른 전략, 신고한 문장, 알림 문구). */
data class ReportLocal(
    val pickedStrategies: Set<String> = emptySet(),
    val savedPlan: Boolean = false,
    val acceptedThresholds: Set<ContextSlot> = emptySet(),
    val reportedKeys: Set<String> = emptySet(),
    val notice: ReportNotice? = null,
)

/** 화면 아래에 잠깐 보여 줄 알림. */
enum class ReportNotice { QUOTA, FAILED, REPORTED }

/** 리포트 탭 상태. */
sealed interface ReportUiState {
    /** 읽는 중. */
    data object Loading : ReportUiState

    /** 표시. */
    data class Ready(val report: WeeklyReportState, val local: ReportLocal) : ReportUiState
}

/** 리포트 탭 동작. */
sealed interface ReportAction {
    /** AI 분석 받기(처음) 또는 다시 만들기. */
    data class Generate(val force: Boolean) : ReportAction

    /** 가설 판정. */
    data class SetVerdict(val hypothesisId: String, val verdict: Verdict) : ReportAction

    /** 전략 고르기·풀기(저장은 버튼으로만). */
    data class ToggleStrategy(val id: String) : ReportAction

    /** 고른 전략을 계획으로 저장. */
    data object SavePlans : ReportAction

    /** 임계값 제안 수락. */
    data class AcceptThreshold(val slot: ContextSlot, val minutes: Int) : ReportAction

    /** AI 문장 신고. [key]는 화면에서 그 문장을 숨기는 데 쓴다. */
    data class ReportSentence(val callId: Long, val key: String) : ReportAction

    /** 알림 닫기. */
    data object DismissNotice : ReportAction
}

/** 리포트 탭 ViewModel. 계산·검증은 `core:ai`·`core:insights`가 하고 여기서는 묶기만. */
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val generator: WeeklyReportGenerator,
    private val planRepository: PlanRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: AiCallLogger,
    private val clock: AppClock,
) : ViewModel() {

    private val local = MutableStateFlow(ReportLocal())

    /** 화면 상태. */
    val uiState: StateFlow<ReportUiState> = combine(generator.observe(), local) { report, l ->
        ReportUiState.Ready(report, l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReportUiState.Loading)

    init {
        // 화면이 열려 있을 때만 만든다. 이미 이번 주 리포트가 있으면 아무것도 하지 않는다.
        viewModelScope.launch { generator.generate(force = false) }
    }

    /** 동작 처리. */
    fun onAction(action: ReportAction) {
        viewModelScope.launch {
            when (action) {
                is ReportAction.Generate -> when (generator.generate(action.force)) {
                    GenerateResult.QUOTA_REACHED -> local.update { it.copy(notice = ReportNotice.QUOTA) }
                    GenerateResult.FAILED -> local.update { it.copy(notice = ReportNotice.FAILED) }
                    else -> Unit
                }

                is ReportAction.SetVerdict -> generator.setVerdict(action.hypothesisId, action.verdict)

                is ReportAction.ToggleStrategy -> local.update {
                    val picked = if (action.id in
                        it.pickedStrategies
                    ) {
                        it.pickedStrategies - action.id
                    } else {
                        it.pickedStrategies + action.id
                    }
                    it.copy(pickedStrategies = picked, savedPlan = false)
                }

                ReportAction.SavePlans -> savePlans()

                is ReportAction.AcceptThreshold -> {
                    settingsRepository.setThreshold(action.slot, action.minutes)
                    local.update { it.copy(acceptedThresholds = it.acceptedThresholds + action.slot) }
                }

                is ReportAction.ReportSentence -> {
                    logger.report(action.callId)
                    local.update {
                        it.copy(reportedKeys = it.reportedKeys + action.key, notice = ReportNotice.REPORTED)
                    }
                }

                ReportAction.DismissNotice -> local.update { it.copy(notice = null) }
            }
        }
    }

    private suspend fun savePlans() {
        val ready = uiState.value as? ReportUiState.Ready ?: return
        val picked = ready.local.pickedStrategies
        val strategies: List<AiStrategy> = ready.report.ai?.strategies.orEmpty().filter { it.id in picked }
        if (strategies.isEmpty()) return
        val source = if (settingsRepository.settings.first().demoMode) DataSource.DEMO else DataSource.REAL
        strategies.forEach { s ->
            planRepository.add(IfThenPlan(0, s.context, s.ifText, s.thenText, clock.now(), active = true), source)
        }
        local.update { it.copy(savedPlan = true, pickedStrategies = emptySet()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
