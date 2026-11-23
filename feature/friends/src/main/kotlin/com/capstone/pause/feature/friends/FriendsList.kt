package com.capstone.pause.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.Reveal
import com.capstone.pause.core.designsystem.theme.PauseColors
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.RankingView
import com.capstone.pause.core.social.Standing

/**
 * 친구 목록(ADR-0012): 이름 + (시청 중이면) 초록 점 ……… 말풍선(잔소리) · 숫자 불꽃(연속 일수).
 * 말풍선은 지금 시청 중인 친구에게만 켜진다. 차단한 친구는 목록에 없다(친구 관리에서 본다).
 */
@Composable
internal fun FriendListCard(overview: FriendsOverview, feedback: PokeFeedback?, onAction: (FriendsAction) -> Unit) {
    val colors = PauseTheme.colors
    PauseCard(title = stringResource(R.string.friends_list_title)) {
        if (overview.friends.isEmpty()) {
            Text(stringResource(R.string.friends_list_empty), style = PauseTheme.type.bodySmall, color = colors.ink2)
            return@PauseCard
        }
        Text(stringResource(R.string.friends_list_hint), style = PauseTheme.type.bodySmall, color = colors.ink2)
        Column {
            overview.friends.forEach { friend ->
                FriendListRow(friend = friend, onPoke = { onAction(FriendsAction.OpenPoke(friend.id)) })
            }
        }
        feedback?.let { PokeFeedbackText(it) }
    }
}

@Composable
private fun FriendListRow(friend: Standing, onPoke: () -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val rowLabel = if (friend.watching) {
        stringResource(R.string.friends_row_watching_a11y, friend.nickname, friend.streakDays)
    } else {
        stringResource(R.string.friends_row_a11y, friend.nickname, friend.streakDays)
    }
    val pokeLabel = if (friend.watching) {
        stringResource(R.string.friends_poke_button_a11y, friend.nickname)
    } else {
        stringResource(R.string.friends_poke_button_off_a11y, friend.nickname)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouch),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { contentDescription = rowLabel },
        ) {
            InitialAvatar(friend.nickname)
            Text(friend.nickname, style = PauseTheme.type.body, color = colors.ink)
            if (friend.watching) WatchingDot()
        }
        IconButton(
            onClick = onPoke,
            enabled = friend.watching,
            modifier = Modifier
                .size(spacing.minTouch)
                .semantics { contentDescription = pokeLabel },
        ) {
            Icon(
                painter = painterResource(R.drawable.friends_ic_poke),
                contentDescription = null,
                tint = if (friend.watching) colors.social else colors.ink3,
            )
        }
        StreakFlame(days = friend.streakDays)
    }
}

/** 이름 첫 글자 동그라미. */
@Composable
private fun InitialAvatar(name: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(PauseSizes.rankAvatar)
            .background(PauseTheme.colors.social, PauseTheme.shapes.pill),
    ) {
        Text(name.take(1), style = PauseTheme.type.label, color = PauseTheme.colors.surface)
    }
}

/** 이름 바로 오른쪽 초록 점: 최근 5초 안에 숏폼을 보고 있었다(서버 판정). */
@Composable
private fun WatchingDot() {
    val label = stringResource(R.string.friends_watching_dot_a11y)
    Box(
        modifier = Modifier
            .size(PauseSizes.legendDot)
            .background(PauseTheme.colors.success, PauseTheme.shapes.pill)
            .semantics { contentDescription = label },
    )
}

/** 숫자가 적힌 불꽃(연속 일수). 숫자는 늘 어두운 잉크라 라이트·다크 모두 불꽃 위에서 읽힌다. */
@Composable
internal fun StreakFlame(days: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.friends_streak_a11y, days)
    IconWithNumber(
        icon = R.drawable.friends_ic_flame,
        tint = PauseTheme.colors.streak,
        number = days,
        numberOffset = FLAME_NUMBER_OFFSET,
        modifier = modifier.semantics { contentDescription = label },
    )
}

/**
 * 친구 랭킹(ADR-0012): 목표 이하 연속 일수가 긴 순서, 나를 포함해 모두. 1위 금 왕관, 2·3위 은·동 메달, 그 아래는 숫자.
 */
@Composable
internal fun StreakRankingCard(ranking: RankingView) {
    if (ranking.rows.isEmpty()) return
    val colors = PauseTheme.colors
    PauseCard(title = stringResource(R.string.friends_streak_ranking_title)) {
        var detail by rememberSaveable { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch)
                .clickable(role = Role.Button) { detail = !detail },
        ) {
            Text(
                text = stringResource(R.string.friends_streak_ranking_subtitle),
                style = PauseTheme.type.bodySmall,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (detail) R.string.friends_more_hide else R.string.friends_more_show),
                style = PauseTheme.type.label,
                color = colors.ink,
            )
        }
        Reveal(detail) {
            Text(
                text = stringResource(R.string.friends_streak_ranking_detail),
                style = PauseTheme.type.bodySmall,
                color = colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceAlt, PauseTheme.shapes.nested)
                    .padding(PauseTheme.spacing.md),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs)) {
            ranking.rows.forEach { row -> StreakRankRow(rank = row.rank, standing = row.standing) }
        }
    }
}

@Composable
private fun StreakRankRow(rank: Int, standing: Standing) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val name = if (standing.isMe) stringResource(R.string.friends_rank_me) else standing.nickname
    val label = stringResource(R.string.friends_rank_a11y, rank, name, standing.streakDays)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = spacing.minTouch)
            .then(
                if (standing.isMe) {
                    Modifier.background(colors.accentContainer, PauseTheme.shapes.nested)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spacing.xs)
            .clearAndSetSemantics { contentDescription = label },
    ) {
        RankBadge(rank)
        Text(
            text = name,
            style = PauseTheme.type.body,
            fontWeight = if (standing.isMe) FontWeight.SemiBold else null,
            color = if (standing.isMe) colors.onAccentContainer else colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.friends_streak_days, standing.streakDays),
            style = PauseTheme.type.label,
            color = if (standing.isMe) colors.onAccentContainer else colors.ink,
        )
    }
}

/** 이름 왼쪽 순위 표시: 1 = 금 왕관, 2 = 은 메달, 3 = 동 메달(숫자를 안에 같이), 4위부터 숫자만. */
@Composable
private fun RankBadge(rank: Int) {
    val colors = PauseTheme.colors
    when (rank) {
        1 -> IconWithNumber(R.drawable.friends_ic_crown, colors.rankGold, rank, CROWN_NUMBER_OFFSET)

        2 -> IconWithNumber(R.drawable.friends_ic_medal, colors.rankSilver, rank, MEDAL_NUMBER_OFFSET)

        3 -> IconWithNumber(R.drawable.friends_ic_medal, colors.rankBronze, rank, MEDAL_NUMBER_OFFSET)

        else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PauseSizes.rankAvatar)) {
            Text(rank.toString(), style = PauseTheme.type.title, color = colors.ink2)
        }
    }
}

/** 채운 아이콘 위에 숫자([numberOffset]만큼 아래로). 숫자는 늘 어두운 잉크. */
@Composable
private fun IconWithNumber(icon: Int, tint: Color, number: Int, numberOffset: Float, modifier: Modifier = Modifier) {
    val size: Dp = PauseSizes.rankAvatar
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size))
        Text(
            text = number.toString(),
            style = if (number >= THREE_DIGITS) PauseTheme.type.caption else PauseTheme.type.label,
            fontWeight = FontWeight.Bold,
            color = PauseColors.BuddyInk,
            modifier = Modifier.offset(y = size * numberOffset),
        )
    }
}

private const val THREE_DIGITS = 100
private const val FLAME_NUMBER_OFFSET = 0.12f
private const val CROWN_NUMBER_OFFSET = 0.08f
private const val MEDAL_NUMBER_OFFSET = 0.17f
