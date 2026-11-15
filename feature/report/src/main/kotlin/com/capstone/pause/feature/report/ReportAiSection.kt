package com.capstone.pause.feature.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.ai.report.AiHypothesis
import com.capstone.pause.core.ai.report.AiReport
import com.capstone.pause.core.ai.report.GenerationStatus
import com.capstone.pause.core.ai.report.WeeklyReportState
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.BuddyBubble
import com.capstone.pause.core.designsystem.component.CardFooter
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.component.StrategyCard
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.Verdict

/** 화면 맨 위 르르 한 줄(AI 요약). 분석이 아직 없거나 신고한 문장이면 그리지 않는다. */
@Composable
internal fun AiSummary(report: WeeklyReportState, local: ReportLocal, onAction: (ReportAction) -> Unit) {
    val ai = report.ai ?: return
    if ("summary" in local.reportedKeys) return
    val fill = rememberPlaceholderFiller(report)
    AiLine(onReport = { onAction(ReportAction.ReportSentence(ai.callId, "summary")) }) {
        BuddyBubble(
            text = fill(ai.summaryLine),
            avatar = { BuddyAvatar(BuddyExpression.CHEER, PauseSizes.bubbleBuddy) },
        )
    }
}

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

    val observations = report.observations.associateBy { it.id }
    ai.hypotheses.filter { it.id !in hidden }.forEach { h ->
        HypothesisItem(
            hypothesis = h,
            text = fill(h.text),
            question = fill(h.confirmQuestion),
            evidence = h.evidence.mapNotNull { id -> observations[id]?.let { observationText(it) } },
            onVerdict = { onAction(ReportAction.SetVerdict(h.id, it)) },
            onReport = reportAction(h.id),
        )
    }
    if (ai.strategies.isNotEmpty()) {
        SectionTitle(stringResource(R.string.report_ai_strategies))
        ai.strategies.filter { "s:${it.id}" !in hidden }.forEach { s ->
            StrategyCard(
                context = s.context,
                ifText = s.ifText,
                thenText = fill(s.thenText),
                selected = s.id in local.pickedStrategies,
                onToggle = { onAction(ReportAction.ToggleStrategy(s.id)) },
                footer = { CardFooter(fill(s.why)) { ReportButton(reportAction("s:${s.id}")) } },
            )
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(stringResource(R.string.report_ai_regenerate, report.regenerationsLeft), color = colors.ink2)
        }
    }
}

/**
 * 가설 카드: 얼마나 확실한지(쉬운 말) → 문장 → 확인 질문 → 맞아/반쯤/아니야 → '왜 이렇게 생각했어?'.
 * 근거는 관찰 번호 대신 그 관찰의 문장을 펼쳐 보여 준다(코드가 센 기록이라고 따로 밝힌다).
 */
@Composable
private fun HypothesisItem(
    hypothesis: AiHypothesis,
    text: String,
    question: String,
    evidence: List<String>,
    onVerdict: (Verdict) -> Unit,
    onReport: () -> Unit,
) {
    val colors = PauseTheme.colors
    var open by rememberSaveable(hypothesis.id) { mutableStateOf(false) }
    PauseCard {
        Text(stringResource(confidenceRes(hypothesis.confidence)), style = PauseTheme.type.label, color = colors.ink2)
        Text(text, style = PauseTheme.type.body, color = colors.ink)
        if (question.isNotBlank()) Text(question, style = PauseTheme.type.bodySmall, color = colors.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            Verdict.entries.forEach { v ->
                ChoicePill(
                    text = stringResource(v.labelRes()),
                    selected = hypothesis.verdict == v,
                    onClick = { onVerdict(v) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (evidence.isNotEmpty()) {
                // 글자 왼쪽 끝을 카드 안 문장과 맞춘다(TextButton은 안쪽 여백 때문에 들여 써진다).
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .heightIn(min = PauseTheme.spacing.minTouch)
                        .clickable(role = Role.Button) { open = !open },
                ) {
                    Text(
                        stringResource(if (open) R.string.report_evidence_hide else R.string.report_evidence_show),
                        style = PauseTheme.type.label,
                        color = colors.ink,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            ReportButton(onReport)
        }
        if (open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceAlt, PauseTheme.shapes.small)
                    .padding(PauseTheme.spacing.md),
            ) {
                Text(
                    stringResource(R.string.report_evidence_caption),
                    style = PauseTheme.type.caption,
                    color = colors.ink2,
                )
                evidence.forEach { Text(it, style = PauseTheme.type.bodySmall, color = colors.ink) }
            }
        }
    }
}

private fun confidenceRes(confidence: Confidence): Int = when (confidence) {
    Confidence.HIGH -> R.string.report_confidence_high
    Confidence.MEDIUM -> R.string.report_confidence_medium
    Confidence.LOW -> R.string.report_confidence_low
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
internal fun ReportButton(onClick: () -> Unit) {
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
