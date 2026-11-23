package com.capstone.pause.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.demo.DemoDataSeeder
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.monitoring.LiveSessionSource
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.DefaultThresholds
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.data.settings.ThresholdGroup
import com.capstone.pause.core.intervention.reminder.ReminderScheduler
import com.capstone.pause.core.intervention.service.MonitoringController
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
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
    /** 숏폼을 보는 중에 감지를 끄려 해서 한 번 더 묻는 중. */
    val confirmMonitoringOff: Boolean = false,
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

    /** 알림 기준 묶음(평소·밤)의 임계값(분). 묶음 안 맥락에 같은 값을 쓴다. */
    data class SetThresholdGroup(val group: ThresholdGroup, val minutes: Int) : SettingsAction

    /** 감지 켜기·끄기(사용자 탭에서만 서비스를 시작한다). */
    data class SetMonitoring(val enabled: Boolean) : SettingsAction

    /** 보는 중에 끄기 확인 창에서 '그래도 끌래'. */
    data object ConfirmMonitoringOff : SettingsAction

    /** 보는 중에 끄기 확인 창을 닫음(끄지 않음). */
    data object CancelMonitoringOff : SettingsAction

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
    private val liveSessionSource: LiveSessionSource,
    sessionRepository: SessionRepository,
    clock: AppClock,
) : ViewModel() {

    private val seeding = MutableStateFlow(false)
    private val needsUsageAccess = MutableStateFlow(false)
    private val confirmOff = MutableStateFlow(false)

    /** 화면 상태. */
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        seeding,
        sessionRepository.observeRecent(clock.now().minus(Duration.ofDays(RECENT_DAYS)), RECENT_LIMIT),
        combine(monitoringController.isMonitoring, needsUsageAccess, reminderScheduler.reminderTimes, ::Triple),
        confirmOff,
    ) { s, busy, recent, (monitoring, needsAccess, reminders), confirm ->
        SettingsUiState(
            confirmMonitoringOff = confirm,
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

                is SettingsAction.SetThresholdGroup ->
                    settingsRepository.setThresholds(DefaultThresholds.slotsOf(action.group), action.minutes)

                is SettingsAction.SetMonitoring -> if (action.enabled) {
                    needsUsageAccess.value = !monitoringController.start()
                } else if (watchingNow()) {
                    // 끄는 것은 막지 않는다(ADR-0001). 보는 중이면 기록이 여기서 멈춘다는 것만 한 번 알린다.
                    confirmOff.value = true
                } else {
                    monitoringController.stop()
                }

                SettingsAction.ConfirmMonitoringOff -> {
                    confirmOff.value = false
                    monitoringController.stop()
                }

                SettingsAction.CancelMonitoringOff -> confirmOff.value = false

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

    /** 지금 실제 숏폼 세션이 이어지는 중인지(숏폼을 떠난 지 60초 안 포함). */
    private suspend fun watchingNow(): Boolean = liveSessionSource.current.first()?.source == DataSource.REAL

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val RECENT_DAYS = 2L
        const val RECENT_LIMIT = 20
        const val STEP_MIN = 30L
        val DEFAULT_REMINDER: LocalTime = LocalTime.of(23, 30)
    }
}
