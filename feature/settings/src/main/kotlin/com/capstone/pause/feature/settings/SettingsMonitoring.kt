package com.capstone.pause.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 사용 기록 권한이 없을 때의 고지(무엇을·왜·안 하는 것) + '동의하고 설정으로 이동'.
 * 뒤로 가기나 취소는 동의가 아니다(ONE_SHOT 8절 4번).
 */
@Composable
internal fun UsageAccessNotice(onAgree: () -> Unit, onCancel: () -> Unit) {
    val colors = PauseTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceAlt, PauseTheme.shapes.small)
            .padding(PauseTheme.spacing.md),
    ) {
        Text(stringResource(R.string.settings_usage_notice_title), style = PauseTheme.type.label, color = colors.ink)
        Text(
            stringResource(R.string.settings_usage_notice_body),
            style = PauseTheme.type.bodySmall,
            color = colors.ink2,
        )
        PrimaryButton(text = stringResource(R.string.settings_usage_notice_agree), onClick = onAgree)
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch)) {
            Text(stringResource(R.string.settings_usage_notice_cancel), color = colors.ink2)
        }
    }
}

/** 예약 알림 목록(최대 3개): 시각 ±30분, 지우기, 추가. */
@Composable
internal fun ReminderRows(
    reminders: List<LocalTime>,
    max: Int,
    onShift: (Int, Long) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = PauseTheme.colors
    Text(stringResource(R.string.settings_reminder_desc), style = PauseTheme.type.bodySmall, color = colors.ink2)
    reminders.forEachIndexed { i, time ->
        val label = time.format(TIME)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(text = label, style = PauseTheme.type.title, color = colors.ink, modifier = Modifier.weight(1f))
            val earlier = stringResource(R.string.settings_reminder_earlier, label)
            val later = stringResource(R.string.settings_reminder_later, label)
            val remove = stringResource(R.string.settings_reminder_remove, label)
            TextButton(onClick = {
                onShift(i, -STEP)
            }, modifier = Modifier.semantics { contentDescription = earlier }) {
                Text(stringResource(R.string.settings_minus), color = colors.ink)
            }
            TextButton(onClick = { onShift(i, STEP) }, modifier = Modifier.semantics { contentDescription = later }) {
                Text(stringResource(R.string.settings_plus), color = colors.ink)
            }
            TextButton(onClick = { onRemove(i) }, modifier = Modifier.semantics { contentDescription = remove }) {
                Text(stringResource(R.string.settings_reminder_remove_short), color = colors.ink2)
            }
        }
    }
    if (reminders.size < max) {
        TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch)) {
            Text(stringResource(R.string.settings_reminder_add), color = colors.ink)
        }
    }
}

private const val STEP = 30L
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
