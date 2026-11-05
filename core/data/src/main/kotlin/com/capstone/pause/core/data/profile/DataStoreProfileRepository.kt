package com.capstone.pause.core.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.capstone.pause.core.data.di.ProfileStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore로 프로필을 저장한다. */
@Singleton
internal class DataStoreProfileRepository @Inject constructor(
    @ProfileStore private val store: DataStore<Preferences>,
) : ProfileRepository {

    override val hasProfile: Flow<Boolean> = store.data.map { it[ONBOARDING_DONE] ?: false }

    override suspend fun markOnboardingDone() {
        store.edit { it[ONBOARDING_DONE] = true }
    }

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
