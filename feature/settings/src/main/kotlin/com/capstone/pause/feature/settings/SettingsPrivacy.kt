package com.capstone.pause.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.TrackedApp

/** 감지 방식: 앱 단위(선택됨) / 정밀 모드(P1, 비활성). */
@Composable
internal fun DetectionModeRows() {
    Text(
        stringResource(R.string.settings_detection_title),
        style = PauseTheme.type.label,
        color = PauseTheme.colors.ink,
    )
    ModeRow(
        label = stringResource(R.string.settings_detection_app),
        desc = stringResource(R.string.settings_detection_app_desc),
        selected = true,
        enabled = true,
    )
    ModeRow(
        label = stringResource(R.string.settings_detection_precise),
        desc = stringResource(R.string.settings_detection_precise_desc),
        selected = false,
        enabled = false,
    )
}

@Composable
private fun ModeRow(label: String, desc: String, selected: Boolean, enabled: Boolean) {
    val colors = PauseTheme.colors
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = {}),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(label, style = PauseTheme.type.body, color = if (enabled) colors.ink else colors.ink3)
            Text(desc, style = PauseTheme.type.bodySmall, color = colors.ink2)
        }
    }
}

/** 추적 앱 켜기·끄기. 이름은 앱 고유 이름(데이터), 아래 줄은 패키지 이름. */
@Composable
internal fun TrackedAppRows(apps: List<TrackedApp>, onToggle: (String, Boolean) -> Unit) {
    val colors = PauseTheme.colors
    Text(stringResource(R.string.settings_tracked_desc), style = PauseTheme.type.bodySmall, color = colors.ink2)
    apps.forEach { app ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch)
                .toggleable(value = app.enabled, role = Role.Switch, onValueChange = { onToggle(app.packageName, it) }),
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = PauseTheme.type.body, color = colors.ink)
                Text(app.packageName, style = PauseTheme.type.caption, color = colors.ink3)
            }
            Switch(
                checked = app.enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.primary,
                    checkedThumbColor = colors.onPrimary,
                ),
            )
        }
    }
}

/** 개인정보: 무엇이 어디 남는지 요약 + 내보내기 + 모든 기록 삭제. */
@Composable
internal fun PrivacyRows(state: PrivacyUiState, onExport: () -> Unit, onAction: (PrivacyAction) -> Unit) {
    val colors = PauseTheme.colors
    Summary(R.string.settings_privacy_device_title, R.string.settings_privacy_device_body)
    Summary(R.string.settings_privacy_cloud_title, R.string.settings_privacy_cloud_body)
    Summary(R.string.settings_privacy_never_title, R.string.settings_privacy_never_body)
    Text(stringResource(R.string.settings_export_desc), style = PauseTheme.type.bodySmall, color = colors.ink2)
    PrimaryButton(
        text = stringResource(
            if (state.export == ExportStatus.RUNNING) R.string.settings_export_running else R.string.settings_export,
        ),
        onClick = onExport,
        enabled = state.export != ExportStatus.RUNNING && !state.erasing,
    )
    when (state.export) {
        ExportStatus.DONE -> Text(
            stringResource(R.string.settings_export_done),
            style = PauseTheme.type.bodySmall,
            color = colors.success,
        )

        ExportStatus.FAILED -> Text(
            stringResource(R.string.settings_export_failed),
            style = PauseTheme.type.bodySmall,
            color = colors.nudgeText,
        )

        else -> Unit
    }
    Text(stringResource(R.string.settings_erase_desc), style = PauseTheme.type.bodySmall, color = colors.ink2)
    TextButton(
        onClick = { onAction(PrivacyAction.AskErase) },
        enabled = !state.erasing,
        modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(
            stringResource(if (state.erasing) R.string.settings_erase_running else R.string.settings_erase),
            style = PauseTheme.type.label,
            color = colors.nudgeText,
        )
    }
    if (state.eraseFailed.isNotEmpty()) {
        Text(
            stringResource(R.string.settings_erase_failed, state.eraseFailed.joinToString()),
            style = PauseTheme.type.bodySmall,
            color = colors.nudgeText,
        )
    }
    if (state.confirmErase) {
        AlertDialog(
            onDismissRequest = { onAction(PrivacyAction.CancelErase) },
            title = { Text(stringResource(R.string.settings_erase_confirm_title)) },
            text = { Text(stringResource(R.string.settings_erase_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(PrivacyAction.ConfirmErase) }) {
                    Text(stringResource(R.string.settings_erase_confirm_yes), color = colors.nudgeText)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(PrivacyAction.CancelErase) }) {
                    Text(stringResource(R.string.settings_erase_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun Summary(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs)) {
        Text(stringResource(title), style = PauseTheme.type.label, color = PauseTheme.colors.ink)
        Text(stringResource(body), style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink2)
    }
}

/** 정보: 앱 이름·버전, 글꼴 라이선스, AI 안내. */
@Composable
internal fun AboutRows(versionName: String) {
    val colors = PauseTheme.colors
    Text(
        stringResource(R.string.settings_about_version, stringResource(DesignR.string.app_name), versionName),
        style = PauseTheme.type.body,
        color = colors.ink,
    )
    Text(stringResource(R.string.settings_about_font), style = PauseTheme.type.bodySmall, color = colors.ink2)
    Text(stringResource(R.string.settings_about_ai), style = PauseTheme.type.bodySmall, color = colors.ink2)
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivacyRowsPreview() {
    PauseTheme {
        Column {
            TrackedAppRows(
                listOf(
                    TrackedApp("com.google.android.youtube", "YouTube", AppKind.SHORTFORM, true),
                    TrackedApp("com.instagram.android", "Instagram", AppKind.SHORTFORM, false),
                ),
            ) { _, _ -> }
            PrivacyRows(PrivacyUiState(export = ExportStatus.DONE), onExport = {}, onAction = {})
            AboutRows("0.1.0")
        }
    }
}
