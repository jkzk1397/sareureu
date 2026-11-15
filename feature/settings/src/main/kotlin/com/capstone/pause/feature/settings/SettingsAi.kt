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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
            // 동의를 받는 자리라 국외 이전 한 줄은 접지 않는다. 전체 고지는 '자세히'에 그대로 있다.
            Text(
                stringResource(R.string.settings_ai_cloud_short),
                style = PauseTheme.type.bodySmall,
                color = colors.ink,
            )
            MoreNote(
                key = "cloud",
                summary = stringResource(R.string.settings_ai_cloud_more),
                detail = listOf(
                    stringResource(R.string.settings_ai_cloud_send),
                    stringResource(R.string.settings_ai_cloud_not_send),
                    stringResource(R.string.settings_ai_cloud_transfer),
                ).joinToString(PARAGRAPH),
            )
            PrimaryButton(text = stringResource(R.string.settings_ai_cloud_agree), onClick = {
                onAction(AiSettingsAction.ConfirmCloud)
            })
            TextButton(
                onClick = { onAction(AiSettingsAction.CancelCloud) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = PauseTheme.spacing.minTouch),
            ) { Text(stringResource(R.string.settings_ai_cloud_cancel), color = colors.ink2) }
        }
    }
}

/**
 * 설정 › AI가 한 일(감사 로그). 한 줄에 날짜·기능·결과만, 줄을 누르면 제공자·이유·걸린 시간·지문과 길이·받은 답이 펼쳐진다.
 * 최근 [RECENT]개만 보이고 '더 보기'로 나머지를 편다. 입력은 해시·길이만, 출력은 검증 통과본만 남아 있다.
 */
@Composable
internal fun AiCallList(state: AiSettingsUiState, onAction: (AiSettingsAction) -> Unit) {
    val colors = PauseTheme.colors
    MoreNote(
        key = "ai_log",
        summary = stringResource(R.string.settings_ai_log_short),
        detail = stringResource(R.string.settings_ai_log_caption),
    )
    if (state.calls.isEmpty()) {
        Text(stringResource(R.string.settings_ai_log_empty), style = PauseTheme.type.bodySmall, color = colors.ink3)
        return
    }
    var all by rememberSaveable { mutableStateOf(false) }
    val shown = if (all) state.calls else state.calls.take(RECENT)
    shown.forEach { entry -> AiCallRow(entry, expanded = entry.id in state.expanded, onAction = onAction) }
    if (state.calls.size > RECENT) {
        TextButton(
            onClick = { all = !all },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(
                stringResource(if (all) R.string.settings_ai_log_less else R.string.settings_ai_log_more),
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun AiCallRow(entry: AiCallEntry, expanded: Boolean, onAction: (AiSettingsAction) -> Unit) {
    val colors = PauseTheme.colors
    val r = entry.record
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .clickable(role = Role.Button) { onAction(AiSettingsAction.ToggleCall(entry.id)) }
            .padding(vertical = PauseTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.settings_ai_log_row,
                    TIME.format(r.at.atZone(ZoneId.systemDefault())),
                    stringResource(r.feature.labelRes()),
                    stringResource(r.outcome.labelRes()),
                ),
                style = PauseTheme.type.bodySmall,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (expanded) R.string.settings_mark_open else R.string.settings_mark_closed),
                style = PauseTheme.type.label,
                color = colors.ink2,
            )
        }
        if (entry.reported) {
            Text(
                stringResource(R.string.settings_ai_log_reported),
                style = PauseTheme.type.caption,
                color = colors.nudgeText,
            )
        }
        if (expanded) {
            val detail = buildList {
                add(stringResource(R.string.settings_ai_log_provider, stringResource(r.provider.labelRes())))
                r.reason?.let { add(stringResource(R.string.settings_ai_log_reason, it)) }
                add(stringResource(R.string.settings_ai_log_latency, r.latencyMs / MILLIS))
                add(stringResource(R.string.settings_ai_log_input, r.inputHash, r.inputLength))
            }
            detail.forEach { Text(it, style = PauseTheme.type.caption, color = colors.ink2) }
            Text(
                r.outputJson ?: stringResource(R.string.settings_ai_log_no_output),
                style = PauseTheme.type.caption,
                color = colors.ink2,
                maxLines = OUTPUT_LINES,
                overflow = TextOverflow.Ellipsis,
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

private const val RECENT = 5
private const val OUTPUT_LINES = 12
private const val MILLIS = 1000f
private const val PARAGRAPH = "\n\n"
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

internal fun AiMode.labelRes(): Int = when (this) {
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
