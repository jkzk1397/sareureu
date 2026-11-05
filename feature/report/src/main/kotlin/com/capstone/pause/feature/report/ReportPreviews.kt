package com.capstone.pause.feature.report

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.ai.report.AiHypothesis
import com.capstone.pause.core.ai.report.AiReport
import com.capstone.pause.core.ai.report.AiStrategy
import com.capstone.pause.core.ai.report.AiThreshold
import com.capstone.pause.core.ai.report.GenerationStatus
import com.capstone.pause.core.ai.report.ReportObservation
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Verdict
import java.time.Instant
import java.time.LocalDate

/** 미리보기 값(목업 05·06 수치). */
internal fun previewReport(withAi: Boolean) = WeeklyReportState(
    weekStart = LocalDate.of(2026, 10, 27),
    weekEndInclusive = LocalDate.of(2026, 11, 2),
    isDemo = true,
    observations = listOf(
        ReportObservation(
            "o1",
            "WEEK_TOTAL",
            mapOf("totalMin" to 672.0, "avgMinPerDay" to 96.0, "sessions" to 58.0),
            emptyMap(),
        ),
        ReportObservation(
            "o3",
            "TOP_CONTEXT",
            mapOf("minutes" to 208.0, "share" to 0.31, "sessions" to 12.0),
            mapOf("context" to "PRE_SLEEP"),
        ),
        ReportObservation(
            "o10",
            "NUDGES",
            mapOf("shown" to 14.0, "stopped" to 6.0, "stopRate" to 0.43, "extensions" to 11.0),
            emptyMap(),
        ),
    ),
    totalMinutes = 672,
    avgMinPerDay = 96.0,
    reductionRate = 0.23,
    stopRate = 0.43,
    sessionCount = 58,
    viewerType = "NIGHT_OWL",
    enoughForAi = true,
    ai = if (!withAi) {
        null
    } else {
        AiReport(
            summaryLine = "이번 주는 자기 전이 제일 길었어. 그래도 기준선보다 23% 줄었어!",
            hypotheses = listOf(
                AiHypothesis(
                    "h1",
                    "자기 전엔 메신저 정리하다가 숏폼으로 넘어가는 것 같아",
                    Confidence.MEDIUM,
                    listOf("o3", "o12"),
                    "자기 전에 메신저 보다가 넘어간 거 맞아?",
                    Verdict.YES,
                ),
            ),
            strategies = listOf(AiStrategy("PRE_SLEEP_1", ContextSlot.PRE_SLEEP, "누우면", "폰은 책상 충전기에", "관찰 3과 연결된 전략")),
            viewerTypeLine = "밤이 되면 엄지가 바빠지는 밤 산책형!",
            thresholds = listOf(AiThreshold(ContextSlot.PRE_SLEEP, 10, 15, "자기 전 세션이 길어지는 편이야")),
            callId = 1,
            isMock = true,
            createdAt = Instant.EPOCH,
        )
    },
    status = GenerationStatus.IDLE,
    regenerationsLeft = 3,
    usingRealAi = false,
)

@Preview(name = "light", heightDp = 2000)
@Preview(name = "dark", heightDp = 2000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportScreenPreview() {
    PauseTheme {
        ReportScreen(
            uiState = ReportUiState.Ready(previewReport(withAi = true), ReportLocal(), previewCharts()),
            onAction = {},
        )
    }
}

@Preview(name = "light before ai")
@Preview(name = "dark before ai", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportScreenBeforeAiPreview() {
    PauseTheme {
        ReportScreen(uiState = ReportUiState.Ready(previewReport(withAi = false), ReportLocal()), onAction = {})
    }
}
