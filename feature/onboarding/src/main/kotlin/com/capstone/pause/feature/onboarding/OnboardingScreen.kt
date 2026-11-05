package com.capstone.pause.feature.onboarding

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 온보딩 진입점. TODO(BUILD_REPORT#1): 2단계에서 6단계 흐름(연령·프로필·리듬·목표·추적 앱·권한·AI 동의)으로 바꾼다. */
@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingScreen(onStart = { viewModel.finish(onFinished) })
}

/** 온보딩 첫 화면(상태 없음). */
@Composable
fun OnboardingScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    PauseScreenColumn(title = stringResource(DesignR.string.app_name), modifier = modifier) {
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = PauseTheme.type.body,
            color = PauseTheme.colors.ink2,
        )
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = PauseTheme.colors.primary,
                contentColor = PauseTheme.colors.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = PauseTheme.spacing.minTouch),
        ) {
            Text(text = stringResource(R.string.onboarding_start), style = PauseTheme.type.label)
        }
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingScreenPreview() {
    PauseTheme { OnboardingScreen(onStart = {}) }
}
