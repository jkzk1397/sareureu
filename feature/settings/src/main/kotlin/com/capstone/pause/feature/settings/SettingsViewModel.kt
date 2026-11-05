package com.capstone.pause.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.demo.DemoDataSeeder
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 설정 화면 상태. */
data class SettingsUiState(
    val demoMode: Boolean = false,
    val demoPattern: DemoPattern = DemoPattern.NIGHT_OWL,
    val seeding: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showMelt: Boolean = true,
)

/** 설정 화면 동작. */
sealed interface SettingsAction {
    /** 시연 모드 켜기·끄기. 켜면 지금 시각 기준 2주치 가상 기록을 새로 넣는다. */
    data class SetDemoMode(val enabled: Boolean) : SettingsAction

    /** 시연 패턴 고르기(시연 중이면 다시 넣는다). */
    data class SetDemoPattern(val pattern: DemoPattern) : SettingsAction

    /** "2주치 가상 기록 넣기". */
    data object ReseedDemo : SettingsAction

    /** 시연 기록 지우기. */
    data object ClearDemo : SettingsAction

    /** 테마 바꾸기. */
    data class SetTheme(val mode: ThemeMode) : SettingsAction

    /** 르르 녹는 표시. */
    data class SetShowMelt(val enabled: Boolean) : SettingsAction
}

/** 설정 화면 ViewModel. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val demoDataSeeder: DemoDataSeeder,
) : ViewModel() {

    private val seeding = MutableStateFlow(false)

    /** 화면 상태. */
    val uiState: StateFlow<SettingsUiState> = combine(settingsRepository.settings, seeding) { s, busy ->
        SettingsUiState(
            demoMode = s.demoMode,
            demoPattern = s.demoPattern,
            seeding = busy,
            themeMode = s.themeMode,
            showMelt = s.showMelt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    /** 사용자 동작 처리. */
    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.SetDemoMode -> {
                    if (action.enabled) seed(settingsRepository.settings.first().demoPattern)
                    settingsRepository.setDemoMode(action.enabled)
                }

                is SettingsAction.SetDemoPattern -> {
                    settingsRepository.setDemoPattern(action.pattern)
                    if (settingsRepository.settings.first().demoMode) seed(action.pattern)
                }

                SettingsAction.ReseedDemo -> seed(settingsRepository.settings.first().demoPattern)

                SettingsAction.ClearDemo -> demoDataSeeder.clear()

                is SettingsAction.SetTheme -> settingsRepository.setThemeMode(action.mode)

                is SettingsAction.SetShowMelt -> settingsRepository.setShowMelt(action.enabled)
            }
        }
    }

    private suspend fun seed(pattern: DemoPattern) {
        seeding.value = true
        try {
            demoDataSeeder.seed(pattern)
        } finally {
            seeding.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
