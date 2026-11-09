package com.capstone.pause.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.settings.RhythmSettingsRoute
import com.capstone.pause.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

/** 설정 탭 경로 키. */
@Serializable
data object SettingsKey : NavKey

/** 설정 › 하루 리듬 경로 키. */
@Serializable
data object SettingsRhythmKey : NavKey

/** 설정 화면을 entry provider에 붙인다. [onOpenRhythm]은 하루 리듬 화면을 백 스택에 올리고, [onBack]은 내린다(백 스택은 app이 가진다). */
fun EntryProviderScope<NavKey>.settingsEntries(onOpenRhythm: () -> Unit, onBack: () -> Unit) {
    entry<SettingsKey> { SettingsRoute(onOpenRhythm = onOpenRhythm) }
    entry<SettingsRhythmKey> { RhythmSettingsRoute(onBack = onBack) }
}
