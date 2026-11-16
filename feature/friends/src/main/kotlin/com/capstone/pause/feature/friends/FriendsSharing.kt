package com.capstone.pause.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 공유 동의·철회 진행 상태. */
data class SharingUiState(val busy: Boolean = false, val failed: Boolean = false, val stopFailed: Boolean = false)

/**
 * 랭킹 공유 동의 화면(5-2). 무엇이 나가는지·안 나가는지·친구에게 무엇이 보이는지를 먼저 보여 주고,
 * 버튼을 눌렀을 때만 동의한다(뒤로 가기는 동의가 아님).
 */
@Composable
internal fun SharingConsentCard(sharing: SharingUiState, onAction: (FriendsAction) -> Unit) {
    PauseCard(title = stringResource(R.string.friends_sharing_title)) {
        Text(
            text = stringResource(R.string.friends_sharing_intro),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        ConsentBlock(R.string.friends_sharing_send_title, R.string.friends_sharing_send_body)
        ConsentBlock(R.string.friends_sharing_visible_title, R.string.friends_sharing_visible_body)
        ConsentBlock(R.string.friends_sharing_never_title, R.string.friends_sharing_never_body)
        Text(
            text = stringResource(R.string.friends_sharing_when),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        if (sharing.failed || sharing.stopFailed) {
            Text(
                text = stringResource(
                    if (sharing.failed) R.string.friends_sharing_failed else R.string.friends_sharing_stop_failed,
                ),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink,
            )
        }
        Button(
            onClick = { onAction(FriendsAction.GrantSharing) },
            enabled = !sharing.busy,
            shape = PauseTheme.shapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = PauseTheme.colors.ink,
                contentColor = PauseTheme.colors.bg,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(
                stringResource(if (sharing.busy) R.string.friends_sharing_busy else R.string.friends_sharing_agree),
            )
        }
    }
}

@Composable
private fun ConsentBlock(title: Int, body: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xxs),
        modifier = Modifier
            .fillMaxWidth()
            .background(PauseTheme.colors.surfaceAlt, PauseTheme.shapes.nested)
            .padding(PauseTheme.spacing.md),
    ) {
        Text(
            text = stringResource(title),
            style = PauseTheme.type.label,
            color = PauseTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = stringResource(body), style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink)
    }
}

/** 공유 그만하기 버튼 + 확인 창. 그만하면 서버의 내 기록과 친구 연결이 지워진다. */
@Composable
internal fun StopSharingButton(sharing: SharingUiState, onAction: (FriendsAction) -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { confirm = true },
        enabled = !sharing.busy,
        modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch),
    ) {
        Text(stringResource(R.string.friends_sharing_stop), color = PauseTheme.colors.ink2)
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.friends_sharing_stop_title)) },
            text = { Text(stringResource(R.string.friends_sharing_stop_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onAction(FriendsAction.RevokeSharing)
                }) { Text(stringResource(R.string.friends_sharing_stop_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.friends_cancel)) }
            },
        )
    }
}
