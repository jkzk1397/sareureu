package com.capstone.pause.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.component.ChoicePill
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.Occupation

/** 1단계: 연령대(만 18세 미만이면 안내 후 종료, 저장 안 함). */
@Composable
internal fun AgeStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = null,
        primaryText = stringResource(R.string.onboarding_next),
        primaryEnabled = state.ageBand != null,
        onPrimary = { onAction(OnboardingAction.Next) },
    ) {
        BuddyAvatar(BuddyExpression.NEUTRAL, PauseSizes.bubbleBuddy)
        StepHeader(stringResource(R.string.onboarding_age_title), stringResource(R.string.onboarding_age_body))
        Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.xs)) {
            AgeBand.entries.forEach { band ->
                ChoicePill(
                    text = stringResource(band.labelRes()),
                    selected = state.ageBand == band,
                    onClick = { onAction(OnboardingAction.SelectAge(band)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ChoicePill(
                text = stringResource(R.string.onboarding_age_under_18),
                selected = false,
                onClick = { onAction(OnboardingAction.SelectAge(null)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 만 18세 미만 안내(ADR-0009). 닫으면 앱을 끝낸다. */
@Composable
internal fun UnderageStep(state: OnboardingUiState, onBack: () -> Unit, onClose: () -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = onBack,
        primaryText = stringResource(R.string.onboarding_underage_close),
        onPrimary = onClose,
    ) {
        BuddyAvatar(BuddyExpression.WORRIED, PauseSizes.bubbleBuddy)
        StepHeader(
            stringResource(R.string.onboarding_underage_title),
            stringResource(R.string.onboarding_underage_body),
        )
    }
}

/** 약관·개인정보 처리 안내(무엇이 기기에 남고 무엇이 나가는지). */
@Composable
internal fun TermsStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_terms_accept),
        onPrimary = { onAction(OnboardingAction.AcceptTerms) },
    ) {
        StepHeader(stringResource(R.string.onboarding_terms_title), null)
        InfoCard(R.string.onboarding_terms_device_title, R.string.onboarding_terms_device_body)
        InfoCard(R.string.onboarding_terms_cloud_title, R.string.onboarding_terms_cloud_body)
        InfoCard(R.string.onboarding_terms_never_title, R.string.onboarding_terms_never_body)
        Text(
            text = stringResource(R.string.onboarding_terms_control),
            style = PauseTheme.type.bodySmall,
            color = PauseTheme.colors.ink2,
        )
    }
}

/** 제목·본문 카드. */
@Composable
internal fun InfoCard(title: Int, body: Int) {
    PauseCard(title = stringResource(title)) {
        Text(text = stringResource(body), style = PauseTheme.type.body, color = PauseTheme.colors.ink)
    }
}

/** 2단계: 닉네임·신분. */
@Composable
internal fun ProfileStep(state: OnboardingUiState, onAction: (OnboardingAction) -> Unit) {
    OnboardingFrame(
        step = state.step,
        onBack = { onAction(OnboardingAction.Back) },
        primaryText = stringResource(R.string.onboarding_next),
        onPrimary = { onAction(OnboardingAction.Next) },
    ) {
        StepHeader(stringResource(R.string.onboarding_profile_title), stringResource(R.string.onboarding_profile_body))
        OutlinedTextField(
            value = state.nickname,
            onValueChange = { onAction(OnboardingAction.SetNickname(it)) },
            label = { Text(stringResource(R.string.onboarding_nickname_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        StepHeader(
            stringResource(R.string.onboarding_occupation_title),
            stringResource(R.string.onboarding_occupation_hint),
        )
        ChipFlow {
            Occupation.entries.forEach { occ ->
                ChoicePill(
                    text = stringResource(occ.labelRes()),
                    selected = state.occupation == occ,
                    onClick = { onAction(OnboardingAction.SetOccupation(occ)) },
                )
            }
        }
    }
}

/** 연령대 이름. */
internal fun AgeBand.labelRes(): Int = when (this) {
    AgeBand.AGE_18_24 -> R.string.onboarding_age_18_24
    AgeBand.AGE_25_29 -> R.string.onboarding_age_25_29
    AgeBand.AGE_30_39 -> R.string.onboarding_age_30_39
    AgeBand.AGE_40_PLUS -> R.string.onboarding_age_40_plus
}

/** 신분 이름. */
internal fun Occupation.labelRes(): Int = when (this) {
    Occupation.UNIVERSITY -> R.string.onboarding_occ_university
    Occupation.GRAD_OR_JOBSEEKER -> R.string.onboarding_occ_grad
    Occupation.OFFICE_WORKER -> R.string.onboarding_occ_office
    Occupation.SHIFT_OR_FREELANCE -> R.string.onboarding_occ_shift
    Occupation.OTHER -> R.string.onboarding_occ_other
}
