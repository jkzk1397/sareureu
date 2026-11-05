package com.capstone.pause.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.capstone.pause.core.data.privacy.PrivacyRepository
import com.capstone.pause.core.data.trackedapp.TrackedAppRepository
import com.capstone.pause.core.intervention.reminder.ReminderScheduler
import com.capstone.pause.core.intervention.service.MonitoringController
import com.capstone.pause.core.model.TrackedApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 내보내기 진행 상태. */
enum class ExportStatus { IDLE, RUNNING, DONE, FAILED }

/** 추적 앱·개인정보 화면 상태. */
data class PrivacyUiState(
    val trackedApps: List<TrackedApp> = emptyList(),
    val export: ExportStatus = ExportStatus.IDLE,
    val confirmErase: Boolean = false,
    val erasing: Boolean = false,
    /** 지우지 못한 단계 이름(비어 있으면 실패 없음). */
    val eraseFailed: List<String> = emptyList(),
)

/** 추적 앱·개인정보 동작. */
sealed interface PrivacyAction {
    /** 추적 앱 켜기·끄기. */
    data class SetTracked(val packageName: String, val enabled: Boolean) : PrivacyAction

    /** 사용자가 고른 위치에 내보내기(SAF `CreateDocument` 결과). null이면 취소. */
    data class ExportTo(val uri: Uri?) : PrivacyAction

    /** 삭제 확인 창 열기. */
    data object AskErase : PrivacyAction

    /** 삭제 확인 창 닫기. */
    data object CancelErase : PrivacyAction

    /** 정말 모두 지우기. */
    data object ConfirmErase : PrivacyAction
}

/**
 * 추적 앱·개인정보 ViewModel.
 * '모든 기록 삭제' 순서: 감지 끄기 → 예약 알림 비우기 → WorkManager 작업 취소 → 로컬 저장소 지우기(Room·DataStore·캐시).
 * 프로필이 사라지면 앱 셸이 온보딩으로 돌아간다. 친구 서버(원격)는 아직 꺼져 있어 지울 것이 없다.
 */
@HiltViewModel
class PrivacySettingsViewModel @Inject constructor(
    private val trackedAppRepository: TrackedAppRepository,
    private val privacyRepository: PrivacyRepository,
    private val monitoringController: MonitoringController,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val local = MutableStateFlow(PrivacyUiState())

    /** 화면 상태. */
    val uiState: StateFlow<PrivacyUiState> = combine(trackedAppRepository.trackedApps, local) { apps, l ->
        l.copy(trackedApps = apps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PrivacyUiState())

    /** 동작 처리. */
    fun onAction(action: PrivacyAction) {
        when (action) {
            is PrivacyAction.SetTracked -> viewModelScope.launch {
                trackedAppRepository.setEnabled(action.packageName, action.enabled)
            }

            is PrivacyAction.ExportTo -> export(action.uri)

            PrivacyAction.AskErase -> local.value = local.value.copy(confirmErase = true, eraseFailed = emptyList())

            PrivacyAction.CancelErase -> local.value = local.value.copy(confirmErase = false)

            PrivacyAction.ConfirmErase -> erase()
        }
    }

    private fun export(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            local.value = local.value.copy(export = ExportStatus.RUNNING)
            val ok = privacyRepository.exportTo(uri)
            local.value = local.value.copy(export = if (ok) ExportStatus.DONE else ExportStatus.FAILED)
        }
    }

    private fun erase() {
        viewModelScope.launch {
            local.value = local.value.copy(confirmErase = false, erasing = true)
            val failed = mutableListOf<String>()
            runCatching { monitoringController.stop() }.onFailure { failed += "monitoring" }
            runCatching { reminderScheduler.setReminderTimes(emptyList()) }.onFailure { failed += "reminders" }
            runCatching { WorkManager.getInstance(context).cancelAllWork() }.onFailure { failed += "work" }
            failed += privacyRepository.eraseLocal()
            // 성공하면 프로필이 사라져 앱이 온보딩으로 바뀌므로 이 화면은 곧 사라진다.
            local.value = local.value.copy(erasing = false, eraseFailed = failed)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
