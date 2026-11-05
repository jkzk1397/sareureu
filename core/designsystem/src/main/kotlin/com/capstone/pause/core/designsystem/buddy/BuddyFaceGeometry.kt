package com.capstone.pause.core.designsystem.buddy

import com.capstone.pause.core.model.BuddyExpression

/** 르르 얼굴을 이루는 도형. 좌표·굵기는 모두 px(정사각형 `sizePx` 안). */
sealed interface FaceShape {
    /** 타원(채움). */
    data class Ellipse(
        val cx: Float,
        val cy: Float,
        val rx: Float,
        val ry: Float,
        val filled: Boolean = true,
        val strokeWidth: Float = 0f,
    ) : FaceShape

    /** 2차 베지어 선. */
    data class QuadStroke(
        val x0: Float,
        val y0: Float,
        val cx: Float,
        val cy: Float,
        val x1: Float,
        val y1: Float,
        val strokeWidth: Float,
    ) : FaceShape

    /** 직선. */
    data class LineStroke(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val strokeWidth: Float) :
        FaceShape

    /** 꺾은선. [points]는 x, y 쌍. */
    data class Polyline(val points: List<Pair<Float, Float>>, val strokeWidth: Float) : FaceShape

    /** 네 갈래 반짝이(채움). 곡선 조절점은 반지름의 0.28. */
    data class Sparkle(val cx: Float, val cy: Float, val r: Float) : FaceShape

    /** 원(선만). */
    data class Circle(val cx: Float, val cy: Float, val r: Float, val strokeWidth: Float) : FaceShape
}

/**
 * 06 문서 8절 표와 `docs/brand/ruru_face_reference.py`를 그대로 옮긴 얼굴 기하. 순수 Kotlin.
 * `BuddyAvatar`(Compose)와 `BuddyBitmap`(android.graphics)이 이것 하나만 그린다.
 */
object BuddyFaceGeometry {
    private const val AX = 39.7f
    private const val AY = 78.0f
    private const val G0 = 11.3f
    private const val G_MAX = 32f

    // 작은 크기(28~56dp)에서도 눈코입이 또렷하게 보이도록 최솟값을 키웠다(06 문서 8절).
    private const val MIN_GAP_DP = 14f
    private const val MIN_EYE_DP = 2f
    private const val MIN_STROKE_DP = 1.6f

    /** 표정 [expression]을 [sizePx] 정사각형에 그릴 도형들. [density]는 1dp당 px. */
    fun shapes(expression: BuddyExpression, sizePx: Float, density: Float): List<FaceShape> {
        val unitPerDp = 100f * density / sizePx // 1dp를 0~100 단위로
        val g = minOf(maxOf(G0, MIN_GAP_DP * unitPerDp), G_MAX)
        val ry = maxOf(0.095f * g, MIN_EYE_DP * unitPerDp)
        val rx = ry * 0.62f
        val sw = maxOf(0.06f * g, MIN_STROKE_DP * unitPerDp)
        val k = sizePx / 100f // 단위 → px

        fun px(v: Float) = v * k
        fun x(dx: Float) = px(AX + dx * g)
        fun y(dy: Float) = px(AY + dy * g)
        fun quad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) =
            FaceShape.QuadStroke(x(x0), y(y0), x(cx), y(cy), x(x1), y(y1), px(sw))

        fun eyes(dx: Float = 0f, scale: Float = 1f): List<FaceShape> = listOf(-0.5f, 0.5f).map { sx ->
            FaceShape.Ellipse(
                cx = x(sx + dx),
                cy = y(0f),
                rx = px(rx * if (scale >= 1f) 1f else 1.05f),
                ry = px(ry * scale),
            )
        }

        fun arcEyes(up: Boolean): List<FaceShape> = listOf(-0.5f, 0.5f).map { sx ->
            val endY = if (up) 0.03f else 0f
            val midY = if (up) -0.11f else 0.08f
            quad(sx - 0.1f, endY, sx, midY, sx + 0.1f, endY)
        }

        fun sparkle(sx: Float, sy: Float, s: Float) = FaceShape.Sparkle(x(sx), y(sy), px(s * g))

        return when (expression) {
            BuddyExpression.NEUTRAL -> eyes() + quad(-0.13f, 0.17f, 0f, 0.29f, 0.13f, 0.17f)

            BuddyExpression.WATCHING -> eyes(dx = 0.2f, scale = 0.72f) +
                FaceShape.LineStroke(x(-0.06f), y(0.22f), x(0.1f), y(0.22f), px(sw))

            BuddyExpression.CALLING -> eyes(scale = 1.1f) +
                quad(-0.62f, -0.34f, -0.5f, -0.44f, -0.38f, -0.34f) +
                quad(0.38f, -0.34f, 0.5f, -0.44f, 0.62f, -0.34f) +
                FaceShape.Ellipse(
                    cx = x(0f),
                    cy = y(0.24f),
                    rx = px(maxOf(0.075f * g, 1.5f * unitPerDp)),
                    ry = px(maxOf(0.09f * g, 1.7f * unitPerDp)),
                )

            BuddyExpression.WORRIED -> eyes() +
                FaceShape.LineStroke(x(-0.66f), y(-0.22f), x(-0.36f), y(-0.33f), px(sw)) +
                FaceShape.LineStroke(x(0.66f), y(-0.22f), x(0.36f), y(-0.33f), px(sw)) +
                quad(-0.12f, 0.27f, 0f, 0.18f, 0.12f, 0.27f)

            BuddyExpression.CHEER -> arcEyes(up = true) +
                quad(-0.18f, 0.15f, 0f, 0.36f, 0.18f, 0.15f) +
                sparkle(1.0f, -0.55f, 0.17f) +
                sparkle(1.28f, -0.2f, 0.1f)

            BuddyExpression.SLEEPY -> {
                val z = 0.22f
                arcEyes(up = false) +
                    FaceShape.Circle(x(0f), y(0.24f), px(maxOf(0.05f * g, 1.3f * unitPerDp)), px(sw * 0.85f)) +
                    FaceShape.Polyline(
                        listOf(
                            x(0.9f) to y(-0.72f),
                            x(0.9f + z) to y(-0.72f),
                            x(0.9f) to y(-0.72f + z),
                            x(0.9f + z) to y(-0.72f + z),
                        ),
                        px(sw),
                    )
            }
        }
    }

    /**
     * 녹아서 몸이 바닥 기준으로 [scaleY]배 눌렸을 때, 얼굴은 눌린 몸을 따라 내려가되 모양은 그대로 둔다
     * (눈이 납작해져 안 보이지 않게). y만 `바닥 - (바닥 - y) × scaleY`로 옮긴다.
     */
    fun squashed(shapes: List<FaceShape>, sizePx: Float, scaleY: Float): List<FaceShape> {
        if (scaleY == 1f) return shapes
        fun m(y: Float) = sizePx - (sizePx - y) * scaleY
        return shapes.map { s ->
            when (s) {
                is FaceShape.Ellipse -> s.copy(cy = m(s.cy))
                is FaceShape.QuadStroke -> s.copy(y0 = m(s.y0), cy = m(s.cy), y1 = m(s.y1))
                is FaceShape.LineStroke -> s.copy(y0 = m(s.y0), y1 = m(s.y1))
                is FaceShape.Polyline -> s.copy(points = s.points.map { (x, y) -> x to m(y) })
                is FaceShape.Sparkle -> s.copy(cy = m(s.cy))
                is FaceShape.Circle -> s.copy(cy = m(s.cy))
            }
        }
    }

    /** 눈 간격(0~100 단위). 테스트·디버그용. */
    fun eyeGapUnits(sizePx: Float, density: Float): Float =
        minOf(maxOf(G0, MIN_GAP_DP * 100f * density / sizePx), G_MAX)

    /** 반짝이 곡선의 조절점 비율. */
    const val SPARKLE_CONTROL = 0.28f
}
