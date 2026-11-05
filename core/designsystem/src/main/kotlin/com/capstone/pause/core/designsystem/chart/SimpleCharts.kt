package com.capstone.pause.core.designsystem.chart

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot

/** 맥락별 가로 누적 막대. 조각 사이에 2dp 바탕 간격. 라벨은 범례(`ContextLegendRow`)로 함께 쓴다. */
@Composable
fun ContextBar(values: List<Pair<ContextSlot, Int>>, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val shown = values.filter { it.second > 0 }.sortedBy { it.first.ordinal }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PauseSizes.barHeight),
        horizontalArrangement = Arrangement.spacedBy(PauseSizes.barGap),
    ) {
        shown.forEach { (slot, min) ->
            Box(
                Modifier
                    .weight(min.toFloat())
                    .fillMaxHeight()
                    .background(colors.contextColor(slot), PauseTheme.shapes.small),
            )
        }
    }
}

/** 세션 길이 분포. [buckets] = (구간 이름, 비율 0..1). 막대 위에 비율 글자를 직접 쓴다. */
@Composable
fun DurationHistogram(buckets: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val max = buckets.maxOfOrNull { it.second }?.takeIf { it > 0f } ?: 1f
    val description = stringResource(R.string.designsystem_histogram_a11y) + ": " +
        buckets.joinToString { "${it.first} ${(it.second * 100).toInt()}%" }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { (label, ratio) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(ratio * 100).toInt()}%", style = PauseTheme.type.label, color = colors.ink)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(PauseSizes.histogramHeight * (ratio / max))
                        .background(colors.ink2, PauseTheme.shapes.small),
                )
                Text(label, style = PauseTheme.type.caption, color = colors.ink3, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 추세선: 주별 하루 평균(실선) + 기준선(점선). */
@Composable
fun TrendLine(points: List<Float>, baseline: Float?, labels: List<String>, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val description = stringResource(R.string.designsystem_trend_a11y)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(PauseSizes.trendHeight),
        ) {
            val top = maxOf(points.maxOrNull() ?: 0f, baseline ?: 0f).takeIf { it > 0f } ?: 1f
            fun y(v: Float) = size.height * (1f - v / top * 0.9f)
            val stroke = PauseSizes.chartStroke.toPx()
            drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), stroke / 2)
            baseline?.let {
                val dash = PauseSizes.dash.toPx()
                drawLine(
                    colors.ink3,
                    Offset(0f, y(it)),
                    Offset(size.width, y(it)),
                    stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )
            }
            if (points.isNotEmpty()) {
                val step = if (points.size > 1) size.width / (points.size - 1) else 0f
                val path = Path()
                points.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v)) }
                drawPath(path, colors.ink, style = Stroke(stroke))
                points.forEachIndexed { i, v -> drawCircle(colors.ink, stroke * 1.5f, Offset(i * step, y(v))) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { Text(it, style = PauseTheme.type.caption, color = colors.ink3) }
        }
    }
}

/** 하루 리듬 링의 한 구간(분, 0..1440). */
@Immutable
data class RhythmSegment(val slot: ContextSlot, val startMinute: Int, val endMinute: Int)

/** 온보딩의 하루 리듬 미리보기: 맥락 색 링. 방향은 하루 시계와 같다(위가 0시). */
@Composable
fun RhythmRing(segments: List<RhythmSegment>, modifier: Modifier = Modifier, diameter: Dp = PauseSizes.rhythmRing) {
    val colors = PauseTheme.colors
    val description = stringResource(R.string.designsystem_rhythm_a11y)
    Canvas(
        modifier
            .size(diameter)
            .semantics { contentDescription = description },
    ) {
        val w = PauseSizes.rhythmRingStroke.toPx()
        val r = size.minDimension / 2f - w / 2f
        val tl = Offset(size.width / 2f - r, size.height / 2f - r)
        val sz = Size(r * 2, r * 2)
        drawArc(colors.surfaceAlt, 0f, 360f, false, tl, sz, style = Stroke(w))
        segments.forEach { s ->
            val span = ((s.endMinute - s.startMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY).toFloat()
            drawArc(
                colors.contextColor(s.slot),
                s.startMinute / 4f - 90f,
                span / 4f,
                false,
                tl,
                sz,
                style = Stroke(w),
            )
        }
    }
}

private const val MINUTES_PER_DAY = 1440

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SimpleChartsPreview() {
    PauseTheme {
        Column(
            Modifier
                .background(PauseTheme.colors.surface)
                .padding(PauseTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            ContextBar(listOf(ContextSlot.PRE_SLEEP to 208, ContextSlot.COMMUTE to 161, ContextSlot.BREAK to 114))
            DurationHistogram(
                listOf(
                    "5분 미만" to .41f,
                    "5–10분" to .26f,
                    "10–20분" to .18f,
                    "20–40분" to .11f,
                    "40분+" to .04f,
                ),
            )
            TrendLine(listOf(124f, 110f, 96f), baseline = 124f, labels = listOf("1주", "2주", "3주"))
            Spacer(Modifier.height(PauseTheme.spacing.xs))
            RhythmRing(
                listOf(
                    RhythmSegment(ContextSlot.WAKE_UP, 7 * 60, 8 * 60),
                    RhythmSegment(ContextSlot.CLASS_WORK, 9 * 60, 18 * 60),
                    RhythmSegment(ContextSlot.PRE_SLEEP, 23 * 60, 60),
                ),
            )
        }
    }
}
