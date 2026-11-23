package com.capstone.pause.feature.friends

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.ChallengeRow
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.Standing
import com.capstone.pause.core.social.StreakRanking

/** 미리보기용 값(시연용 가상 친구). 민지는 지금 시청 중. */
private fun previewState(): FriendsUiState {
    val me = Standing("me", "지수", 0.23, 0.91, 7, isMe = true, streakDays = 4)
    val friends = listOf(
        Standing("h", "하은", 0.31, 0.96, 7, false, streakDays = 12),
        Standing("m", "민지", 0.18, 0.88, 6, false, streakDays = 5, watching = true),
        Standing("j", "준호", 0.12, 0.71, 4, false, streakDays = 2),
        Standing("s", "서연", 0.05, 0.8, 5, false, streakDays = 0, watching = true),
    )
    return FriendsUiState(
        overview = FriendsOverview(
            isDemo = true,
            available = true,
            me = me,
            ranking = StreakRanking.build(friends + me),
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
        pokeFeedback = PokeFeedback.Sent("민지", demo = true),
    )
}

@Preview(name = "light", heightDp = 2000)
@Preview(name = "dark", heightDp = 2000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsScreenPreview() {
    PauseTheme { FriendsScreen(uiState = previewState(), onAction = {}) }
}

@Preview(name = "light unavailable")
@Preview(name = "dark unavailable", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsUnavailablePreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(overview = base.overview?.copy(available = false, isDemo = false)),
            onAction = {},
        )
    }
}

@Preview(name = "light sharing consent", heightDp = 1200)
@Preview(name = "dark sharing consent", heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsSharingConsentPreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(
                overview = base.overview?.copy(available = false, isDemo = false, sharing = SharingState.NOT_CONSENTED),
            ),
            onAction = {},
        )
    }
}

@Preview(name = "light server friends", heightDp = 2200)
@Preview(name = "dark server friends", heightDp = 2200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsServerPreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(
                overview = base.overview?.copy(
                    isDemo = false,
                    sharing = SharingState.CONSENTED,
                    challenge = emptyList(),
                ),
                invite = InviteUiState(
                    code = "K7P2QXM4",
                    expires = java.time.LocalDateTime.of(2026, 11, 17, 21, 30),
                    qr = InviteQr.encode("https://sareureu.example.workers.dev/invite/K7P2QXM4"),
                    link = "https://sareureu.example.workers.dev/invite/K7P2QXM4",
                    qrOpen = true,
                    qrWatching = true,
                ),
                requests = previewRequests(),
                pokeFeedback = null,
            ),
            onAction = {},
        )
    }
}

private fun previewRequests(): RequestsUiState {
    val at = java.time.Instant.parse("2026-11-17T03:00:00Z")
    return RequestsUiState(
        received = listOf(
            com.capstone.pause.core.social.ReceivedRequest("r1", "준호", at, at.plusSeconds(WEEK_SECONDS)),
            com.capstone.pause.core.social.ReceivedRequest("r2", "하은", at, at.plusSeconds(WEEK_SECONDS)),
        ),
        sent = listOf(com.capstone.pause.core.social.SentRequest("s1", at, at.plusSeconds(WEEK_SECONDS))),
    )
}

private const val WEEK_SECONDS = 604_800L

/** 서버에 닿지 못해 저장해 둔 목록을 보이는 상태: 안내 띠, 초록 점과 잔소리 버튼 꺼짐, 서버 기록 지우는 중 안내. */
@Preview(name = "light offline", heightDp = 1800)
@Preview(name = "dark offline", heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsOfflinePreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(
                overview = base.overview?.let { o ->
                    val friends = o.friends.map { it.copy(watching = false) }
                    o.copy(
                        isDemo = false,
                        sharing = SharingState.CONSENTED,
                        challenge = emptyList(),
                        friends = friends,
                        allFriends = friends,
                        ranking = StreakRanking.build(friends + o.me),
                        offline = true,
                    )
                },
                staleMinutes = 12,
                pokeFeedback = null,
                invite = InviteUiState(input = "K0P2", result = null),
            ),
            onAction = {},
        )
    }
}

/** 초대 링크로 열렸을 때 먼저 뜨는 확인 창. */
@Preview(name = "light link confirm", heightDp = 900)
@Preview(name = "dark link confirm", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsLinkConfirmPreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(
                overview = base.overview?.copy(
                    isDemo = false,
                    sharing = SharingState.CONSENTED,
                    challenge = emptyList(),
                ),
                invite = InviteUiState(pending = "K7P2QXM4", linkToConfirm = "K7P2QXM4"),
                pokeFeedback = null,
            ),
            onAction = {},
        )
    }
}

@Preview(name = "light poke dialog", heightDp = 900)
@Preview(name = "dark poke dialog", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun FriendsPokeDialogPreview() {
    val base = previewState()
    PauseTheme {
        FriendsScreen(
            uiState = base.copy(
                pokeFeedback = null,
                poke = PokeDraftState(
                    friendId = "m",
                    text = "내일 1교시잖아, 쇼츠 그만",
                    drafts = listOf("내일 아침 수업이잖아. 쇼츠 그만 보고 자라"),
                ),
            ),
            onAction = {},
        )
    }
}
