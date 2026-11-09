package com.capstone.pause.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.model.Goal

/** 3. 목표: 목표 칩 + 하루 목표 분. 관심사·말투는 기본값(설정에서 바꿈). */
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

/** 목표 이름. */
internal fun Goal.labelRes(): Int = when (this) {
    Goal.SLEEP -> R.string.onboarding_goal_sleep
    Goal.FOCUS -> R.string.onboarding_goal_focus
    Goal.TIME_BACK -> R.string.onboarding_goal_time_back
    Goal.MOOD -> R.string.onboarding_goal_mood
    Goal.OTHER -> R.string.onboarding_goal_other
}
