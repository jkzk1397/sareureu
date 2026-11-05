package com.capstone.pause.feature.onboarding.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.onboarding.OnboardingRoute
import kotlinx.serialization.Serializable

/** 온보딩 경로 키. */
@Serializable
data object OnboardingKey : NavKey

/** 온보딩 화면을 entry provider에 붙인다. 끝나면 [onFinished]. */
fun EntryProviderScope<NavKey>.onboardingEntries(onFinished: () -> Unit) {
    entry<OnboardingKey> { OnboardingRoute(onFinished = onFinished) }
}
