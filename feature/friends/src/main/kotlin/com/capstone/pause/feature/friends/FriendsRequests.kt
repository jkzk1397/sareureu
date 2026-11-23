package com.capstone.pause.feature.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.social.AcceptResult
import com.capstone.pause.core.social.ReceivedRequest
import com.capstone.pause.core.social.SocialError

/**
 * 연결 요청(ADR-0014). 받은 요청은 닉네임만 보이고 수락·거절(신고·차단)을 고른다. 보낸 요청은 기다리는 중이고 취소할 수 있다.
 * 요청한 쪽에는 상대가 누구인지 보이지 않는다(수락 전에는 서버가 주지 않는다). 요청이 없으면 그리지 않는다.
 */
@Composable
internal fun RequestsCard(requests: RequestsUiState, onAction: (FriendsAction) -> Unit) {
    if (requests.received.isEmpty() && requests.sent.isEmpty() && requests.result == null) return
    var acceptTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var declineTarget by rememberSaveable { mutableStateOf<String?>(null) }
    PauseCard(title = stringResource(R.string.friends_requests_title)) {
        requests.result?.let { result ->
            Text(text = acceptResultText(result), style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink)
        }
        if (requests.received.isNotEmpty()) {
            SectionLabel(stringResource(R.string.friends_requests_received))
            requests.received.forEach { request ->
                ReceivedRow(
                    request = request,
                    enabled = requests.busyId == null,
                    onAccept = { acceptTarget = request.id },
                    onDecline = { declineTarget = request.id },
                )
            }
        }
        if (requests.sent.isNotEmpty()) {
            SectionLabel(stringResource(R.string.friends_requests_sent))
            Text(
                text = stringResource(R.string.friends_requests_sent_hint),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
            requests.sent.forEachIndexed { index, request ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.friends_requests_sent_row, index + 1),
                        style = PauseTheme.type.body,
                        color = PauseTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onAction(FriendsAction.CancelRequest(request.id)) },
                        enabled = requests.busyId == null,
                        modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
                    ) {
                        Text(stringResource(R.string.friends_requests_cancel), color = PauseTheme.colors.ink2)
                    }
                }
            }
        }
    }
    requests.received.firstOrNull { it.id == acceptTarget }?.let { request ->
        AlertDialog(
            onDismissRequest = { acceptTarget = null },
            title = { Text(stringResource(R.string.friends_requests_accept_title, request.fromNickname)) },
            text = { Text(stringResource(R.string.friends_requests_accept_body)) },
            confirmButton = {
                TextButton(onClick = {
                    acceptTarget = null
                    onAction(FriendsAction.AcceptRequest(request.id))
                }) { Text(stringResource(R.string.friends_requests_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { acceptTarget = null }) { Text(stringResource(R.string.friends_cancel)) }
            },
        )
    }
    requests.received.firstOrNull { it.id == declineTarget }?.let { request ->
        DeclineDialog(
            nickname = request.fromNickname,
            onDecline = { onAction(FriendsAction.DeclineRequest(request.id)) },
            onBlock = { onAction(FriendsAction.DeclineRequest(request.id, block = true)) },
            onReport = { onAction(FriendsAction.ReportRequest(request.id)) },
            onClose = { declineTarget = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = PauseTheme.type.label,
        color = PauseTheme.colors.ink,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ReceivedRow(request: ReceivedRequest, enabled: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = request.fromNickname.ifBlank { stringResource(R.string.friends_requests_no_name) },
            style = PauseTheme.type.body,
            color = PauseTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onDecline,
            enabled = enabled,
            modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(stringResource(R.string.friends_requests_decline), color = PauseTheme.colors.ink2)
        }
        OutlinedButton(
            onClick = onAccept,
            enabled = enabled,
            shape = PauseTheme.shapes.pill,
            modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(stringResource(R.string.friends_requests_accept), color = PauseTheme.colors.ink)
        }
    }
}

/** 거절 창: 거절만 / 거절하고 차단 / 신고하고 거절. 어느 것도 상대에게 알리지 않는다. */
@Composable
private fun DeclineDialog(
    nickname: String,
    onDecline: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.friends_requests_decline_title, nickname)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs)) {
                Text(stringResource(R.string.friends_requests_decline_body))
                for ((label, action) in listOf(
                    R.string.friends_requests_decline_only to onDecline,
                    R.string.friends_requests_decline_block to onBlock,
                    R.string.friends_requests_decline_report to onReport,
                )) {
                    TextButton(
                        onClick = {
                            action()
                            onClose()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = PauseTheme.spacing.minTouch),
                    ) { Text(stringResource(label), color = PauseTheme.colors.ink) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.friends_cancel)) }
        },
    )
}

@Composable
private fun acceptResultText(result: AcceptResult): String = when (result) {
    is AcceptResult.Accepted -> stringResource(
        if (hasFinalConsonant(result.nickname)) R.string.friends_connect_done_gwa else R.string.friends_connect_done_wa,
        result.nickname,
    )

    AcceptResult.NotFound -> stringResource(R.string.friends_requests_gone)

    AcceptResult.TooManyFriends -> stringResource(R.string.friends_requests_too_many_friends)

    is AcceptResult.Error -> socialErrorText(result.error)
}

/** 서버 오류를 종류별 문장으로(ADR-0014): 인터넷 없음, 점검, 너무 많이 시도함(남은 시간), 로그인 만료, 시계, 앱이 오래됨. */
@Composable
internal fun socialErrorText(error: SocialError): String = when (error) {
    SocialError.Offline -> stringResource(R.string.friends_error_offline)

    SocialError.Maintenance -> stringResource(R.string.friends_error_maintenance)

    is SocialError.TooMany -> {
        val seconds = error.retryAfterSeconds
        if (seconds == null) {
            stringResource(R.string.friends_error_too_many)
        } else {
            val minutes = ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
            if (minutes >= MINUTES_PER_HOUR) {
                val hours = (minutes + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR
                stringResource(R.string.friends_error_too_many_hours, hours)
            } else {
                stringResource(R.string.friends_error_too_many_minutes, minutes)
            }
        }
    }

    SocialError.SessionExpired -> stringResource(R.string.friends_error_session)

    SocialError.ClockSkew -> stringResource(R.string.friends_error_clock)

    SocialError.UpgradeRequired -> stringResource(R.string.friends_error_upgrade)

    SocialError.Unknown -> stringResource(R.string.friends_connect_failed)
}

private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60
