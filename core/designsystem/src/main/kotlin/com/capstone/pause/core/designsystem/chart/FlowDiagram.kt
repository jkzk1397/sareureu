package com.capstone.pause.core.designsystem.chart

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 흐름도 마디(진입 경로·숏폼 앱·이탈 경로 하나). [label]은 화면 글자 그대로. */
@Immutable
data class FlowNode(val id: String, val label: String, val value: Float)

/** 흐름도 띠: 진입→앱 또는 앱→이탈. */
@Immutable
data class FlowLink(val from: String, val to: String, val value: Float)

private class NodeBox(val top: Float, val height: Float, var outCursor: Float, var inCursor: Float)

/** 진입 → 숏폼 앱 → 이탈 3단 흐름도. 띠 두께 = 세션 수(값). 상위 5 + 기타는 호출하는 쪽이 접는다. */
@Composable
fun FlowDiagram(
    entries: List<FlowNode>,
    apps: List<FlowNode>,
    exits: List<FlowNode>,
    links: List<FlowLink>,
    modifier: Modifier = Modifier,
) {
    val colors = PauseTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = PauseTheme.type.caption.copy(color = colors.ink2)
    val description = stringResource(R.string.designsystem_flow_a11y) + ": " +
        (entries + apps + exits).joinToString { it.label }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(PauseSizes.flowHeight)
            .semantics { contentDescription = description },
    ) {
        val nodeW = PauseSizes.flowNodeWidth.toPx()
        val labelGap = PauseSizes.dash.toPx()
        val labelW = size.width * LABEL_FRACTION
        val xs = listOf(labelW, (size.width - nodeW) / 2f, size.width - labelW - nodeW)
        val boxes = HashMap<String, NodeBox>()
        listOf(entries, apps, exits).forEach { nodes -> layoutColumn(nodes, boxes) }
        // 띠
        links.forEach { link ->
            val a = boxes[link.from] ?: return@forEach
            val b = boxes[link.to] ?: return@forEach
            val fromCol = colOf(link.from, entries, apps)
            val toCol = fromCol + 1
            if (toCol > 2) return@forEach
            val h = link.value * scaleOf(boxes, link.from, entries, apps, exits)
            val x0 = xs[fromCol] + nodeW
            val x1 = xs[toCol]
            val y0 = a.outCursor
            val y1 = b.inCursor
            a.outCursor += h
            b.inCursor += h
            drawBand(x0, y0, x1, y1, h, colors.line)
        }
        // 마디 + 라벨
        listOf(entries, apps, exits).forEachIndexed { col, nodes ->
            nodes.forEach { n ->
                val box = boxes[n.id] ?: return@forEach
                drawRect(colors.ink, Offset(xs[col], box.top), Size(nodeW, box.height))
                val textX = if (col == 0) 0f else xs[col] + nodeW + labelGap
                drawLabel(measurer, n.label, labelStyle, textX, box.top + box.height / 2f, labelW - labelGap)
            }
        }
    }
}

private const val LABEL_FRACTION = 0.26f
private const val NODE_GAP_FRACTION = 0.03f

private fun DrawScope.layoutColumn(nodes: List<FlowNode>, boxes: HashMap<String, NodeBox>) {
    val total = nodes.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    val gap = size.height * NODE_GAP_FRACTION
    val usable = size.height - gap * (nodes.size - 1).coerceAtLeast(0)
    var y = 0f
    nodes.forEach { n ->
        val h = usable * n.value / total
        boxes[n.id] = NodeBox(y, h, y, y)
        y += h + gap
    }
}

private fun colOf(id: String, entries: List<FlowNode>, apps: List<FlowNode>): Int = when {
    entries.any { it.id == id } -> 0
    apps.any { it.id == id } -> 1
    else -> 2
}

/** 그 마디가 속한 열의 값 → px 배율. */
private fun scaleOf(
    boxes: Map<String, NodeBox>,
    id: String,
    entries: List<FlowNode>,
    apps: List<FlowNode>,
    exits: List<FlowNode>,
): Float {
    val col = listOf(entries, apps, exits)[colOf(id, entries, apps)]
    val node = col.firstOrNull { it.id == id } ?: return 0f
    val box = boxes[id] ?: return 0f
    return if (node.value > 0f) box.height / node.value else 0f
}

private fun DrawScope.drawBand(x0: Float, y0: Float, x1: Float, y1: Float, h: Float, color: Color) {
    val mid = (x0 + x1) / 2f
    val path = Path().apply {
        moveTo(x0, y0)
        cubicTo(mid, y0, mid, y1, x1, y1)
        lineTo(x1, y1 + h)
        cubicTo(mid, y1 + h, mid, y0 + h, x0, y0 + h)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    cy: Float,
    maxW: Float,
) {
    val m = measurer.measure(
        text,
        style,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        constraints = Constraints(maxWidth = maxW.toInt().coerceAtLeast(1)),
    )
    drawText(m, topLeft = Offset(x, cy - m.size.height / 2f))
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FlowDiagramPreview() {
    PauseTheme {
        Box(
            Modifier
                .background(PauseTheme.colors.surface)
                .padding(PauseTheme.spacing.md),
        ) {
            FlowDiagram(
                entries = listOf(
                    FlowNode("e1", "메신저 38%", 22f),
                    FlowNode("e2", "잠금 해제 24%", 14f),
                    FlowNode("e3", "홈 21%", 12f),
                ),
                apps = listOf(FlowNode("a1", "쇼츠", 30f), FlowNode("a2", "릴스", 18f)),
                exits = listOf(
                    FlowNode("x1", "화면 끔 41%", 24f),
                    FlowNode("x2", "메신저 22%", 14f),
                    FlowNode("x3", "홈 19%", 10f),
                ),
                links = listOf(
                    FlowLink("e1", "a1", 14f),
                    FlowLink("e1", "a2", 8f),
                    FlowLink("e2", "a1", 10f),
                    FlowLink("e2", "a2", 4f),
                    FlowLink("e3", "a1", 6f),
                    FlowLink("e3", "a2", 6f),
                    FlowLink("a1", "x1", 16f),
                    FlowLink("a1", "x2", 8f),
                    FlowLink("a1", "x3", 6f),
                    FlowLink("a2", "x1", 8f),
                    FlowLink("a2", "x2", 6f),
                    FlowLink("a2", "x3", 4f),
                ),
            )
        }
    }
}
