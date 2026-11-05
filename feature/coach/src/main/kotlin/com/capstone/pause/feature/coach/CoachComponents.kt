package com.capstone.pause.feature.coach

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.capstone.pause.core.ai.coach.ReplyDetails
import com.capstone.pause.core.ai.engine.safety.HelpLine
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression

/** 말풍선 최대 폭(화면 폭의 대부분). */
private val BubbleMaxWidth = 300.dp

/** 채팅 아바타(06 문서 8절: 28dp). */
private val AvatarSize = 28.dp

/** 대화 글 하나. AI 답이면 근거 칩·계획 제안·신고가 붙는다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatRow(item: ChatItem, onAction: (CoachAction) -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val mine = item.from is ChatItem.From.Me
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (item.wasAlert) {
            val sender = when (val f = item.from) {
                is ChatItem.From.Friend -> f.nickname
                else -> stringResource(DesignR.string.buddy_name)
            }
            Text(
                text = stringResource(R.string.coach_alert_caption, sender) + "  " + timeLabel(item.at),
                style = PauseTheme.type.caption,
                color = colors.ink3,
                modifier = Modifier.padding(start = AvatarSize + spacing.xs, bottom = spacing.xxs),
            )
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (!mine) {
                Box(Modifier.padding(top = spacing.xxs)) {
                    BuddyAvatar(expression = BuddyExpression.NEUTRAL, size = AvatarSize)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier
                    .widthIn(max = BubbleMaxWidth)
                    .background(
                        when {
                            mine -> colors.accent
                            item.from is ChatItem.From.Friend -> colors.surfaceAlt
                            else -> colors.surface
                        },
                        PauseTheme.shapes.card,
                    )
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
            ) {
                if (item.from is ChatItem.From.Friend) {
                    Text(text = item.from.nickname, style = PauseTheme.type.label, color = colors.social)
                }
                Text(
                    text = item.text,
                    style = PauseTheme.type.body,
                    color = if (mine) PauseColorsInk else colors.ink,
                )
                item.details?.let { ReplyExtras(it, onAction) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyExtras(details: ReplyDetails, onAction: (CoachAction) -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    if (details.evidence.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            details.evidence.forEach { EvidenceTag(evidenceLabel(it)) }
        }
    }
    details.planProposal?.let { plan ->
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentContainer, PauseTheme.shapes.card)
                .padding(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.coach_plan_title),
                style = PauseTheme.type.label,
                color = colors.onAccentContainer,
            )
            Text(
                text = stringResource(R.string.coach_plan_body, plan.ifText, plan.thenText),
                style = PauseTheme.type.body,
                color = colors.onAccentContainer,
            )
            if (details.planSaved) {
                Text(
                    text = stringResource(R.string.coach_plan_saved),
                    style = PauseTheme.type.label,
                    color = colors.onAccentContainer,
                )
            } else {
                PrimaryButton(
                    text = stringResource(R.string.coach_plan_save),
                    onClick = { onAction(CoachAction.SavePlan(details.messageId)) },
                )
            }
        }
    }
    if (details.reported) {
        Text(text = stringResource(R.string.coach_reported), style = PauseTheme.type.caption, color = colors.ink3)
    } else {
        val desc = stringResource(R.string.coach_report_desc)
        TextButton(
            onClick = { onAction(CoachAction.Report(details.messageId)) },
            modifier = Modifier
                .heightIn(min = spacing.minTouch)
                .semantics { contentDescription = desc },
        ) {
            Text(text = stringResource(R.string.coach_report), style = PauseTheme.type.caption, color = colors.ink3)
        }
    }
}

/** 근거 칩(읽기만, 누르지 않음). */
@Composable
private fun EvidenceTag(text: String) {
    val colors = PauseTheme.colors
    Text(
        text = text,
        style = PauseTheme.type.label,
        color = colors.ink,
        modifier = Modifier
            .border(PauseTheme.spacing.hairline, colors.line, PauseTheme.shapes.pill)
            .padding(horizontal = PauseTheme.spacing.sm, vertical = PauseTheme.spacing.xxs),
    )
}

/** 위기 표현 도움 카드: AI를 멈추고 상담 전화로 안내한다. */
@Composable
internal fun CrisisCard(lines: List<HelpLine>, onClose: () -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, PauseTheme.shapes.card)
            .border(spacing.hairline, colors.line, PauseTheme.shapes.card)
            .padding(spacing.lg),
    ) {
        Text(text = stringResource(R.string.coach_crisis_title), style = PauseTheme.type.title, color = colors.ink)
        Text(text = stringResource(R.string.coach_crisis_body), style = PauseTheme.type.body, color = colors.ink2)
        lines.forEach { line ->
            val label = stringResource(
                if (line.key == "suicide_prevention") R.string.coach_crisis_suicide else R.string.coach_crisis_mental,
            )
            PrimaryButton(
                text = label,
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_DIAL,
                                Uri.parse("tel:${line.number}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
        TextButton(onClick = onClose, modifier = Modifier.heightIn(min = spacing.minTouch)) {
            Text(text = stringResource(R.string.coach_crisis_close), color = colors.ink2)
        }
    }
}

/** 추천 질문 칩 줄. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionRow(suggestions: List<String>, enabled: Boolean, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        suggestions.forEach { s ->
            com.capstone.pause.core.designsystem.component.ChoicePill(
                text = s,
                selected = false,
                onClick = { if (enabled) onPick(s) },
            )
        }
    }
}

private val PauseColorsInk = com.capstone.pause.core.designsystem.theme.PauseColors.BuddyInk
