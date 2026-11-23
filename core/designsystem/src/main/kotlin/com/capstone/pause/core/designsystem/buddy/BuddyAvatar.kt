package com.capstone.pause.core.designsystem.buddy

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.designsystem.theme.PauseMotion
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.designsystem.theme.rememberReducedMotion
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.MeltStage

/**
 * 르르. 몸은 녹는 프레임 6장 중 하나([BuddyMelt]), 얼굴은 [BuddyFaceGeometry]로 그린다(06 문서 8절, ADR-0016).
 * 녹아도 표정은 바뀌지 않는다. 밝은 바탕에서는 그림자를 깐다.
 *
 * 녹은 정도는 두 가지로 줄 수 있다.
 * - [melt]: 하루 기준 단계(오늘 탭). 같은 프레임 중 하나를 고른다.
 * - [meltFraction]: 0~1(플로팅 버블의 '이번 구간 남은 시간'). 주면 [melt]보다 먼저 쓰고, 가장 가까운 프레임을 고른다.
 * 프레임이 바뀔 때는 두 장이 짧게 겹쳐 보이고, '애니메이션 제거' 설정이면 바로 바뀐다.
 * 설정의 '녹는 각설탕 보기'가 꺼져 있으면 부르는 쪽이 0(또는 [MeltStage.NONE])을 넘긴다.
 */
@Composable
fun BuddyAvatar(
    expression: BuddyExpression,
    size: Dp,
    modifier: Modifier = Modifier,
    melt: MeltStage = MeltStage.NONE,
    shadow: Boolean = !PauseTheme.colors.isDark,
    meltFraction: Float? = null,
) {
    val frame = BuddyMelt.frameIndex(meltFraction ?: BuddyMelt.fraction(melt))
    val description = stringResource(
        R.string.designsystem_buddy_description,
        stringResource(R.string.buddy_name),
        stringResource(expression.labelRes()),
    )
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        // 몸: 프레임이 바뀌면 앞 장과 뒤 장이 겹쳐 보이다가 넘어간다.
        Crossfade(
            targetState = frame,
            // '애니메이션 제거' 설정이면 겹침 없이 바로 바뀐다.
            animationSpec = if (rememberReducedMotion()) snap() else tween(PauseMotion.MELT_MS),
            label = "buddyMeltFrame",
        ) { index ->
            val body = ImageBitmap.imageResource(BuddyMelt.drawableRes(index))
            Canvas(Modifier.fillMaxSize()) { drawBody(body, shadow) }
        }
        // 얼굴: 겹쳐 넘어가는 동안 흐려지지 않게 몸 위에 따로 그린다.
        Canvas(Modifier.fillMaxSize()) {
            val side = this.size.minDimension
            drawFace(BuddyFaceGeometry.shapes(expression, side, density, BuddyMelt.anchor(frame)), PauseColors.BuddyInk)
        }
    }
}

/** 표정 이름 문자열. */
@StringRes
fun BuddyExpression.labelRes(): Int = when (this) {
    BuddyExpression.NEUTRAL -> R.string.designsystem_buddy_expr_neutral
    BuddyExpression.WATCHING -> R.string.designsystem_buddy_expr_watching
    BuddyExpression.CALLING -> R.string.designsystem_buddy_expr_calling
    BuddyExpression.WORRIED -> R.string.designsystem_buddy_expr_worried
    BuddyExpression.CHEER -> R.string.designsystem_buddy_expr_cheer
    BuddyExpression.SLEEPY -> R.string.designsystem_buddy_expr_sleepy
}

/** 몸 그림 한 장(그림 안에 웅덩이까지 들어 있다). 밝은 바탕에서는 1dp 아래로 옅은 그림자를 먼저 깐다. */
private fun DrawScope.drawBody(body: ImageBitmap, shadow: Boolean) {
    val side = size.minDimension
    val dst = IntSize(side.toInt(), side.toInt())
    if (shadow) {
        translate(top = 1.dp.toPx()) {
            drawImage(
                image = body,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(body.width, body.height),
                dstSize = dst,
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = SHADOW_ALPHA), BlendMode.SrcIn),
            )
        }
    }
    drawImage(image = body, srcOffset = IntOffset.Zero, srcSize = IntSize(body.width, body.height), dstSize = dst)
}

private const val SHADOW_ALPHA = 0.2f

/** 얼굴 도형을 [color]로 그린다. */
internal fun DrawScope.drawFace(shapes: List<FaceShape>, color: Color) {
    shapes.forEach { s ->
        when (s) {
            is FaceShape.Ellipse -> drawOval(color, Offset(s.cx - s.rx, s.cy - s.ry), Size(s.rx * 2, s.ry * 2))

            is FaceShape.QuadStroke -> drawPath(
                Path().apply {
                    moveTo(s.x0, s.y0)
                    quadraticTo(s.cx, s.cy, s.x1, s.y1)
                },
                color,
                style = roundStroke(s.strokeWidth),
            )

            is FaceShape.LineStroke ->
                drawLine(color, Offset(s.x0, s.y0), Offset(s.x1, s.y1), s.strokeWidth, cap = StrokeCap.Round)

            is FaceShape.Polyline -> drawPath(
                Path().apply {
                    s.points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
                },
                color,
                style = roundStroke(s.strokeWidth),
            )

            is FaceShape.Sparkle -> drawPath(sparklePath(s), color)

            is FaceShape.Circle -> drawCircle(color, s.r, Offset(s.cx, s.cy), style = Stroke(s.strokeWidth))
        }
    }
}

private fun roundStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun sparklePath(s: FaceShape.Sparkle): Path {
    val q = s.r * BuddyFaceGeometry.SPARKLE_CONTROL
    return Path().apply {
        moveTo(s.cx, s.cy - s.r)
        quadraticTo(s.cx + q, s.cy - q, s.cx + s.r, s.cy)
        quadraticTo(s.cx + q, s.cy + q, s.cx, s.cy + s.r)
        quadraticTo(s.cx - q, s.cy + q, s.cx - s.r, s.cy)
        quadraticTo(s.cx - q, s.cy - q, s.cx, s.cy - s.r)
        close()
    }
}

@Composable
internal fun BuddySheet() {
    Column(
        modifier = Modifier
            .background(PauseTheme.colors.bg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 프레임 6장 × 표정 6개를 세 크기로: 눈·입이 각설탕 앞면 안에 있는지 눈으로 확인하는 판.
        listOf(28.dp, 56.dp, 72.dp).forEach { s ->
            (0 until BuddyMelt.FRAME_COUNT).forEach { frame ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuddyExpression.entries.forEach {
                        BuddyAvatar(
                            expression = it,
                            size = s,
                            meltFraction = frame / (BuddyMelt.FRAME_COUNT - 1f),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "light", widthDp = 520)
@Preview(name = "dark", widthDp = 520, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun BuddyAvatarSheetPreview() {
    PauseTheme { BuddySheet() }
}
