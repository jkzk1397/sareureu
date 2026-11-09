package com.capstone.pause.feature.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.rhythm.RhythmDraft
import com.capstone.pause.core.designsystem.component.rhythm.RhythmEditorPanel
import com.capstone.pause.core.designsystem.theme.PauseTheme

/** 설정 › 하루 리듬. */
@Composable
fun RhythmSettingsRoute(onBack: () -> Unit, viewModel: RhythmSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RhythmSettingsScreen(uiState = uiState, onAction = viewModel::onAction, onBack = onBack)
}

/** 하루 리듬 편집 화면(상태 없음). */
@Composable
fun RhythmSettingsScreen(
    uiState: RhythmSettingsUiState,
    onAction: (RhythmSettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PauseScreenColumn(title = null, modifier = modifier) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = PauseTheme.spacing.minTouch)) {
            Text(stringResource(R.string.settings_rhythm_back), color = PauseTheme.colors.ink2)
        }
        Text(
            stringResource(R.string.settings_rhythm_title),
            style = PauseTheme.type.headline,
            color = PauseTheme.colors.ink,
        )
        Text(
            stringResource(R.string.settings_rhythm_hint),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
        val draft = uiState.draft
        if (draft == null) {
            Text(
                stringResource(R.string.settings_rhythm_no_profile),
                style = PauseTheme.type.body,
                color = PauseTheme.colors.ink2,
            )
        } else {
            RhythmEditorPanel(
                draft = draft,
                weekend = uiState.weekend,
                onWeekendChange = { onAction(RhythmSettingsAction.ShowWeekend(it)) },
                onShift = { field, end, d -> onAction(RhythmSettingsAction.Shift(field, end, d)) },
                onToggle = { field, on -> onAction(RhythmSettingsAction.Toggle(field, on)) },
            )
        }
    }
}

@Preview(name = "light")
@Preview(name = "dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RhythmSettingsPreview() {
    PauseTheme {
        RhythmSettingsScreen(RhythmSettingsUiState(draft = RhythmDraft()), onAction = {}, onBack = {})
    }
}
