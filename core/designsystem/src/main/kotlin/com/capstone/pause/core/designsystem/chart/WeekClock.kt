package com.capstone.pause.core.designsystem.chart

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot
import kotlin.math.ceil

/** 일주일 시계: 하루 시계와 같은 다이얼 + "시계 / 표" 토글. 표는 [Heatmap]. */
@Composable
fun WeekClock(
    data: DayClockData,
    heatmapCells: List<List<Int>>,
    dayLabels: List<String>,
    riskCells: Set<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
    diameter: Dp = PauseSizes.dayClock,
    onHourClick: ((Int) -> Unit)? = null,
) {
    var showTable by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs),
            modifier = Modifier.align(Alignment.End),
        ) {
            ChoicePill(stringResource(R.string.designsystem_clock_toggle_clock), !showTable, { showTable = false })
            ChoicePill(stringResource(R.string.designsystem_clock_toggle_table), showTable, { showTable = true })
        }
        Spacer(Modifier.height(PauseTheme.spacing.sm))
        if (showTable) {
            Heatmap(heatmapCells, dayLabels, riskCells, Modifier.fillMaxWidth())
        } else {
            DayClock(data.copy(scale = DayClockScale.Week), diameter = diameter, onHourClick = onHourClick)
        }
    }
}

/**
 * 요일×2시간 히트맵(7×12). 0분 칸은 `surfaceAlt`, 위험 칸은 잉크 테두리.
 * [cells]는 요일(행) × 2시간 칸(열)의 분.
 */
@Composable
fun Heatmap(
    cells: List<List<Int>>,
    dayLabels: List<String>,
    riskCells: Set<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
) {
    val colors = PauseTheme.colors
    val max = cells.flatten().maxOrNull() ?: 0
    val gap = PauseSizes.heatmapGap
    val description = stringResource(R.string.designsystem_heatmap_a11y)
    Column(
        modifier = modifier.semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        cells.forEachIndexed { day, row ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                Text(
                    text = dayLabels.getOrElse(day) { "" },
                    style = PauseTheme.type.caption,
                    color = colors.ink3,
                    modifier = Modifier.width(PauseSizes.icon),
                )
                row.forEachIndexed { col, minutes ->
                    val risk = (day to col) in riskCells
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PauseSizes.heatmapCell)
                            .background(heatColor(minutes, max, colors), PauseTheme.shapes.small)
                            .then(
                                if (risk) {
                                    Modifier.border(PauseSizes.heatmapRiskBorder, colors.ink, PauseTheme.shapes.small)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        HeatmapAxis()
        HeatmapLegend(colors)
    }
}

/** 칸 색: 0분 → surfaceAlt, 나머지는 최댓값 대비 5단계. */
internal fun heatColor(minutes: Int, max: Int, colors: PauseColors): Color {
    if (minutes <= 0 || max <= 0) return colors.surfaceAlt
    val steps = colors.heatmap.size
    val level = (ceil(minutes.toDouble() / max * steps).toInt() - 1).coerceIn(0, steps - 1)
    return colors.heatmap[level]
}

@Composable
private fun HeatmapAxis() {
    val marks = mapOf(0 to "0", 3 to "6", 6 to "12", 9 to "18", 11 to "22")
    Row(horizontalArrangement = Arrangement.spacedBy(PauseSizes.heatmapGap)) {
        Spacer(Modifier.width(PauseSizes.icon))
        repeat(12) { col ->
            Text(
                text = marks[col] ?: "",
                style = PauseTheme.type.captionTabular,
                color = PauseTheme.colors.ink3,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeatmapLegend(colors: PauseColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs),
        modifier = Modifier.padding(top = PauseTheme.spacing.xs),
    ) {
        Text(stringResource(R.string.designsystem_heatmap_less), style = PauseTheme.type.caption, color = colors.ink3)
        colors.heatmap.forEach { c ->
            Box(
                Modifier
                    .size(width = PauseSizes.icon, height = PauseSizes.legendDot)
                    .background(c, PauseTheme.shapes.small),
            )
        }
        Text(stringResource(R.string.designsystem_heatmap_more), style = PauseTheme.type.caption, color = colors.ink3)
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeekClockPreview() {
    val hours = List(24) { h ->
        when (h) {
            in 22..23 -> mapOf(ContextSlot.PRE_SLEEP to 40 + h, ContextSlot.LATE_NIGHT to 10)
            in 0..1 -> mapOf(ContextSlot.LATE_NIGHT to 30)
            8 -> mapOf(ContextSlot.COMMUTE to 35)
            12 -> mapOf(ContextSlot.MEAL to 25)
            else -> emptyMap()
        }
    }
    val cells = List(7) { d -> List(12) { c -> if (c == 11) 30 + d * 5 else (c * d) % 9 * 3 } }
    PauseTheme {
        Box(
            Modifier
                .background(PauseTheme.colors.surface)
                .padding(PauseTheme.spacing.md),
        ) {
            WeekClock(
                data = DayClockData(hours, "11시간 12분", "일주일", riskHours = listOf(22..23)),
                heatmapCells = cells,
                dayLabels = listOf("월", "화", "수", "목", "금", "토", "일"),
                riskCells = setOf(1 to 11, 2 to 11),
            )
        }
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeatmapPreview() {
    val cells = List(7) { d -> List(12) { c -> if (c == 11) 30 + d * 5 else (c * d) % 9 * 3 } }
    PauseTheme {
        Box(
            Modifier
                .background(PauseTheme.colors.surface)
                .padding(PauseTheme.spacing.md),
        ) {
            Heatmap(cells, listOf("월", "화", "수", "목", "금", "토", "일"), setOf(1 to 11, 2 to 11))
        }
    }
}
