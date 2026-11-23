package com.capstone.pause.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 권한 고지 문구 묶음. [line]은 처음 보이는 한 문장, 나머지는 '자세히'. */
internal data class PermissionCopy(
    val title: Int,
    val line: Int,
    val what: Int,
    val why: Int,
    val notDone: Int,
    val ifDenied: Int,
)

/** 권한별 고지 문구. 권한마다 따로 보여 주고 다른 고지와 섞지 않는다(Play 정책). */
internal fun permissionCopy(kind: PermissionKind): PermissionCopy = when (kind) {
    PermissionKind.USAGE -> PermissionCopy(
        R.string.onboarding_perm_usage_row,
        R.string.onboarding_perm_usage_line,
        R.string.onboarding_perm_usage_what,
        R.string.onboarding_perm_usage_why,
        R.string.onboarding_perm_usage_not,
        R.string.onboarding_perm_usage_denied,
    )

    PermissionKind.NOTIFICATIONS -> PermissionCopy(
        R.string.onboarding_perm_noti_row,
        R.string.onboarding_perm_noti_line,
        R.string.onboarding_perm_noti_what,
        R.string.onboarding_perm_noti_why,
        R.string.onboarding_perm_noti_not,
        R.string.onboarding_perm_noti_denied,
    )

    PermissionKind.OVERLAY -> PermissionCopy(
        R.string.onboarding_perm_overlay_row,
        R.string.onboarding_perm_overlay_line,
        R.string.onboarding_perm_overlay_what,
        R.string.onboarding_perm_overlay_why,
        R.string.onboarding_perm_overlay_not,
        R.string.onboarding_perm_overlay_denied,
    )
}

/**
 * 4. 권한: 사용 기록(필수)·알림·다른 앱 위 표시를 한 화면에 세 줄로. 줄마다 한 문장 + [허용] 또는 허용됨.
 * [허용]을 누르면 고지 동의를 기록하고 설정을 연다(Route). 돌아오면 상태를 다시 읽는다.
 * 허용하지 않아도 다음으로 갈 수 있다(시연 모드는 권한 없이 돈다).
 */
@Composable
internal fun PermissionsStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_next),
        onPrimary = { onAction(OnboardingAction.Next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_perms_title), stringResource(R.string.onboarding_perms_body))
        PermissionKind.entries.forEach { kind ->
            PermissionRow(kind, state.permissions.granted(kind)) { onAction(OnboardingAction.AllowPermission(kind)) }
        }
        if (!state.permissions.usage) {
            Text(
                stringResource(R.string.onboarding_perm_usage_denied),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun PermissionRow(kind: PermissionKind, granted: Boolean, onAllow: () -> Unit) {
    val copy = permissionCopy(kind)
    val colors = PauseTheme.colors
    PauseCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(copy.title), style = PauseTheme.type.title, color = colors.ink)
                Text(stringResource(copy.line), style = PauseTheme.type.bodySmall, color = colors.ink2)
            }
            if (granted) {
                Text(
                    stringResource(R.string.onboarding_perm_granted_mark),
                    style = PauseTheme.type.label,
                    color = colors.ink,
                )
            } else {
                TextButton(onClick = onAllow, modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch)) {
                    Text(stringResource(R.string.onboarding_perm_allow_short), color = colors.ink)
                }
            }
        }
        MoreDetails {
            Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
                listOf(copy.what, copy.why, copy.notDone, copy.ifDenied).forEach {
                    Text(stringResource(it), style = PauseTheme.type.bodySmall, color = colors.ink)
                }
                if (kind == PermissionKind.USAGE) {
                    Text(
                        stringResource(R.string.onboarding_perm_usage_steps),
                        style = PauseTheme.type.bodySmall,
                        color = colors.ink2,
                    )
                }
            }
        }
    }
}

/**
 * 5. AI: "AI 친구 쓰기 / 나중에". 국외 이전 한 줄 + '자세히'(04 문서 5절 고지 전문).
 * 둘 다 동의 기록(`ConsentItem.AI_USE`) 뒤 프로필을 저장하고 시작한다.
 */
@Composable
internal fun AiStep(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    onFinish: (useAi: Boolean) -> Unit,
) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_ai_use),
        primaryEnabled = !state.saving,
        onPrimary = { onFinish(true) },
        secondaryText = stringResource(R.string.onboarding_later),
        onSecondary = { if (!state.saving) onFinish(false) },
    ) {
        StepHeader(stringResource(R.string.onboarding_ai_title), stringResource(R.string.onboarding_ai_line))
        MoreDetails {
            InfoCard(R.string.onboarding_ai_sent_title, R.string.onboarding_ai_sent_body)
            InfoCard(R.string.onboarding_ai_not_sent_title, R.string.onboarding_ai_not_sent_body)
            Text(
                stringResource(R.string.onboarding_ai_transfer),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
        Text(
            stringResource(R.string.onboarding_ai_later_note),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
    }
}
