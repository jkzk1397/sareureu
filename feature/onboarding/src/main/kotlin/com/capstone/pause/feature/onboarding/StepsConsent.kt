package com.capstone.pause.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PermissionExplainer
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression

/** 권한 고지 문구 묶음. */
internal data class PermissionCopy(val title: Int, val what: Int, val why: Int, val notDone: Int, val ifDenied: Int)

/** 단계별 고지 문구. 고지마다 따로 보여 주고 다른 고지와 묶지 않는다(Play 정책). */
internal fun permissionCopy(step: OnboardingStep): PermissionCopy = when (step) {
    OnboardingStep.PERM_USAGE -> PermissionCopy(
        R.string.onboarding_perm_usage_title,
        R.string.onboarding_perm_usage_what,
        R.string.onboarding_perm_usage_why,
        R.string.onboarding_perm_usage_not,
        R.string.onboarding_perm_usage_denied,
    )

    OnboardingStep.PERM_NOTIFICATIONS -> PermissionCopy(
        R.string.onboarding_perm_noti_title,
        R.string.onboarding_perm_noti_what,
        R.string.onboarding_perm_noti_why,
        R.string.onboarding_perm_noti_not,
        R.string.onboarding_perm_noti_denied,
    )

    else -> PermissionCopy(
        R.string.onboarding_perm_overlay_title,
        R.string.onboarding_perm_overlay_what,
        R.string.onboarding_perm_overlay_why,
        R.string.onboarding_perm_overlay_not,
        R.string.onboarding_perm_overlay_denied,
    )
}

/** 지금 단계의 권한이 이미 허용됐는지. */
internal fun PermissionStates.granted(step: OnboardingStep): Boolean = when (step) {
    OnboardingStep.PERM_USAGE -> usage
    OnboardingStep.PERM_NOTIFICATIONS -> notifications
    OnboardingStep.PERM_OVERLAY -> overlay
    else -> true
}

/**
 * 5단계: 권한 하나. 허용 전에는 고지 화면([PermissionExplainer]) → '동의하고 설정으로'를 눌러야 설정을 연다.
 * 허용돼 있으면 확인 화면만. '지금은 안 할래'면 대체 흐름 안내 후 다음 단계.
 */
@Composable
internal fun PermissionStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    val step = state.step
    val copy = permissionCopy(step)
    if (state.permissions.granted(step)) {
        OnboardingFrame(
            step = step,
            onBack = { onAction(OnboardingAction.Back) },
            primaryText = stringResource(R.string.onboarding_next),
            onPrimary = { onAction(OnboardingAction.Next) },
        ) {
            BuddyAvatar(BuddyExpression.CHEER, PauseSizes.bubbleBuddy)
            StepHeader(stringResource(copy.title), stringResource(R.string.onboarding_perm_granted))
        }
        return
    }
    val primary = if (step ==
        OnboardingStep.PERM_NOTIFICATIONS
    ) {
        R.string.onboarding_perm_allow
    } else {
        R.string.onboarding_perm_open_settings
    }
    OnboardingFrame(step = step, onBack = { onAction(OnboardingAction.Back) }) {
        PermissionExplainer(
            title = stringResource(copy.title),
            what = stringResource(copy.what),
            why = stringResource(copy.why),
            notDone = stringResource(copy.notDone),
            ifDenied = stringResource(copy.ifDenied),
            primaryText = stringResource(primary),
            onPrimary = { onAction(OnboardingAction.PermissionChoice(step, accepted = true)) },
            secondaryText = stringResource(R.string.onboarding_perm_not_now),
            onSecondary = { onAction(OnboardingAction.PermissionChoice(step, accepted = false)) },
        )
        if (step == OnboardingStep.PERM_USAGE) {
            Text(
                stringResource(R.string.onboarding_perm_usage_steps),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

/** 6단계: AI 사용 방식 동의(국외 이전·무료 등급 고지 포함, 04 문서 5절). */
@Composable
internal fun AiStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_ai_confirm),
        onPrimary = { onAction(OnboardingAction.ConfirmAi) },
    ) {
        StepHeader(stringResource(R.string.onboarding_ai_title), stringResource(R.string.onboarding_ai_body))
        Column {
            AiOption(
                AiMode.CLOUD,
                R.string.onboarding_ai_cloud,
                R.string.onboarding_ai_cloud_desc,
                state.aiMode,
                onAction,
            )
            AiOption(
                AiMode.ON_DEVICE,
                R.string.onboarding_ai_device,
                R.string.onboarding_ai_device_desc,
                state.aiMode,
                onAction,
            )
            AiOption(AiMode.OFF, R.string.onboarding_ai_off, R.string.onboarding_ai_off_desc, state.aiMode, onAction)
        }
        if (state.aiMode == AiMode.CLOUD) {
            InfoCard(R.string.onboarding_ai_sent_title, R.string.onboarding_ai_sent_body)
            InfoCard(R.string.onboarding_ai_not_sent_title, R.string.onboarding_ai_not_sent_body)
            Text(
                stringResource(R.string.onboarding_ai_transfer),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun AiOption(mode: AiMode, title: Int, desc: Int, selected: AiMode, onAction: (OnboardingAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PauseTheme.spacing.minTouch)
            .selectable(selected = mode == selected, role = Role.RadioButton, onClick = {
                onAction(OnboardingAction.SetAiMode(mode))
            }),
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Spacer(Modifier.width(PauseTheme.spacing.xs))
        Column {
            Text(stringResource(title), style = PauseTheme.type.body, color = PauseTheme.colors.ink)
            Text(stringResource(desc), style = PauseTheme.type.bodySmall, color = PauseTheme.colors.ink2)
        }
    }
}

/** 6단계: 제조사 절전 안내(설정 화면만 연다, 예외를 직접 요청하지 않는다). */
@Composable
internal fun BatteryStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit, onOpenSettings: () -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_battery_open),
        onPrimary = onOpenSettings,
        secondaryText = stringResource(R.string.onboarding_next),
        onSecondary = { onAction(OnboardingAction.Next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_battery_title), stringResource(R.string.onboarding_battery_body))
    }
}

/** 끝: 저장하고 앱으로. */
@Composable
internal fun DoneStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit, onFinish: () -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_done_start),
        primaryEnabled = !state.saving,
        onPrimary = onFinish,
    ) {
        BuddyAvatar(BuddyExpression.CHEER, PauseSizes.bubbleBuddy)
        StepHeader(stringResource(R.string.onboarding_done_title), stringResource(R.string.onboarding_done_body))
        PauseCard {
            val granted = listOf(
                state.permissions.usage,
                state.permissions.notifications,
                state.permissions.overlay,
            ).count {
                it
            }
            Text(
                text = stringResource(R.string.onboarding_done_permissions, granted, PERMISSION_COUNT),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

private const val PERMISSION_COUNT = 3
