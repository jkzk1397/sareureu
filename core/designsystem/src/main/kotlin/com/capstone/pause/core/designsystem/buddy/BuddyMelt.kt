package com.capstone.pause.core.designsystem.buddy

import androidx.annotation.DrawableRes
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.model.MeltStage
import kotlin.math.roundToInt

/**
 * 한 프레임의 얼굴 자리(몸 그림 정사각형을 0~100으로 본 값). [x]·[y]는 두 눈 사이 가운데,
 * [frontTop]은 각설탕 앞면의 위 모서리(얼굴이 이 위로 올라가면 윗면에 걸친다).
 */
data class FaceAnchor(val x: Float, val y: Float, val frontTop: Float)

/**
 * 녹는 프레임 6장(0·20·40·60·80·100%, ADR-0016, 06 문서 8절). 순수 값이라 Compose·Bitmap이 같이 쓴다.
 * 그림은 `docs/design/reference/ruru_melt_*.png`를 `docs/brand/tools/cutout.py`로 같은 크기·같은 바닥선에 맞춰 만들었다.
 */
object BuddyMelt {
    /** 프레임 수. */
    const val FRAME_COUNT = 6

    /** 녹은 정도 [fraction](0 = 새 각설탕, 1 = 다 녹음) → 가장 가까운 프레임 번호(0~5). */
    fun frameIndex(fraction: Float): Int {
        val safe = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return (safe * (FRAME_COUNT - 1)).roundToInt()
    }

    /** 하루 기준 녹는 단계 → 같은 프레임 중 하나(오늘 탭). NONE 0%, SOFT 20%, PUDDLE 60%, MELTED 100%. */
    fun fraction(stage: MeltStage): Float = when (stage) {
        MeltStage.NONE -> 0f
        MeltStage.SOFT -> 0.2f
        MeltStage.PUDDLE -> 0.6f
        MeltStage.MELTED -> 1f
    }

    /** [index]번째 프레임 그림. */
    @DrawableRes
    fun drawableRes(index: Int): Int = FRAMES[index.coerceIn(0, FRAME_COUNT - 1)]

    /** [index]번째 프레임의 얼굴 자리. */
    fun anchor(index: Int): FaceAnchor = ANCHORS[index.coerceIn(0, FRAME_COUNT - 1)]

    private val FRAMES = intArrayOf(
        R.drawable.designsystem_buddy_melt_00,
        R.drawable.designsystem_buddy_melt_20,
        R.drawable.designsystem_buddy_melt_40,
        R.drawable.designsystem_buddy_melt_60,
        R.drawable.designsystem_buddy_melt_80,
        R.drawable.designsystem_buddy_melt_100,
    )

    /**
     * 장마다 잰 값. 각설탕은 위에서부터 내려앉고 바닥(96)은 그대로라, 앞면 아래쪽에 있는 눈 자리(78)는
     * 모든 장에서 앞면 안에 남는다. 그래서 눈 자리는 그대로 두고(프레임이 바뀌어도 얼굴이 튀지 않는다)
     * 앞면 위 모서리만 장마다 내려간다.
     */
    private val ANCHORS = listOf(
        FaceAnchor(x = 39.7f, y = 78.0f, frontTop = 23f),
        FaceAnchor(x = 39.7f, y = 78.0f, frontTop = 24f),
        FaceAnchor(x = 39.7f, y = 78.0f, frontTop = 28f),
        FaceAnchor(x = 39.7f, y = 78.0f, frontTop = 35f),
        FaceAnchor(x = 39.7f, y = 78.0f, frontTop = 41f),
        FaceAnchor(x = 39.7f, y = 78.0f, frontTop = 47f),
    )
}
