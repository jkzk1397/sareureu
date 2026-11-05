package com.capstone.pause.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 설정 화면 상태. */
data class SettingsUiState(
    val demoMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showMelt: Boolean = true,
)

/** 설정 화면 동작. */
sealed interface SettingsAction {
    /** 시연 모드 켜기·끄기. */
    data class SetDemoMode(val enabled: Boolean) : SettingsAction

    /** 테마 바꾸기. */
    data class SetTheme(val mode: ThemeMode) : SettingsAction

    /** 르르 녹는 표시. */
    data class SetShowMelt(val enabled: Boolean) : SettingsAction
}

/** 설정 화면 ViewModel. */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {

    /** 화면 상태. */
    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map { SettingsUiState(demoMode = it.demoMode, themeMode = it.themeMode, showMelt = it.showMelt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /** 사용자 동작 처리. */
    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SetDemoMode -> settingsRepository.setDemoMode(action.enabled)
                is SettingsAction.SetTheme -> settingsRepository.setThemeMode(action.mode)
                is SettingsAction.SetShowMelt -> settingsRepository.setShowMelt(action.enabled)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
