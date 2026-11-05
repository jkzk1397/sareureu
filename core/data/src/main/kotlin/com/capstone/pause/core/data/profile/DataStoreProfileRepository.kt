package com.capstone.pause.core.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.data.di.ProfileStore
import com.capstone.pause.core.model.Profile
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** 프로필·동의 기록을 프로필 DataStore에 JSON으로 저장한다. */
@Singleton
internal class DataStoreProfileRepository @Inject constructor(
    @ProfileStore private val store: DataStore<Preferences>,
    private val clock: AppClock,
    private val log: AppLog,
) : ProfileRepository,
    ConsentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override val profile: Flow<Profile?> = store.data.map { prefs ->
        prefs[PROFILE]?.let { text ->
            try {
                json.decodeFromString<ProfileDto>(text).toModel()
            } catch (e: SerializationException) {
                // 내용은 남기지 않는다(개인정보).
                log.w(TAG, "profile decode failed", e)
                null
            } catch (e: IllegalArgumentException) {
                log.w(TAG, "profile decode failed", e)
                null
            }
        }
    }

    override val hasProfile: Flow<Boolean> = profile.map { it != null }.distinctUntilChanged()

    override suspend fun save(profile: Profile) {
        store.edit { it[PROFILE] = json.encodeToString(ProfileDto.serializer(), profile.toDto()) }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    override val consents: Flow<Map<ConsentItem, ConsentRecord>> = store.data.map { prefs ->
        decodeConsents(prefs[CONSENTS])
    }

    override suspend fun record(item: ConsentItem, version: Int, granted: Boolean) {
        store.edit { prefs ->
            val current = decodeConsents(prefs[CONSENTS]).toMutableMap()
            current[item] = ConsentRecord(item, version, granted, clock.now())
            val dtos = current.values.map { ConsentDto(it.item.name, it.version, it.granted, it.at.toEpochMilli()) }
            prefs[CONSENTS] =
                json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ConsentDto.serializer()), dtos)
        }
    }

    private fun decodeConsents(text: String?): Map<ConsentItem, ConsentRecord> {
        if (text == null) return emptyMap()
        val list = try {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ConsentDto.serializer()), text)
        } catch (e: SerializationException) {
            log.w(TAG, "consent decode failed", e)
            emptyList()
        }
        return list.mapNotNull { dto ->
            ConsentItem.entries.firstOrNull { it.name == dto.item }?.let { item ->
                item to ConsentRecord(item, dto.version, dto.granted, Instant.ofEpochMilli(dto.atEpochMs))
            }
        }.toMap()
    }

    private companion object {
        const val TAG = "ProfileRepo"
        val PROFILE = stringPreferencesKey("profile_json")
        val CONSENTS = stringPreferencesKey("consents_json")
    }
}
