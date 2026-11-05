package com.capstone.pause.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.insight.InsightRepository
import com.capstone.pause.core.data.insight.TodaySummary
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
    ) : TodayUiState
}

/** 오늘 탭 동작. */
sealed interface TodayAction {
    /** 보는 중 카드의 '지금 그만할래'(홈 화면으로). */
    data object StopNow : TodayAction

    /** 르르 메시지 보기(코치 탭). */
    data object OpenMessages : TodayAction
}

/** 오늘 탭 ViewModel. 계산은 하지 않고 저장소 값을 묶기만 한다. */
@HiltViewModel
class TodayViewModel @Inject constructor(
    insightRepository: InsightRepository,
    planRepository: PlanRepository,
    pokeRepository: PokeRepository,
    settingsRepository: SettingsRepository,
    clock: AppClock,
) : ViewModel() {

    /** 화면 상태. */
    val uiState: StateFlow<TodayUiState> = combine(
        insightRepository.observeToday(),
        planRepository.observeActivePlans(),
        pokeRepository.observeUnused(clock.now()),
        settingsRepository.settings,
    ) { summary, plans, pokes, settings ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TodayUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
