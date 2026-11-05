package com.capstone.pause.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.ai.AiCallEntry
import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 설정 › AI 상태: 사용 방식 + AI가 한 일(감사 로그). */
data class AiSettingsUiState(
    val mode: AiMode = AiMode.OFF,
    /** 클라우드로 바꾸기 전 국외 이전 고지를 보여 주는 중. */
    val confirmingCloud: Boolean = false,
    val calls: List<AiCallEntry> = emptyList(),
    val expanded: Set<Long> = emptySet(),
)

/** 설정 › AI 동작. */
sealed interface AiSettingsAction {
    /** 방식 고르기. 클라우드는 고지 화면을 먼저 보인다. */
    data class SelectMode(val mode: AiMode) : AiSettingsAction

    /** 고지에 동의하고 클라우드로. */
    data object ConfirmCloud : AiSettingsAction

    /** 고지 닫기(동의 아님). */
    data object CancelCloud : AiSettingsAction

    /** 기록 펼치기·접기. */
    data class ToggleCall(val id: Long) : AiSettingsAction

    /** 그 호출의 AI 문장 신고. */
    data class Report(val id: Long) : AiSettingsAction
}

/** 설정 › AI ViewModel. */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val consentRepository: ConsentRepository,
    private val logger: AiCallLogger,
) : ViewModel() {

    private val local = MutableStateFlow(AiSettingsUiState())

    /** 화면 상태. */
    val uiState: StateFlow<AiSettingsUiState> = combine(
        settingsRepository.settings,
        logger.observeRecent(LOG_LIMIT),
        local,
    ) { settings, calls, l -> l.copy(mode = settings.aiMode, calls = calls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AiSettingsUiState())

    /** 동작 처리. */
    fun onAction(action: AiSettingsAction) {
        viewModelScope.launch {
            when (action) {
                is AiSettingsAction.SelectMode -> if (action.mode == AiMode.CLOUD) {
                    local.update { it.copy(confirmingCloud = true) }
                } else {
                    settingsRepository.setAiMode(action.mode)
                    consentRepository.record(ConsentItem.AI_USE, AI_NOTICE_VERSION, granted = false)
                }

                AiSettingsAction.ConfirmCloud -> {
                    // 버튼을 눌렀을 때만 동의로 남긴다(뒤로 가기·닫기는 동의가 아님).
                    consentRepository.record(ConsentItem.AI_USE, AI_NOTICE_VERSION, granted = true)
                    settingsRepository.setAiMode(AiMode.CLOUD)
                    local.update { it.copy(confirmingCloud = false) }
                }

                AiSettingsAction.CancelCloud -> local.update { it.copy(confirmingCloud = false) }

                is AiSettingsAction.ToggleCall -> local.update { it.copy(expanded = it.expanded.toggle(action.id)) }

                is AiSettingsAction.Report -> logger.report(action.id)
            }
        }
    }

    private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val LOG_LIMIT = 50

        /** 온보딩 AI 동의와 같은 고지 문구 버전. */
        const val AI_NOTICE_VERSION = 1
    }
}
