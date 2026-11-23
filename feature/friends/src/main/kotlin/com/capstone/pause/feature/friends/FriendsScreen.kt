package com.capstone.pause.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.SharingState

/** 친구 탭 진입점. 화면에 보이는 동안만 친구·시청 중을 몇 초마다 새로 받는다. */
@Composable
fun FriendsRoute(viewModel: FriendsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.setVisible(true)
        onStopOrDispose { viewModel.setVisible(false) }
    }
    FriendsScreen(uiState = uiState, onAction = viewModel::onAction)
}

/**
 * 친구 탭(상태 없음, ADR-0012): 친구 연결(QR) → 친구 목록(시청 중·잔소리·연속 일수) → 목표 지킴 랭킹 → 함께 챌린지 → 친구 관리.
 * 시연 모드에서는 가상 친구로 보인다. 주간 리그 카드([LeagueCard])는 리그가 열릴 때까지 그리지 않는다.
 */
@Composable
fun FriendsScreen(uiState: FriendsUiState, onAction: (FriendsAction) -> Unit, modifier: Modifier = Modifier) {
    PauseScreenColumn(title = stringResource(R.string.friends_title), modifier = modifier) {
        val overview = uiState.overview ?: return@PauseScreenColumn
        if (overview.sharing == SharingState.NOT_CONSENTED) {
            SharingConsentCard(sharing = uiState.sharing, pendingInvite = uiState.invite.pending, onAction = onAction)
            return@PauseScreenColumn
        }
        if (!overview.available) {
            PauseCard(title = stringResource(R.string.friends_unavailable_title)) {
                Text(
                    text = stringResource(R.string.friends_unavailable_body),
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
            }
            return@PauseScreenColumn
        }
        if (overview.isDemo) {
            Text(
                text = stringResource(R.string.friends_demo_notice),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.onAccentContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PauseTheme.colors.accentContainer, PauseTheme.shapes.small)
                    .padding(horizontal = PauseTheme.spacing.sm, vertical = PauseTheme.spacing.sm),
            )
        }
        val remote = overview.sharing == SharingState.CONSENTED
        if (remote) InviteCard(invite = uiState.invite, onAction = onAction)
        FriendListCard(overview = overview, feedback = uiState.pokeFeedback, onAction = onAction)
        StreakRankingCard(overview.ranking)
        ChallengeCard(overview.challenge)
        ManageCard(overview = overview, onAction = onAction)
        if (remote) StopSharingButton(sharing = uiState.sharing, onAction = onAction)
        uiState.poke.friendId?.let { id ->
            overview.friends.firstOrNull {
                it.id == id
            }?.let { PokeDialog(friend = it, poke = uiState.poke, onAction = onAction) }
        }
    }
}

// TODO(#26): 친구 서버가 열리면 제목 줄 오른쪽에 다시 그리고 실제 초대 코드 흐름으로 잇는다.

/** '+ 초대' 버튼과 안내 창. 서버가 열릴 때까지 화면에서 뺐다. */
@Composable
internal fun InviteButton() {
    var showInvite by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showInvite = true },
        shape = PauseTheme.shapes.pill,
        modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(text = stringResource(R.string.friends_invite), color = PauseTheme.colors.ink)
    }
    if (showInvite) {
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text(stringResource(R.string.friends_invite_title)) },
            text = { Text(stringResource(R.string.friends_invite_body)) },
            confirmButton = {
                TextButton(onClick = { showInvite = false }) { Text(stringResource(R.string.friends_ok)) }
            },
        )
    }
}

// TODO(#26): 리그 그룹이 생기면 순위 카드 아래에 다시 그린다(LeagueState가 ComingSoon이 아닐 때).

/** 주간 리그 카드. 서버가 열릴 때까지 화면에서 뺐다. */
@Composable
internal fun LeagueCard() {
    val colors = PauseTheme.colors
    // 라이트는 잉크 바탕(목업 08), 다크는 표면 바탕.
    val container = if (colors.isDark) colors.surface else colors.ink
    val onContainer = if (colors.isDark) colors.ink else colors.bg
    Column(
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .background(container, PauseTheme.shapes.card)
            .padding(PauseTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.friends_league_title),
                style = PauseTheme.type.title,
                color = onContainer,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.friends_league_soon),
                style = PauseTheme.type.label,
                color = colors.accent,
            )
        }
        Text(
            text = stringResource(R.string.friends_league_body),
            style = PauseTheme.type.bodySmall,
            color = onContainer,
        )
    }
}

/** 받침이 있으면 true(조사 고르기). 한글이 아니면 false. */
internal fun hasFinalConsonant(word: String): Boolean {
    val last = word.lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last - '가') % JONGSEONG_COUNT != 0
}

internal const val PERCENT = 100
private const val JONGSEONG_COUNT = 28
