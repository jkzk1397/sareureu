package com.capstone.pause.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.pause.core.data.di.SettingsStore
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
            themeMode = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            showMelt = prefs[SHOW_MELT] ?: true,
        )
    }

    override suspend fun setDemoMode(enabled: Boolean) {
        store.edit { it[DEMO_MODE] = enabled }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    override suspend fun setShowMelt(enabled: Boolean) {
        store.edit { it[SHOW_MELT] = enabled }
    }

    private companion object {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val THEME = stringPreferencesKey("theme_mode")
        val SHOW_MELT = booleanPreferencesKey("show_melt")
    }
}
