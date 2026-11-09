package com.capstone.pause.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.rhythm.RhythmEditorPanel
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.Goal
import com.capstone.pause.core.model.Tone
import java.time.LocalTime

/** 3단계: 하루 리듬(목업 09). 편집기는 `core:designsystem`의 [RhythmEditorPanel](설정과 공용). */
@Composable
internal fun RhythmStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_next),
        onPrimary = { onAction(OnboardingAction.Next) },
        secondaryText = stringResource(R.string.onboarding_later),
        onSecondary = { onAction(OnboardingAction.Next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_rhythm_title), stringResource(R.string.onboarding_rhythm_body))
        RhythmEditorPanel(
            draft = state.rhythm,
            weekend = state.showWeekend,
            onWeekendChange = { onAction(OnboardingAction.ShowWeekend(it)) },
            onShift = { field, end, d -> onAction(OnboardingAction.ShiftTime(field, end, d)) },
            onToggle = { field, on -> onAction(OnboardingAction.ToggleRange(field, on)) },
        )
        Text(
            stringResource(R.string.onboarding_local_only),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
    }
}

/** 4단계: 목표·관심사·말투·하루 목표. */
@Composable
internal fun GoalsStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_next),
        onPrimary = { onAction(OnboardingAction.Next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_goals_title), stringResource(R.string.onboarding_goals_hint))
        ChipFlow {
            Goal.entries.forEach { goal ->
                ChoicePill(stringResource(goal.labelRes()), goal in state.goals, {
                    onAction(OnboardingAction.ToggleGoal(goal))
                })
            }
        }
        StepHeader(
            stringResource(R.string.onboarding_interests_title),
            stringResource(R.string.onboarding_interests_hint),
        )
        ChipFlow {
            stringArrayResource(R.array.onboarding_interest_choices).forEach { interest ->
                ChoicePill(interest, interest in state.interests, {
                    onAction(OnboardingAction.ToggleInterest(interest))
                })
            }
        }
        StepHeader(stringResource(R.string.onboarding_tone_title), null)
        ChipFlow {
            Tone.entries.forEach { tone ->
                ChoicePill(stringResource(tone.labelRes()), state.tone == tone, {
                    onAction(OnboardingAction.SetTone(tone))
                })
            }
        }
        StepHeader(
            stringResource(R.string.onboarding_daily_goal_title),
            stringResource(R.string.onboarding_daily_goal_hint),
        )
        ChipFlow {
            OnboardingUiState.DAILY_GOAL_CHOICES.forEach { m ->
                ChoicePill(stringResource(R.string.onboarding_minutes, m), state.dailyGoalMinutes == m, {
                    onAction(OnboardingAction.SetDailyGoal(m))
                })
            }
        }
    }
}

/** 4단계: 추적 앱(목록은 core:data의 기본값, 끄고 켤 수 있다). */
@Composable
internal fun AppsStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_next),
        onPrimary = { onAction(OnboardingAction.Next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_apps_title), stringResource(R.string.onboarding_apps_body))
        PauseCard {
            state.apps.forEach { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = PauseTheme.spacing.minTouch)
                        .toggleable(
                            value = app.enabled,
                            role = Role.Checkbox,
                            onValueChange = { onAction(OnboardingAction.ToggleApp(app.packageName, it)) },
                        ),
                ) {
                    Checkbox(checked = app.enabled, onCheckedChange = null)
                    Column {
                        Text(text = app.label, style = PauseTheme.type.body, color = PauseTheme.colors.ink)
                        Text(text = app.packageName, style = PauseTheme.type.caption, color = PauseTheme.colors.ink3)
                    }
                }
            }
        }
    }
}

/** 목표 이름. */
internal fun Goal.labelRes(): Int = when (this) {
    Goal.SLEEP -> R.string.onboarding_goal_sleep
    Goal.FOCUS -> R.string.onboarding_goal_focus
    Goal.TIME_BACK -> R.string.onboarding_goal_time_back
    Goal.MOOD -> R.string.onboarding_goal_mood
    Goal.OTHER -> R.string.onboarding_goal_other
}

/** 말투 이름. */
internal fun Tone.labelRes(): Int = when (this) {
    Tone.PLAYFUL -> R.string.onboarding_tone_playful
    Tone.WARM -> R.string.onboarding_tone_warm
    Tone.PLAIN -> R.string.onboarding_tone_plain
}
