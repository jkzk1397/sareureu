package com.capstone.pause.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.capstone.pause.core.ai.AiCallEntry
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.run.CallOutcome
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** AI 사용 방식 고르기 + 클라우드 국외 이전 고지(04 문서 5절). */
@Composable
internal fun AiModeRows(state: AiSettingsUiState, onAction: (AiSettingsAction) -> Unit) {
    val colors = PauseTheme.colors
    AiMode.entries.forEach { mode ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch)
                .selectable(selected = state.mode == mode, role = Role.RadioButton) {
                    onAction(AiSettingsAction.SelectMode(mode))
                },
        ) {
            RadioButton(selected = state.mode == mode, onClick = null)
            Spacer(Modifier.width(PauseTheme.spacing.xs))
            Column {
                Text(stringResource(mode.labelRes()), style = PauseTheme.type.body, color = colors.ink)
                Text(stringResource(mode.descRes()), style = PauseTheme.type.bodySmall, color = colors.ink2)
            }
        }
    }
    if (state.confirmingCloud) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceAlt, PauseTheme.shapes.small)
                .padding(PauseTheme.spacing.md),
        ) {
            Text(stringResource(R.string.settings_ai_cloud_send), style = PauseTheme.type.bodySmall, color = colors.ink)
            Text(
                stringResource(R.string.settings_ai_cloud_not_send),
                style = PauseTheme.type.bodySmall,
                color = colors.ink,
            )
            Text(
                stringResource(R.string.settings_ai_cloud_transfer),
                style = PauseTheme.type.bodySmall,
                color = colors.ink2,
            )
            PrimaryButton(text = stringResource(R.string.settings_ai_cloud_agree), onClick = {
                onAction(AiSettingsAction.ConfirmCloud)
            })
            TextButton(
                onClick = { onAction(AiSettingsAction.CancelCloud) },
                modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
            ) { Text(stringResource(R.string.settings_ai_cloud_cancel), color = colors.ink2) }
        }
    }
}

/** 설정 › AI가 한 일(감사 로그). 입력은 해시·길이만, 출력은 검증 통과본만 보인다. */
@Composable
internal fun AiCallList(state: AiSettingsUiState, onAction: (AiSettingsAction) -> Unit) {
    val colors = PauseTheme.colors
    Text(stringResource(R.string.settings_ai_log_caption), style = PauseTheme.type.bodySmall, color = colors.ink2)
    if (state.calls.isEmpty()) {
        Text(stringResource(R.string.settings_ai_log_empty), style = PauseTheme.type.bodySmall, color = colors.ink3)
        return
    }
    state.calls.forEach { entry ->
        val r = entry.record
        val expanded = entry.id in state.expanded
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch)
                .clickable { onAction(AiSettingsAction.ToggleCall(entry.id)) }
                .padding(vertical = PauseTheme.spacing.xs),
        ) {
            Text(
                stringResource(
                    R.string.settings_ai_log_row,
                    TIME.format(r.at.atZone(ZoneId.systemDefault())),
                    stringResource(r.feature.labelRes()),
                    stringResource(r.provider.labelRes()),
                ),
                style = PauseTheme.type.body,
                color = colors.ink,
            )
            Text(
                stringResource(
                    R.string.settings_ai_log_detail,
                    stringResource(r.outcome.labelRes()),
                    r.latencyMs,
                    r.model.orEmpty(),
                ),
                style = PauseTheme.type.bodySmall,
                color = colors.ink2,
            )
            if (entry.reported) {
                Text(
                    stringResource(R.string.settings_ai_log_reported),
                    style = PauseTheme.type.caption,
                    color = colors.nudgeText,
                )
            }
            if (expanded) {
                Text(
                    stringResource(R.string.settings_ai_log_input, r.inputHash, r.inputLength),
                    style = PauseTheme.type.captionTabular,
                    color = colors.ink3,
                )
                r.reason?.let {
                    Text(
                        stringResource(R.string.settings_ai_log_reason, it),
                        style = PauseTheme.type.caption,
                        color = colors.ink3,
                    )
                }
                Text(
                    r.outputJson ?: stringResource(R.string.settings_ai_log_no_output),
                    style = PauseTheme.type.caption,
                    color = colors.ink2,
                )
                if (!entry.reported) {
                    TextButton(
                        onClick = { onAction(AiSettingsAction.Report(entry.id)) },
                        modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
                    ) { Text(stringResource(R.string.settings_ai_log_report), color = colors.ink2) }
                }
            }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

private fun AiMode.labelRes(): Int = when (this) {
    AiMode.CLOUD -> R.string.settings_ai_mode_cloud
    AiMode.ON_DEVICE -> R.string.settings_ai_mode_device
    AiMode.OFF -> R.string.settings_ai_mode_off
}

private fun AiMode.descRes(): Int = when (this) {
    AiMode.CLOUD -> R.string.settings_ai_mode_cloud_desc
    AiMode.ON_DEVICE -> R.string.settings_ai_mode_device_desc
    AiMode.OFF -> R.string.settings_ai_mode_off_desc
}

private fun AiFeature.labelRes(): Int = when (this) {
    AiFeature.WEEKLY_REPORT -> R.string.settings_ai_feature_weekly
    AiFeature.COPY_PACK -> R.string.settings_ai_feature_copy
    AiFeature.CONTEXT_CHECK -> R.string.settings_ai_feature_context
    AiFeature.COACH_CHAT -> R.string.settings_ai_feature_coach
    AiFeature.POKE_DRAFT -> R.string.settings_ai_feature_poke
}

private fun Provider.labelRes(): Int = when (this) {
    Provider.CLOUD -> R.string.settings_ai_provider_cloud
    Provider.ON_DEVICE -> R.string.settings_ai_provider_device
    Provider.MOCK -> R.string.settings_ai_provider_mock
    Provider.REST_EVAL -> R.string.settings_ai_provider_eval
}

private fun CallOutcome.labelRes(): Int = when (this) {
    CallOutcome.PASSED -> R.string.settings_ai_outcome_passed
    CallOutcome.RETRIED_PASSED -> R.string.settings_ai_outcome_retried
    CallOutcome.MOCK_FALLBACK -> R.string.settings_ai_outcome_mock
    CallOutcome.SAFETY_BLOCKED -> R.string.settings_ai_outcome_safety
    CallOutcome.SKIPPED -> R.string.settings_ai_outcome_skipped
}
