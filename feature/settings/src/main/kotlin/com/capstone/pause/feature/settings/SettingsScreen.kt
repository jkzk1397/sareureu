package com.capstone.pause.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(uiState = uiState, onAction = viewModel::onAction)
}

/** 설정 탭(상태 없음). TODO(BUILD_REPORT#2): 5단계에서 추적 앱·감지 방식·AI·개인정보 항목을 더한다. */
@Composable
fun SettingsScreen(uiState: SettingsUiState, onAction: (SettingsAction) -> Unit, modifier: Modifier = Modifier) {
    PauseScreenColumn(title = stringResource(R.string.settings_title), modifier = modifier) {
        SettingsSection(title = stringResource(R.string.settings_section_demo)) {
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
        SettingsSection(title = stringResource(R.string.settings_section_threshold)) {
            ThresholdRows(uiState.thresholds, onSet = { slot, m -> onAction(SettingsAction.SetThreshold(slot, m)) })
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
        SettingsSection(title = stringResource(R.string.settings_section_dev)) {
            RecentSessions(uiState.recentSessions)
        }
    }
}

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
        Text(text = label, style = PauseTheme.type.body, color = PauseTheme.colors.ink)
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    PauseTheme { SettingsScreen(uiState = SettingsUiState(demoMode = true), onAction = {}) }
}
