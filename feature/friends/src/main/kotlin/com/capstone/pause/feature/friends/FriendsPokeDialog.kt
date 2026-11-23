package com.capstone.pause.feature.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.capstone.pause.core.designsystem.buddy.FriendAvatar
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.PokeRules
import com.capstone.pause.core.social.Standing

/**
 * 잔소리 보내기 창(ADR-0012): 친구 목록의 말풍선을 누르면 열린다. 직접 쓰거나 AI 초안을 고른 뒤 보내면
 * 친구 폰에 '내 이름 + 이 말' 알림이 뜬다. 그 사이 친구가 숏폼을 멈추면 보내기 버튼이 꺼진다.
 */
@Composable
internal fun PokeDialog(friend: Standing, characterId: Int, poke: PokeDraftState, onAction: (FriendsAction) -> Unit) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    val canSend = friend.watching && !poke.sending
    AlertDialog(
        onDismissRequest = { onAction(FriendsAction.ClosePoke) },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                FriendAvatar(
                    characterId = characterId,
                    size = PauseSizes.rankAvatar,
                    expression = if (friend.watching) BuddyExpression.WATCHING else BuddyExpression.NEUTRAL,
                )
                Text(stringResource(R.string.friends_poke_dialog_title, friend.nickname))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                val body = if (friend.watching) {
                    R.string.friends_poke_dialog_body
                } else {
                    R.string.friends_poke_dialog_stopped
                }
                Text(
                    text = stringResource(body),
                    style = PauseTheme.type.bodySmall,
                    color = colors.ink2,
                )
                OutlinedTextField(
                    value = poke.text,
                    onValueChange = { onAction(FriendsAction.EditText(it)) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.friends_poke_rule), color = colors.ink3) },
                    supportingText = {
                        val length = poke.text.trim().length
                        Text(
                            text = stringResource(R.string.friends_poke_counter, length, PokeRules.MAX_LENGTH),
                            color = if (length > PokeRules.MAX_LENGTH) colors.nudgeText else colors.ink3,
                            style = PauseTheme.type.captionTabular,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                if (poke.drafts.isNotEmpty()) {
                    Text(
                        stringResource(R.string.friends_poke_draft_pick),
                        style = PauseTheme.type.caption,
                        color = colors.ink2,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        poke.drafts.forEach { d ->
                            ChoicePill(
                                text = d,
                                selected = poke.text == d,
                                onClick = { onAction(FriendsAction.PickDraft(d)) },
                            )
                        }
                    }
                }
                poke.result?.let { PokeFeedbackText(it) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(FriendsAction.SendPoke) },
                enabled = canSend,
                shape = PauseTheme.shapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = colors.social, contentColor = colors.surface),
                modifier = Modifier.heightIn(min = spacing.minTouch),
            ) {
                Text(stringResource(R.string.friends_poke_dialog_send), style = PauseTheme.type.label)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onAction(FriendsAction.ClosePoke) },
                modifier = Modifier.heightIn(min = spacing.minTouch),
            ) { Text(stringResource(R.string.friends_cancel), color = colors.ink) }
        },
    )
}

/** 보내지 못한 이유 한 줄. 한도·정지·서버 오류는 종류마다 다른 말을 한다(ADR-0014). */
@Composable
private fun entrustFailureText(reason: EntrustResult): String = when (reason) {
    EntrustResult.Empty, EntrustResult.Entrusted -> stringResource(R.string.friends_poke_empty)
    EntrustResult.TooLong -> stringResource(R.string.friends_poke_too_long)
    EntrustResult.Banned -> stringResource(R.string.friends_poke_banned)
    EntrustResult.Failed -> stringResource(R.string.friends_poke_failed)
    EntrustResult.NotWatching -> stringResource(R.string.friends_poke_not_watching)
    is EntrustResult.Limited -> stringResource(R.string.friends_poke_limited)
    EntrustResult.Suspended -> stringResource(R.string.friends_poke_suspended)
    is EntrustResult.Error -> socialErrorText(reason.error)
}

/** 보내기·초안 결과 한 줄. */
@Composable
internal fun PokeFeedbackText(result: PokeFeedback) {
    val colors = PauseTheme.colors
    val (text, ok) = when (result) {
        is PokeFeedback.Sent -> stringResource(
            if (result.demo) R.string.friends_poke_sent_demo_eul else R.string.friends_poke_sent_now_eul,
            result.nickname,
        ) to true

        PokeFeedback.DraftFailed -> stringResource(R.string.friends_poke_draft_failed) to false

        is PokeFeedback.Rejected -> entrustFailureText(result.reason) to false
    }
    Text(text = text, style = PauseTheme.type.bodySmall, color = if (ok) colors.success else colors.nudgeText)
}
