package com.capstone.pause.core.intervention.overlay

import androidx.compose.foundation.BorderStroke
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

/*
 * 모든 단계가 같은 순서다: 때 표시 → "N분째야" → 르르 한마디 → (그 단계의 입력) → 버튼.
 * 버튼도 같은 순서·무게다: '그만 볼래'(주 버튼) → 'N분만 더'(보조) → '오늘은 그만 알려 줘'(글자 버튼).
 * 단계 번호는 사용자에게 뜻이 없어 화면에 쓰지 않는다.
 */

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
            ContextChip(card.context)
            CardHeadline(card)
            CardBody(card.text)
            TypeToExtendField(card, breathLeft = 0, typed = typed, onTyped = { typed = it })
            CardButtons(card, onAction, canExtend = FrictionGate.canExtendTyped(0, typed, card.typeToExtend)) {
                NudgeUserAction.Extend(card.level, answer = typed)
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
        CardTop(card)
        if (card.reasonChips.isNotEmpty()) {
            Text(
                text = stringResource(R.string.intervention_l2_question),
                style = PauseTheme.type.label,
                color = PauseTheme.colors.ink,
            )
            // 고르면 알약에 ✓가 붙는다(눌렀다는 반응). 따로 답 문장을 더 보여 주지 않는다.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
            ) {
                card.reasonChips.forEach { reason ->
                    ChoicePill(text = reason.label, selected = chip == reason.key, onClick = { chip = reason.key })
                }
            }
        }
        val chosen = chip != null || card.reasonChips.isEmpty()
        if (chosen && card.breathSeconds > 0 && left > 0) BreathRing(left = left, total = card.breathSeconds)
        if (chosen) TypeToExtendField(card, breathLeft = left, typed = typed, onTyped = { typed = it })
        CardButtons(
            card,
            onAction,
            canExtend = chosen && FrictionGate.canExtendTyped(left, typed, card.typeToExtend),
        ) { NudgeUserAction.Extend(card.level, reasonChip = chip, answer = typed) }
    }
}

@Composable
private fun LevelThreeCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier) {
    val left = rememberBreathLeft(card)
    var typed by rememberSaveable(card.copyId, card.level) { mutableStateOf("") }
    NudgeCardSurface(modifier = modifier) {
        CardTop(card)
        if (left > 0) BreathRing(left = left, total = card.breathSeconds)
        TypeToExtendField(card, breathLeft = left, typed = typed, onTyped = { typed = it })
        CardButtons(card, onAction, canExtend = FrictionGate.canExtendTyped(left, typed, card.typeToExtend)) {
            NudgeUserAction.Extend(card.level, answer = typed)
        }
    }
}

@Composable
private fun LevelFourCard(card: CardContent, onAction: (NudgeUserAction) -> Unit, modifier: Modifier) {
    var answer by rememberSaveable(card.copyId, card.level) { mutableStateOf("") }
    val left = rememberBreathLeft(card)
    // 4단계의 한마디는 답을 받을 질문이다. 질문이 따로 있으면 그것만 보여 준다(같은 문장이 두 번 나오지 않게).
    val question = card.question ?: card.text.ifBlank { stringResource(R.string.intervention_l4_title) }
    NudgeCardSurface(modifier = modifier) {
        CardTop(card, body = question)
        if (card.breathSeconds > 0 && left > 0) BreathRing(left = left, total = card.breathSeconds)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            enabled = FrictionGate.inputEnabled(left),
            label = { Text(stringResource(R.string.intervention_l4_answer_label)) },
            supportingText = {
                Text(
                    stringResource(R.string.intervention_l4_hint, card.extendMinutes, FrictionGate.MIN_ANSWER_CHARS),
                    color = PauseTheme.colors.ink2,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        CardButtons(card, onAction, canExtend = FrictionGate.canExtendAnswered(answer, left)) {
            NudgeUserAction.Extend(card.level, answer = answer.trim())
        }
    }
}

/** 카드 윗부분(2~4단계 공용): 때 표시, 작은 르르 + "N분째야" + 한마디. */
@Composable
private fun CardTop(card: CardContent, body: String = card.text) {
    ContextChip(card.context)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
    ) {
        BuddyAvatar(expression = BuddyExpression.WORRIED, size = OverlayDimens.cardBuddySmall)
        Column(modifier = Modifier.weight(1f)) {
            CardHeadline(card)
            CardBody(body)
        }
    }
}

/** 버튼 세 개. 모든 단계에서 같은 순서: 그만(주) → N분만 더(보조) → 오늘은 그만 알려 줘(글자). */
@Composable
private fun CardButtons(
    card: CardContent,
    onAction: (NudgeUserAction) -> Unit,
    canExtend: Boolean,
    extend: () -> NudgeUserAction,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
        PrimaryButton(text = stringResource(R.string.intervention_stop), onClick = {
            onAction(NudgeUserAction.Stop(card.level))
        })
        ExtendButton(card, enabled = canExtend) { onAction(extend()) }
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
        Text(text = text, style = PauseTheme.type.body, color = PauseTheme.colors.ink)
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
            // 1초마다 바뀌는 숫자라 고정폭(숫자가 바뀔 때 좌우로 흔들리지 않게).
            Text(
                text = left.toString(),
                style = PauseTheme.type.display.copy(fontFeatureSettings = "tnum"),
                color = PauseTheme.colors.ink,
            )
            Text(
                text = stringResource(R.string.intervention_l3_seconds_left),
                style = PauseTheme.type.caption,
                color = PauseTheme.colors.ink2,
            )
        }
    }
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
