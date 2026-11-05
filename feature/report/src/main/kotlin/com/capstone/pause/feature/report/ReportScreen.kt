package com.capstone.pause.feature.report

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.designsystem.component.PauseEmptyState
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 리포트 탭 진입점. TODO(BUILD_REPORT#2): 해당 단계에서 ViewModel과 실제 화면으로 채운다. */
@Composable
fun ReportRoute(modifier: Modifier = Modifier) {
    ReportScreen(modifier = modifier)
}

/** 리포트 탭(상태 없음). */
@Composable
fun ReportScreen(modifier: Modifier = Modifier) {
    PauseEmptyState(
        title = stringResource(R.string.report_title),
        body = stringResource(R.string.report_empty_body),
        modifier = modifier,
    )
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReportScreenPreview() {
    PauseTheme { ReportScreen() }
}
