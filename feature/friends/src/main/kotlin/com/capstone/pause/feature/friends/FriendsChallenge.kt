package com.capstone.pause.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.Reveal
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.ChallengeRow
import com.capstone.pause.core.social.FriendsOverview
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** 함께 챌린지: 이번 주 월~일 '한밤중 0분' 달성 칸(달성 = 잉크, 아님 = 옅은 칸, 아직 안 온 날 = 빈 테두리). */
@Composable
internal fun ChallengeCard(rows: List<ChallengeRow>) {
    if (rows.isEmpty()) return
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    PauseCard(
        title = stringResource(R.string.friends_challenge_title),
        trailing = {
            Text(stringResource(R.string.friends_challenge_label), style = PauseTheme.type.label, color = colors.ink2)
        },
    ) {
        rows.forEach { row ->
            val name = if (row.isMe) stringResource(R.string.friends_challenge_me) else row.nickname
            val a11y = stringResource(R.string.friends_challenge_a11y, name, row.days.count { it == true })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = a11y },
            ) {
                Text(name, style = PauseTheme.type.label, color = colors.ink, modifier = Modifier.width(NAME_WIDTH))
                row.days.forEach { done ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PauseSizes.heatmapCell)
                            .then(
                                when (done) {
                                    true -> Modifier.background(colors.ink, PauseTheme.shapes.small)
                                    false -> Modifier.background(colors.surfaceAlt, PauseTheme.shapes.small)
                                    null -> Modifier.border(spacing.hairline, colors.line, PauseTheme.shapes.small)
                                },
                            ),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(NAME_WIDTH))
            DayOfWeek.entries.forEach { d ->
                Text(
                    text = d.getDisplayName(TextStyle.NARROW, Locale.KOREAN),
                    style = PauseTheme.type.caption,
                    color = colors.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 친구 관리(로컬): 이름 옆 ⋮ 메뉴 안에 차단·신고. 차단하면 순위·잔소리·챌린지에서 빠진다.
 * 자주 쓰는 것이 아니라 처음에는 접혀 있다.
 */
@Composable
internal fun ManageCard(overview: FriendsOverview, onAction: (FriendsAction) -> Unit) {
    val colors = PauseTheme.colors
    var reportTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf(false) }
    val entries = overview.allFriends.map { it.id to it.nickname }
    if (entries.isEmpty()) return
    TextButton(
        onClick = { open = !open },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(
            stringResource(if (open) R.string.friends_manage_hide else R.string.friends_manage_show),
            style = PauseTheme.type.label,
            color = colors.ink,
        )
    }
    Reveal(open) {
        PauseCard(title = stringResource(R.string.friends_manage_title)) {
            entries.forEach { (id, name) ->
                FriendRow(
                    name = name,
                    blocked = id in overview.blocked,
                    reported = id in overview.reported,
                    onBlockToggle = { blocked ->
                        onAction(if (blocked) FriendsAction.Unblock(id) else FriendsAction.Block(id))
                    },
                    onReport = { reportTarget = id },
                )
            }
        }
    }
    val target = reportTarget
    if (target != null) {
        val name = entries.firstOrNull { it.first == target }?.second.orEmpty()
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text(stringResource(R.string.friends_report_title, name)) },
            text = { Text(stringResource(R.string.friends_report_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(FriendsAction.Report(target))
                    reportTarget = null
                }) { Text(stringResource(R.string.friends_report_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text(stringResource(R.string.friends_cancel)) }
            },
        )
    }
}

private val NAME_WIDTH = PauseSizes.rankAvatar + PauseSizes.legendDot

/** 친구 한 줄: 이름(+상태) + ⋮ 메뉴(차단·신고). */
@Composable
private fun FriendRow(
    name: String,
    blocked: Boolean,
    reported: Boolean,
    onBlockToggle: (Boolean) -> Unit,
    onReport: () -> Unit,
) {
    val colors = PauseTheme.colors
    var menu by rememberSaveable(name) { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.friends_menu_a11y, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = PauseTheme.type.body, color = colors.ink)
            val state = listOfNotNull(
                if (blocked) stringResource(R.string.friends_blocked_label) else null,
                if (reported) stringResource(R.string.friends_reported) else null,
            )
            if (state.isNotEmpty()) {
                Text(state.joinToString(STATE_SEPARATOR), style = PauseTheme.type.caption, color = colors.ink2)
            }
        }
        Box {
            TextButton(
                onClick = { menu = true },
                modifier = Modifier
                    .heightIn(min = PauseTheme.spacing.minTouch)
                    .semantics { contentDescription = menuLabel },
            ) {
                Text(stringResource(R.string.friends_menu_mark), style = PauseTheme.type.title, color = colors.ink)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (blocked) R.string.friends_unblock else R.string.friends_block)) },
                    onClick = {
                        menu = false
                        onBlockToggle(blocked)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (reported) R.string.friends_reported else R.string.friends_report))
                    },
                    enabled = !reported,
                    onClick = {
                        menu = false
                        onReport()
                    },
                )
            }
        }
    }
}

private const val STATE_SEPARATOR = " · "
