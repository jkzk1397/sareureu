package com.capstone.pause.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.ConnectResult
import java.time.LocalDateTime

/** 초대 코드 카드 상태. */
data class InviteUiState(
    val code: String? = null,
    /** 코드를 쓸 수 있는 마지막 시각(기기 시간대, ViewModel이 주입한 Clock으로 바꾼다). */
    val expires: LocalDateTime? = null,
    val input: String = "",
    val busy: Boolean = false,
    val inviteFailed: Boolean = false,
    val result: ConnectResult? = null,
    /** 초대 QR(친구 서버의 `/invite/{코드}` 주소, ADR-0012). */
    val qr: QrCode? = null,
    /** QR로 앱이 열려 받은, 아직 연결하지 않은 코드(공유 동의를 기다리는 중). */
    val pending: String? = null,
)

/**
 * 친구 초대(5-3, ADR-0012): 내 코드 만들기 → QR을 친구가 찍거나 코드를 직접 알려 주기, 친구 코드 입력 → 연결.
 * QR을 찍으면 앱이 열려 바로 연결되고, 앱이 없으면 설치 파일 화면으로 간다.
 * 코드를 만든 사람과 입력한 사람(또는 찍은 사람)이 각각 동의한 것이 된다. 이름·연락처는 묻지 않는다.
 */
@Composable
internal fun InviteCard(invite: InviteUiState, onAction: (FriendsAction) -> Unit) {
    PauseCard(title = stringResource(R.string.friends_invite_card_title)) {
        val code = invite.code
        invite.qr?.let { qr ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                QrImage(
                    code = qr,
                    description = stringResource(R.string.friends_invite_qr_a11y),
                    modifier = Modifier.fillMaxWidth(QR_WIDTH_FRACTION),
                )
            }
            Text(
                text = stringResource(R.string.friends_invite_qr_hint),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
        if (code != null) {
            val label = code.chunked(CODE_GROUP).joinToString(" ")
            val a11y = stringResource(R.string.friends_invite_code_a11y, code.toList().joinToString(" "))
            Text(
                text = label,
                style = PauseTheme.type.display,
                color = PauseTheme.colors.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PauseTheme.colors.surfaceAlt, PauseTheme.shapes.nested)
                    .padding(PauseTheme.spacing.md)
                    .semantics { contentDescription = a11y },
            )
            invite.expires?.let { at ->
                Text(
                    text = stringResource(
                        R.string.friends_invite_code_expires,
                        at.monthValue,
                        at.dayOfMonth,
                        at.hour,
                        at.minute,
                    ),
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.friends_invite_code_hint),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
        if (invite.inviteFailed) {
            Text(
                text = stringResource(R.string.friends_invite_failed),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink,
            )
        }
        OutlinedButton(
            onClick = { onAction(FriendsAction.CreateInvite) },
            enabled = !invite.busy,
            shape = PauseTheme.shapes.pill,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(
                stringResource(if (code == null) R.string.friends_invite_make else R.string.friends_invite_remake),
                color = PauseTheme.colors.ink,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        ) {
            OutlinedTextField(
                value = invite.input,
                onValueChange = { onAction(FriendsAction.EditCode(it)) },
                label = { Text(stringResource(R.string.friends_invite_input)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = { onAction(FriendsAction.Connect) },
                enabled = !invite.busy && invite.input.isNotBlank(),
                shape = PauseTheme.shapes.pill,
                modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
            ) {
                Text(stringResource(R.string.friends_invite_connect), color = PauseTheme.colors.ink)
            }
        }
        if (invite.busy && invite.code == null && invite.input.isBlank()) {
            Text(
                text = stringResource(R.string.friends_invite_connecting),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
        invite.result?.let { result ->
            Text(
                text = when (result) {
                    is ConnectResult.Connected -> stringResource(
                        if (hasFinalConsonant(result.nickname)) {
                            R.string.friends_connect_done_gwa
                        } else {
                            R.string.friends_connect_done_wa
                        },
                        result.nickname,
                    )

                    ConnectResult.NotFound -> stringResource(R.string.friends_connect_not_found)

                    ConnectResult.OwnCode -> stringResource(R.string.friends_connect_own)

                    ConnectResult.AlreadyFriends -> stringResource(R.string.friends_connect_already)

                    ConnectResult.Unavailable -> stringResource(R.string.friends_connect_unavailable)

                    ConnectResult.Failed -> stringResource(R.string.friends_connect_failed)

                    // TODO(#59, 5-B): 요청 뒤 수락 화면과 오류 종류별 문구는 5-B에서.
                    is ConnectResult.Requested,
                    ConnectResult.TooManyRequests,
                    is ConnectResult.InvalidCode,
                    is ConnectResult.Error,
                    -> stringResource(R.string.friends_connect_failed)
                },
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink,
            )
        }
    }
}

private const val CODE_GROUP = 3
private const val QR_WIDTH_FRACTION = 0.7f
