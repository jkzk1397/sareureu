package com.capstone.pause.core.intervention.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.nudge.CardContent
import kotlinx.coroutines.delay

/** 카드의 숨 고르기 남은 초. 카드(문구·단계)가 바뀌면 다시 센다. 화면 회전에도 이어진다. */
@Composable
internal fun rememberBreathLeft(card: CardContent): Int {
    var left by rememberSaveable(card.copyId, card.level) { mutableIntStateOf(card.breathSeconds) }
    LaunchedEffect(card.copyId, card.level) {
        while (left > 0) {
            delay(SECOND_MS)
            left -= 1
        }
    }
    return left
}

/**
 * '5분만 더'를 누르기 전에 문장을 그대로 따라 쓰는 칸(ADR-0010). 숨 고르기 중에는 잠겨 있다.
 * [card]에 따라 쓸 문장이 없으면 아무것도 그리지 않는다.
 */
@Composable
internal fun TypeToExtendField(card: CardContent, breathLeft: Int, typed: String, onTyped: (String) -> Unit) {
    val target = card.typeToExtend ?: return
    val open = FrictionGate.inputEnabled(breathLeft)
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.intervention_l3_type_label),
            style = PauseTheme.type.label,
            color = PauseTheme.colors.ink,
        )
        Text(text = target, style = PauseTheme.type.title, color = PauseTheme.colors.ink)
        OutlinedTextField(
            value = typed,
            onValueChange = onTyped,
            enabled = open,
            placeholder = { Text(text = target, color = PauseTheme.colors.ink3) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (open) {
                stringResource(R.string.intervention_l3_hint_open, card.extendMinutes)
            } else {
                stringResource(R.string.intervention_breath_short, breathLeft)
            },
            style = PauseTheme.type.caption,
            color = PauseTheme.colors.ink2,
        )
    }
}

private const val SECOND_MS = 1_000L
