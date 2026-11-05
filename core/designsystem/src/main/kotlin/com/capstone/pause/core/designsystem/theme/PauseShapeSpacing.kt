package com.capstone.pause.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 모양 토큰(06 문서 4절). */
@Immutable
data class PauseShape(
    val card: RoundedCornerShape = RoundedCornerShape(28.dp),
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val small: RoundedCornerShape = RoundedCornerShape(12.dp),
)

/** 간격 토큰(4dp 단위). */
@Immutable
data class PauseSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 40.dp,
    /** 화면 좌우 여백. */
    val screen: Dp = 20.dp,
    /** 최소 터치 영역. */
    val minTouch: Dp = 48.dp,
    /** 구분선 두께. */
    val hairline: Dp = 1.dp,
    /** 오버레이 카드만 쓰는 그림자. */
    val overlayElevation: Dp = 8.dp,
)

/** 움직임 토큰: 기본 220ms FastOutSlowIn. */
object PauseMotion {
    /** 기본 길이(ms). */
    const val DEFAULT_MS = 220

    /** 녹는 단계 전환(ms). */
    const val MELT_MS = 600

    /** 기본 트윈. */
    fun <T> standard(): TweenSpec<T> = tween(DEFAULT_MS, easing = FastOutSlowInEasing)
}
