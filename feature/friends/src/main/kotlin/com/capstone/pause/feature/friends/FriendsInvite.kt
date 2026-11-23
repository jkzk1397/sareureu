package com.capstone.pause.feature.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.AcceptResult
import com.capstone.pause.core.social.CodeCheck
import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.InviteCodes
import com.capstone.pause.core.social.ReceivedRequest
import com.capstone.pause.core.social.SentRequest
import com.capstone.pause.core.social.SocialLimits
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
    /** QR·링크로 앱이 열려 받은, 아직 요청하지 않은 코드. */
    val pending: String? = null,
    /** 확인 창으로 물어볼 코드([pending]이 있고 공유에 동의한 상태일 때만). */
    val linkToConfirm: String? = null,
    /** 초대 주소(공유 버튼으로 보낼 것). */
    val link: String? = null,
    /** 내 QR 화면이 열려 있는지. */
    val qrOpen: Boolean = false,
    /** QR 화면을 띄워 둔 동안 받은 요청을 자동으로 확인하는 중인지(최대 2분). */
    val qrWatching: Boolean = false,
)

/** 연결 요청 카드 상태. */
data class RequestsUiState(
    val received: List<ReceivedRequest> = emptyList(),
    val sent: List<SentRequest> = emptyList(),
    /** 처리 중인 요청 ID. */
    val busyId: String? = null,
    /** 방금 수락한 결과(안내 한 줄). */
    val result: AcceptResult? = null,
)

/**
 * 친구 초대(ADR-0012, ADR-0014): 내 코드 만들기 → QR을 친구가 찍거나 코드를 알려 주기, 친구 코드 입력 → **연결 요청**.
 * 코드 주인이 수락해야 친구가 된다. 이름·연락처는 묻지 않는다.
 * 코드는 한 번 누르면 복사되고, 공유 버튼은 안드로이드 공유 시트로 초대 주소와 안내 문장을 보낸다.
 */
@Composable
internal fun InviteCard(invite: InviteUiState, onAction: (FriendsAction) -> Unit) {
    PauseCard(title = stringResource(R.string.friends_invite_card_title)) {
        val code = invite.code
        if (code != null) {
            MyCode(code = code, invite = invite, onAction = onAction)
        } else {
            Text(
                text = stringResource(R.string.friends_invite_code_hint_v2),
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
        CodeInput(invite = invite, onAction = onAction)
    }
}

/** 내 코드: 누르면 복사, QR 보기, 공유, 취소. */
@Composable
private fun MyCode(code: String, invite: InviteUiState, onAction: (FriendsAction) -> Unit) {
    val context = LocalContext.current
    var copied by rememberSaveable(code) { mutableStateOf(false) }
    val label = code.chunked(CODE_GROUP).joinToString(" ")
    val a11y = stringResource(R.string.friends_invite_code_a11y, code.toList().joinToString(" "))
    val copyLabel = stringResource(R.string.friends_invite_copy_a11y)
    val clipLabel = stringResource(R.string.friends_invite_card_title)
    Text(
        text = label,
        style = PauseTheme.type.display,
        color = PauseTheme.colors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .background(PauseTheme.colors.surfaceAlt, PauseTheme.shapes.nested)
            .clickable(role = Role.Button, onClickLabel = copyLabel) {
                copied = copyText(context, clipLabel, code)
            }
            .padding(PauseTheme.spacing.md)
            .semantics { contentDescription = a11y },
    )
    Text(
        text = stringResource(if (copied) R.string.friends_invite_copied else R.string.friends_invite_copy_hint),
        style = PauseTheme.type.bodySmall,
        color = PauseTheme.colors.ink2,
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
    Row(horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs), modifier = Modifier.fillMaxWidth()) {
        if (invite.qr != null) {
            OutlinedButton(
                onClick = { onAction(FriendsAction.ShowQr(!invite.qrOpen)) },
                shape = PauseTheme.shapes.pill,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = PauseTheme.spacing.minTouch),
            ) {
                Text(
                    stringResource(
                        if (invite.qrOpen) R.string.friends_invite_qr_hide else R.string.friends_invite_qr_show,
                    ),
                    color = PauseTheme.colors.ink,
                )
            }
        }
        val shareText = stringResource(R.string.friends_invite_share_text, invite.link ?: code, code)
        val shareTitle = stringResource(R.string.friends_invite_share)
        OutlinedButton(
            onClick = { shareText(context, shareTitle, shareText) },
            shape = PauseTheme.shapes.pill,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(shareTitle, color = PauseTheme.colors.ink)
        }
    }
    val qr = invite.qr
    if (invite.qrOpen && qr != null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            QrImage(
                code = qr,
                description = stringResource(R.string.friends_invite_qr_a11y_v2),
                modifier = Modifier.fillMaxWidth(QR_WIDTH_FRACTION),
            )
        }
        Text(
            text = stringResource(R.string.friends_invite_qr_hint_v2),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        Text(
            text = stringResource(
                if (invite.qrWatching) R.string.friends_invite_qr_watching else R.string.friends_invite_qr_watch_ended,
            ),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
    }
    TextButton(
        onClick = { onAction(FriendsAction.CancelInvite) },
        enabled = !invite.busy,
        modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(stringResource(R.string.friends_invite_cancel), color = PauseTheme.colors.ink2)
    }
}

/** 친구 코드 입력: 다듬은 값만 보이고, 글자표에 없는 글자는 보내기 전에 알려 준다. */
@Composable
private fun CodeInput(invite: InviteUiState, onAction: (FriendsAction) -> Unit) {
    val check = InviteCodes.check(invite.input)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
    ) {
        OutlinedTextField(
            value = invite.input,
            onValueChange = { onAction(FriendsAction.EditCode(it)) },
            label = { Text(stringResource(R.string.friends_invite_input_v2, SocialLimits.CODE_LENGTH)) },
            singleLine = true,
            isError = check is CodeCheck.BadCharacters,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
            ),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { onAction(FriendsAction.Connect) },
            enabled = !invite.busy && check is CodeCheck.Ok,
            shape = PauseTheme.shapes.pill,
            modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(stringResource(R.string.friends_invite_request), color = PauseTheme.colors.ink)
        }
    }
    if (check is CodeCheck.BadCharacters) {
        Text(
            text = stringResource(R.string.friends_invite_bad_chars, check.characters.joinToString(" ")),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink,
        )
    }
    if (invite.busy && invite.code == null && invite.input.isBlank()) {
        Text(
            text = stringResource(R.string.friends_invite_requesting),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
    }
    invite.result?.let { result ->
        Text(text = connectResultText(result), style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink)
    }
}

/** 연결 요청 결과 한 줄. 실패는 종류마다 다른 말을 한다. */
@Composable
private fun connectResultText(result: ConnectResult): String = when (result) {
    is ConnectResult.Requested -> stringResource(R.string.friends_connect_requested)

    is ConnectResult.Connected -> stringResource(
        if (hasFinalConsonant(result.nickname)) R.string.friends_connect_done_gwa else R.string.friends_connect_done_wa,
        result.nickname,
    )

    ConnectResult.NotFound -> stringResource(R.string.friends_connect_not_found)

    ConnectResult.OwnCode -> stringResource(R.string.friends_connect_own)

    ConnectResult.AlreadyFriends -> stringResource(R.string.friends_connect_already)

    ConnectResult.Unavailable -> stringResource(R.string.friends_connect_unavailable)

    ConnectResult.TooManyRequests -> stringResource(R.string.friends_connect_too_many_requests)

    is ConnectResult.InvalidCode -> when (val check = result.check) {
        is CodeCheck.BadCharacters ->
            stringResource(R.string.friends_invite_bad_chars, check.characters.joinToString(" "))

        else -> stringResource(R.string.friends_invite_wrong_length, SocialLimits.CODE_LENGTH)
    }

    is ConnectResult.Error -> socialErrorText(result.error)

    ConnectResult.Failed -> stringResource(R.string.friends_connect_failed)
}

/** 클립보드에 복사. 됐으면 true. */
private fun copyText(context: Context, label: String, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    true
}.getOrDefault(false)

/** 안드로이드 공유 시트 열기(어느 앱으로 보낼지는 사용자가 고른다). */
private fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val CODE_GROUP = 4
private const val QR_WIDTH_FRACTION = 0.7f
