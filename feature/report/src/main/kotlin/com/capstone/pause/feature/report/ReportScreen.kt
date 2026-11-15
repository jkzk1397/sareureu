package com.capstone.pause.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.capstone.pause.core.designsystem.component.Reveal
import com.capstone.pause.core.designsystem.component.StatHero
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.designsystem.theme.isLargeFont
import kotlin.math.abs
import kotlin.math.roundToInt

/** 리포트 탭 진입점. */
@Composable
fun ReportRoute(
    focusPlans: Boolean = false,
    onFocusHandled: () -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReportScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        focusPlans = focusPlans,
        onFocusHandled = onFocusHandled,
    )
}

/**
 * 리포트 탭. 결론부터: 르르 한 줄 → 요약 숫자 → 유형 → 왜 그럴까(AI 추측 + 해 볼 것) → 언제·어떤 때 봤는지.
 * 나머지(보기 직전·끝, 한 번 길이, 또래 비교, 숫자 목록)는 '더 자세히'를 펼쳐야 보인다(펼침만 화면이 기억한다).
 * 관찰과 추측은 다른 카드·다른 제목으로 나눈다(AGENTS: 관찰(코드)과 가설(AI)을 화면에서 분리).
 */
@Composable
fun ReportScreen(
    uiState: ReportUiState,
    onAction: (ReportAction) -> Unit,
    modifier: Modifier = Modifier,
    focusPlans: Boolean = false,
    onFocusHandled: () -> Unit = {},
) {
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
        AiSummary(report = report, local = ready.local, onAction = onAction)
        StatRow(report)
        if (report.typeReady) {
            ViewerTypeCard(report = report, local = ready.local, onAction = onAction)
        } else {
            Text(
                stringResource(R.string.report_type_not_ready),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
        AiSection(
            report = report,
            local = ready.local,
            onAction = onAction,
            focusPlans = focusPlans,
            onFocusHandled = onFocusHandled,
        )
        ready.charts?.let { ReportMainCharts(it) }
        var details by rememberSaveable { mutableStateOf(false) }
        TextButton(
            onClick = { details = !details },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(
                stringResource(if (details) R.string.report_detail_hide else R.string.report_detail_show),
                style = PauseTheme.type.label,
                color = PauseTheme.colors.ink,
            )
        }
        Reveal(details) {
            Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md)) {
                ready.charts?.let { ReportDetailCharts(it) }
                ObservationsCard(report)
            }
        }
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
    TileGroup { tile ->
        StatHero(
            value = durationLabel(report.avgMinPerDay.roundToInt()),
            caption = stringResource(R.string.report_stat_avg),
            valueStyle = TileValueStyle,
            modifier = tile,
        )
        val rate = report.reductionRate
        if (rate == null) {
            StatHero(
                value = stringResource(R.string.report_stat_reduction_measuring),
                caption = stringResource(R.string.report_stat_reduction),
                valueStyle = TileValueStyle,
                modifier = tile,
            )
        } else {
            StatHero(
                value = stringResource(R.string.report_percent, (abs(rate) * PERCENT).roundToInt()),
                caption = stringResource(R.string.report_stat_reduction),
                valueStyle = TileValueStyle,
                direction = if (rate >= 0) ChangeDirection.DOWN else ChangeDirection.UP,
                change = stringResource(if (rate >= 0) R.string.report_stat_less else R.string.report_stat_more),
                modifier = tile,
            )
        }
        val stop = report.stopRate
        StatHero(
            value = if (stop == null) {
                stringResource(R.string.report_stat_none)
            } else {
                stringResource(R.string.report_percent, (stop * PERCENT).roundToInt())
            },
            caption = stringResource(R.string.report_stat_stop),
            valueStyle = TileValueStyle,
            modifier = tile,
        )
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
                observationText(o)?.let { Text(it, style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink) }
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

/**
 * 통계 칸 묶음: 카드 한 장 안에 세 칸. 칸마다 카드를 두면 좁은 폰(가로 360dp)에서 "1시간 21분"이 두 줄로 꺾인다.
 * 큰 글자에서는 세로로 쌓는다. [content]는 칸마다 받은 Modifier를 쓴다.
 */
@Composable
private fun TileGroup(content: @Composable (Modifier) -> Unit) {
    PauseCard {
        if (isLargeFont()) {
            Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md)) {
                content(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                content(Modifier.weight(1f))
            }
        }
    }
}
