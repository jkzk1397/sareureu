package com.capstone.pause.core.designsystem.buddy

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 르르 그림 판 PNG 뽑기(에뮬레이터 없이). 녹는 프레임 6장 × 표정 6개를 28·56·72dp로 그려,
 * 눈·입이 각설탕 앞면 안에 있는지와 6장의 바닥선·크기가 맞는지 눈으로 확인한다.
 * `./gradlew recordRoborazziDebug`일 때만 파일이 생기고, 평소 `check`에서는 그려지기만 한다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// SDK 36 샌드박스는 JDK 25에서 FileDescriptor 내부 접근이 막혀 시작하지 못한다. 그리는 결과는 35와 같다.
@Config(sdk = [35])
class BuddyScreenshotTest {
    @Test
    fun buddyMeltSheet() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "night" else "notnight"
            RuntimeEnvironment.setQualifiers("w520dp-h1400dp-$mode-xxhdpi")
            captureRoboImage("buddy_melt_sheet_${if (dark) "dark" else "light"}.png") { BuddyAvatarSheetPreview() }
        }
    }

    /** 친구 캐릭터 8종의 얼굴 자리 확인용 판. */
    @Test
    fun friendAvatarSheet() {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "night" else "notnight"
            RuntimeEnvironment.setQualifiers("w420dp-h320dp-$mode-xxhdpi")
            captureRoboImage("friend_avatar_sheet_${if (dark) "dark" else "light"}.png") { FriendAvatarSheetPreview() }
        }
    }
}
