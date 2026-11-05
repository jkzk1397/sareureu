package com.capstone.pause.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.trackedapp.TrackedAppRepository
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
    private val trackedAppRepository: TrackedAppRepository,
) : ViewModel() {

    private val state = MutableStateFlow(OnboardingUiState())

    /** 화면 상태. */
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            trackedAppRepository.trackedApps.collect { apps -> state.update { it.copy(apps = apps) } }
        }
    }

    /** 동작 처리. [OnboardingStep.DONE]의 저장은 [finish]. */
    fun onAction(action: OnboardingAction) {
        when (action) {
            is OnboardingAction.SelectAge -> state.update {
                // 만 18세 미만은 저장하지 않고 안내 화면으로(ADR-0009).
                if (action.band ==
                    null
                ) {
                    it.copy(ageBand = null, step = OnboardingStep.UNDERAGE)
                } else {
                    it.copy(ageBand = action.band)
                }
            }

            OnboardingAction.Next -> state.update { it.copy(step = OnboardingFlow.next(it.step)) }

            OnboardingAction.Back -> state.update { it.copy(step = OnboardingFlow.previous(it.step)) }

            OnboardingAction.AcceptTerms -> {
                record(ConsentItem.TERMS, granted = true)
                state.update { it.copy(step = OnboardingFlow.next(OnboardingStep.TERMS)) }
            }

            is OnboardingAction.SetNickname -> state.update {
                it.copy(nickname = action.value.take(OnboardingUiState.NICKNAME_MAX))
            }

            is OnboardingAction.SetOccupation -> state.update { it.copy(occupation = action.value) }

            is OnboardingAction.ShowWeekend -> state.update { it.copy(showWeekend = action.weekend) }

            is OnboardingAction.ShiftTime -> state.update {
                it.copy(
                    rhythm = RhythmEditor.shift(
                        it.rhythm,
                        action.field,
                        it.showWeekend,
                        action.end,
                        action.deltaMinutes,
                    ),
                )
            }

            is OnboardingAction.ToggleRange -> state.update {
                it.copy(rhythm = RhythmEditor.toggle(it.rhythm, action.field, action.enabled))
            }

            is OnboardingAction.ToggleGoal -> state.update { it.copy(goals = it.goals.toggle(action.goal)) }

            is OnboardingAction.ToggleInterest -> state.update {
                it.copy(interests = it.interests.toggle(action.interest))
            }

            is OnboardingAction.SetTone -> state.update { it.copy(tone = action.tone) }

            is OnboardingAction.SetDailyGoal -> state.update { it.copy(dailyGoalMinutes = action.minutes) }

            is OnboardingAction.ToggleApp -> viewModelScope.launch {
                trackedAppRepository.setEnabled(action.packageName, action.enabled)
            }

            is OnboardingAction.PermissionChoice -> {
                permissionConsent(action.step)?.let { record(it, granted = action.accepted) }
                // '나중에'를 누르면 대체 흐름 안내만 남기고 다음으로 간다. 수락하면 화면이 설정을 연다.
                if (!action.accepted) state.update { it.copy(step = OnboardingFlow.next(action.step)) }
            }

            is OnboardingAction.SetAiMode -> state.update { it.copy(aiMode = action.mode) }

            OnboardingAction.ConfirmAi -> {
                val mode = state.value.aiMode
                record(ConsentItem.AI_USE, granted = mode != AiMode.OFF)
                viewModelScope.launch { settingsRepository.setAiMode(mode) }
                state.update { it.copy(step = OnboardingFlow.next(OnboardingStep.AI)) }
            }

            is OnboardingAction.RefreshPermissions -> state.update { it.copy(permissions = action.states) }
        }
    }

    /** 프로필을 저장하고 [onDone]. [defaultNickname]은 닉네임을 비웠을 때. */
    fun finish(defaultNickname: String, onDone: () -> Unit) {
        if (state.value.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            profileRepository.save(state.value.toProfile(defaultNickname))
            onDone()
        }
    }

    private fun record(item: ConsentItem, granted: Boolean) {
        viewModelScope.launch { consentRepository.record(item, NOTICE_VERSION, granted) }
    }

    private fun permissionConsent(step: OnboardingStep): ConsentItem? = when (step) {
        OnboardingStep.PERM_USAGE -> ConsentItem.USAGE_ACCESS
        OnboardingStep.PERM_NOTIFICATIONS -> ConsentItem.NOTIFICATIONS
        OnboardingStep.PERM_OVERLAY -> ConsentItem.OVERLAY
        else -> null
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    private companion object {
        /** 고지 문구 버전. 문구가 바뀌면 올려서 다시 묻는다. */
        const val NOTICE_VERSION = 1
    }
}

/** 단계 순서(만 18세 미만 안내는 순서 밖). */
internal object OnboardingFlow {
    private val order = OnboardingStep.entries.filter { it != OnboardingStep.UNDERAGE }

    /** 다음 단계. 마지막이면 그대로. */
    fun next(step: OnboardingStep): OnboardingStep = order.getOrElse(order.indexOf(step) + 1) { step }

    /** 이전 단계. 처음이거나 미성년 안내면 연령 확인으로. */
    fun previous(step: OnboardingStep): OnboardingStep = if (step ==
        OnboardingStep.UNDERAGE
    ) {
        OnboardingStep.AGE
    } else {
        order.getOrElse(order.indexOf(step) - 1) { OnboardingStep.AGE }
    }
}
