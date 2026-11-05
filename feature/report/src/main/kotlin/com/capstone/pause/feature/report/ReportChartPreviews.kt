package com.capstone.pause.feature.report

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.data.insight.ContextShare
import com.capstone.pause.core.data.insight.EntryLengthSummary
import com.capstone.pause.core.data.insight.FlowSummary
import com.capstone.pause.core.data.insight.PeakSummary
import com.capstone.pause.core.data.insight.PeerSummary
import com.capstone.pause.core.data.insight.WeekChartData
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot
import java.time.DayOfWeek
import java.time.LocalDate

/** 미리보기용 차트 값(목업 05 수치). */
internal fun previewCharts(): WeekChartData {
    val contexts = listOf(
        ContextSlot.PRE_SLEEP to 208,
        ContextSlot.COMMUTE to 161,
        ContextSlot.BREAK to 114,
        ContextSlot.MEAL to 81,
        ContextSlot.LATE_NIGHT to 60,
        ContextSlot.CLASS_WORK to 34,
        ContextSlot.WAKE_UP to 14,
    )
    val hours = List(24) { h ->
        when (h) {
            7 -> mapOf(ContextSlot.WAKE_UP to 14)
            8 -> mapOf(ContextSlot.COMMUTE to 90)
            18 -> mapOf(ContextSlot.COMMUTE to 71, ContextSlot.MEAL to 30)
            12 -> mapOf(ContextSlot.MEAL to 51)
            10, 14 -> mapOf(ContextSlot.CLASS_WORK to 17)
            15, 20 -> mapOf(ContextSlot.BREAK to 57)
            22 -> mapOf(ContextSlot.PRE_SLEEP to 120)
            23 -> mapOf(ContextSlot.PRE_SLEEP to 88)
            0, 1 -> mapOf(ContextSlot.LATE_NIGHT to 30)
            else -> emptyMap()
        }
    }
    return WeekChartData(
        weekStart = LocalDate.of(2026, 10, 27),
        weekEndInclusive = LocalDate.of(2026, 11, 2),
        isDemo = true,
        totalMinutes = 672,
        sessionCount = 58,
        minutesByHour = hours,
        riskHours = listOf(22..23),
        heatmapDays = DayOfWeek.entries.toList(),
        heatmap = List(7) { d -> List(12) { c -> if (c == 11) 40 + d * 4 else (c * (d + 1)) % 9 * 3 } },
        riskCells = setOf(1 to 11, 2 to 11, 6 to 11),
        peak = PeakSummary(22, 208),
        contexts = contexts.map { (slot, min) -> ContextShare(slot, min, min / 672.0, min / 208f) },
        flow = FlowSummary(
            entries = listOf(
                "MESSENGER" to 22,
                "UNLOCK" to 14,
                "HOME" to 12,
                "SHORTFORM" to 6,
                FlowSummary.OTHER_GROUP to 4,
            ),
            apps = listOf("YOUTUBE" to 29, "INSTAGRAM" to 17, "TIKTOK" to 12),
            exits = listOf(
                "SCREEN_OFF" to 24,
                "MESSENGER" to 13,
                "HOME" to 11,
                "NUDGE_STOP" to 6,
                FlowSummary.OTHER_GROUP to 4,
            ),
            entryToApp = listOf(
                Triple("MESSENGER", "YOUTUBE", 12),
                Triple("MESSENGER", "INSTAGRAM", 10),
                Triple("UNLOCK", "YOUTUBE", 9),
                Triple("UNLOCK", "TIKTOK", 5),
                Triple("HOME", "YOUTUBE", 8),
                Triple("HOME", "INSTAGRAM", 4),
                Triple("SHORTFORM", "INSTAGRAM", 3),
                Triple("SHORTFORM", "TIKTOK", 3),
                Triple(FlowSummary.OTHER_GROUP, "TIKTOK", 4),
            ),
            appToExit = listOf(
                Triple("YOUTUBE", "SCREEN_OFF", 15),
                Triple("YOUTUBE", "MESSENGER", 8),
                Triple("YOUTUBE", "NUDGE_STOP", 6),
                Triple("INSTAGRAM", "HOME", 11),
                Triple("INSTAGRAM", "SCREEN_OFF", 6),
                Triple("TIKTOK", "MESSENGER", 5),
                Triple("TIKTOK", "SCREEN_OFF", 3),
                Triple("TIKTOK", FlowSummary.OTHER_GROUP, 4),
            ),
        ),
        longestEntry = EntryLengthSummary("MESSENGER", 22, 18),
        lengthShares = listOf(0.41, 0.26, 0.18, 0.11, 0.04),
        peer = PeerSummary(96, 118, 152, 125, 120, isDemo = true, scaleMaxMinutes = 240),
    )
}

@Preview(name = "light", heightDp = 2400)
@Preview(name = "dark", heightDp = 2400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportChartsPreview() {
    PauseTheme {
        Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md)) {
            ViewerTypeCard(previewReport(withAi = true), ReportLocal(), onAction = {})
            ReportCharts(previewCharts())
        }
    }
}
