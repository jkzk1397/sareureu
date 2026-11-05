package com.capstone.pause.feature.today.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.today.TodayRoute
import kotlinx.serialization.Serializable

/** 오늘 탭 경로 키. */
@Serializable
data object TodayKey : NavKey

/** 오늘 탭 화면을 entry provider에 붙인다. */
fun EntryProviderScope<NavKey>.todayEntries() {
    entry<TodayKey> { TodayRoute() }
}
