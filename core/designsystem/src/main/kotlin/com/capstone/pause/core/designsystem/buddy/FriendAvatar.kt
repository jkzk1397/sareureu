package com.capstone.pause.core.designsystem.buddy

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.capstone.pause.core.designsystem.R
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression

/**
 * 친구 프로필 캐릭터 8종(06 문서 8절 '친구 캐릭터' 표). 몸은 그림 한 장씩, 얼굴은 르르와 같은 [BuddyFaceGeometry]로 그린다
 * (표정 그림 파일을 따로 만들지 않는다). [anchor]는 그림마다 잰 얼굴 자리, [lightInk]는 몸이 어두워 얼굴을 밝은 색으로 그리는 캐릭터.
 * 순서가 곧 번호(0~7)라 바꾸지 않는다: 두 폰에서 같은 친구가 같은 캐릭터로 보여야 한다.
 */
enum class FriendCharacter(@DrawableRes val drawable: Int, val anchor: FaceAnchor, val lightInk: Boolean = false) {
    /** 흑설탕 각설탕. */
    BROWN(R.drawable.designsystem_friend_brown, FaceAnchor(x = 40f, y = 62f, frontTop = 30f)),

    /** 초콜릿(몸이 어두워 얼굴은 밝은 색). */
    CHOCO(R.drawable.designsystem_friend_choco, FaceAnchor(x = 39f, y = 62f, frontTop = 28f), lightInk = true),

    /** 솜사탕. */
    COTTON(R.drawable.designsystem_friend_cotton, FaceAnchor(x = 48f, y = 58f, frontTop = 20f)),

    /** 사탕. */
    DROP(R.drawable.designsystem_friend_drop, FaceAnchor(x = 48f, y = 56f, frontTop = 12f)),

    /** 젤리. */
    JELLY(R.drawable.designsystem_friend_jelly, FaceAnchor(x = 40f, y = 64f, frontTop = 32f)),

    /** 마시멜로. */
    MARSH(R.drawable.designsystem_friend_marsh, FaceAnchor(x = 48f, y = 66f, frontTop = 36f)),

    /** 민트 각설탕. */
    MINT(R.drawable.designsystem_friend_mint, FaceAnchor(x = 40f, y = 63f, frontTop = 30f)),

    /** 별사탕. */
    STAR(R.drawable.designsystem_friend_star, FaceAnchor(x = 50f, y = 57f, frontTop = 30f)),
    ;

    /** 번호로 고르기. */
    companion object {
        /** 캐릭터 수. 번호는 0부터 [COUNT]-1. */
        const val COUNT = 8

        /** [characterId]번 캐릭터. 범위 밖 번호도 0~7로 접어 쓴다. */
        fun of(characterId: Int): FriendCharacter = entries[Math.floorMod(characterId, COUNT)]
    }
}

/**
 * 친구 프로필 그림. [characterId]는 친구의 공개 ID(또는 가상 친구 이름)를 해시해 정한 0~7(`core:social`의 `FriendCharacterIds`).
 * 지금 숏폼을 보는 친구는 [expression]을 `WATCHING`으로 준다. 이름 옆에 놓는 꾸밈이라 화면 읽기 설명은 달지 않는다
 * (이름과 상태는 그 줄이 읽어 준다). 내 캐릭터는 이것이 아니라 르르([BuddyAvatar])다.
 */
@Composable
fun FriendAvatar(
    characterId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    expression: BuddyExpression = BuddyExpression.NEUTRAL,
) {
    val character = FriendCharacter.of(characterId)
    val body = ImageBitmap.imageResource(character.drawable)
    val ink = if (character.lightInk) PauseColors.FriendInkLight else PauseColors.BuddyInk
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        drawImage(
            image = body,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(body.width, body.height),
            dstSize = IntSize(side.toInt(), side.toInt()),
        )
        drawFace(BuddyFaceGeometry.shapes(expression, side, density, character.anchor), ink)
    }
}

/** 친구 캐릭터 8종 × 표정 2개(기본·보는 중)를 목록 크기(40dp)와 큰 크기(64dp)로 놓은 판. 얼굴 자리를 눈으로 확인한다. */
@Preview(name = "light", widthDp = 420)
@Preview(name = "dark", widthDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendAvatarSheetPreview() {
    PauseTheme {
        Column(
            modifier = Modifier
                .background(PauseTheme.colors.surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (size in listOf(40.dp, 64.dp)) {
                for (expression in listOf(BuddyExpression.NEUTRAL, BuddyExpression.WATCHING)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(FriendCharacter.COUNT) {
                            FriendAvatar(characterId = it, size = size, expression = expression)
                        }
                    }
                }
            }
        }
    }
}
