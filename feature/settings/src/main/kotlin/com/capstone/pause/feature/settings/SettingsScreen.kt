package com.capstone.pause.feature.settings

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.settings.DefaultThresholds
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.data.settings.ThresholdGroup
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.component.Reveal
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 설정 탭 진입점. */
@Composable
fun SettingsRoute(
    onOpenRhythm: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    aiViewModel: AiSettingsViewModel = hiltViewModel(),
    privacyViewModel: PrivacySettingsViewModel = hiltViewModel(),
    preciseViewModel: PreciseModeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val privacyState by privacyViewModel.uiState.collectAsStateWithLifecycle()
    val preciseState by preciseViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 정밀 모드: 동의하면 시스템 접근성 설정을 연다(사용자가 거기서 직접 켠다). 돌아오면 켜졌는지 다시 본다.
    LaunchedEffect(preciseState.openSystemSettings) {
        if (preciseState.openSystemSettings) {
            preciseViewModel.onAction(PreciseAction.SettingsOpened)
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
    LifecycleResumeEffect(Unit) {
        preciseViewModel.onAction(PreciseAction.Refresh)
        onPauseOrDispose { }
    }
    // 저장 위치는 사용자가 고른다(SAF). 저장소 권한이 필요 없다.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME)) {
        privacyViewModel.onAction(PrivacyAction.ExportTo(it))
    }
    val exportName = stringResource(R.string.settings_export_file_name)
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        aiState = aiState,
        onAiAction = aiViewModel::onAction,
        privacyState = privacyState,
        onPrivacyAction = privacyViewModel::onAction,
        preciseState = preciseState,
        onPreciseAction = preciseViewModel::onAction,
        onExport = { exportLauncher.launch(exportName) },
        versionName = versionName,
        onOpenRhythm = onOpenRhythm,
        onOpenUsageAccess = {
            viewModel.onAction(SettingsAction.UsageAccessHandled)
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        onOpenBatterySettings = {
            // 배터리 최적화 목록 화면만 연다. 예외를 직접 요청하지 않는다(03 문서 9절).
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}

/**
 * 설정 탭(상태 없음). 접는 묶음 6개: 알림 → 감지 → AI → 개인정보 → 화면 → 더 보기. 처음에는 모두 접혀 있고
 * 제목 줄 오른쪽에 지금 값을 한 마디로 보여 준다. 감지가 꺼져 있으면 묶음 밖 맨 위에 '감지 켜기' 카드를 따로 둔다.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    onOpenUsageAccess: () -> Unit = {},
    aiState: AiSettingsUiState = AiSettingsUiState(),
    onAiAction: (AiSettingsAction) -> Unit = {},
    privacyState: PrivacyUiState = PrivacyUiState(),
    onPrivacyAction: (PrivacyAction) -> Unit = {},
    preciseState: PreciseModeState = PreciseModeState(),
    onPreciseAction: (PreciseAction) -> Unit = {},
    onExport: () -> Unit = {},
    versionName: String = "",
    onOpenRhythm: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val group: @Composable (String, Int, String, @Composable () -> Unit) -> Unit = { id, title, summary, content ->
        SettingsGroup(
            title = stringResource(title),
            summary = summary,
            expanded = open == id,
            onToggle = { open = if (open == id) null else id },
            content = content,
        )
    }
    PauseScreenColumn(title = stringResource(R.string.settings_title), modifier = modifier) {
        if (!uiState.monitoring) MonitoringOffCard(uiState, onAction, onOpenUsageAccess)
        Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            group(GROUP_NUDGE, R.string.settings_group_nudge, thresholdSummary(uiState)) {
                NudgeGroup(uiState, onAction, onOpenRhythm)
            }
            group(
                GROUP_SENSING,
                R.string.settings_section_monitoring,
                stringResource(if (uiState.monitoring) R.string.settings_summary_on else R.string.settings_summary_off),
            ) {
                SensingGroup(
                    uiState,
                    onAction,
                    privacyState,
                    onPrivacyAction,
                    onOpenBatterySettings,
                    preciseState,
                    onPreciseAction,
                )
            }
            group(GROUP_AI, R.string.settings_group_ai, stringResource(aiState.mode.labelRes())) {
                SubTitle(stringResource(R.string.settings_section_ai))
                AiModeRows(aiState, onAiAction)
                SubTitle(stringResource(R.string.settings_section_ai_log))
                AiCallList(aiState, onAiAction)
            }
            group(GROUP_PRIVACY, R.string.settings_section_privacy, stringResource(R.string.settings_summary_privacy)) {
                PrivacyRows(privacyState, onExport = onExport, onAction = onPrivacyAction)
            }
            group(GROUP_DISPLAY, R.string.settings_section_display, stringResource(uiState.themeMode.labelRes())) {
                ThemeMode.entries.forEach { mode ->
                    RadioRow(
                        label = stringResource(mode.labelRes()),
                        selected = uiState.themeMode == mode,
                        onClick = { onAction(SettingsAction.SetTheme(mode)) },
                    )
                }
                SwitchRow(
                    label = stringResource(R.string.settings_show_melt),
                    description = stringResource(R.string.settings_show_melt_desc),
                    checked = uiState.showMelt,
                    onCheckedChange = { onAction(SettingsAction.SetShowMelt(it)) },
                )
            }
            group(
                GROUP_MORE,
                R.string.settings_group_more,
                stringResource(
                    if (uiState.demoMode) R.string.settings_summary_demo_on else R.string.settings_summary_more,
                ),
            ) {
                MoreGroup(uiState, onAction, versionName)
            }
        }
    }
}

/** 감지가 꺼져 있을 때만 맨 위에 뜨는 카드. 가장 중요한 상태라 접지 않는다. */
@Composable
private fun MonitoringOffCard(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenUsageAccess: () -> Unit,
) {
    PauseCard(containerColor = PauseTheme.colors.accentContainer) {
        SwitchRow(
            label = stringResource(R.string.settings_monitoring),
            description = stringResource(R.string.settings_monitoring_off_hint),
            checked = false,
            onCheckedChange = { onAction(SettingsAction.SetMonitoring(it)) },
        )
        if (uiState.needsUsageAccess) {
            UsageAccessNotice(onAgree = onOpenUsageAccess, onCancel = { onAction(SettingsAction.UsageAccessHandled) })
        }
    }
}

@Composable
private fun thresholdSummary(uiState: SettingsUiState): String = stringResource(
    R.string.settings_summary_threshold,
    DefaultThresholds.groupValue(uiState.thresholds, ThresholdGroup.NORMAL),
    DefaultThresholds.groupValue(uiState.thresholds, ThresholdGroup.NIGHT),
)

/** 알림 묶음: 알림 기준(평소·밤) → 예약 알림 → 하루 리듬 고치기. */
@Composable
private fun NudgeGroup(uiState: SettingsUiState, onAction: (SettingsAction) -> Unit, onOpenRhythm: () -> Unit) {
    SubTitle(stringResource(R.string.settings_section_threshold))
    ThresholdRows(uiState.thresholds, onSet = { g, m -> onAction(SettingsAction.SetThresholdGroup(g, m)) })
    SubTitle(stringResource(R.string.settings_section_reminder))
    ReminderRows(
        reminders = uiState.reminders,
        max = MAX_REMINDERS,
        onShift = { i, m -> onAction(SettingsAction.ShiftReminder(i, m)) },
        onRemove = { onAction(SettingsAction.RemoveReminder(it)) },
        onAdd = { onAction(SettingsAction.AddReminder) },
    )
    SubTitle(stringResource(R.string.settings_section_rhythm))
    Text(
        stringResource(R.string.settings_rhythm_hint),
        style = PauseTheme.type.bodySmall,
        color = PauseTheme.colors.ink2,
    )
    WideTextButton(stringResource(R.string.settings_rhythm_open), onOpenRhythm)
}

/** 감지 묶음: 감지 켜기(켜져 있을 때만 여기, 꺼져 있으면 맨 위 카드) → 추적 앱 → 감지 방식 → 배터리 안내. */
@Composable
private fun SensingGroup(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    privacyState: PrivacyUiState,
    onPrivacyAction: (PrivacyAction) -> Unit,
    onOpenBatterySettings: () -> Unit,
    preciseState: PreciseModeState,
    onPreciseAction: (PreciseAction) -> Unit,
) {
    if (uiState.monitoring) {
        SwitchRow(
            label = stringResource(R.string.settings_monitoring),
            description = null,
            checked = true,
            onCheckedChange = { onAction(SettingsAction.SetMonitoring(it)) },
        )
    }
    if (uiState.confirmMonitoringOff) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.CancelMonitoringOff) },
            title = { Text(stringResource(R.string.settings_monitoring_off_confirm_title)) },
            text = { Text(stringResource(R.string.settings_monitoring_off_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.ConfirmMonitoringOff) }) {
                    Text(stringResource(R.string.settings_monitoring_off_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.CancelMonitoringOff) }) {
                    Text(stringResource(R.string.settings_monitoring_off_confirm_no))
                }
            },
        )
    }
    MoreNote(
        key = "monitoring",
        summary = stringResource(R.string.settings_monitoring_short),
        detail = stringResource(R.string.settings_monitoring_desc),
    )
    SubTitle(stringResource(R.string.settings_section_tracked))
    TrackedAppRows(privacyState.trackedApps) { pkg, on -> onPrivacyAction(PrivacyAction.SetTracked(pkg, on)) }
    DetectionModeRows(preciseState, onPreciseAction)
    MoreNote(
        key = "battery",
        summary = stringResource(R.string.settings_battery_short),
        detail = stringResource(R.string.settings_battery_body),
    )
    WideTextButton(stringResource(R.string.settings_battery_open), onOpenBatterySettings)
}

/** 더 보기 묶음: 시연 모드 → 개발자 메뉴(접힘) → 정보. */
@Composable
private fun MoreGroup(uiState: SettingsUiState, onAction: (SettingsAction) -> Unit, versionName: String) {
    DemoRows(uiState, onAction)
    var dev by rememberSaveable { mutableStateOf(false) }
    WideTextButton(
        stringResource(if (dev) R.string.settings_dev_hide else R.string.settings_dev_show),
        onClick = { dev = !dev },
    )
    Reveal(dev) { RecentSessions(uiState.recentSessions) }
    SubTitle(stringResource(R.string.settings_section_about))
    AboutRows(versionName)
}

@Composable
private fun WideTextButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(text, color = PauseTheme.colors.ink)
    }
}

@Composable
private fun DemoRows(uiState: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    SwitchRow(
        label = stringResource(R.string.settings_demo_mode),
        description = stringResource(R.string.settings_demo_mode_desc),
        checked = uiState.demoMode,
        onCheckedChange = { onAction(SettingsAction.SetDemoMode(it)) },
    )
    if (uiState.demoMode) {
        DemoPattern.entries.forEach { pattern ->
            RadioRow(
                label = stringResource(pattern.labelRes()),
                selected = uiState.demoPattern == pattern,
                onClick = { onAction(SettingsAction.SetDemoPattern(pattern)) },
            )
        }
        PrimaryButton(
            text = stringResource(
                if (uiState.seeding) R.string.settings_demo_seeding else R.string.settings_demo_reseed,
            ),
            onClick = { onAction(SettingsAction.ReseedDemo) },
            enabled = !uiState.seeding,
        )
        TextButton(
            onClick = { onAction(SettingsAction.ClearDemo) },
            enabled = !uiState.seeding,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(text = stringResource(R.string.settings_demo_clear), color = PauseTheme.colors.ink2)
        }
    }
}

private const val EXPORT_MIME = "application/json"

private const val MAX_REMINDERS = 3

private const val GROUP_NUDGE = "nudge"
private const val GROUP_SENSING = "sensing"
private const val GROUP_AI = "ai"
private const val GROUP_PRIVACY = "privacy"
private const val GROUP_DISPLAY = "display"
private const val GROUP_MORE = "more"

private fun DemoPattern.labelRes(): Int = when (this) {
    DemoPattern.NIGHT_OWL -> R.string.settings_demo_pattern_night_owl
    DemoPattern.COMMUTER -> R.string.settings_demo_pattern_commuter
    DemoPattern.DOMINO -> R.string.settings_demo_pattern_domino
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun SettingsScreenPreview() {
    PauseTheme { SettingsScreen(uiState = SettingsUiState(demoMode = true), onAction = {}) }
}
