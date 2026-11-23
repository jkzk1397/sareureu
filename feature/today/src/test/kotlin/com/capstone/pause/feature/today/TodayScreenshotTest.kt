package com.capstone.pause.feature.today

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 화면 PNG 뽑기(에뮬레이터 없이). 미리보기(@Preview)의 가짜 상태를 그대로 라이트·다크로 찍는다.
 * `./gradlew recordRoborazziDebug`일 때만 파일이 생기고, 평소 `check`에서는 그려지기만 한다(그리다 죽으면 실패).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 36 샌드박스는 JDK 25에서 FileDescriptor 내부 접근이 막혀 시작하지 못한다. 그리는 결과는 35와 같다.
@Config(sdk = [35])
class TodayScreenshotTest {
    @Test
    fun today() = shoot("today", heightDp = 1400) { TodayScreenPreview() }

    @Test
    fun todayLive() = shoot("today_live", heightDp = 1400) { TodayScreenLivePreview() }

    /** 큰 글자(시스템 글자 크기 1.3배): 글자가 잘리거나 한 글자씩 꺾이지 않는지 본다. */
    @Test
    fun todayLargeFont() = shoot("today_font13", heightDp = 1800, fontScale = 1.3f) { TodayScreenPreview() }

    /** 가로 360dp(갤럭시 S22), xxhdpi. 화면이 길어 [heightDp]만큼 세로를 늘려 한 장에 담는다. */
    private fun shoot(name: String, heightDp: Int, fontScale: Float = 1f, content: @Composable () -> Unit) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "night" else "notnight"
            RuntimeEnvironment.setQualifiers("w360dp-h${heightDp}dp-$mode-xxhdpi")
            RuntimeEnvironment.setFontScale(fontScale)
            captureRoboImage("${name}_${if (dark) "dark" else "light"}.png") { content() }
        }
        RuntimeEnvironment.setFontScale(1f)
    }
}
