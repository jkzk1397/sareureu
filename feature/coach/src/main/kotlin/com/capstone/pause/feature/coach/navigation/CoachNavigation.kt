package com.capstone.pause.feature.coach.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.coach.CoachRoute
import kotlinx.serialization.Serializable

/** 코치 탭 경로 키. */
@Serializable
data object CoachKey : NavKey

/** 코치 탭 화면을 entry provider에 붙인다. */
fun EntryProviderScope<NavKey>.coachEntries() {
    entry<CoachKey> { CoachRoute() }
}
