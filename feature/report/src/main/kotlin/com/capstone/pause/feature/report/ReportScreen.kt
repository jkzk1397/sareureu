package com.capstone.pause.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.designsystem.component.ChangeDirection
import com.capstone.pause.core.designsystem.component.DemoBadge
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.StatHero
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.theme.PauseTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** 리포트 탭 진입점. */
@Composable
fun ReportRoute(viewModel: ReportViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReportScreen(uiState = uiState, onAction = viewModel::onAction)
}

/**
 * 리포트 탭(상태 없음). 위에서부터(목업 05): 기간·요약 숫자 → 유형 → 일주일 시계 → 맥락별 합계 → 경로 → 세션 길이 →
 * 또래 비교 → 관찰(코드) → AI 주간 분석(추측).
 * 관찰과 추측은 다른 카드·다른 제목으로 나눈다(AGENTS: 관찰(코드)과 가설(AI)을 화면에서 분리).
 */
@Composable
fun ReportScreen(uiState: ReportUiState, onAction: (ReportAction) -> Unit, modifier: Modifier = Modifier) {
    PauseScreenColumn(title = stringResource(R.string.report_title), modifier = modifier) {
        val ready = uiState as? ReportUiState.Ready
        if (ready == null) {
            Text(stringResource(R.string.report_loading), style = PauseTheme.type.body, color = PauseTheme.colors.ink2)
            return@PauseScreenColumn
        }
        val report = ready.report
        WeekHeader(report)
        if (report.sessionCount == 0) {
            PauseCard(title = stringResource(R.string.report_no_data_title)) {
                Text(
                    stringResource(R.string.report_no_data_body),
                    style = PauseTheme.type.body,
                    color = PauseTheme.colors.ink2,
                )
            }
            return@PauseScreenColumn
        }
        StatRow(report)
        ViewerTypeCard(report = report, local = ready.local, onAction = onAction)
        ready.charts?.let { ReportCharts(it) }
        ObservationsCard(report)
        AiSection(report = report, local = ready.local, onAction = onAction)
    }
}

@Composable
private fun WeekHeader(report: WeeklyReportState) {
    Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                R.string.report_week_range,
                report.weekStart.monthValue,
                report.weekStart.dayOfMonth,
                report.weekEndInclusive.monthValue,
                report.weekEndInclusive.dayOfMonth,
            ),
            style = PauseTheme.type.label,
            color = PauseTheme.colors.ink2,
        )
        if (report.isDemo) DemoBadge()
    }
}

@Composable
private fun StatRow(report: WeeklyReportState) {
    Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
        PauseCard(modifier = Modifier.weight(1f)) {
            StatHero(
                value = durationLabel(report.avgMinPerDay.roundToInt()),
                caption = stringResource(R.string.report_stat_avg),
                valueStyle = TileValueStyle,
            )
        }
        PauseCard(modifier = Modifier.weight(1f)) {
            val rate = report.reductionRate
            if (rate == null) {
                StatHero(
                    value = stringResource(R.string.report_stat_reduction_measuring),
                    caption = stringResource(R.string.report_stat_reduction),
                    valueStyle = TileValueStyle,
                )
            } else {
                StatHero(
                    value = stringResource(R.string.report_percent, (abs(rate) * PERCENT).roundToInt()),
                    caption = stringResource(R.string.report_stat_reduction),
                    valueStyle = TileValueStyle,
                    direction = if (rate >= 0) ChangeDirection.DOWN else ChangeDirection.UP,
                    change = stringResource(if (rate >= 0) R.string.report_stat_less else R.string.report_stat_more),
                )
            }
        }
        PauseCard(modifier = Modifier.weight(1f)) {
            val stop = report.stopRate
            StatHero(
                value = if (stop == null) {
                    stringResource(R.string.report_stat_none)
                } else {
                    stringResource(R.string.report_percent, (stop * PERCENT).roundToInt())
                },
                caption = stringResource(R.string.report_stat_stop),
                valueStyle = TileValueStyle,
            )
        }
    }
}

@Composable
private fun ObservationsCard(report: WeeklyReportState) {
    PauseCard(title = stringResource(R.string.report_obs_title)) {
        Text(
            stringResource(R.string.report_obs_caption),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            report.observations.forEach { o ->
                observationText(o)?.let { text ->
                    Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
                        Text(o.id, style = PauseTheme.type.captionTabular, color = PauseTheme.colors.ink3)
                        Text(text, style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink)
                    }
                }
            }
        }
    }
}

/** 섹션 제목. */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = PauseTheme.type.title,
        color = PauseTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
}

internal const val PERCENT = 100

/** 세 칸 요약 숫자: 좁은 칸에서도 "1시간 36분"이 한 줄에 들어가게 본문 크기 + 굵게. */
private val TileValueStyle: TextStyle
    @Composable get() = PauseTheme.type.body.copy(fontWeight = FontWeight.Bold)
