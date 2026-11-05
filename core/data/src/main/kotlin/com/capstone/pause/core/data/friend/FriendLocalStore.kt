package com.capstone.pause.core.data.friend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 내가 친구에게 맡긴 잔소리(보낸 쪽 기록). 전달 여부는 받는 사람이 허락했을 때만 안다. */
data class EntrustedPoke(val id: String, val friendId: String, val text: String, val at: Instant)

/** 친구 기능의 로컬 기록: 맡긴 잔소리, 차단·신고한 친구. 기기 밖으로 나가지 않는다. */
interface FriendLocalStore {
    /** 맡긴 잔소리(최신순). */
    val entrusted: Flow<List<EntrustedPoke>>

    /** 차단한 친구 ID. */
    val blocked: Flow<Set<String>>

    /** 신고한 친구 ID. */
    val reported: Flow<Set<String>>

    /** 잔소리 맡기기 기록. */
    suspend fun addEntrusted(poke: EntrustedPoke)

    /** 차단. */
    suspend fun block(friendId: String)

    /** 차단 풀기. */
    suspend fun unblock(friendId: String)

    /** 신고 기록(친구 기능이 연결되면 서버로 보낸다). */
    suspend fun report(friendId: String)

    /** 모두 지우기(개인정보 › 모든 기록 삭제). */
    suspend fun clear()
}

/** 친구 기록 DataStore 한정자. 파일당 인스턴스 하나. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FriendStore

@Serializable
private data class EntrustedDto(val id: String, val friendId: String, val text: String, val atMs: Long)

@Singleton
internal class DataStoreFriendLocalStore @Inject constructor(@FriendStore private val store: DataStore<Preferences>) :
    FriendLocalStore {
    private val json = Json { ignoreUnknownKeys = true }

    override val entrusted: Flow<List<EntrustedPoke>> = store.data.map { prefs ->
        decodeList(prefs[ENTRUSTED]).map { EntrustedPoke(it.id, it.friendId, it.text, Instant.ofEpochMilli(it.atMs)) }
            .sortedByDescending { it.at }
    }

    override val blocked: Flow<Set<String>> = store.data.map { decodeSet(it[BLOCKED]) }

    override val reported: Flow<Set<String>> = store.data.map { decodeSet(it[REPORTED]) }

    override suspend fun addEntrusted(poke: EntrustedPoke) {
        store.edit { prefs ->
            val list = decodeList(prefs[ENTRUSTED]) +
                EntrustedDto(poke.id, poke.friendId, poke.text, poke.at.toEpochMilli())
            prefs[ENTRUSTED] = json.encodeToString(list.takeLast(MAX_ENTRUSTED))
        }
    }

    override suspend fun block(friendId: String) = editSet(BLOCKED) { it + friendId }

    override suspend fun unblock(friendId: String) = editSet(BLOCKED) { it - friendId }

    override suspend fun report(friendId: String) = editSet(REPORTED) { it + friendId }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private suspend fun editSet(key: Preferences.Key<String>, change: (Set<String>) -> Set<String>) {
        store.edit { prefs -> prefs[key] = json.encodeToString(change(decodeSet(prefs[key])).toList()) }
    }

    private fun decodeList(raw: String?): List<EntrustedDto> =
        raw?.let { runCatching { json.decodeFromString<List<EntrustedDto>>(it) }.getOrNull() }.orEmpty()

    private fun decodeSet(raw: String?): Set<String> =
        raw?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty().toSet()

    private companion object {
        val ENTRUSTED = stringPreferencesKey("entrusted_pokes")
        val BLOCKED = stringPreferencesKey("blocked_friends")
        val REPORTED = stringPreferencesKey("reported_friends")
        const val MAX_ENTRUSTED = 50
    }
}
