package com.capstone.pause.core.data.friend

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    /** 서버에서 마지막으로 받은 친구 목록(서버가 안 될 때 보여 주려고). 없으면 null. */
    val friendsCache: Flow<FriendsCache?> get() = flowOf(null)

    /** 받은 친구 목록 저장([json]은 서버 응답 그대로, 닉네임·연속 일수 등 친구에게 보이는 값뿐). */
    suspend fun saveFriendsCache(cache: FriendsCache) {}

    /** 저장해 둔 친구 목록 지우기(공유 철회). */
    suspend fun clearFriendsCache() {}

    /** 철회 뒤 서버의 내 기록을 아직 지우지 못했는지. */
    val remoteDeletePending: Flow<Boolean> get() = flowOf(false)

    /** [remoteDeletePending] 바꾸기. */
    suspend fun setRemoteDeletePending(pending: Boolean) {}
}

/** 저장해 둔 친구 목록 한 장. */
data class FriendsCache(val json: String, val savedAt: Instant)

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

    override val friendsCache: Flow<FriendsCache?> = store.data.map { prefs ->
        val body = prefs[FRIENDS_CACHE] ?: return@map null
        FriendsCache(body, Instant.ofEpochMilli(prefs[FRIENDS_CACHE_AT] ?: 0))
    }

    override suspend fun saveFriendsCache(cache: FriendsCache) {
        store.edit { prefs ->
            prefs[FRIENDS_CACHE] = cache.json.take(MAX_CACHE_CHARS)
            prefs[FRIENDS_CACHE_AT] = cache.savedAt.toEpochMilli()
        }
    }

    override suspend fun clearFriendsCache() {
        store.edit { prefs ->
            prefs.remove(FRIENDS_CACHE)
            prefs.remove(FRIENDS_CACHE_AT)
        }
    }

    override val remoteDeletePending: Flow<Boolean> = store.data.map { it[DELETE_PENDING] ?: false }

    override suspend fun setRemoteDeletePending(pending: Boolean) {
        store.edit { prefs -> if (pending) prefs[DELETE_PENDING] = true else prefs.remove(DELETE_PENDING) }
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
        val FRIENDS_CACHE = stringPreferencesKey("friends_cache")
        val FRIENDS_CACHE_AT = longPreferencesKey("friends_cache_at")
        val DELETE_PENDING = booleanPreferencesKey("remote_delete_pending")

        // 친구 50명 × 한 줄 약 200자보다 넉넉하게.
        const val MAX_CACHE_CHARS = 40_000
        const val MAX_ENTRUSTED = 50
    }
}
