package com.capstone.pause.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.capstone.pause.core.data.insight.LiveSummary
import com.capstone.pause.core.data.insight.RiskSummary
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.component.iconRes
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.IfThenPlan
import com.capstone.pause.core.model.MeltStage

/** 보는 중 카드(목업 01 다크): 경과 링 안의 르르 + 경과 분 + 기준까지 남은 분 + '지금 그만할래'. */
@Composable
internal fun LiveCard(live: LiveSummary, melt: MeltStage, onStop: () -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, PauseTheme.shapes.card)
            .border(spacing.hairline * 2, colors.accent, PauseTheme.shapes.card)
            .padding(spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (live.elapsedMinutes.toFloat() / live.thresholdMinutes).coerceIn(0f, 1f) },
                    color = colors.accent,
                    trackColor = colors.surfaceAlt,
                    modifier = Modifier.size(PauseSizes.bubbleBuddy + spacing.sm),
                )
                BuddyAvatar(expression = BuddyExpression.WATCHING, size = LIVE_BUDDY, melt = melt)
            }
            Column {
                Text(
                    text = stringResource(
                        R.string.today_live_title,
                        stringResource(appNameRes(live.packageName)),
                        stringResource(live.context.labelRes()),
                    ),
                    style = PauseTheme.type.label,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.today_live_elapsed, live.elapsedMinutes),
                    style = PauseTheme.type.display,
                    color = colors.ink,
                )
                val left = live.thresholdMinutes - live.elapsedMinutes
                Text(
                    text = if (left >= 0) {
                        stringResource(R.string.today_live_left, live.thresholdMinutes, left)
                    } else {
                        stringResource(R.string.today_live_over, live.thresholdMinutes, -left)
                    },
                    style = PauseTheme.type.bodySmall,
                    color = colors.ink2,
                )
            }
        }
        PrimaryButton(text = stringResource(R.string.today_live_stop), onClick = onStop)
    }
}

/** 위험 시간대 카드: 잉크 바탕(라이트) + 라임 원 안의 달. */
@Composable
internal fun RiskCard(risk: RiskSummary) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    // 라이트는 잉크 바탕·밝은 글자, 다크는 표면 바탕·잉크 글자(토큰 반전).
    val container = if (colors.isDark) colors.surface else colors.ink
    val onContainer = if (colors.isDark) colors.ink else colors.bg
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .background(container, PauseTheme.shapes.card)
            .padding(spacing.lg),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PauseSizes.bubbleBuddy)
                .background(colors.accent, PauseTheme.shapes.pill),
        ) {
            Icon(
                painter = painterResource(ContextSlot.LATE_NIGHT.iconRes()),
                contentDescription = null,
                tint = PauseColorsInk,
            )
        }
        Column {
            Text(
                text = stringResource(R.string.today_risk_title, hourLabel(risk.hour)),
                style = PauseTheme.type.title,
                color = onContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    R.string.today_risk_body,
                    dayName(risk.dayOfWeek),
                    stringResource(periodRes(risk.hour)),
                    durationLabel(risk.averageMinutes),
                ),
                style = PauseTheme.type.bodySmall,
                color = onContainer,
            )
        }
    }
}

/** 받은 잔소리 카드. 내용은 숨기고 '맡겨 뒀다'만 알린다(숏폼 2단계에서 꺼냄). */
@Composable
internal fun PokeCard(poke: ReceivedPoke, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    SmallCard(modifier = modifier, container = colors.surface, bordered = true) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(PauseSizes.rankAvatar)
                    .background(colors.social, PauseTheme.shapes.pill),
            ) {
                Text(text = poke.fromNickname.take(1), style = PauseTheme.type.label, color = colors.surface)
            }
            Text(text = stringResource(R.string.today_poke_title), style = PauseTheme.type.label, color = colors.social)
        }
        val body = if (hasFinalConsonant(poke.fromNickname)) R.string.today_poke_body_i else R.string.today_poke_body_ga
        Text(text = stringResource(body, poke.fromNickname), style = PauseTheme.type.body, color = colors.ink)
    }
}

/** 내 계획 카드(부드러운 라임 바탕). */
@Composable
internal fun PlanCard(plan: IfThenPlan, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    SmallCard(modifier = modifier, container = colors.accentContainer, bordered = false) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        ) {
            Icon(
                painter = painterResource((plan.context ?: ContextSlot.OTHER).iconRes()),
                contentDescription = null,
                tint = colors.onAccentContainer,
                modifier = Modifier.size(PauseSizes.iconSmall),
            )
            Text(
                text = stringResource(R.string.today_plan_title),
                style = PauseTheme.type.label,
                color = colors.onAccentContainer,
            )
        }
        Text(
            text = stringResource(R.string.today_plan_body, plan.ifText, plan.thenText),
            style = PauseTheme.type.body,
            color = colors.onAccentContainer,
        )
    }
}

@Composable
private fun SmallCard(
    modifier: Modifier,
    container: androidx.compose.ui.graphics.Color,
    bordered: Boolean,
    content: @Composable () -> Unit,
) {
    val spacing = PauseTheme.spacing
    val base = modifier
        .heightIn(min = PauseSizes.bubbleBuddy * 2)
        .background(container, PauseTheme.shapes.card)
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = (
            if (bordered) {
                base.border(
                    spacing.hairline,
                    PauseTheme.colors.line,
                    PauseTheme.shapes.card,
                )
            } else {
                base
            }
            )
            .padding(spacing.md),
    ) { content() }
}

private val PauseColorsInk = com.capstone.pause.core.designsystem.theme.PauseColors.BuddyInk
private val LIVE_BUDDY: Dp = PauseSizes.liveBuddy
