package com.capstone.pause.core.designsystem.component.rhythm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.chart.RhythmRing
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseTheme
import java.time.LocalTime

/**
 * 하루 리듬 편집기(평일·주말 고르기 + 링 미리보기 + 시각 칸). 온보딩과 설정 › 하루 리듬이 같이 쓴다.
 * 상태는 부르는 쪽이 가진다: [onShift](칸, 끝인지, 분), [onToggle](칸, 켜기).
 */
@Composable
fun RhythmEditorPanel(
    draft: RhythmDraft,
    weekend: Boolean,
    onWeekendChange: (Boolean) -> Unit,
    onShift: (field: RhythmField, end: Boolean, deltaMinutes: Int) -> Unit,
    onToggle: (field: RhythmField, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            ChoicePill(
                text = stringResource(R.string.designsystem_rhythm_weekday),
                selected = !weekend,
                onClick = { onWeekendChange(false) },
                modifier = Modifier.weight(1f),
            )
            ChoicePill(
                text = stringResource(R.string.designsystem_rhythm_weekend),
                selected = weekend,
                onClick = { onWeekendChange(true) },
                modifier = Modifier.weight(1f),
            )
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            RhythmRing(segments = RhythmEditor.preview(draft, weekend))
        }
        PauseCard {
            TimeRow(
                R.string.designsystem_rhythm_wake,
                if (weekend) draft.weekendWake else draft.weekdayWake,
            ) { onShift(RhythmField.WAKE, false, it) }
            HorizontalDivider(color = PauseTheme.colors.line)
            TimeRow(
                R.string.designsystem_rhythm_sleep,
                if (weekend) draft.weekendSleep else draft.weekdaySleep,
            ) { onShift(RhythmField.SLEEP, false, it) }
            if (weekend) {
                Text(
                    stringResource(R.string.designsystem_rhythm_weekend_note),
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
            } else {
                RangeRow(
                    R.string.designsystem_rhythm_commute_morning,
                    RhythmField.COMMUTE_MORNING,
                    draft.commuteMorning,
                    onShift,
                    onToggle,
                )
                RangeRow(
                    R.string.designsystem_rhythm_commute_evening,
                    RhythmField.COMMUTE_EVENING,
                    draft.commuteEvening,
                    onShift,
                    onToggle,
                )
                RangeRow(R.string.designsystem_rhythm_work, RhythmField.WORK, draft.work, onShift, onToggle)
            }
            RangeRow(R.string.designsystem_rhythm_lunch, RhythmField.LUNCH, draft.lunch, onShift, onToggle)
            RangeRow(R.string.designsystem_rhythm_dinner, RhythmField.DINNER, draft.dinner, onShift, onToggle)
        }
    }
}

@Composable
private fun TimeRow(label: Int, time: LocalTime, onShift: (Int) -> Unit) {
    val name = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = name, style = PauseTheme.type.body, color = PauseTheme.colors.ink, modifier = Modifier.weight(1f))
        RhythmTimeStepper(label = name, time = time, onShift = onShift)
    }
}

@Composable
private fun RangeRow(
    label: Int,
    field: RhythmField,
    range: TimeRange?,
    onShift: (RhythmField, Boolean, Int) -> Unit,
    onToggle: (RhythmField, Boolean) -> Unit,
) {
    val name = stringResource(label)
    HorizontalDivider(color = PauseTheme.colors.line)
    // 줄 전체를 스위치로 묶어 화면 읽기가 이름과 켜짐 상태를 함께 읽는다.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .toggleable(value = range != null, role = Role.Switch, onValueChange = { onToggle(field, it) }),
    ) {
        Text(text = name, style = PauseTheme.type.body, color = PauseTheme.colors.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(PauseTheme.spacing.sm))
        Switch(checked = range != null, onCheckedChange = null)
    }
    if (range == null) {
        Text(
            stringResource(R.string.designsystem_rhythm_range_off),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink3,
        )
    } else {
        Column {
            RhythmTimeStepper(
                caption = stringResource(R.string.designsystem_rhythm_range_start_short),
                label = stringResource(R.string.designsystem_rhythm_range_start, name),
                time = range.start,
                onShift = { onShift(field, false, it) },
            )
            RhythmTimeStepper(
                caption = stringResource(R.string.designsystem_rhythm_range_end_short),
                label = stringResource(R.string.designsystem_rhythm_range_end, name),
                time = range.end,
                onShift = { onShift(field, true, it) },
            )
        }
    }
}

/** 시각 하나를 −/+ 30분으로 고치는 칸(터치 48dp). [label]은 화면 읽기용 버튼 이름, [caption]은 보이는 짧은 이름(시작·끝). 온보딩 자는 시간에도 쓴다. */
@Composable
fun RhythmTimeStepper(label: String, time: LocalTime, onShift: (Int) -> Unit, caption: String? = null) {
    val colors = PauseTheme.colors
    val earlier = stringResource(R.string.designsystem_rhythm_time_earlier, label)
    val later = stringResource(R.string.designsystem_rhythm_time_later, label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (caption != null) {
            Text(text = caption, style = PauseTheme.type.bodySmall, color = colors.ink2)
        }
        TextButton(
            onClick = { onShift(-STEP_MIN) },
            modifier = Modifier
                .heightIn(min = PauseTheme.spacing.minTouch)
                .semantics { contentDescription = earlier },
        ) {
            Text(text = MINUS, style = PauseTheme.type.title, color = colors.ink)
        }
        Text(
            text = stringResource(R.string.designsystem_rhythm_time, time.hour, time.minute),
            style = PauseTheme.type.title,
            color = colors.ink,
        )
        TextButton(
            onClick = { onShift(STEP_MIN) },
            modifier = Modifier
                .heightIn(min = PauseTheme.spacing.minTouch)
                .semantics { contentDescription = later },
        ) {
            Text(text = PLUS, style = PauseTheme.type.title, color = colors.ink)
        }
    }
}

private const val MINUS = "−"
private const val PLUS = "+"
private const val STEP_MIN = 30
