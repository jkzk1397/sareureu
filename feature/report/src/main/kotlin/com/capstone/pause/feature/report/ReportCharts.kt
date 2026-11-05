package com.capstone.pause.feature.report

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.data.insight.ContextShare
import com.capstone.pause.core.data.insight.WeekChartData
import com.capstone.pause.core.designsystem.chart.DayClockData
import com.capstone.pause.core.designsystem.chart.DayClockScale
import com.capstone.pause.core.designsystem.chart.DurationHistogram
import com.capstone.pause.core.designsystem.chart.FlowDiagram
import com.capstone.pause.core.designsystem.chart.FlowLink
import com.capstone.pause.core.designsystem.chart.FlowNode
import com.capstone.pause.core.designsystem.chart.WeekClock
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.component.iconRes
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.designsystem.theme.isLargeFont
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** 목업 05 순서의 차트 묶음: 일주일 시계 → 맥락별 합계 → 경로 → 세션 길이 → 또래 비교. */
@Composable
internal fun ReportCharts(charts: WeekChartData) {
    WeekClockCard(charts)
    ContextTotalsCard(charts)
    FlowCard(charts)
    LengthCard(charts)
    charts.peer?.let { PeerCard(charts) }
}

@Composable
private fun WeekClockCard(charts: WeekChartData) {
    PauseCard(title = stringResource(R.string.report_clock_title)) {
        Caption(stringResource(R.string.report_clock_caption))
        WeekClock(
            data = DayClockData(
                minutesByHour = charts.minutesByHour,
                totalLabel = durationLabel(charts.totalMinutes),
                caption = stringResource(R.string.report_clock_center),
                riskHours = charts.riskHours,
                scale = DayClockScale.Week,
            ),
            heatmapCells = charts.heatmap,
            dayLabels = charts.heatmapDays.map { it.getDisplayName(TextStyle.SHORT, Locale.KOREAN) },
            riskCells = charts.riskCells,
        )
        charts.peak?.let { peak ->
            Insight(
                stringResource(
                    R.string.report_peak,
                    reportHourLabel(peak.startHour),
                    reportHourLabel((peak.startHour + 2) % HOURS),
                    durationLabel(peak.minutes),
                ),
            )
        }
    }
}

@Composable
private fun ContextTotalsCard(charts: WeekChartData) {
    PauseCard(title = stringResource(R.string.report_context_title)) {
        Caption(stringResource(R.string.report_context_caption, durationLabel(charts.totalMinutes)))
        charts.contexts.forEach { ContextRow(it, it.barFraction) }
    }
}

@Composable
private fun ContextRow(item: ContextShare, fraction: Float) {
    val colors = PauseTheme.colors
    val name = stringResource(item.slot.labelRes())
    val value =
        stringResource(R.string.report_context_value, durationLabel(item.minutes), (item.share * PERCENT).roundToInt())
    val label: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(item.slot.iconRes()),
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(PauseSizes.iconSmall),
            )
            Spacer(Modifier.width(PauseTheme.spacing.xs))
            Text(name, style = PauseTheme.type.label, color = colors.ink)
        }
    }
    val bar: @Composable (Modifier) -> Unit = { m ->
        Column(modifier = m) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(MIN_BAR, 1f))
                    .height(PauseSizes.barHeight)
                    .background(colors.contextColor(item.slot), PauseTheme.shapes.small),
            )
            Text(value, style = PauseTheme.type.captionTabular, color = colors.ink2)
        }
    }
    val rowModifier = Modifier
        .fillMaxWidth()
        .clearAndSetSemantics { contentDescription = "$name $value" }
    if (isLargeFont()) {
        // 큰 글자: 이름을 막대 위에 둬서 이름이 한 글자씩 줄바꿈되지 않게.
        Column(modifier = rowModifier) {
            label()
            bar(Modifier.fillMaxWidth())
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = rowModifier) {
            Box(Modifier.width(LABEL_WIDTH)) { label() }
            bar(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlowCard(charts: WeekChartData) {
    val flow = charts.flow
    val total = charts.sessionCount.coerceAtLeast(1)
    PauseCard(title = stringResource(R.string.report_flow_title)) {
        Caption(stringResource(R.string.report_flow_caption, charts.sessionCount))
        val entries = flow.entries.map { (k, n) -> FlowNode(ENTRY + k, nodeLabel(keyLabel(k), n, total), n.toFloat()) }
        val apps = flow.apps.map { (k, n) -> FlowNode(APP + k, reportAppLabel(k), n.toFloat()) }
        val exits = flow.exits.map { (k, n) -> FlowNode(EXIT + k, nodeLabel(keyLabel(k), n, total), n.toFloat()) }
        if (isLargeFont()) {
            // 큰 글자에서는 띠 그림 대신 같은 값을 표로 보인다(라벨이 겹치지 않게).
            FlowTable(entries, apps.map { it.copy(label = nodeLabel(it.label, it.value.toInt(), total)) }, exits)
        } else {
            FlowDiagram(
                entries = entries,
                apps = apps,
                exits = exits,
                links = flow.entryToApp.map { FlowLink(ENTRY + it.first, APP + it.second, it.third.toFloat()) } +
                    flow.appToExit.map { FlowLink(APP + it.first, EXIT + it.second, it.third.toFloat()) },
            )
        }
        charts.longestEntry?.let {
            Insight(stringResource(R.string.report_flow_insight, keyLabel(it.key), durationLabel(it.medianMinutes)))
        }
    }
}

/** 흐름도의 표 보기: 진입·앱·이탈마다 이름과 비율. */
@Composable
private fun FlowTable(entries: List<FlowNode>, apps: List<FlowNode>, exits: List<FlowNode>) {
    val colors = PauseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm)) {
        listOf(
            R.string.report_flow_col_entry to entries,
            R.string.report_flow_col_app to apps,
            R.string.report_flow_col_exit to exits,
        ).forEach { (title, nodes) ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(title), style = PauseTheme.type.label, color = colors.ink2)
                nodes.forEach { Text(it.label, style = PauseTheme.type.body, color = colors.ink) }
            }
        }
    }
}

@Composable
private fun nodeLabel(name: String, count: Int, total: Int): String =
    stringResource(R.string.report_flow_node, name, (count * PERCENT.toDouble() / total).roundToInt())

@Composable
private fun LengthCard(charts: WeekChartData) {
    val labels = listOf(
        R.string.report_length_0_5,
        R.string.report_length_5_10,
        R.string.report_length_10_20,
        R.string.report_length_20_40,
        R.string.report_length_40_plus,
    )
    PauseCard(title = stringResource(R.string.report_length_title)) {
        DurationHistogram(
            buckets = labels.zip(charts.lengthShares) { res, share ->
                stringResource(res) to
                    share.toFloat()
            },
        )
    }
}

@Composable
private fun PeerCard(charts: WeekChartData) {
    val peer = charts.peer ?: return
    val colors = PauseTheme.colors
    PauseCard(title = stringResource(R.string.report_peer_title)) {
        Caption(stringResource(if (peer.isDemo) R.string.report_peer_caption_demo else R.string.report_peer_caption))
        val description = stringResource(
            R.string.report_peer_a11y,
            durationLabel(peer.myMinutesPerDay),
            durationLabel(peer.peerMedian),
        )
        Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
            PeerMarker(
                stringResource(R.string.report_peer_me),
                durationLabel(peer.myMinutesPerDay),
                peer.position(peer.myMinutesPerDay),
                colors.accent,
            )
            PeerTrack()
            PeerMarker(
                stringResource(R.string.report_peer_peer),
                durationLabel(peer.peerMedian),
                peer.position(peer.peerMedian),
                colors.ink2,
            )
            peer.myBaseline?.let {
                PeerMarker(
                    stringResource(R.string.report_peer_baseline),
                    durationLabel(it),
                    peer.position(it),
                    colors.ink3,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(durationLabel(0), style = PauseTheme.type.captionTabular, color = colors.ink3)
                Text(durationLabel(peer.scaleMaxMinutes), style = PauseTheme.type.captionTabular, color = colors.ink3)
            }
        }
    }
}

@Composable
private fun PeerTrack() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(PauseSizes.chartStroke * 2)
            .background(PauseTheme.colors.ink, PauseTheme.shapes.pill),
    )
}

/** 막대 위 [position](0~1) 자리에 점 + 이름·값. 위치는 데이터 쪽에서 계산했다. */
@Composable
private fun PeerMarker(name: String, value: String, position: Float, dot: androidx.compose.ui.graphics.Color) {
    val colors = PauseTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (position > 0f) Spacer(Modifier.weight(position))
        Box(
            Modifier
                .size(PauseSizes.legendDot)
                .background(dot, PauseTheme.shapes.pill),
        )
        Spacer(Modifier.width(PauseTheme.spacing.xxs))
        Text("$name $value", style = PauseTheme.type.caption, color = colors.ink)
        if (position < 1f) Spacer(Modifier.weight(1f - position + MIN_WEIGHT))
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink2)
}

/** 코드가 센 숫자로 만든 한 줄 요약(라임 바탕, 목업 05). */
@Composable
private fun Insight(text: String) {
    Text(
        text = text,
        style = PauseTheme.type.body,
        color = PauseTheme.colors.onAccentContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(PauseTheme.colors.accentContainer, PauseTheme.shapes.small)
            .padding(PauseTheme.spacing.md),
    )
}

private const val HOURS = 24
private const val MIN_BAR = 0.04f
private const val MIN_WEIGHT = 0.001f
private const val ENTRY = "e:"
private const val APP = "a:"
private const val EXIT = "x:"
private val LABEL_WIDTH = PauseSizes.dayClock / 3
