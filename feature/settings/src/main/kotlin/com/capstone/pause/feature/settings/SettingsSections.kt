package com.capstone.pause.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.data.settings.DefaultThresholds
import com.capstone.pause.core.data.settings.ThresholdGroup
import com.capstone.pause.core.data.trackedapp.TrackedApps
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import com.capstone.pause.core.model.ShortformSession
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** 알림 기준 두 줄(평소·밤). 1~60분, 슬라이더로 크게, −/+로 1분씩. 드래그 중에는 화면만 바꾸고 놓을 때 저장한다. */
@Composable
internal fun ThresholdRows(thresholds: Map<ContextSlot, Int>, onSet: (ThresholdGroup, Int) -> Unit) {
    Text(
        stringResource(R.string.settings_threshold_hint),
        style = PauseTheme.type.bodySmall,
        color = PauseTheme.colors.ink2,
    )
    ThresholdGroup.entries.forEach { group ->
        ThresholdGroupRow(
            name = stringResource(
                if (group ==
                    ThresholdGroup.NIGHT
                ) {
                    R.string.settings_threshold_night
                } else {
                    R.string.settings_threshold_normal
                },
            ),
            saved = DefaultThresholds.groupValue(thresholds, group),
            onSet = { onSet(group, it) },
        )
    }
}

@Composable
private fun ThresholdGroupRow(name: String, saved: Int, onSet: (Int) -> Unit) {
    val range = DefaultThresholds.RANGE
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: saved
    val less = stringResource(R.string.settings_threshold_less, name)
    val more = stringResource(R.string.settings_threshold_more, name)
    val colors = PauseTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(name, style = PauseTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { onSet(saved - 1) },
                enabled = saved > range.first,
                modifier = Modifier
                    .heightIn(min = PauseTheme.spacing.minTouch)
                    .semantics { contentDescription = less },
            ) { Text(MINUS, style = PauseTheme.type.title) }
            Text(
                text = stringResource(R.string.settings_minutes, shown),
                style = PauseTheme.type.body,
                color = colors.ink,
            )
            TextButton(
                onClick = { onSet(saved + 1) },
                enabled = saved < range.last,
                modifier = Modifier
                    .heightIn(min = PauseTheme.spacing.minTouch)
                    .semantics { contentDescription = more },
            ) { Text(PLUS, style = PauseTheme.type.title) }
        }
        Slider(
            value = dragging ?: saved.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSet(it.roundToInt().coerceIn(range)) }
                dragging = null
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.line,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = name },
        )
    }
}

/** 개발자 메뉴: 최근 세션(시각·앱·맥락·진입→이탈·분). 실제 기록인지 시연인지는 시연 모드가 정한다. */
@Composable
internal fun RecentSessions(sessions: List<ShortformSession>, zone: ZoneId = ZoneId.systemDefault()) {
    if (sessions.isEmpty()) {
        Text(
            stringResource(R.string.settings_dev_empty),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        return
    }
    sessions.forEachIndexed { i, s ->
        if (i > 0) HorizontalDivider(color = PauseTheme.colors.line)
        val start = ZonedDateTime.ofInstant(s.start, zone)
        val minutes = s.end?.let { Duration.between(s.start, it).toMinutes() }
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(
                    R.string.settings_dev_session_title,
                    start.monthValue,
                    start.dayOfMonth,
                    start.hour,
                    start.minute,
                    s.segments.joinToString(SEPARATOR) { appLabel(it.packageName) },
                ),
                style = PauseTheme.type.label,
                color = PauseTheme.colors.ink,
            )
            Text(
                text = stringResource(
                    R.string.settings_dev_session_body,
                    stringResource(s.context.labelRes()),
                    entryLabel(s.entry),
                    exitLabel(s.exit),
                    minutes?.let { stringResource(R.string.settings_minutes, it.toInt()) }
                        ?: stringResource(R.string.settings_dev_live),
                ),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun entryLabel(entry: EntryPoint): String = when (entry) {
    EntryPoint.Unlock -> stringResource(R.string.settings_path_unlock)
    EntryPoint.Home -> stringResource(R.string.settings_path_home)
    is EntryPoint.FromApp -> kindLabel(entry.kind)
    is EntryPoint.FromShortform -> stringResource(R.string.settings_path_shortform)
    EntryPoint.Unknown -> stringResource(R.string.settings_path_unknown)
}

@Composable
private fun exitLabel(exit: ExitPoint?): String = when (exit) {
    ExitPoint.ScreenOff -> stringResource(R.string.settings_path_screen_off)
    ExitPoint.Home -> stringResource(R.string.settings_path_home)
    is ExitPoint.ToApp -> kindLabel(exit.kind)
    ExitPoint.AfterNudgeStop -> stringResource(R.string.settings_path_nudge_stop)
    ExitPoint.Unknown, null -> stringResource(R.string.settings_path_unknown)
}

@Composable
private fun kindLabel(kind: AppKind): String = stringResource(
    when (kind) {
        AppKind.MESSENGER -> R.string.settings_kind_messenger
        AppKind.SOCIAL -> R.string.settings_kind_social
        AppKind.SHORTFORM -> R.string.settings_path_shortform
        AppKind.VIDEO -> R.string.settings_kind_video
        AppKind.STUDY_WORK -> R.string.settings_kind_study
        AppKind.BROWSER -> R.string.settings_kind_browser
        AppKind.GAME -> R.string.settings_kind_game
        AppKind.SHOPPING -> R.string.settings_kind_shopping
        AppKind.LAUNCHER -> R.string.settings_path_home
        AppKind.OTHER -> R.string.settings_kind_other
    },
)

private const val MINUS = "−"
private const val PLUS = "+"
private const val SEPARATOR = "→"

/** 개발자 메뉴용 앱 이름: 추적 앱 목록의 이름, 없으면 패키지 이름 그대로(`com.instagram.android`가 "android"로 보이지 않게). */
private fun appLabel(packageName: String): String =
    TrackedApps.DEFAULT.firstOrNull { it.packageName == packageName }?.label ?: packageName
