package com.capstone.pause.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.demo.DemoController
import com.capstone.pause.core.data.insight.InsightRepository
import com.capstone.pause.core.data.insight.TodaySummary
import com.capstone.pause.core.data.monitoring.MonitoringState
import com.capstone.pause.core.data.monitoring.MonitoringStatus
import com.capstone.pause.core.data.monitoring.MonitoringSwitch
import com.capstone.pause.core.data.plan.PlanRepository
import com.capstone.pause.core.data.plan.PokeRepository
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.model.IfThenPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 르르 인사 종류. 문장은 화면이 strings.xml에서 고른다. */
sealed interface TodayGreeting {
    /** 오늘 아직 안 봤어. */
    data object NothingYet : TodayGreeting

    /** 카드가 뜬 세션 중 멈춘 수. */
    data class Stopped(val nudged: Int, val stopped: Int) : TodayGreeting

    /** 오늘 본 시간만. */
    data class Watched(val minutes: Int) : TodayGreeting
}

/** 오늘 탭 상태. */
sealed interface TodayUiState {
    /** 읽는 중. */
    data object Loading : TodayUiState

    /** 표시할 값. */
    data class Ready(
        val summary: TodaySummary,
        val greeting: TodayGreeting,
        val poke: ReceivedPoke?,
        val plan: IfThenPlan?,
        val showMelt: Boolean,
        /** 시연 숏폼이 재생 중인지(시연 모드에서만 버튼을 보인다). */
        val demoRunning: Boolean = false,
        /** 실제 모드인데 감지가 꺼져 있음(처음이거나, 작업 관리자 등으로 멈춤). 켜기 버튼을 보인다. */
        val monitoringOff: Boolean = false,
    ) : TodayUiState
}

/** 오늘 탭 동작. */
sealed interface TodayAction {
    /** 보는 중 카드의 '지금 그만할래'(홈 화면으로). */
    data object StopNow : TodayAction

    /** 르르 메시지 보기(코치 탭). */
    data object OpenMessages : TodayAction

    /** 시연: 숏폼 보기 시작. */
    data object StartDemo : TodayAction

    /** 시연 멈추기. */
    data object StopDemo : TodayAction

    /** 감지 켜기(권한이 없으면 사용 기록 설정으로 안내). */
    data object TurnOnMonitoring : TodayAction
}

/** 오늘 탭 ViewModel. 계산은 하지 않고 저장소 값을 묶기만 한다. */
@HiltViewModel
class TodayViewModel @Inject constructor(
    insightRepository: InsightRepository,
    planRepository: PlanRepository,
    pokeRepository: PokeRepository,
    settingsRepository: SettingsRepository,
    clock: AppClock,
    private val demoController: DemoController,
    monitoringStatus: MonitoringStatus,
    private val monitoringSwitch: MonitoringSwitch,
) : ViewModel() {

    /** 권한 설정에 다녀오는 중이면 돌아왔을 때 감지를 켠다. */
    private var turnOnAfterSettings = false

    /** 화면 상태. */
    val uiState: StateFlow<TodayUiState> = combine(
        insightRepository.observeToday(),
        planRepository.observeActivePlans(),
        pokeRepository.observeUnused(clock.now()),
        combine(settingsRepository.settings, monitoringStatus.state) { s, m -> s to m },
        demoController.isRunning,
    ) { summary, plans, pokes, (settings, monitoring), demoRunning ->
        TodayUiState.Ready(
            summary = summary,
            greeting = when {
                summary.nudgedSessions > 0 -> TodayGreeting.Stopped(summary.nudgedSessions, summary.stoppedSessions)
                summary.totalMinutes == 0 -> TodayGreeting.NothingYet
                else -> TodayGreeting.Watched(summary.totalMinutes)
            },
            poke = pokes.firstOrNull(),
            plan = plans.firstOrNull(),
            showMelt = settings.showMelt,
            demoRunning = demoRunning,
            monitoringOff = !settings.demoMode && monitoring != MonitoringState.ON,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState.Loading)

    /** 시연 버튼 처리. 나머지 동작은 화면(Route)이 처리한다. */
    fun onDemoAction(action: TodayAction) {
        viewModelScope.launch {
            when (action) {
                TodayAction.StartDemo -> demoController.startShortformDemo()
                TodayAction.StopDemo -> demoController.stop()
                else -> Unit
            }
        }
    }

    /** 감지 켜기. 사용 기록 권한이 없으면 [openUsageSettings]로 안내하고, 돌아오면([onResume]) 다시 켠다. */
    fun turnOnMonitoring(openUsageSettings: () -> Unit) {
        viewModelScope.launch {
            if (monitoringSwitch.turnOn()) {
                turnOnAfterSettings = false
            } else {
                turnOnAfterSettings = true
                openUsageSettings()
            }
        }
    }

    /** 화면으로 돌아왔을 때. */
    fun onResume() {
        if (!turnOnAfterSettings || !monitoringSwitch.hasUsageAccess()) return
        viewModelScope.launch { if (monitoringSwitch.turnOn()) turnOnAfterSettings = false }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
