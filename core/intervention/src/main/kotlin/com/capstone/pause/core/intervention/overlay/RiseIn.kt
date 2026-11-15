package com.capstone.pause.core.intervention.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.capstone.pause.core.designsystem.theme.rememberReducedMotion

/**
 * 숏폼 위 카드가 처음 뜰 때 아래에서 조금(16dp) 올라오며 나타난다(250ms, ease-out).
 * 하루에 몇 번 뜨는 것이라 움직임을 줘도 거슬리지 않고, 갑자기 화면을 덮는 느낌을 줄인다.
 * '애니메이션 제거'면 바로 보인다.
 */
@Composable
internal fun RiseIn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(RISE_MS, easing = EaseOut))
    }
    val rise = with(LocalDensity.current) { RISE_DP.dp.toPx() }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * rise
        },
    ) { content() }
}

private const val RISE_MS = 250
private const val RISE_DP = 16
private val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
