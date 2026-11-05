package com.capstone.pause.core.designsystem.buddy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.model.BuddyExpression

/** 알림 큰 아이콘 등 Compose 밖에서 쓰는 르르 비트맵. [BuddyAvatar]와 같은 기하를 쓴다. */
object BuddyBitmap {
    /** [expression] 얼굴의 르르를 [sizePx] 정사각형 비트맵으로 그린다. */
    fun render(context: Context, expression: BuddyExpression, sizePx: Int): Bitmap {
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        val body = BitmapFactory.decodeResource(context.resources, R.drawable.designsystem_buddy_body)
        if (body != null) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(body, Rect(0, 0, body.width, body.height), Rect(0, 0, sizePx, sizePx), paint)
        }
        val density = context.resources.displayMetrics.density
        drawFace(canvas, BuddyFaceGeometry.shapes(expression, sizePx.toFloat(), density))
        return out
    }

    private fun drawFace(canvas: Canvas, shapes: List<FaceShape>) {
        val ink = PauseColors.BuddyInk.toArgb()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            style = Paint.Style.FILL
        }

        fun stroke(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        shapes.forEach { s ->
            when (s) {
                is FaceShape.Ellipse -> canvas.drawOval(RectF(s.cx - s.rx, s.cy - s.ry, s.cx + s.rx, s.cy + s.ry), fill)

                is FaceShape.QuadStroke -> canvas.drawPath(
                    Path().apply {
                        moveTo(s.x0, s.y0)
                        quadTo(s.cx, s.cy, s.x1, s.y1)
                    },
                    stroke(s.strokeWidth),
                )

                is FaceShape.LineStroke -> canvas.drawLine(s.x0, s.y0, s.x1, s.y1, stroke(s.strokeWidth))

                is FaceShape.Polyline -> canvas.drawPath(
                    Path().apply {
                        s.points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
                    },
                    stroke(s.strokeWidth),
                )

                is FaceShape.Sparkle -> {
                    val q = s.r * BuddyFaceGeometry.SPARKLE_CONTROL
                    val p = Path().apply {
                        moveTo(s.cx, s.cy - s.r)
                        quadTo(s.cx + q, s.cy - q, s.cx + s.r, s.cy)
                        quadTo(s.cx + q, s.cy + q, s.cx, s.cy + s.r)
                        quadTo(s.cx - q, s.cy + q, s.cx - s.r, s.cy)
                        quadTo(s.cx - q, s.cy - q, s.cx, s.cy - s.r)
                        close()
                    }
                    canvas.drawPath(p, fill)
                }

                is FaceShape.Circle -> canvas.drawCircle(s.cx, s.cy, s.r, stroke(s.strokeWidth))
            }
        }
    }
}
