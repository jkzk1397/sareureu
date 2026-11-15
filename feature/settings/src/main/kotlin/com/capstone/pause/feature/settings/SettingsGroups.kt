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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.capstone.pause.core.designsystem.component.Reveal
import com.capstone.pause.core.designsystem.component.pauseSurface
import com.capstone.pause.core.designsystem.theme.PauseTheme

/**
 * 접는 묶음: 제목 줄(제목 + 지금 값 한 마디 + ▾▴)을 누르면 [content]가 펼쳐진다. 처음에는 접혀 있다.
 * 펼침 상태는 부르는 쪽이 `rememberSaveable`로 든다.
 */
@Composable
internal fun SettingsGroup(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = PauseTheme.colors
    val state = stringResource(if (expanded) R.string.settings_group_open else R.string.settings_group_closed)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pauseSurface(PauseTheme.shapes.card),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { stateDescription = state }
                .padding(horizontal = PauseTheme.spacing.md, vertical = PauseTheme.spacing.xs),
        ) {
            Text(
                text = title,
                style = PauseTheme.type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(PauseTheme.spacing.sm))
            Text(
                text = if (expanded) "" else summary,
                style = PauseTheme.type.bodySmall,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(PauseTheme.spacing.xs))
            Text(
                text = stringResource(if (expanded) R.string.settings_mark_open else R.string.settings_mark_closed),
                style = PauseTheme.type.label,
                color = colors.ink2,
            )
        }
        Reveal(expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PauseTheme.spacing.md,
                        end = PauseTheme.spacing.md,
                        bottom = PauseTheme.spacing.md,
                    ),
            ) { content() }
        }
    }
}

/** 묶음 안 작은 제목(예: 알림 묶음 안의 '예약 알림'). */
@Composable
internal fun SubTitle(text: String) {
    Text(
        text = text,
        style = PauseTheme.type.label,
        color = PauseTheme.colors.ink,
        modifier = Modifier
            .padding(top = PauseTheme.spacing.sm)
            .semantics { heading() },
    )
}

/**
 * 한 줄 요약 + '자세히'. 긴 설명·고지는 지우지 않고 [detail]에 그대로 두되 눌러야 보인다.
 * [key]는 같은 화면에서 펼침 상태를 구분하는 이름.
 */
@Composable
internal fun MoreNote(key: String, summary: String, detail: String) {
    val colors = PauseTheme.colors
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { open = !open },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(summary, style = PauseTheme.type.bodySmall, color = colors.ink2, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(PauseTheme.spacing.sm))
            Text(
                stringResource(if (open) R.string.settings_more_hide else R.string.settings_more_show),
                style = PauseTheme.type.label,
                color = colors.ink,
            )
        }
        Reveal(open) {
            Text(
                text = detail,
                style = PauseTheme.type.bodySmall,
                color = colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceAlt, PauseTheme.shapes.small)
                    .padding(PauseTheme.spacing.md),
            )
        }
    }
}

/** 이름 + (있으면) 한 줄 설명 + 스위치. */
@Composable
internal fun SwitchRow(label: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
            if (description != null) Text(text = description, style = PauseTheme.type.bodySmall, color = colors.ink2)
        }
        Spacer(Modifier.width(PauseTheme.spacing.sm))
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

/** 하나만 고르는 줄. */
@Composable
internal fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
