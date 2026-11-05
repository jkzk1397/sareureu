package com.capstone.pause.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.PokeRules

/** 잔소리 맡기기 카드: 친구 고르기 → 글(40자) → AI 초안 → 맡기기. */
@Composable
internal fun PokeCard(overview: FriendsOverview, poke: PokeDraftState, onAction: (FriendsAction) -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val selected = overview.friends.firstOrNull { it.id == poke.friendId }
    PauseCard(title = stringResource(R.string.friends_poke_title)) {
        Text(stringResource(R.string.friends_poke_subtitle), style = PauseTheme.type.bodySmall, color = colors.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            overview.friends.forEach { f ->
                val isSelected = f.id == poke.friendId
                val label = stringResource(R.string.friends_avatar_a11y, f.nickname)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onAction(FriendsAction.SelectFriend(f.id)) },
                        )
                        .semantics { contentDescription = label },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(PauseTheme.spacing.minTouch + spacing.xs)
                            .then(
                                if (isSelected) {
                                    Modifier.border(spacing.hairline * 2, colors.ink, PauseTheme.shapes.pill)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(PauseSizes.rankAvatar + spacing.xs)
                                .background(colors.social, PauseTheme.shapes.pill),
                        ) {
                            Text(f.nickname.take(1), style = PauseTheme.type.title, color = colors.surface)
                        }
                    }
                    Text(
                        f.nickname,
                        style = PauseTheme.type.caption,
                        color = if (isSelected) colors.ink else colors.ink2,
                    )
                }
            }
        }
        if (selected == null) return@PauseCard
        Text(
            text = stringResource(R.string.friends_poke_to_eul, selected.nickname),
            style = PauseTheme.type.label,
            color = colors.ink,
        )
        OutlinedTextField(
            value = poke.text,
            onValueChange = { onAction(FriendsAction.EditText(it)) },
            placeholder = { Text(stringResource(R.string.friends_poke_hint), color = colors.ink3) },
            supportingText = {
                val over = poke.text.trim().length > PokeRules.MAX_LENGTH
                Text(
                    text = stringResource(R.string.friends_poke_counter, poke.text.trim().length, PokeRules.MAX_LENGTH),
                    color = if (over) colors.nudgeText else colors.ink3,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(
                onClick = { onAction(FriendsAction.RequestDraft) },
                enabled = !poke.drafting,
                shape = PauseTheme.shapes.pill,
                modifier = Modifier.heightIn(min = spacing.minTouch),
            ) {
                Text(
                    text = stringResource(
                        when {
                            poke.drafting -> R.string.friends_poke_drafting
                            poke.drafts.isEmpty() -> R.string.friends_poke_draft
                            else -> R.string.friends_poke_draft_again
                        },
                    ),
                    color = colors.ink,
                )
            }
            Text(stringResource(R.string.friends_poke_rule), style = PauseTheme.type.caption, color = colors.ink2)
        }
        if (poke.drafts.isNotEmpty()) {
            Text(stringResource(R.string.friends_poke_draft_pick), style = PauseTheme.type.caption, color = colors.ink2)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                poke.drafts.forEach { d ->
                    ChoicePill(text = d, selected = poke.text == d, onClick = { onAction(FriendsAction.PickDraft(d)) })
                }
            }
        }
        val notice = if (hasFinalConsonant(selected.nickname)) {
            R.string.friends_poke_notice_i
        } else {
            R.string.friends_poke_notice_ga
        }
        Text(stringResource(notice, selected.nickname), style = PauseTheme.type.bodySmall, color = colors.ink2)
        Button(
            onClick = { onAction(FriendsAction.Entrust) },
            shape = PauseTheme.shapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = colors.social, contentColor = colors.surface),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = spacing.minTouch),
        ) {
            Text(stringResource(R.string.friends_poke_send), style = PauseTheme.type.label)
        }
        poke.result?.let { FeedbackText(it) }
    }
}

@Composable
private fun FeedbackText(result: PokeFeedback) {
    val colors = PauseTheme.colors
    val (text, ok) = when (result) {
        is PokeFeedback.Entrusted -> stringResource(R.string.friends_poke_sent_eul, result.nickname) to true

        PokeFeedback.DraftFailed -> stringResource(R.string.friends_poke_draft_failed) to false

        is PokeFeedback.Rejected -> stringResource(
            when (result.reason) {
                EntrustResult.Empty -> R.string.friends_poke_empty
                EntrustResult.TooLong -> R.string.friends_poke_too_long
                EntrustResult.Banned -> R.string.friends_poke_banned
                EntrustResult.Entrusted -> R.string.friends_poke_empty
            },
        ) to false
    }
    Text(text = text, style = PauseTheme.type.bodySmall, color = if (ok) colors.success else colors.nudgeText)
}
