package com.capstone.pause.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.designsystem.component.rhythm.RhythmDraft
import com.capstone.pause.core.designsystem.component.rhythm.RhythmEditor
import com.capstone.pause.core.designsystem.component.rhythm.RhythmField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 설정 › 하루 리듬 상태. 프로필이 없으면(온보딩 전) 편집할 수 없다. */
data class RhythmSettingsUiState(val draft: RhythmDraft? = null, val weekend: Boolean = false)

/** 하루 리듬 화면 동작. */
sealed interface RhythmSettingsAction {
    /** 평일·주말 보기. */
    data class ShowWeekend(val weekend: Boolean) : RhythmSettingsAction

    /** 시각 칸 하나를 옮긴다. */
    data class Shift(val field: RhythmField, val end: Boolean, val deltaMinutes: Int) : RhythmSettingsAction

    /** 구간 켜기·끄기. */
    data class Toggle(val field: RhythmField, val enabled: Boolean) : RhythmSettingsAction
}

/** 하루 리듬 편집. 고칠 때마다 프로필에 바로 저장한다(그 뒤 세션의 맥락 분류가 이 값을 쓴다). */
@HiltViewModel
class RhythmSettingsViewModel @Inject constructor(private val profileRepository: ProfileRepository) : ViewModel() {

    private val weekend = MutableStateFlow(false)

    /** 화면 상태. */
    val uiState: StateFlow<RhythmSettingsUiState> =
        combine(profileRepository.profile, weekend) { profile, w ->
            RhythmSettingsUiState(draft = profile?.let { RhythmDraft.from(it.rhythm) }, weekend = w)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RhythmSettingsUiState())

    /** 동작 처리. */
    fun onAction(action: RhythmSettingsAction) {
        when (action) {
            is RhythmSettingsAction.ShowWeekend -> weekend.value = action.weekend

            is RhythmSettingsAction.Shift -> edit {
                RhythmEditor.shift(it, action.field, weekend.value, action.end, action.deltaMinutes)
            }

            is RhythmSettingsAction.Toggle -> edit { RhythmEditor.toggle(it, action.field, action.enabled) }
        }
    }

    private fun edit(change: (RhythmDraft) -> RhythmDraft) {
        viewModelScope.launch {
            val profile = profileRepository.profile.first() ?: return@launch
            val next = change(RhythmDraft.from(profile.rhythm))
            profileRepository.save(profile.copy(rhythm = next.toRhythm()))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
