package com.capstone.pause.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.capstone.pause.core.model.ContextSlot

/** UI 색 토큰(06 문서 2.1절). 화면에서 hex를 직접 쓰지 않고 이것만 쓴다. */
@Immutable
data class PauseColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val social: Color,
    val nudge: Color,
    val nudgeText: Color,
    val success: Color,
    val scrim: Color,
    /** 하루 시계 다이얼의 밤 쪽(18시→0시→6시). */
    val dialNight: Color,
    /** 히트맵 단계 색(0분 다음 칸부터 가장 진한 칸까지). */
    val heatmap: List<Color>,
    /** 맥락 색(06 문서 2.2절). 순서를 바꾸지 않는다. */
    val context: Map<ContextSlot, Color>,
    val isDark: Boolean,
) {
    /** 맥락 색. */
    fun contextColor(slot: ContextSlot): Color = context.getValue(slot)

    companion object {
        /** 르르 얼굴 잉크(라이트·다크 동일, 06 문서 8절). */
        val BuddyInk = Color(0xFF141416)

        /** 녹은 웅덩이 색(06 문서 8절, 95%). */
        val BuddyPuddle = Color(0xF2ECF0E8)

        /** 라임(앱 아이콘 배경과 같은 값). */
        val Lime = Color(0xFFC6F24E)
    }
}

/** 라이트 토큰. */
val LightPauseColors = PauseColors(
    bg = Color(0xFFF3F3EE),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEAEAE3),
    ink = Color(0xFF141416),
    ink2 = Color(0xFF55555E),
    ink3 = Color(0xFF6E6E78),
    line = Color(0xFFDCDCD4),
    primary = Color(0xFF141416),
    onPrimary = Color(0xFFD4F75C),
    accent = Color(0xFFC6F24E),
    accentContainer = Color(0xFFEEFBC2),
    onAccentContainer = Color(0xFF2B3A00),
    social = Color(0xFF6A4DF5),
    nudge = Color(0xFFE5482F),
    nudgeText = Color(0xFFC23A22),
    success = Color(0xFF177A45),
    scrim = Color(0x7A141416),
    dialNight = Color(0xFFE4E4DC),
    heatmap = listOf(
        Color(0xFFCDE2FB),
        Color(0xFF9CC4F3),
        Color(0xFF5E9BE3),
        Color(0xFF2C6CC0),
        Color(0xFF104281),
    ),
    context = mapOf(
        ContextSlot.WAKE_UP to Color(0xFFEB6834),
        ContextSlot.COMMUTE to Color(0xFF1BAF7A),
        ContextSlot.CLASS_WORK to Color(0xFF2A78D6),
        ContextSlot.MEAL to Color(0xFFEDA100),
        ContextSlot.BREAK to Color(0xFF008300),
        ContextSlot.PRE_SLEEP to Color(0xFFE87BA4),
        ContextSlot.LATE_NIGHT to Color(0xFF6250D6),
        ContextSlot.OTHER to Color(0xFFA3A3AD),
    ),
    isDark = false,
)

/** 다크 토큰. */
val DarkPauseColors = PauseColors(
    bg = Color(0xFF0E0E11),
    surface = Color(0xFF1A1A1F),
    surfaceAlt = Color(0xFF24242B),
    ink = Color(0xFFF2F2EC),
    ink2 = Color(0xFFB4B4BD),
    ink3 = Color(0xFF8C8C97),
    line = Color(0xFF2E2E36),
    primary = Color(0xFFC6F24E),
    onPrimary = Color(0xFF141416),
    accent = Color(0xFFC6F24E),
    accentContainer = Color(0xFF2D3810),
    onAccentContainer = Color(0xFFE3FA9E),
    social = Color(0xFFA796FF),
    nudge = Color(0xFFFF7A62),
    nudgeText = Color(0xFFFF7A62),
    success = Color(0xFF4CC27F),
    scrim = Color(0x99000000),
    dialNight = Color(0xFF141418),
    // 다크는 어두운 쪽을 0으로 뒤집는다(06 문서 2.3절).
    heatmap = listOf(
        Color(0xFF104281),
        Color(0xFF2C6CC0),
        Color(0xFF5E9BE3),
        Color(0xFF9CC4F3),
        Color(0xFFCDE2FB),
    ),
    context = mapOf(
        ContextSlot.WAKE_UP to Color(0xFFD95926),
        ContextSlot.COMMUTE to Color(0xFF199E70),
        ContextSlot.CLASS_WORK to Color(0xFF3987E5),
        ContextSlot.MEAL to Color(0xFFC98500),
        ContextSlot.BREAK to Color(0xFF008300),
        ContextSlot.PRE_SLEEP to Color(0xFFD55181),
        ContextSlot.LATE_NIGHT to Color(0xFF9085E9),
        ContextSlot.OTHER to Color(0xFF5E5E68),
    ),
    isDark = true,
)
