package com.capstone.pause.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.di.SettingsStore
import com.capstone.pause.core.model.ContextSlot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore로 설정을 저장한다. */
@Singleton
internal class DataStoreSettingsRepository @Inject constructor(
    @SettingsStore private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            demoMode = prefs[DEMO_MODE] ?: false,
            demoPattern = enumOr(prefs[DEMO_PATTERN], DemoPattern.NIGHT_OWL),
            themeMode = enumOr(prefs[THEME], ThemeMode.SYSTEM),
            showMelt = prefs[SHOW_MELT] ?: true,
            aiMode = enumOr(prefs[AI_MODE], AiMode.OFF),
            thresholds = ContextSlot.entries.associateWith { slot ->
                prefs[thresholdKey(slot)] ?: DefaultThresholds.ALL.getValue(slot)
            },
        )
    }

    override suspend fun setDemoMode(enabled: Boolean) {
        store.edit { it[DEMO_MODE] = enabled }
    }

    override suspend fun setDemoPattern(pattern: DemoPattern) {
        store.edit { it[DEMO_PATTERN] = pattern.name }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    override suspend fun setShowMelt(enabled: Boolean) {
        store.edit { it[SHOW_MELT] = enabled }
    }

    override suspend fun setAiMode(mode: AiMode) {
        store.edit { it[AI_MODE] = mode.name }
    }

    override suspend fun setThreshold(slot: ContextSlot, minutes: Int) {
        require(minutes in DefaultThresholds.CHOICES) { "threshold must be one of ${DefaultThresholds.CHOICES}" }
        store.edit { it[thresholdKey(slot)] = minutes }
    }

    override suspend fun reset() {
        store.edit { it.clear() }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val DEMO_PATTERN = stringPreferencesKey("demo_pattern")
        val THEME = stringPreferencesKey("theme_mode")
        val SHOW_MELT = booleanPreferencesKey("show_melt")
        val AI_MODE = stringPreferencesKey("ai_mode")

        fun thresholdKey(slot: ContextSlot) = intPreferencesKey("threshold_${slot.name}")
    }
}
