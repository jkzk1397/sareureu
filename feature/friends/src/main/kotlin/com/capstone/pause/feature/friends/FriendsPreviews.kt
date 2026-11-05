package com.capstone.pause.feature.friends

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.ChallengeRow
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.Ranking
import com.capstone.pause.core.social.Standing

/** 미리보기용 값(목업 08 수치, 시연용 가상 친구). */
private fun previewState(): FriendsUiState {
    val me = Standing("me", "지수", 0.23, 0.91, 7, isMe = true)
    val friends = listOf(
        Standing("h", "하은", 0.31, 0.96, 7, false),
        Standing("m", "민지", 0.18, 0.88, 6, false),
        Standing("j", "준호", 0.12, 0.71, 4, false),
    )
    return FriendsUiState(
        overview = FriendsOverview(
            isDemo = true,
            available = true,
            me = me,
            ranking = Ranking.build(friends + me),
            friends = friends,
            allFriends = friends,
            blocked = emptySet(),
            reported = emptySet(),
            challenge = listOf(
                ChallengeRow("지수", true, listOf(true, false, true, true, null, null, null)),
                ChallengeRow("하은", false, listOf(true, true, true, false, null, null, null)),
                ChallengeRow("민지", false, listOf(false, true, true, false, null, null, null)),
                ChallengeRow("준호", false, listOf(true, false, false, false, null, null, null)),
            ),
        ),
        poke = PokeDraftState(friendId = "j", drafts = listOf("내일 아침 수업이잖아. 쇼츠 그만 보고 자라")),
    )
}

@Preview(name = "light", heightDp = 2000)
@Preview(name = "dark", heightDp = 2000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FriendsScreenPreview() {
    PauseTheme { FriendsScreen(uiState = previewState(), onAction = {}) }
}

@Preview(name = "light unavailable")
@Preview(name = "dark unavailable", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FriendsUnavailablePreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(overview = base.overview?.copy(available = false, isDemo = false)),
            onAction = {},
        )
    }
}
