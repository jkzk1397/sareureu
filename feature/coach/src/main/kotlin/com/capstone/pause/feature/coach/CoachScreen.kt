package com.capstone.pause.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.BuddyExpression

/** 코치 탭 진입점. */
@Composable
fun CoachRoute(modifier: Modifier = Modifier, viewModel: CoachViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CoachScreen(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

/** 코치 탭(상태 없음, 목업 07): 르르 메시지 알림과 채팅이 한 대화방. */
@Composable
fun CoachScreen(uiState: CoachUiState, onAction: (CoachAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.items.size, uiState.thinking) {
        val last = uiState.items.size + (if (uiState.thinking) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last - 1)
    }
    Column(modifier = modifier.fillMaxSize().background(colors.bg).imePadding()) {
        CoachHeader(usingRealAi = uiState.usingRealAi)
        HorizontalDivider(color = colors.line)
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = spacing.screen),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = spacing.md),
        ) {
            var lastDay: java.time.LocalDate? = null
            val withSeparators = uiState.items.map { item ->
                val day = item.at.toLocalDate()
                val sep = day != lastDay
                lastDay = day
                item to sep
            }
            items(withSeparators, key = { it.first.id }) { (item, separator) ->
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    if (separator) {
                        Text(
                            text = dayLabel(item.at, uiState.today),
                            style = PauseTheme.type.caption,
                            color = colors.ink3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ChatRow(item, onAction)
                }
            }
            if (uiState.thinking) {
                item(key = "thinking") {
                    Text(
                        text = stringResource(R.string.coach_thinking),
                        style = PauseTheme.type.label,
                        color = colors.ink3,
                    )
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.sm),
        ) {
            uiState.crisis?.let { CrisisCard(it, onClose = { onAction(CoachAction.DismissCrisis) }) }
            if (uiState.quotaReached) {
                Text(
                    text = stringResource(R.string.coach_quota),
                    style = PauseTheme.type.bodySmall,
                    color = colors.ink2,
                )
            }
            val followUps = uiState.items.lastOrNull { it.details != null }?.details?.followUps.orEmpty()
            val suggestions = followUps.ifEmpty {
                listOf(
                    stringResource(R.string.coach_suggest_when),
                    stringResource(R.string.coach_suggest_why_night),
                    stringResource(R.string.coach_suggest_commute),
                )
            }
            SuggestionRow(suggestions = suggestions.take(MAX_SUGGESTIONS), enabled = !uiState.thinking) {
                onAction(CoachAction.Send(it))
            }
            InputRow(
                value = input,
                onValueChange = { input = it },
                enabled = !uiState.thinking,
                onSend = {
                    onAction(CoachAction.Send(input))
                    input = ""
                },
            )
        }
    }
}

@Composable
private fun CoachHeader(usingRealAi: Boolean) {
    val colors = PauseTheme.colors
    val spacing = PauseTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screen, vertical = spacing.sm),
    ) {
        BuddyAvatar(expression = BuddyExpression.NEUTRAL, size = HeaderAvatar)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(DesignR.string.buddy_name),
                style = PauseTheme.type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.coach_header_sub),
                style = PauseTheme.type.bodySmall,
                color = colors.ink2,
            )
            if (!usingRealAi) {
                Text(
                    text = stringResource(R.string.coach_mock_badge),
                    style = PauseTheme.type.caption,
                    color = colors.ink3,
                )
            }
        }
    }
}

@Composable
private fun InputRow(value: String, onValueChange: (String) -> Unit, enabled: Boolean, onSend: () -> Unit) {
    val colors = PauseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(stringResource(R.string.coach_input_hint, stringResource(DesignR.string.buddy_name)))
            },
            shape = PauseTheme.shapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedBorderColor = colors.ink2,
                unfocusedBorderColor = colors.line,
            ),
            maxLines = MAX_INPUT_LINES,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            colors = IconButtonDefaults.iconButtonColors(containerColor = colors.accent, contentColor = PauseColorsInk),
            modifier = Modifier.size(PauseTheme.spacing.minTouch + PauseTheme.spacing.xs),
        ) {
            Icon(
                painter = painterResource(R.drawable.coach_ic_send),
                contentDescription = stringResource(R.string.coach_send),
            )
        }
    }
}

private val HeaderAvatar = androidx.compose.ui.unit.Dp(40f)
private val PauseColorsInk = com.capstone.pause.core.designsystem.theme.PauseColors.BuddyInk
private const val MAX_SUGGESTIONS = 3
private const val MAX_INPUT_LINES = 4
