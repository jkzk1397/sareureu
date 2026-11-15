package com.capstone.pause.feature.coach

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

/** 대화 글 하나. AI 답이면 펼치는 근거·계획 제안·신고가 붙는다. */
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

/**
 * AI 답 아래: 계획 제안(있으면) → 한 줄에 '왜 이렇게 답했어?'(왼쪽)와 '신고'(오른쪽) → 펼친 근거 문장.
 * 근거는 전에는 누를 수 있게 생긴 칩인데 눌러도 반응이 없었다. 리포트의 가설 카드처럼 눌러서 문장으로 편다.
 */
@Composable
private fun ReplyExtras(details: ReplyDetails, onAction: (CoachAction) -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    var open by rememberSaveable(details.messageId) { mutableStateOf(false) }
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
                // 저장하면 버튼이 사라지고 이 글자만 남는다(다시 누를 수 없다).
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (details.evidence.isNotEmpty()) {
            SmallAction(
                text = stringResource(if (open) R.string.coach_evidence_hide else R.string.coach_evidence_show),
                color = colors.ink,
                onClick = { open = !open },
            )
        }
        Box(Modifier.weight(1f))
        if (details.reported) {
            Text(text = stringResource(R.string.coach_reported), style = PauseTheme.type.caption, color = colors.ink3)
        } else {
            val desc = stringResource(R.string.coach_report_desc)
            SmallAction(
                text = stringResource(R.string.coach_report),
                color = colors.ink3,
                onClick = { onAction(CoachAction.Report(details.messageId)) },
                modifier = Modifier.semantics { contentDescription = desc },
            )
        }
    }
    if (open && details.evidence.isNotEmpty()) {
        // 맨 아래 답에서 펼치면 펼친 부분이 입력창 뒤에 가려진다. 펼친 상자가 보이는 곳까지 따라 내려간다.
        val bring = remember { BringIntoViewRequester() }
        LaunchedEffect(Unit) { bring.bringIntoView() }
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bring)
                .background(colors.surfaceAlt, PauseTheme.shapes.small)
                .padding(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.coach_evidence_caption),
                style = PauseTheme.type.caption,
                color = colors.ink2,
            )
            details.evidence.forEach {
                Text(text = evidenceLabel(it), style = PauseTheme.type.bodySmall, color = colors.ink)
            }
        }
    }
}

/** 말풍선 안 작은 글자 버튼(48dp 터치 영역, 글자 왼쪽 끝은 말풍선 글과 맞춘다). */
@Composable
private fun SmallAction(text: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = PauseTheme.spacing.minTouch)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(text = text, style = PauseTheme.type.label, color = color)
    }
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
