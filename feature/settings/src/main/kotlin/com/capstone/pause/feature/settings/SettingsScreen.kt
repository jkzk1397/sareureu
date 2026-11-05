package com.capstone.pause.feature.settings

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 설정 탭 진입점. */
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    aiViewModel: AiSettingsViewModel = hiltViewModel(),
    privacyViewModel: PrivacySettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by aiViewModel.uiState.collectAsStateWithLifecycle()
    val privacyState by privacyViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
        onExport = { exportLauncher.launch(exportName) },
        versionName = versionName,
        onOpenUsageAccess = {
            viewModel.onAction(SettingsAction.UsageAccessHandled)
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

/**
 * 설정 탭(상태 없음). 묶음 순서: 감지 → 알림 → 맥락별 기준 → 추적 앱 → AI → 개인정보 → 화면 → 시연 → 개발자 → 정보.
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
    onExport: () -> Unit = {},
    versionName: String = "",
) {
    PauseScreenColumn(title = stringResource(R.string.settings_title), modifier = modifier) {
        SettingsSection(title = stringResource(R.string.settings_section_monitoring)) {
            SwitchRow(
                label = stringResource(R.string.settings_monitoring),
                description = stringResource(R.string.settings_monitoring_desc),
                checked = uiState.monitoring,
                onCheckedChange = { onAction(SettingsAction.SetMonitoring(it)) },
            )
            if (uiState.needsUsageAccess) {
                UsageAccessNotice(
                    onAgree = onOpenUsageAccess,
                    onCancel = { onAction(SettingsAction.UsageAccessHandled) },
                )
            }
            DetectionModeRows()
        }
        SettingsSection(title = stringResource(R.string.settings_section_reminder)) {
            ReminderRows(
                reminders = uiState.reminders,
                max = MAX_REMINDERS,
                onShift = { i, m -> onAction(SettingsAction.ShiftReminder(i, m)) },
                onRemove = { onAction(SettingsAction.RemoveReminder(it)) },
                onAdd = { onAction(SettingsAction.AddReminder) },
            )
        }
        SettingsSection(title = stringResource(R.string.settings_section_threshold)) {
            ThresholdRows(uiState.thresholds, onSet = { slot, m -> onAction(SettingsAction.SetThreshold(slot, m)) })
        }
        SettingsSection(title = stringResource(R.string.settings_section_tracked)) {
            TrackedAppRows(privacyState.trackedApps) { pkg, on -> onPrivacyAction(PrivacyAction.SetTracked(pkg, on)) }
        }
        SettingsSection(title = stringResource(R.string.settings_section_ai)) {
            AiModeRows(aiState, onAiAction)
        }
        SettingsSection(title = stringResource(R.string.settings_section_ai_log)) {
            AiCallList(aiState, onAiAction)
        }
        SettingsSection(title = stringResource(R.string.settings_section_privacy)) {
            PrivacyRows(privacyState, onExport = onExport, onAction = onPrivacyAction)
        }
        SettingsSection(title = stringResource(R.string.settings_section_display)) {
            SwitchRow(
                label = stringResource(R.string.settings_show_melt),
                description = stringResource(R.string.settings_show_melt_desc),
                checked = uiState.showMelt,
                onCheckedChange = { onAction(SettingsAction.SetShowMelt(it)) },
            )
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = stringResource(mode.labelRes()),
                    selected = uiState.themeMode == mode,
                    onClick = { onAction(SettingsAction.SetTheme(mode)) },
                )
            }
        }
        SettingsSection(title = stringResource(R.string.settings_section_demo)) {
            DemoRows(uiState, onAction)
        }
        SettingsSection(title = stringResource(R.string.settings_section_dev)) {
            RecentSessions(uiState.recentSessions)
        }
        SettingsSection(title = stringResource(R.string.settings_section_about)) {
            AboutRows(versionName)
        }
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
            modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(text = stringResource(R.string.settings_demo_clear), color = PauseTheme.colors.ink2)
        }
    }
}

private const val EXPORT_MIME = "application/json"

private const val MAX_REMINDERS = 3

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

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val colors = PauseTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(PauseTheme.spacing.hairline, colors.line, PauseTheme.shapes.card)
            .padding(PauseTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
    ) {
        Text(text = title, style = PauseTheme.type.title, color = colors.ink)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = PauseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = PauseTheme.type.body, color = colors.ink)
            Text(text = description, style = PauseTheme.type.bodySmall, color = colors.ink2)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.onPrimary,
            ),
        )
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(PauseTheme.spacing.xs))
        Text(text = label, style = PauseTheme.type.body, color = PauseTheme.colors.ink)
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    PauseTheme { SettingsScreen(uiState = SettingsUiState(demoMode = true), onAction = {}) }
}
