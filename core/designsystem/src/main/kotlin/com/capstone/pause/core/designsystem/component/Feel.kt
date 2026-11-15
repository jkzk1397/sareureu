package com.capstone.pause.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.designsystem.theme.rememberReducedMotion

/*
 * 화면이 '잘 만든 것처럼' 느껴지게 하는 작은 규칙들(06 문서 4절, jakub.kr "make-interfaces-feel-better"를 Compose로 옮김).
 * - 깊이는 테두리가 아니라 그림자로: 바탕 위에 뜬 카드는 [pauseSurface]. 테두리는 구분선·선택 상태에만 쓴다.
 * - 누르면 살짝 작아진다: [pressScale] 0.96(0.95 아래로는 줄이지 않는다).
 * - 펼치기는 끊기지 않게: [Reveal]. 자주 누르는 것이라 짧게(펼침 200ms, 접힘 150ms), ease-out.
 * '애니메이션 제거'(시스템 설정)이면 모두 바로 바뀐다.
 */

/** 누를 때 줄어드는 비율. 0.95보다 작으면 장난감처럼 보여서 이 값으로 고정한다. */
private const val PRESSED_SCALE = 0.96f

/** 누름·펼침 길이(ms). 자주 쓰는 동작이라 150ms 안팎. */
private const val PRESS_MS = 120
private const val REVEAL_IN_MS = 200
private const val REVEAL_OUT_MS = 150

/** ease-out(빨리 시작해 부드럽게 멈춤). */
private val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * 바탕 위에 뜬 표면(카드·말풍선). 라이트는 아주 옅은 테두리 한 겹 + 그림자 두 겹으로 깊이를 내고,
 * 다크는 그림자가 보이지 않으므로 밝은 테두리 한 겹만 둔다. [color]는 표면 색.
 */
@Composable
fun Modifier.pauseSurface(shape: Shape, color: Color = PauseTheme.colors.surface): Modifier {
    val colors = PauseTheme.colors
    return if (colors.isDark) {
        this
            .background(color, shape)
            .border(PauseTheme.spacing.hairline, Color.White.copy(alpha = DARK_RING), shape)
    } else {
        this
            .dropShadow(
                shape,
                Shadow(radius = 4.dp, color = Color.Black.copy(alpha = SOFT), offset = DpOffset(0.dp, 2.dp)),
            )
            .dropShadow(
                shape,
                Shadow(
                    radius = 2.dp,
                    color = Color.Black.copy(alpha = TIGHT),
                    offset = DpOffset(0.dp, 1.dp),
                    spread = (-1).dp,
                ),
            )
            .background(color, shape)
            .border(PauseTheme.spacing.hairline, Color.Black.copy(alpha = LIGHT_RING), shape)
    }
}

private const val LIGHT_RING = 0.06f
private const val TIGHT = 0.06f
private const val SOFT = 0.04f
private const val DARK_RING = 0.08f

/** 누르는 동안 [PRESSED_SCALE]로 줄어든다. 같은 [interactionSource]를 클릭 쪽에도 넘겨야 한다. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) PRESSED_SCALE else 1f,
        animationSpec = if (reduced) snap() else tween(PRESS_MS, easing = EaseOut),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 펼치고 접는 내용. 위에서 아래로 열리며 살짝 나타난다. 도중에 다시 눌러도 그 자리에서 되돌아간다.
 * 처음 그릴 때 이미 [visible]이면 움직이지 않고 바로 보인다.
 */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) {
            fadeIn(snap())
        } else {
            expandVertically(tween(REVEAL_IN_MS, easing = EaseOut)) + fadeIn(tween(REVEAL_IN_MS, easing = EaseOut))
        },
        exit = if (reduced) {
            fadeOut(snap())
        } else {
            shrinkVertically(tween(REVEAL_OUT_MS, easing = EaseOut)) + fadeOut(tween(REVEAL_OUT_MS, easing = EaseOut))
        },
    ) {
        Column(content = content)
    }
}
