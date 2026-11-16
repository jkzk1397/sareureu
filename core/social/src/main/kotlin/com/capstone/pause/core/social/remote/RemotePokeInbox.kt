package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.friend.EntrustedPoke
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.plan.PokeRepository
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.LocalPokeInbox
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.PokeRules
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 잔소리 서버 몸통(순수). */
object PokePayloads {
    /** `POST /api/v1/pokes`. 받는 사람은 공개 ID. */
    fun send(friendId: String, text: String): JsonObject =
        JsonObject(mapOf("to" to JsonPrimitive(friendId), "text" to JsonPrimitive(text.trim())))

    /** `GET /api/v1/pokes` → 받은 잔소리. 모양이 이상한 것은 뺀다. */
    fun inbox(body: JsonElement?): List<ReceivedPoke> = runCatching {
        body?.jsonObject?.get("pokes")?.jsonArray.orEmpty().mapNotNull { e ->
            runCatching {
                val p = e.jsonObject
                val text = p.getValue("text").jsonPrimitive.content.trim()
                if (text.isEmpty() || text.length > PokeRules.MAX_LENGTH) return@runCatching null
                ReceivedPoke(
                    id = p.getValue("id").jsonPrimitive.content,
                    fromId = p.getValue("fromId").jsonPrimitive.content,
                    fromNickname = p["fromNickname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    text = text,
                    createdAt = Instant.ofEpochMilli(p.getValue("createdAtMs").jsonPrimitive.longOrNull ?: 0),
                    expiresAt = Instant.ofEpochMilli(p.getValue("expiresAtMs").jsonPrimitive.longOrNull ?: 0),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}

/**
 * 잔소리 서버 구현(5-4). 공유에 동의했고 시연 모드가 아닐 때만 서버를 쓰고, 아니면 [LocalPokeInbox](가상 친구)에 맡긴다.
 * 받기는 앱이 화면에 있을 때(15분에 한 번까지)와 WorkManager(12시간마다)만. 푸시(FCM)는 쓰지 않는다.
 * 금지 표현 검사는 화면(feature:friends)이 보내기 전에 한다.
 */
@Singleton
internal class RemotePokeInbox @Inject constructor(
    private val local: LocalPokeInbox,
    private val transport: SocialTransport,
    private val sharingRepository: SharingRepository,
    private val pokeRepository: PokeRepository,
    private val localStore: FriendLocalStore,
    private val clock: AppClock,
) : PokeInbox {
    private val mutex = Mutex()
    private var lastFetch: Instant? = null

    override val entrusted: Flow<List<EntrustedPoke>> = localStore.entrusted

    private suspend fun remote(): Boolean = sharingRepository.state.first() == SharingState.CONSENTED

    override suspend fun entrust(friendId: String, text: String): EntrustResult {
        PokeRules.check(text)?.let { return it }
        if (!remote()) return local.entrust(friendId, text)
        val response = transport.call("POST", "/api/v1/pokes", PokePayloads.send(friendId, text))
        if (!response.ok) return EntrustResult.Failed
        val id = (response.body as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull ?: return EntrustResult.Failed
        localStore.addEntrusted(EntrustedPoke(id, friendId, text.trim(), clock.now()))
        return EntrustResult.Entrusted
    }

    override suspend fun fetch(): Int = mutex.withLock {
        if (!remote()) return 0
        val now = clock.now()
        if (lastFetch?.let { Duration.between(it, now) < MIN_INTERVAL } == true) return 0
        val response = transport.call("GET", "/api/v1/pokes")
        if (!response.ok) return 0
        lastFetch = now
        val pokes = PokePayloads.inbox(response.body).filter { it.expiresAt.isAfter(now) }
        // 실제 친구가 보낸 것이므로 REAL. 시연 모드에서는 보이지 않는다.
        if (pokes.isNotEmpty()) pokeRepository.add(pokes, DataSource.REAL)
        pokes.size
    }

    private companion object {
        val MIN_INTERVAL: Duration = Duration.ofMinutes(15)
    }
}
