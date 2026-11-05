package com.capstone.pause.core.designsystem.buddy

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.scale
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
import com.capstone.pause.core.designsystem.theme.motionSpec
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.MeltStage

/**
 * 르르. 몸은 그림 한 장, 얼굴은 [BuddyFaceGeometry]로 그린다(06 문서 8절).
 * 녹아도 표정은 바뀌지 않는다. 밝은 바탕에서는 그림자를 깐다.
 */
@Composable
fun BuddyAvatar(
    expression: BuddyExpression,
    size: Dp,
    modifier: Modifier = Modifier,
    melt: MeltStage = MeltStage.NONE,
    shadow: Boolean = !PauseTheme.colors.isDark,
) {
    val body = ImageBitmap.imageResource(R.drawable.designsystem_buddy_body)
    val puddle by animateFloatAsState(
        BuddyMelt.puddleWidth(melt),
        motionSpec(tween(PauseMotion.MELT_MS)),
        label = "puddle",
    )
    val scaleY by animateFloatAsState(
        BuddyMelt.bodyScaleY(melt),
        motionSpec(tween(PauseMotion.MELT_MS)),
        label = "bodyScale",
    )
    val description = stringResource(
        R.string.designsystem_buddy_description,
        stringResource(R.string.buddy_name),
        stringResource(expression.labelRes()),
    )
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        drawBuddy(body, expression, puddle, scaleY, shadow)
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

private fun DrawScope.drawBuddy(
    body: ImageBitmap,
    expression: BuddyExpression,
    puddleWidth: Float,
    bodyScaleY: Float,
    shadow: Boolean,
) {
    val side = size.minDimension
    val dst = IntSize(side.toInt(), side.toInt())
    if (puddleWidth > 0f) {
        val w = side * puddleWidth
        val h = side * BuddyMelt.PUDDLE_HEIGHT
        drawOval(
            color = PauseColors.BuddyPuddle,
            topLeft = Offset((side - w) / 2f, side * BuddyMelt.PUDDLE_CENTER_Y - h / 2f),
            size = Size(w, h),
        )
    }
    scale(scaleX = 1f, scaleY = bodyScaleY, pivot = Offset(side / 2f, side)) {
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
    // 얼굴은 눌린 몸을 따라 내려가되 납작해지지 않게 scale 밖에서 그린다.
    val face = BuddyFaceGeometry.shapes(expression, side, density)
    drawFace(BuddyFaceGeometry.squashed(face, side, bodyScaleY), PauseColors.BuddyInk)
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
private fun BuddySheet() {
    Column(
        modifier = Modifier
            .background(PauseTheme.colors.bg)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(28.dp, 56.dp, 72.dp).forEach { s ->
            MeltStage.entries.forEach { melt ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuddyExpression.entries.forEach { BuddyAvatar(expression = it, size = s, melt = melt) }
                }
            }
        }
    }
}

@Preview(name = "light", widthDp = 520)
@Preview(name = "dark", widthDp = 520, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BuddyAvatarSheetPreview() {
    PauseTheme { BuddySheet() }
}
