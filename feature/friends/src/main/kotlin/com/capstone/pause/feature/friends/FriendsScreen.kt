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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.component.ChangeDirection
import com.capstone.pause.core.designsystem.component.DemoBadge
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.RankRow
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.RankingView
import kotlin.math.abs
import kotlin.math.roundToInt

/** 친구 탭 진입점. */
@Composable
fun FriendsRoute(viewModel: FriendsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FriendsScreen(uiState = uiState, onAction = viewModel::onAction)
}

/** 친구 탭(상태 없음). 목업 08: 감소율 랭킹 → 주간 리그 → 잔소리 맡기기 → 함께 챌린지 → 친구 관리. */
@Composable
fun FriendsScreen(uiState: FriendsUiState, onAction: (FriendsAction) -> Unit, modifier: Modifier = Modifier) {
    var showInvite by rememberSaveable { mutableStateOf(false) }
    PauseScreenColumn(title = null, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.friends_title),
                style = PauseTheme.type.headline,
                color = PauseTheme.colors.ink,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            OutlinedButton(
                onClick = { showInvite = true },
                shape = PauseTheme.shapes.pill,
                modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
            ) {
                Text(text = stringResource(R.string.friends_invite), color = PauseTheme.colors.ink)
            }
        }
        val overview = uiState.overview ?: return@PauseScreenColumn
        if (overview.isDemo) DemoBadge()
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
        RankingCard(overview)
        LeagueCard()
        PokeCard(overview = overview, poke = uiState.poke, onAction = onAction)
        ChallengeCard(overview.challenge)
        ManageCard(overview = overview, onAction = onAction)
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

@Composable
private fun RankingCard(overview: FriendsOverview) {
    PauseCard(title = stringResource(R.string.friends_ranking_title)) {
        Text(
            text = stringResource(R.string.friends_ranking_subtitle),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        RankingRows(overview.ranking)
        overview.ranking.measuring.forEach { s ->
            Text(
                text = if (s.isMe) {
                    stringResource(R.string.friends_me_measuring, s.validDays)
                } else {
                    val res = if (hasFinalConsonant(s.nickname)) {
                        R.string.friends_measuring_eun
                    } else {
                        R.string.friends_measuring_neun
                    }
                    stringResource(res, s.nickname, s.validDays)
                },
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun RankingRows(ranking: RankingView) {
    Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs)) {
        ranking.rows.forEach { row ->
            val s = row.standing
            val rate = s.reductionRate ?: 0.0
            val percent = (abs(rate) * PERCENT).roundToInt()
            RankRow(
                rank = row.rank,
                name = s.nickname,
                reductionLabel = stringResource(
                    if (rate >= 0) R.string.friends_reduction_down else R.string.friends_reduction_up,
                    percent,
                ),
                coveragePercent = s.coverage?.let { (it * PERCENT).roundToInt() },
                isMe = s.isMe,
                reductionDirection = if (rate >= 0) ChangeDirection.DOWN else ChangeDirection.UP,
            )
        }
    }
}

@Composable
private fun LeagueCard() {
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
