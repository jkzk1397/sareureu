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
    fun meltFractionPicksTheNearestOfSixFrames() {
        assertEquals(6, BuddyMelt.FRAME_COUNT)
        assertEquals(0, BuddyMelt.frameIndex(0f))
        assertEquals(0, BuddyMelt.frameIndex(0.09f))
        assertEquals(1, BuddyMelt.frameIndex(0.11f))
        assertEquals(2, BuddyMelt.frameIndex(0.4f))
        assertEquals(4, BuddyMelt.frameIndex(0.75f))
        assertEquals(5, BuddyMelt.frameIndex(1f))
        // 범위 밖·이상한 값은 끝으로.
        assertEquals(0, BuddyMelt.frameIndex(-1f))
        assertEquals(5, BuddyMelt.frameIndex(3f))
        assertEquals(0, BuddyMelt.frameIndex(Float.NaN))
        // 하루 기준 단계는 같은 프레임 중 하나를 고른다.
        val stages = com.capstone.pause.core.model.MeltStage.entries.map {
            BuddyMelt.frameIndex(BuddyMelt.fraction(it))
        }
        assertEquals(listOf(0, 1, 3, 5), stages)
    }

    @Test
    fun everyFrameKeepsTheFaceOnTheFrontOfTheCube() {
        // 프레임이 녹을수록 앞면 위 모서리가 내려온다. 가장 작은 크기에서도 얼굴이 그 위로 넘어가지 않아야 한다.
        listOf(28, 44, 56, 72, 96).forEach { dp ->
            val size = sizePx(dp)
            val u = size / 100f
            (0 until BuddyMelt.FRAME_COUNT).forEach { frame ->
                val anchor = BuddyMelt.anchor(frame)
                BuddyExpression.entries.forEach { expr ->
                    BuddyFaceGeometry.shapes(expr, size, density, anchor).forEach { shape ->
                        points(shape).forEach { (_, y) ->
                            assertTrue(y >= anchor.frontTop * u && y <= 92 * u, "$expr@$dp frame $frame: y=${y / u}")
                        }
                    }
                }
            }
        }
        // 앞면 위 모서리는 녹을수록 내려오고, 눈 자리는 그대로다(프레임이 바뀌어도 얼굴이 튀지 않는다).
        val tops = (0 until BuddyMelt.FRAME_COUNT).map { BuddyMelt.anchor(it).frontTop }
        assertEquals(tops.sorted(), tops)
        assertEquals(1, (0 until BuddyMelt.FRAME_COUNT).map { BuddyMelt.anchor(it).y }.toSet().size)
    }

    @Test
    fun friendCharactersAreEightAndTheirFacesStayInsideTheBody() {
        assertEquals(FriendCharacter.COUNT, FriendCharacter.entries.size)
        // 번호는 0~7로 접어 쓴다(음수·큰 수도).
        assertEquals(FriendCharacter.BROWN, FriendCharacter.of(0))
        assertEquals(FriendCharacter.STAR, FriendCharacter.of(7))
        assertEquals(FriendCharacter.BROWN, FriendCharacter.of(8))
        assertEquals(FriendCharacter.STAR, FriendCharacter.of(-1))
        // 친구 목록에서 쓰는 두 표정이 목록 크기(40dp)에서도 그림 안(앞면 위 모서리 아래, 바닥 위)에 들어온다.
        listOf(40, 64).forEach { dp ->
            val size = sizePx(dp)
            val u = size / 100f
            FriendCharacter.entries.forEach { character ->
                listOf(BuddyExpression.NEUTRAL, BuddyExpression.WATCHING).forEach { expr ->
                    BuddyFaceGeometry.shapes(expr, size, density, character.anchor).forEach { shape ->
                        points(shape).forEach { (x, y) ->
                            assertTrue(
                                x in 10 * u..90 * u && y >= character.anchor.frontTop * u && y <= 90 * u,
                                "$character $expr@$dp: (${x / u}, ${y / u})",
                            )
                        }
                    }
                }
            }
        }
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
