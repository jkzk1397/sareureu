package com.capstone.pause.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression
import kotlin.math.roundToInt

/**
 * "이번 주 너는 밤 산책형"(목업 05 위쪽 어두운 카드). 유형은 코드(`ViewerTypeClassifier`)가 정하고,
 * 아래 한 줄은 코드가 센 밤 비중, 그 아래 AI 한 줄(있으면, 신고 가능).
 */
@Composable
internal fun ViewerTypeCard(report: WeeklyReportState, local: ReportLocal, onAction: (ReportAction) -> Unit) {
    val colors = PauseTheme.colors
    // 라이트는 잉크 바탕·밝은 글자, 다크는 표면 바탕(위험 시간대 카드와 같은 반전).
    val container = if (colors.isDark) colors.surface else colors.ink
    val onContainer = if (colors.isDark) colors.ink else colors.bg
    val nightShare = report.observations.firstOrNull { it.kind == "NIGHT_SHARE" }?.numbers?.get("share")
    val fill = rememberPlaceholderFiller(report)
    val ai = report.ai
    Column(
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .background(container, PauseTheme.shapes.card)
            .padding(PauseTheme.spacing.lg),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(BuddyExpression.SLEEPY, PauseSizes.bubbleBuddy, shadow = false)
            Column {
                Text(stringResource(R.string.report_type_title), style = PauseTheme.type.label, color = colors.accent)
                Text(
                    stringResource(viewerTypeRes(report.viewerType)),
                    style = PauseTheme.type.headline,
                    color = onContainer,
                )
                nightShare?.let {
                    Text(
                        stringResource(R.string.report_type_fact, (it * PERCENT).roundToInt()),
                        style = PauseTheme.type.bodySmall,
                        color = onContainer,
                    )
                }
            }
        }
        if (ai != null && "type" !in local.reportedKeys) {
            Text(fill(ai.viewerTypeLine), style = PauseTheme.type.bodySmall, color = onContainer)
            ReportButton { onAction(ReportAction.ReportSentence(ai.callId, "type")) }
        }
    }
}
