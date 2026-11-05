package com.capstone.pause

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 앱 셸 상태: 온보딩 게이트와 테마. */
sealed interface MainUiState {
    /** DataStore를 읽는 중(스플래시 유지). */
    data object Loading : MainUiState

    /** 읽기 끝. */
    data class Ready(val needsOnboarding: Boolean, val themeMode: ThemeMode) : MainUiState
}

/** 앱 셸 ViewModel. */
@HiltViewModel
class MainViewModel @Inject constructor(profileRepository: ProfileRepository, settingsRepository: SettingsRepository) :
    ViewModel() {

    /** 셸 상태. */
    val uiState: StateFlow<MainUiState> =
        combine(profileRepository.hasProfile, settingsRepository.settings) { hasProfile, settings ->
            MainUiState.Ready(needsOnboarding = !hasProfile, themeMode = settings.themeMode)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
}
