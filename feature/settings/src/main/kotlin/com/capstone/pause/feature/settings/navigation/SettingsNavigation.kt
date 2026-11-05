package com.capstone.pause.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

/** 설정 탭 경로 키. */
@Serializable
data object SettingsKey : NavKey

/** 설정 화면을 entry provider에 붙인다. */
fun EntryProviderScope<NavKey>.settingsEntries() {
    entry<SettingsKey> { SettingsRoute() }
}
