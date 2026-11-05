package com.capstone.pause.core.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Verdict

/** 르르 말풍선(채팅·카드·오늘 인사 공용). [avatar]를 바꿔 끼울 수 있다. */
@Composable
fun BuddyBubble(
    text: String,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit = { BuddyAvatar(BuddyExpression.NEUTRAL, PauseSizes.bubbleBuddy) },
) {
    val colors = PauseTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
    ) {
        avatar()
        Text(
            text = text,
            style = PauseTheme.type.body,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .background(colors.surface, PauseTheme.shapes.card)
                .border(PauseTheme.spacing.hairline, colors.line, PauseTheme.shapes.card)
                .padding(horizontal = PauseTheme.spacing.lg, vertical = PauseTheme.spacing.md),
        )
    }
}

/** AI 문장 옆 근거 칩("화 23:40 · 쇼츠 32분"). 탭하면 그 세션·통계로. */
@Composable
fun EvidenceChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = PauseTheme.type.label,
        color = PauseTheme.colors.ink2,
        modifier = modifier
            .heightIn(min = PauseSizes.chipMinHeight)
            .background(PauseTheme.colors.surfaceAlt, PauseTheme.shapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = PauseTheme.spacing.sm, vertical = PauseTheme.spacing.xs),
    )
}

/** 가설 카드: 문장 + 확신도(글자) + 근거 칩 + 맞아/반쯤/아니야. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HypothesisCard(
    text: String,
    confidence: Confidence,
    evidence: List<String>,
    verdict: Verdict?,
    onVerdict: (Verdict) -> Unit,
    modifier: Modifier = Modifier,
    onEvidenceClick: (Int) -> Unit = {},
) {
    PauseCard(modifier = modifier) {
        Text(
            text = stringResource(confidence.labelRes()),
            style = PauseTheme.type.label,
            color = PauseTheme.colors.ink2,
        )
        Text(text = text, style = PauseTheme.type.body, color = PauseTheme.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            evidence.forEachIndexed { i, e -> EvidenceChip(e, onClick = { onEvidenceClick(i) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            Verdict.entries.forEach { v ->
                ChoicePill(
                    text = stringResource(v.labelRes()),
                    selected = verdict == v,
                    onClick = { onVerdict(v) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 고를 수 있는 알약 버튼(선택되면 라임 바탕). */
@Composable
fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    Text(
        // 고른 상태를 색만으로 알리지 않는다(AGENTS.md 6절): 체크 표시를 함께 붙인다.
        text = if (selected) stringResource(R.string.designsystem_selected_prefix, text) else text,
        style = PauseTheme.type.label,
        color = if (selected) colors.onAccentContainer else colors.ink,
        modifier = modifier
            .heightIn(min = PauseTheme.spacing.minTouch)
            .background(if (selected) colors.accentContainer else colors.surfaceAlt, PauseTheme.shapes.pill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = PauseTheme.spacing.md, vertical = PauseTheme.spacing.sm),
    )
}

/** 대처 전략 카드: 맥락 아이콘 + 만약~하면 + 고르기. */
@Composable
fun StrategyCard(
    context: ContextSlot?,
    ifText: String,
    thenText: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PauseTheme.colors
    PauseCard(modifier = modifier, containerColor = if (selected) colors.accentContainer else colors.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        ) {
            if (context != null) {
                Icon(
                    painter = painterResource(context.iconRes()),
                    contentDescription = stringResource(context.labelRes()),
                    tint = colors.ink,
                    modifier = Modifier.size(PauseSizes.icon),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.designsystem_strategy_if, ifText),
                    style = PauseTheme.type.body,
                    color = colors.ink,
                )
                Text(
                    stringResource(R.string.designsystem_strategy_then, thenText),
                    style = PauseTheme.type.bodySmall,
                    color = colors.ink2,
                )
            }
        }
        ChoicePill(
            text = stringResource(
                if (selected) R.string.designsystem_strategy_picked else R.string.designsystem_strategy_pick,
            ),
            selected = selected,
            onClick = onToggle,
        )
    }
}

/** 확신도 글자. */
fun Confidence.labelRes(): Int = when (this) {
    Confidence.HIGH -> R.string.designsystem_confidence_high
    Confidence.MEDIUM -> R.string.designsystem_confidence_medium
    Confidence.LOW -> R.string.designsystem_confidence_low
}

/** 판정 글자. */
fun Verdict.labelRes(): Int = when (this) {
    Verdict.YES -> R.string.designsystem_verdict_yes
    Verdict.PARTLY -> R.string.designsystem_verdict_partly
    Verdict.NO -> R.string.designsystem_verdict_no
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InsightComponentsPreview() {
    PauseTheme {
        Column(
            Modifier
                .background(PauseTheme.colors.bg)
                .padding(PauseTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyBubble(text = "오늘 알림 3번 중 2번 멈췄어!")
            HypothesisCard(
                text = "메신저 답장 뒤에 숏폼으로 넘어가는 것 같아",
                confidence = Confidence.MEDIUM,
                evidence = listOf("화 23:40 · 쇼츠 32분", "o3"),
                verdict = Verdict.YES,
                onVerdict = {},
            )
            StrategyCard(ContextSlot.PRE_SLEEP, "누우면", "폰은 책상 충전기에", selected = true, onToggle = {})
        }
    }
}
