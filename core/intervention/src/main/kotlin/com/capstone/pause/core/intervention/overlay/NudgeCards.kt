package com.capstone.pause.core.intervention.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.ContextChip
import com.capstone.pause.core.designsystem.component.NudgeCardSurface
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.nudge.CardContent
import com.capstone.pause.core.intervention.nudge.NudgeUserAction
import com.capstone.pause.core.model.BuddyExpression
import kotlinx.coroutines.delay

/** 단계에 맞는 카드(목업 02·03·04). '그만'은 모든 단계에서 한 번 탭. */
@Composable
fun NudgeCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier = Modifier) {
    when (card.level) {
        1 -> LevelOneCard(card, onAction, modifier)
        2 -> LevelTwoCard(card, onAction, modifier)
        3 -> LevelThreeCard(card, onAction, modifier)
        else -> LevelFourCard(card, onAction, modifier)
    }
}

/** '그만' 직후 칭찬(칭찬 표정). */
@Composable
fun PraiseCard(text: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    NudgeCardSurface(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(expression = BuddyExpression.CHEER, size = OverlayDimens.cardBuddySmall)
            Text(
                text = text,
                style = PauseTheme.type.title,
                color = PauseTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.End).heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(text = stringResource(R.string.intervention_close), color = PauseTheme.colors.ink2)
        }
    }
}

@Composable
private fun LevelOneCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier) {
    var typed by rememberSaveable(card.copyId, card.level) { mutableStateOf("") }
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        BuddyAvatar(
            expression = BuddyExpression.CALLING,
            size = OverlayDimens.cardBuddyLarge,
            modifier = Modifier.padding(end = PauseTheme.spacing.xl),
        )
        NudgeCardSurface {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
            ) {
                ContextChip(card.context)
            }
            CardHeadline(card)
            CardBody(card.text)
            StopButton(card, onAction)
            TypeToExtendField(card, breathLeft = 0, typed = typed, onTyped = { typed = it })
            ExtendButton(card, enabled = FrictionGate.canExtendTyped(0, typed, card.typeToExtend)) {
                onAction(NudgeUserAction.Extend(card.level, answer = typed))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelTwoCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier) {
    var chip by rememberSaveable(card.copyId, card.level) { mutableStateOf<String?>(null) }
    var typed by rememberSaveable(card.copyId, card.level) { mutableStateOf("") }
    val left = rememberBreathLeft(card)
    NudgeCardSurface(modifier = modifier) {
        LevelBadge(card)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(expression = BuddyExpression.WORRIED, size = OverlayDimens.cardBuddySmall)
            Column(modifier = Modifier.weight(1f)) {
                CardHeadline(card)
                CardBody(card.text)
            }
        }
        Text(
            text = stringResource(R.string.intervention_l2_question),
            style = PauseTheme.type.title,
            color = PauseTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        ) {
            card.reasonChips.forEach { reason ->
                ChoicePill(text = reason.label, selected = chip == reason.key, onClick = { chip = reason.key })
            }
        }
        if (chip != null) {
            Text(
                text = stringResource(R.string.intervention_l2_reply),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        StopButton(card, onAction)
        val chosen = chip != null || card.reasonChips.isEmpty()
        if (chosen && card.breathSeconds > 0 && left > 0) BreathRing(left = left, total = card.breathSeconds)
        if (chosen) TypeToExtendField(card, breathLeft = left, typed = typed, onTyped = { typed = it })
        ExtendButton(card, enabled = chosen && FrictionGate.canExtendTyped(left, typed, card.typeToExtend)) {
            onAction(NudgeUserAction.Extend(card.level, reasonChip = chip, answer = typed))
        }
        SnoozeLink(card, onAction)
    }
}

@Composable
private fun LevelThreeCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier) {
    val left = rememberBreathLeft(card)
    var typed by rememberSaveable(card.copyId, card.level) { mutableStateOf("") }
    NudgeCardSurface(modifier = modifier) {
        LevelBadge(card)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(expression = BuddyExpression.WORRIED, size = OverlayDimens.cardBuddySmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.intervention_l3_title),
                    style = PauseTheme.type.title,
                    color = PauseTheme.colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.intervention_l3_subtitle, card.breathSeconds),
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
            }
        }
        BreathRing(left = left, total = card.breathSeconds)
        CardBody(card.text)
        StopButton(card, onAction)
        TypeToExtendField(card, breathLeft = left, typed = typed, onTyped = { typed = it })
        ExtendButton(card, enabled = FrictionGate.canExtendTyped(left, typed, card.typeToExtend)) {
            onAction(NudgeUserAction.Extend(card.level, answer = typed))
        }
        SnoozeLink(card, onAction)
    }
}

@Composable
private fun LevelFourCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier) {
    var answer by rememberSaveable(card.copyId, card.level) { mutableStateOf("") }
    val left = rememberBreathLeft(card)
    NudgeCardSurface(modifier = modifier) {
        LevelBadge(card)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
        ) {
            BuddyAvatar(expression = BuddyExpression.WORRIED, size = OverlayDimens.cardBuddySmall)
            CardHeadline(card, Modifier.weight(1f))
        }
        CardBody(card.text)
        Text(
            text = card.question ?: stringResource(R.string.intervention_l4_title),
            style = PauseTheme.type.title,
            color = PauseTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (card.breathSeconds > 0) BreathRing(left = left, total = card.breathSeconds)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            enabled = FrictionGate.inputEnabled(left),
            label = { Text(stringResource(R.string.intervention_l4_answer_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.intervention_l4_hint, card.extendMinutes, FrictionGate.MIN_ANSWER_CHARS),
            style = PauseTheme.type.caption,
            color = PauseTheme.colors.ink2,
        )
        StopButton(card, onAction)
        ExtendButton(card, enabled = FrictionGate.canExtendAnswered(answer, left)) {
            onAction(NudgeUserAction.Extend(card.level, answer = answer.trim()))
        }
        SnoozeLink(card, onAction)
    }
}

@Composable
private fun CardHeadline(card: CardContent, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.intervention_card_title, card.appLabel, card.elapsedMinutes),
        style = PauseTheme.type.headline,
        color = PauseTheme.colors.ink,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun CardBody(text: String) {
    if (text.isNotBlank()) {
        Text(text = text, style = PauseTheme.type.body, color = PauseTheme.colors.ink2)
    }
}

@Composable
private fun LevelBadge(card: CardContent) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.intervention_level_badge, card.level),
            style = PauseTheme.type.label,
            color = PauseTheme.colors.ink2,
            modifier = Modifier
                .background(PauseTheme.colors.surfaceAlt, PauseTheme.shapes.pill)
                .padding(horizontal = PauseTheme.spacing.sm, vertical = PauseTheme.spacing.xxs),
        )
        ContextChip(card.context)
    }
}

@Composable
private fun BreathRing(left: Int, total: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator(
            progress = { if (total <= 0) 0f else left.toFloat() / total },
            color = PauseTheme.colors.ink,
            trackColor = PauseTheme.colors.surfaceAlt,
            strokeWidth = OverlayDimens.breathStroke,
            modifier = Modifier.size(OverlayDimens.breathRing),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.semantics {
                liveRegion =
                    LiveRegionMode.Polite
            },
        ) {
            Text(text = left.toString(), style = PauseTheme.type.display, color = PauseTheme.colors.ink)
            Text(
                text = stringResource(R.string.intervention_l3_seconds_left),
                style = PauseTheme.type.caption,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun StopButton(card: CardContent, onAction: (NudgeUserAction) -> Unit) {
    PrimaryButton(text = stringResource(R.string.intervention_stop), onClick = {
        onAction(NudgeUserAction.Stop(card.level))
    })
}

@Composable
private fun ExtendButton(card: CardContent, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PauseTheme.shapes.pill,
        border = BorderStroke(PauseTheme.spacing.hairline, PauseTheme.colors.line),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(
            text = stringResource(R.string.intervention_extend, card.extendMinutes),
            style = PauseTheme.type.label,
            color = if (enabled) PauseTheme.colors.ink else PauseTheme.colors.ink3,
        )
    }
}

@Composable
private fun SnoozeLink(card: CardContent, onAction: (NudgeUserAction) -> Unit) {
    if (!card.canSnoozeToday) return
    TextButton(
        onClick = { onAction(NudgeUserAction.SnoozeToday(card.level)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(
            text = stringResource(R.string.intervention_snooze_today),
            style = PauseTheme.type.label,
            color = PauseTheme.colors.ink2,
        )
    }
}
