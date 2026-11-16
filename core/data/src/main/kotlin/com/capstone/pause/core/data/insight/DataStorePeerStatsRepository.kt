package com.capstone.pause.core.data.insight

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.capstone.pause.core.data.friend.FriendStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PeerStatsDto(val groups: List<PeerGroupDto>, val fetchedAtMs: Long)

@Serializable
private data class PeerGroupDto(val ageBand: String, val occupation: String, val n: Int, val p50: Int, val p75: Int)

/** 친구 DataStore(`friends`)에 같이 둔다. '모든 기록 삭제'의 friends 단계가 함께 지운다. */
@Singleton
internal class DataStorePeerStatsRepository @Inject constructor(
    @FriendStore private val store: DataStore<Preferences>,
) : PeerStatsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override val stats: Flow<PeerStats?> = store.data.map { prefs ->
        prefs[KEY]?.let { raw -> runCatching { json.decodeFromString<PeerStatsDto>(raw) }.getOrNull() }?.let { dto ->
            PeerStats(
                groups = dto.groups.map { PeerGroupStat(it.ageBand, it.occupation, it.n, it.p50, it.p75) },
                fetchedAt = Instant.ofEpochMilli(dto.fetchedAtMs),
            )
        }
    }

    override suspend fun save(stats: PeerStats) {
        val dto = PeerStatsDto(
            groups = stats.groups.map { PeerGroupDto(it.ageBand, it.occupation, it.n, it.p50, it.p75) },
            fetchedAtMs = stats.fetchedAt.toEpochMilli(),
        )
        store.edit { it[KEY] = json.encodeToString(dto) }
    }

    override suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("peer_stats")
    }
}
