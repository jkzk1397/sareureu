package com.capstone.pause.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.monitoring.MonitoringSwitch
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 온보딩 ViewModel. 프로필은 마지막에 한 번 저장하고, 동의는 버튼을 누를 때 기록한다. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val consentRepository: ConsentRepository,
    private val settingsRepository: SettingsRepository,
    private val monitoringSwitch: MonitoringSwitch,
) : ViewModel() {

    private val state = MutableStateFlow(OnboardingUiState())

    /** 화면 상태. */
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    /** 동작 처리. 마지막 저장은 [finish]. */
    fun onAction(action: OnboardingAction) {
        when (action) {
            // 만 18세 미만은 저장하지 않고 안내 화면으로(ADR-0009).
            is OnboardingAction.SelectAge -> state.update {
                if (action.band == null) {
                    it.copy(ageBand = null, step = OnboardingStep.UNDERAGE)
                } else {
                    it.copy(ageBand = action.band)
                }
            }

            OnboardingAction.Next -> state.update { it.copy(step = OnboardingFlow.next(it.step)) }

            OnboardingAction.Back -> state.update { it.copy(step = OnboardingFlow.previous(it.step)) }

            OnboardingAction.AcceptTerms -> if (state.value.ageBand != null) {
                record(ConsentItem.TERMS, granted = true)
                state.update { it.copy(step = OnboardingFlow.next(OnboardingStep.START)) }
            }

            is OnboardingAction.SetNickname -> state.update {
                it.copy(nickname = action.value.take(OnboardingUiState.NICKNAME_MAX))
            }

            is OnboardingAction.SetOccupation -> state.update { it.copy(occupation = action.value) }

            is OnboardingAction.ShiftSleep -> state.update {
                it.copy(sleep = it.shownSleep.plusMinutes(action.deltaMinutes.toLong()))
            }

            is OnboardingAction.ToggleGoal -> state.update { it.copy(goals = it.goals.toggle(action.goal)) }

            is OnboardingAction.SetDailyGoal -> state.update { it.copy(dailyGoalMinutes = action.minutes) }

            is OnboardingAction.AllowPermission -> record(consentOf(action.kind), granted = true)

            is OnboardingAction.RefreshPermissions -> state.update { it.copy(permissions = action.states) }
        }
    }

    /**
     * 마지막 화면: AI 동의를 기록하고([useAi]면 클라우드, 아니면 끔) 프로필을 저장한 뒤 [onDone].
     * 사용 기록 권한이 있으면 감지를 바로 켠다(사용자가 '시작'을 누른 순간이라 앱이 화면에 있다).
     */
    fun finish(defaultNickname: String, useAi: Boolean, onDone: () -> Unit) {
        if (state.value.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            consentRepository.record(ConsentItem.AI_USE, NOTICE_VERSION, useAi)
            settingsRepository.setAiMode(if (useAi) AiMode.CLOUD else AiMode.OFF)
            profileRepository.save(state.value.toProfile(defaultNickname))
            if (monitoringSwitch.hasUsageAccess()) runCatching { monitoringSwitch.turnOn() }
            onDone()
        }
    }

    private fun record(item: ConsentItem, granted: Boolean) {
        viewModelScope.launch { consentRepository.record(item, NOTICE_VERSION, granted) }
    }

    private fun consentOf(kind: PermissionKind): ConsentItem = when (kind) {
        PermissionKind.USAGE -> ConsentItem.USAGE_ACCESS
        PermissionKind.NOTIFICATIONS -> ConsentItem.NOTIFICATIONS
        PermissionKind.OVERLAY -> ConsentItem.OVERLAY
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    private companion object {
        /** 고지 문구 버전. 고지 내용(04 문서)이 바뀌면 올려서 다시 묻는다. 이번에는 접기만 해서 그대로. */
        const val NOTICE_VERSION = 1
    }
}

/** 단계 순서(만 18세 미만 안내는 순서 밖). */
internal object OnboardingFlow {
    private val order = OnboardingStep.entries.filter { it != OnboardingStep.UNDERAGE }

    /** 다음 단계. 마지막이면 그대로. */
    fun next(step: OnboardingStep): OnboardingStep =
        if (step == OnboardingStep.UNDERAGE) step else order.getOrElse(order.indexOf(step) + 1) { step }

    /** 이전 단계. 처음이거나 미성년 안내면 시작 화면으로. */
    fun previous(step: OnboardingStep): OnboardingStep = if (step == OnboardingStep.UNDERAGE) {
        OnboardingStep.START
    } else {
        order.getOrElse(order.indexOf(step) - 1) { OnboardingStep.START }
    }
}
