package com.capstone.pause.core.designsystem.buddy

import com.capstone.pause.core.model.BuddyExpression
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BuddyFaceGeometryTest {
    private val density = 2.625f

    private fun sizePx(dp: Int) = dp * density

    @Test
    fun smallSizeEnlargesFace() {
        // 56dp: 14dp = 25 단위라 눈 간격은 g0(11.3)보다 크다.
        assertEquals(25f, BuddyFaceGeometry.eyeGapUnits(sizePx(56), density), 0.01f)
    }

    @Test
    fun largeSizeKeepsBaseGap() {
        assertEquals(11.3f, BuddyFaceGeometry.eyeGapUnits(sizePx(512), density), 0.001f)
    }

    @Test
    fun tinySizeIsCapped() {
        assertEquals(32f, BuddyFaceGeometry.eyeGapUnits(sizePx(20), density), 0.001f)
    }

    @Test
    fun neutralHasTwoEyesAndMouth() {
        val shapes = BuddyFaceGeometry.shapes(BuddyExpression.NEUTRAL, sizePx(56), density)
        assertEquals(2, shapes.count { it is FaceShape.Ellipse })
        assertEquals(1, shapes.count { it is FaceShape.QuadStroke })
        assertEquals(3, shapes.size)
    }

    @Test
    fun cheerHasTwoSparkles() {
        val shapes = BuddyFaceGeometry.shapes(BuddyExpression.CHEER, sizePx(72), density)
        assertEquals(2, shapes.count { it is FaceShape.Sparkle })
    }

    @Test
    fun sleepyHasZAndCircleMouth() {
        val shapes = BuddyFaceGeometry.shapes(BuddyExpression.SLEEPY, sizePx(56), density)
        assertEquals(1, shapes.count { it is FaceShape.Polyline })
        assertEquals(1, shapes.count { it is FaceShape.Circle })
    }

    @Test
    fun allShapesStayOnTheCubeFrontFace() {
        // 몸 그림에서 각설탕 앞면은 대략 x 4~86(반짝이는 옆면 끝까지), y 22~92(0~100 단위)다.
        listOf(28, 40, 46, 56, 64, 72, 200).forEach { dp ->
            val size = sizePx(dp)
            val u = size / 100f
            BuddyExpression.entries.forEach { expr ->
                BuddyFaceGeometry.shapes(expr, size, density).forEach { shape ->
                    points(shape).forEach { (x, y) ->
                        assertTrue(x in 4 * u..86 * u && y in 22 * u..92 * u, "$expr@$dp: ($x, $y)")
                    }
                }
            }
        }
    }

    @Test
    fun meltedFaceMovesDownButKeepsItsShape() {
        val size = sizePx(56)
        val face = BuddyFaceGeometry.shapes(BuddyExpression.NEUTRAL, size, density)
        val melted = BuddyFaceGeometry.squashed(face, size, 0.78f)
        val eye = face.first() as FaceShape.Ellipse
        val meltedEye = melted.first() as FaceShape.Ellipse
        assertEquals(eye.ry, meltedEye.ry, 0.001f)
        assertEquals(size - (size - eye.cy) * 0.78f, meltedEye.cy, 0.001f)
        assertTrue(meltedEye.cy > eye.cy)
    }

    @Test
    fun smallFaceIsReadable() {
        // 28dp에서도 눈 높이 4dp(반높이 2dp) 이상.
        val size = sizePx(28)
        val eye = BuddyFaceGeometry.shapes(BuddyExpression.NEUTRAL, size, density).first() as FaceShape.Ellipse
        assertTrue(eye.ry >= 2f * density - 0.01f)
    }

    private fun points(s: FaceShape): List<Pair<Float, Float>> = when (s) {
        is FaceShape.Ellipse -> listOf(s.cx - s.rx to s.cy - s.ry, s.cx + s.rx to s.cy + s.ry)
        is FaceShape.QuadStroke -> listOf(s.x0 to s.y0, s.cx to s.cy, s.x1 to s.y1)
        is FaceShape.LineStroke -> listOf(s.x0 to s.y0, s.x1 to s.y1)
        is FaceShape.Polyline -> s.points
        is FaceShape.Sparkle -> listOf(s.cx - s.r to s.cy - s.r, s.cx + s.r to s.cy + s.r)
        is FaceShape.Circle -> listOf(s.cx - s.r to s.cy - s.r, s.cx + s.r to s.cy + s.r)
    }
}
