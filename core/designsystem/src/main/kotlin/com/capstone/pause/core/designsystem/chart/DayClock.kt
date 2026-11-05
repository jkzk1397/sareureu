package com.capstone.pause.core.designsystem.chart

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 꽃잎 길이 기준(06 문서 7절). */
sealed interface DayClockScale {
    /** 오늘: 30분 = 55dp(지름 300dp 기준), 다이얼 안에서 자른다. */
    data object Today : DayClockScale

    /** 일주일: 가장 긴 시간 칸 = 54dp(지름 300dp 기준). */
    data object Week : DayClockScale
}

/** 하루 시계 데이터. 계산은 `core:insights.ClockBins`가 하고 여기서는 그리기만 한다. */
@Immutable
data class DayClockData(
    /** 24칸, 인덱스 = 시(0..23), 값 = 맥락별 분. */
    val minutesByHour: List<Map<ContextSlot, Int>>,
    val totalLabel: String,
    val caption: String,
    val nowMinuteOfDay: Int? = null,
    /** 위험 시간대. 22..23이면 22:00–24:00 호. */
    val riskHours: List<IntRange> = emptyList(),
    val scale: DayClockScale = DayClockScale.Today,
)

/** 다이얼 비율(지름 대비). 06 문서 7절 값을 지름 300dp 기준으로 환산했다. */
private object Dial {
    const val REF_DP = 300f
    const val RIM = 0.40f
    const val CENTER = 0.135f
    const val PETAL_GAP_START = 0.012f
    const val PETAL_MARGIN = 0.02f
    const val TODAY_PER_30MIN = 55f / REF_DP
    const val WEEK_MAX = 54f / REF_DP
    const val TICK = 0.035f
    const val SMALL_DOT = 0.006f
    const val BIG_DOT = 0.011f
    const val LABEL = 0.455f
    const val NOW_DOT = 0.022f
    const val RISK = 0.425f
    const val HOUR_DEG = 15f
    const val GAP_DEG = 1.5f
}

/** 하루 시계: 위가 0시, 시계 방향, 1시간 = 꽃잎 하나(06 문서 7절). */
@Composable
fun DayClock(
    data: DayClockData,
    modifier: Modifier = Modifier,
    diameter: Dp = PauseSizes.dayClock,
    onHourClick: ((Int) -> Unit)? = null,
) {
    val colors = PauseTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = PauseTheme.type.captionTabular.copy(color = colors.ink3, textAlign = TextAlign.Center)
    val labels = listOf(
        stringResource(R.string.designsystem_clock_label_0),
        stringResource(R.string.designsystem_clock_label_6),
        stringResource(R.string.designsystem_clock_label_12),
        stringResource(R.string.designsystem_clock_label_18),
    )
    val description = clockDescription(data)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Canvas(
            modifier = Modifier
                .size(diameter)
                .then(
                    if (onHourClick == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(onHourClick) {
                            detectTapGestures { p -> hourAt(p, size.width.toFloat())?.let(onHourClick) }
                        }
                    },
                ),
        ) {
            val d = size.minDimension
            val c = Offset(d / 2f, d / 2f)
            drawDial(c, d, colors)
            drawPetals(c, d, data, colors)
            drawTicks(c, d, colors)
            labels.forEachIndexed { i, text ->
                val p = polar(c, d * Dial.LABEL, i * 6 * Dial.HOUR_DEG)
                val m = measurer.measure(text, labelStyle)
                drawText(m, topLeft = Offset(p.x - m.size.width / 2f, p.y - m.size.height / 2f))
            }
            data.riskHours.forEach { r -> drawRisk(c, d, r, colors) }
            drawCircle(colors.surface, d * Dial.CENTER, c)
            data.nowMinuteOfDay?.let { drawNow(c, d, it, colors) }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = diameter * Dial.CENTER * 2f)
                .padding(PauseTheme.spacing.xxs),
        ) {
            // 일주일 합계("11시간 12분")는 가운데 원에 비해 길어서 한 단계 작은 글자로 한 줄에 둔다.
            val totalStyle = if (data.scale == DayClockScale.Week) PauseTheme.type.label else PauseTheme.type.title
            Text(data.totalLabel, style = totalStyle, color = colors.ink, textAlign = TextAlign.Center, maxLines = 1)
            Text(data.caption, style = PauseTheme.type.caption, color = colors.ink2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun clockDescription(data: DayClockData): String {
    val totals = ContextSlot.entries.map { slot -> slot to data.minutesByHour.sumOf { it[slot] ?: 0 } }
        .filter { it.second > 0 }
    val items = totals.map { (slot, min) ->
        stringResource(R.string.designsystem_clock_a11y_item, stringResource(slot.labelRes()), min)
    }.joinToString(", ")
    return stringResource(R.string.designsystem_clock_a11y, data.caption, data.totalLabel, items)
}

/** 화면 좌표(지름 [d])에서 시(0..23). 다이얼 밖이면 null. */
private fun hourAt(p: Offset, d: Float): Int? {
    val dx = p.x - d / 2f
    val dy = p.y - d / 2f
    if (hypot(dx, dy) > d * Dial.RIM) return null
    // 위가 0도, 시계 방향
    val deg = (atan2(dx, -dy) * 180f / PI.toFloat() + 360f) % 360f
    return (deg / Dial.HOUR_DEG).toInt().coerceIn(0, 23)
}

/** 위(0시)에서 시계 방향 [deg]도, 반지름 [r]. */
private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val rad = (deg - 90f) * PI.toFloat() / 180f
    return Offset(c.x + r * cos(rad), c.y + r * sin(rad))
}

private fun DrawScope.drawDial(c: Offset, d: Float, colors: PauseColors) {
    val r = d * Dial.RIM
    drawCircle(colors.bg, r, c)
    // 위 반(18시→0시→6시)은 밤. Compose 각도로 180°에서 시계 방향 180°.
    drawArc(
        colors.dialNight,
        180f,
        180f,
        useCenter = true,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
    )
}

private fun DrawScope.drawTicks(c: Offset, d: Float, colors: PauseColors) {
    val r = d * (Dial.RIM - Dial.TICK)
    for (h in 0 until 24) {
        val big = h % 6 == 0
        drawCircle(colors.ink3, d * if (big) Dial.BIG_DOT else Dial.SMALL_DOT, polar(c, r, h * Dial.HOUR_DEG))
    }
}

private fun DrawScope.drawPetals(c: Offset, d: Float, data: DayClockData, colors: PauseColors) {
    val inner = d * (Dial.CENTER + Dial.PETAL_GAP_START)
    val maxLen = d * (Dial.RIM - Dial.CENTER - Dial.PETAL_GAP_START - Dial.PETAL_MARGIN)
    val perMinute = when (data.scale) {
        DayClockScale.Today -> d * Dial.TODAY_PER_30MIN / 30f

        DayClockScale.Week -> {
            val max = data.minutesByHour.maxOfOrNull { it.values.sum() } ?: 0
            if (max == 0) 0f else d * Dial.WEEK_MAX / max
        }
    }
    data.minutesByHour.forEachIndexed { hour, byCtx ->
        var r0 = inner
        val start = hour * Dial.HOUR_DEG + Dial.GAP_DEG / 2f - 90f
        val sweep = Dial.HOUR_DEG - Dial.GAP_DEG
        ContextSlot.entries.forEach { slot ->
            val min = byCtx[slot] ?: 0
            if (min <= 0) return@forEach
            val r1 = minOf(r0 + min * perMinute, inner + maxLen)
            if (r1 > r0) drawPath(annularSector(c, r0, r1, start, sweep), colors.contextColor(slot))
            r0 = r1
        }
    }
}

private fun annularSector(c: Offset, r0: Float, r1: Float, start: Float, sweep: Float): Path = Path().apply {
    arcTo(Rect(c, r1), start, sweep, forceMoveTo = true)
    arcTo(Rect(c, r0), start + sweep, -sweep, forceMoveTo = false)
    close()
}

private fun DrawScope.drawRisk(c: Offset, d: Float, range: IntRange, colors: PauseColors) {
    val r = d * Dial.RISK
    val dash = PauseSizes.dash.toPx()
    drawArc(
        color = colors.ink,
        startAngle = range.first * Dial.HOUR_DEG - 90f,
        sweepAngle = (range.last - range.first + 1) * Dial.HOUR_DEG,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(
            width = PauseSizes.chartStroke.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
        ),
    )
}

private fun DrawScope.drawNow(c: Offset, d: Float, minuteOfDay: Int, colors: PauseColors) {
    val p = polar(c, d * Dial.RIM, minuteOfDay / 4f)
    val r = d * Dial.NOW_DOT
    drawCircle(colors.accent, r, p)
    drawCircle(colors.ink, r, p, style = Stroke(PauseSizes.chartStroke.toPx()))
}

/** 미리보기·시연용 표본: 목업 01의 오늘. */
internal fun sampleTodayClock(): DayClockData {
    val hours = MutableList(24) { emptyMap<ContextSlot, Int>() }
    hours[8] = mapOf(ContextSlot.COMMUTE to 22)
    hours[10] = mapOf(ContextSlot.CLASS_WORK to 9)
    hours[12] = mapOf(ContextSlot.MEAL to 15)
    hours[15] = mapOf(ContextSlot.BREAK to 14)
    hours[19] = mapOf(ContextSlot.BREAK to 12)
    hours[22] = mapOf(ContextSlot.PRE_SLEEP to 12)
    return DayClockData(hours, "1시간 24분", "오늘 숏폼", nowMinuteOfDay = 23 * 60 + 8, riskHours = listOf(22..23))
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayClockPreview() {
    PauseTheme {
        Box(Modifier.background(PauseTheme.colors.surface)) { DayClock(sampleTodayClock()) }
    }
}
