package com.capstone.pause.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.ai.report.AiReport
import com.capstone.pause.core.ai.report.GenerationStatus
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.BuddyBubble
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.HypothesisCard
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.component.StrategyCard
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression

/** AI 주간 분석(추측) 묶음. 관찰 카드와 따로 그린다. 모든 AI 문장 옆에 '신고'가 있다. */
@Composable
internal fun AiSection(report: WeeklyReportState, local: ReportLocal, onAction: (ReportAction) -> Unit) {
    val colors = PauseTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
        ) {
            SectionTitle(stringResource(R.string.report_ai_title))
            val ai = report.ai
            if ((ai != null && ai.isMock) || (ai == null && !report.usingRealAi)) {
                Text(
                    stringResource(R.string.report_ai_mock_badge),
                    style = PauseTheme.type.caption,
                    color = colors.ink3,
                )
            }
        }
        Text(stringResource(R.string.report_ai_caption), style = PauseTheme.type.bodySmall, color = colors.ink2)
        val ai = report.ai
        when {
            ai != null -> AiBody(ai, report, local, onAction)

            !report.enoughForAi -> Text(
                stringResource(R.string.report_ai_not_enough),
                style = PauseTheme.type.body,
                color = colors.ink2,
            )

            report.status == GenerationStatus.RUNNING -> Text(
                stringResource(R.string.report_ai_making),
                style = PauseTheme.type.body,
                color = colors.ink2,
            )

            else -> PrimaryButton(text = stringResource(R.string.report_ai_make), onClick = {
                onAction(ReportAction.Generate(force = false))
            })
        }
        if (report.status == GenerationStatus.FAILED || local.notice == ReportNotice.FAILED) {
            Text(stringResource(R.string.report_ai_failed), style = PauseTheme.type.bodySmall, color = colors.nudgeText)
        }
        if (local.notice == ReportNotice.QUOTA) {
            Text(stringResource(R.string.report_ai_quota), style = PauseTheme.type.bodySmall, color = colors.ink2)
        }
        if (local.notice == ReportNotice.REPORTED) {
            Text(stringResource(R.string.report_ai_reported), style = PauseTheme.type.bodySmall, color = colors.ink2)
        }
    }
}

@Composable
private fun AiBody(ai: AiReport, report: WeeklyReportState, local: ReportLocal, onAction: (ReportAction) -> Unit) {
    val colors = PauseTheme.colors
    val hidden = local.reportedKeys
    val fill = rememberPlaceholderFiller(report)
    fun reportAction(key: String) = { onAction(ReportAction.ReportSentence(ai.callId, key)) }

    if ("summary" !in hidden) {
        AiLine(onReport = reportAction("summary")) {
            BuddyBubble(
                text = fill(ai.summaryLine),
                avatar = { BuddyAvatar(BuddyExpression.CHEER, PauseSizes.bubbleBuddy) },
            )
        }
    }
    PauseCard(title = stringResource(R.string.report_type_title)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(BuddyExpression.SLEEPY, PauseSizes.bubbleBuddy)
            Column {
                Text(
                    stringResource(viewerTypeRes(report.viewerType)),
                    style = PauseTheme.type.title,
                    color = colors.ink,
                )
                if ("type" !in
                    hidden
                ) {
                    Text(fill(ai.viewerTypeLine), style = PauseTheme.type.bodySmall, color = colors.ink2)
                }
            }
        }
        if ("type" !in hidden) ReportButton(reportAction("type"))
    }
    SectionTitle(stringResource(R.string.report_ai_hypotheses))
    ai.hypotheses.filter { it.id !in hidden }.forEach { h ->
        Column {
            HypothesisCard(
                text = fill(h.text),
                confidence = h.confidence,
                evidence = h.evidence.map { stringResource(R.string.report_evidence_chip, it.removePrefix("o")) },
                verdict = h.verdict,
                onVerdict = { onAction(ReportAction.SetVerdict(h.id, it)) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    fill(h.confirmQuestion),
                    style = PauseTheme.type.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                ReportButton(reportAction(h.id))
            }
        }
    }
    if (ai.strategies.isNotEmpty()) {
        SectionTitle(stringResource(R.string.report_ai_strategies))
        ai.strategies.filter { "s:${it.id}" !in hidden }.forEach { s ->
            Column {
                StrategyCard(
                    context = s.context,
                    ifText = s.ifText,
                    thenText = fill(s.thenText),
                    selected = s.id in local.pickedStrategies,
                    onToggle = { onAction(ReportAction.ToggleStrategy(s.id)) },
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        fill(s.why),
                        style = PauseTheme.type.bodySmall,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    ReportButton(reportAction("s:${s.id}"))
                }
            }
        }
        PrimaryButton(
            text = stringResource(if (local.savedPlan) R.string.report_ai_saved_plan else R.string.report_ai_save_plan),
            onClick = { onAction(ReportAction.SavePlans) },
            enabled = local.pickedStrategies.isNotEmpty(),
        )
    }
    ai.thresholds.filter { "t:${it.slot}" !in hidden }.forEach { t ->
        PauseCard(title = stringResource(R.string.report_ai_threshold_title)) {
            Text(
                stringResource(
                    R.string.report_ai_threshold_body,
                    stringResource(t.slot.labelRes()),
                    t.currentMinutes,
                    t.minutes,
                ),
                style = PauseTheme.type.body,
                color = colors.ink,
            )
            Text(fill(t.why), style = PauseTheme.type.bodySmall, color = colors.ink2)
            val accepted = t.slot in local.acceptedThresholds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
            ) {
                ChoicePill(
                    text = stringResource(
                        if (accepted) R.string.report_ai_threshold_done else R.string.report_ai_threshold_accept,
                    ),
                    selected = accepted,
                    onClick = { if (!accepted) onAction(ReportAction.AcceptThreshold(t.slot, t.minutes)) },
                )
                ReportButton(reportAction("t:${t.slot}"))
            }
        }
    }
    if (report.status != GenerationStatus.RUNNING && report.regenerationsLeft > 0) {
        TextButton(
            onClick = { onAction(ReportAction.Generate(force = true)) },
            modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(stringResource(R.string.report_ai_regenerate, report.regenerationsLeft), color = colors.ink2)
        }
    }
}

/** AI 문장 + 오른쪽 아래 '신고'. */
@Composable
private fun AiLine(onReport: () -> Unit, content: @Composable () -> Unit) {
    Column {
        content()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ReportButton(onReport) }
    }
}

/** 앱을 나가지 않고 누르는 '신고'(Play AI 생성 콘텐츠 정책). */
@Composable
private fun ReportButton(onClick: () -> Unit) {
    val description = stringResource(R.string.report_ai_report_cd)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = PauseTheme.spacing.minTouch)
            .semantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.report_ai_report), style = PauseTheme.type.caption, color = PauseTheme.colors.ink3)
    }
}
