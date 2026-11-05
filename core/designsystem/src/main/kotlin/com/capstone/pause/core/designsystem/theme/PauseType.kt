package com.capstone.pause.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.capstone.pause.core.designsystem.R

/**
 * Pretendard 서브셋 가변 글꼴(`pause_sans_variable.ttf`)을 굵기마다 등록한다(06 문서 3절).
 * 파일 이름과 라이선스(`licenses/Pretendard-OFL.txt`)는 바꾸지 않는다.
 */
@OptIn(ExperimentalTextApi::class)
val PauseSans: FontFamily = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(
            resId = R.font.pause_sans_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)

/** 글자 토큰(06 문서 3절). */
@Immutable
data class PauseType(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** 표·축용 고정폭 숫자. */
    val captionTabular: TextStyle,
)

private fun style(size: Int, line: Int, weight: Int) = TextStyle(
    fontFamily = PauseSans,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
)

/** 기본 글자 토큰. */
val DefaultPauseType = PauseType(
    display = style(40, 46, 800),
    headline = style(26, 34, 700),
    title = style(19, 26, 700),
    body = style(16, 24, 500),
    bodySmall = style(14, 20, 500),
    label = style(13, 18, 600),
    caption = style(12, 16, 500),
    captionTabular = style(12, 16, 500).copy(fontFeatureSettings = "tnum"),
)
