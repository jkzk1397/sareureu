package com.capstone.pause.feature.onboarding

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.AgeBand

/** 온보딩 진입점. 설정 화면에서 돌아올 때마다 권한 상태를 다시 읽는다. */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val defaultNickname = stringResource(R.string.onboarding_nickname_default)
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(OnboardingAction.RefreshPermissions(PermissionStatus.read(context)))
    }
    LifecycleResumeEffect(Unit) {
        viewModel.onAction(OnboardingAction.RefreshPermissions(PermissionStatus.read(context)))
        onPauseOrDispose {}
    }
    // 뒤로 가기는 이전 단계로만 간다(동의로 치지 않는다). 첫 단계에서는 시스템 뒤로 가기.
    BackHandler(enabled = state.step != OnboardingStep.AGE) { viewModel.onAction(OnboardingAction.Back) }

    OnboardingScreen(
        state = state,
        onAction = { action ->
            viewModel.onAction(action)
            if (action is OnboardingAction.PermissionChoice && action.accepted) {
                when (action.step) {
                    OnboardingStep.PERM_USAGE -> context.open(PermissionStatus.usageAccessIntent())

                    OnboardingStep.PERM_NOTIFICATIONS ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.open(PermissionStatus.notificationSettingsIntent(context))
                        }

                    OnboardingStep.PERM_OVERLAY -> context.open(PermissionStatus.overlayIntent(context))

                    else -> Unit
                }
            }
        },
        onCloseApp = { (context as? Activity)?.finish() },
        onOpenBatterySettings = { context.open(PermissionStatus.batterySettingsIntent()) },
        onFinish = { viewModel.finish(defaultNickname, onFinished) },
    )
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // 일부 제조사는 설정 화면이 없다. 상태는 돌아올 때 다시 읽는다.
    }
}

/** 온보딩(상태 없음). 단계마다 한 화면. */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    onCloseApp: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onFinish: () -> Unit,
) {
    when (state.step) {
        OnboardingStep.AGE -> AgeStep(state, onAction)

        OnboardingStep.UNDERAGE -> UnderageStep(state, onBack = {
            onAction(OnboardingAction.Back)
        }, onClose = onCloseApp)

        OnboardingStep.TERMS -> TermsStep(state, onAction)

        OnboardingStep.PROFILE -> ProfileStep(state, onAction)

        OnboardingStep.RHYTHM -> RhythmStep(state, onAction)

        OnboardingStep.GOALS -> GoalsStep(state, onAction)

        OnboardingStep.APPS -> AppsStep(state, onAction)

        OnboardingStep.PERM_USAGE, OnboardingStep.PERM_NOTIFICATIONS, OnboardingStep.PERM_OVERLAY -> PermissionStep(
            state,
            onAction,
        )

        OnboardingStep.AI -> AiStep(state, onAction)

        OnboardingStep.BATTERY -> BatteryStep(state, onAction, onOpenBatterySettings)

        OnboardingStep.DONE -> DoneStep(state, onAction, onFinish)
    }
}

@Composable
private fun PreviewStep(step: OnboardingStep) {
    PauseTheme {
        OnboardingScreen(
            state = OnboardingUiState(step = step, ageBand = AgeBand.AGE_18_24),
            onAction = {},
            onCloseApp = {},
            onOpenBatterySettings = {},
            onFinish = {},
        )
    }
}

@Preview(name = "age light")
@Preview(name = "age dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AgePreview() = PreviewStep(OnboardingStep.AGE)

@Preview(name = "terms light", heightDp = 1000)
@Preview(name = "terms dark", heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TermsPreview() = PreviewStep(OnboardingStep.TERMS)

@Preview(name = "rhythm light", heightDp = 1400)
@Preview(name = "rhythm dark", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RhythmPreview() = PreviewStep(OnboardingStep.RHYTHM)

@Preview(name = "goals light", heightDp = 1100)
@Preview(name = "goals dark", heightDp = 1100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GoalsPreview() = PreviewStep(OnboardingStep.GOALS)

@Preview(name = "usage light", heightDp = 1200)
@Preview(name = "usage dark", heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UsagePreview() = PreviewStep(OnboardingStep.PERM_USAGE)

@Preview(name = "ai light", heightDp = 1200)
@Preview(name = "ai dark", heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AiPreview() = PreviewStep(OnboardingStep.AI)

@Preview(name = "underage light")
@Preview(name = "underage dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnderagePreview() = PreviewStep(OnboardingStep.UNDERAGE)

@Preview(name = "done light")
@Preview(name = "done dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DonePreview() = PreviewStep(OnboardingStep.DONE)
