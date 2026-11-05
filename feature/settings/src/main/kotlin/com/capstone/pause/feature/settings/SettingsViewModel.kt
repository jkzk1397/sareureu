package com.capstone.pause.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.demo.DemoDataSeeder
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.DefaultThresholds
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.intervention.reminder.ReminderScheduler
import com.capstone.pause.core.intervention.service.MonitoringController
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.ShortformSession
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.LocalTime
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
    val thresholds: Map<ContextSlot, Int> = DefaultThresholds.ALL,
    val recentSessions: List<ShortformSession> = emptyList(),
    /** 감지(포그라운드 서비스)가 돌고 있는지. */
    val monitoring: Boolean = false,
    /** 감지를 켜려 했는데 사용 기록 권한이 없어 안내를 보여 줄지. */
    val needsUsageAccess: Boolean = false,
    /** 예약 알림 시각(최대 3개). */
    val reminders: List<LocalTime> = emptyList(),
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

    /** 맥락별 임계값(분). */
    data class SetThreshold(val slot: ContextSlot, val minutes: Int) : SettingsAction

    /** 감지 켜기·끄기(사용자 탭에서만 서비스를 시작한다). */
    data class SetMonitoring(val enabled: Boolean) : SettingsAction

    /** 사용 기록 안내를 닫음(설정 화면으로 갔거나 취소). */
    data object UsageAccessHandled : SettingsAction

    /** 예약 알림 추가(기본 23:30, 최대 3개). */
    data object AddReminder : SettingsAction

    /** 예약 알림 지우기. */
    data class RemoveReminder(val index: Int) : SettingsAction

    /** 예약 알림 시각 바꾸기(±30분). */
    data class ShiftReminder(val index: Int, val minutes: Long) : SettingsAction
}

/** 설정 화면 ViewModel. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val demoDataSeeder: DemoDataSeeder,
    private val monitoringController: MonitoringController,
    private val reminderScheduler: ReminderScheduler,
    sessionRepository: SessionRepository,
    clock: AppClock,
) : ViewModel() {

    private val seeding = MutableStateFlow(false)
    private val needsUsageAccess = MutableStateFlow(false)

    /** 화면 상태. */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        seeding,
        sessionRepository.observeRecent(clock.now().minus(Duration.ofDays(RECENT_DAYS)), RECENT_LIMIT),
        combine(monitoringController.isMonitoring, needsUsageAccess, reminderScheduler.reminderTimes, ::Triple),
    ) { s, busy, recent, (monitoring, needsAccess, reminders) ->
        SettingsUiState(
            monitoring = monitoring,
            needsUsageAccess = needsAccess,
            reminders = reminders,
            demoMode = s.demoMode,
            demoPattern = s.demoPattern,
            seeding = busy,
            themeMode = s.themeMode,
            showMelt = s.showMelt,
            thresholds = s.thresholds,
            recentSessions = recent,
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

                is SettingsAction.SetThreshold -> settingsRepository.setThreshold(action.slot, action.minutes)

                is SettingsAction.SetMonitoring -> if (action.enabled) {
                    needsUsageAccess.value = !monitoringController.start()
                } else {
                    monitoringController.stop()
                }

                SettingsAction.UsageAccessHandled -> needsUsageAccess.value = false

                SettingsAction.AddReminder -> {
                    val now = reminderScheduler.reminderTimes.first()
                    if (now.size < ReminderScheduler.MAX_REMINDERS) {
                        val next = generateSequence(DEFAULT_REMINDER) { it.plusMinutes(STEP_MIN) }
                            .first { it !in now }
                        reminderScheduler.setReminderTimes(now + next)
                    }
                }

                is SettingsAction.RemoveReminder -> {
                    val now = reminderScheduler.reminderTimes.first()
                    reminderScheduler.setReminderTimes(now.filterIndexed { i, _ -> i != action.index })
                }

                is SettingsAction.ShiftReminder -> {
                    val now = reminderScheduler.reminderTimes.first()
                    reminderScheduler.setReminderTimes(
                        now.mapIndexed { i, t -> if (i == action.index) t.plusMinutes(action.minutes) else t },
                    )
                }
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
        const val RECENT_DAYS = 2L
        const val RECENT_LIMIT = 20
        const val STEP_MIN = 30L
        val DEFAULT_REMINDER: LocalTime = LocalTime.of(23, 30)
    }
}
