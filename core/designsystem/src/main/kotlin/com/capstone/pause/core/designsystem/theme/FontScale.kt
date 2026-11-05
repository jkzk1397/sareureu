package com.capstone.pause.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** 큰 글자로 보는 사용자(시스템 글자 크기 1.3배 넘음). 두 칸 배치를 한 칸으로 바꿀 때 쓴다. */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

/**
 * 크기가 정해진 그림(차트) 안 글자를 시스템 글자 크기만큼 끝없이 키우지 않는다(최대 [max]배).
 * 같은 값은 차트 밖의 표·범례·화면 읽기 설명에서 원래 크기로 읽을 수 있어야 한다.
 */
@Composable
fun CappedFontScale(max: Float = CHART_MAX_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, minOf(density.fontScale, max)),
        content = content,
    )
}

/** 차트 안 글자 최대 배율. */
const val CHART_MAX_FONT_SCALE = 1.3f

private const val LARGE_FONT_SCALE = 1.3f
