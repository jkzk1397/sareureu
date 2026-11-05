package com.capstone.pause.core.designsystem.buddy

import com.capstone.pause.core.model.MeltStage

/** 녹는 단계별 그림 값(06 문서 8절 표). 순수 값이라 Compose·Bitmap이 같이 쓴다. */
object BuddyMelt {
    /** 웅덩이 높이(몸 정사각형 대비). */
    const val PUDDLE_HEIGHT = 0.13f

    /** 웅덩이 중심 높이(몸 정사각형 위에서부터 비율). */
    const val PUDDLE_CENTER_Y = 0.9f

    /** 웅덩이 폭 비율(0이면 그리지 않음). */
    fun puddleWidth(stage: MeltStage): Float = when (stage) {
        MeltStage.NONE -> 0f
        MeltStage.SOFT -> 0.62f
        MeltStage.PUDDLE -> 0.82f
        MeltStage.MELTED -> 1f
    }

    /** 몸 세로 배율(바닥 기준). */
    fun bodyScaleY(stage: MeltStage): Float = when (stage) {
        MeltStage.NONE, MeltStage.SOFT -> 1f
        MeltStage.PUDDLE -> 0.88f
        MeltStage.MELTED -> 0.78f
    }
}
