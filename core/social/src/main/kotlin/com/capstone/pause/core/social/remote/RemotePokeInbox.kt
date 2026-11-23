package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.friend.EntrustedPoke
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.friend.PokeTextFilter
import com.capstone.pause.core.data.plan.PokeRepository
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.LocalPokeInbox
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.PokeRules
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.SocialError
import com.capstone.pause.core.social.SocialLimits
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
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

    /** 서버가 내려 준 모든 잔소리 id(모양이 이상해 버린 것도 확인해 줘야 서버에서 지워진다). */
    fun ids(body: JsonElement?): List<String> = runCatching {
        body?.jsonObject?.get("pokes")?.jsonArray.orEmpty().mapNotNull { e ->
            runCatching { e.jsonObject.getValue("id").jsonPrimitive.content }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** 시청 중 신호 몸통. [ack]는 받았다고 확인하는 잔소리 id(ADR-0014: 확인해야 서버에서 지운다). */
    fun presence(watching: Boolean, ack: Collection<String>): JsonObject = JsonObject(
        buildMap {
            put("watching", JsonPrimitive(watching))
            if (ack.isNotEmpty()) put("ack", JsonArray(ack.take(MAX_ACK).map(::JsonPrimitive)))
        },
    )

    /** 보내기 실패를 화면이 쓸 결과로 바꾼다. */
    fun sendFailure(response: ApiResponse): EntrustResult = when {
        response.errorName == "not_watching" -> EntrustResult.NotWatching
        response.errorName == "banned_text" -> EntrustResult.Banned
        response.errorName == "suspended" -> EntrustResult.Suspended
        response.errorName == "daily_limit" -> EntrustResult.Limited(response.retryAfterSeconds)
        else -> EntrustResult.Error(response.error ?: SocialError.Unknown)
    }

    /** 한 번에 확인하는 id 수(서버가 한 번에 내려 주는 개수와 같다). */
    const val MAX_ACK = 20
}

/**
 * 받은 잔소리 id 장부(메모리). 서버는 앱이 확인(ack)할 때까지 같은 잔소리를 다시 내려 주므로,
 * 이미 보여 준 것은 다시 보이지 않고 다음 요청에 확인만 실어 보낸다.
 */
@Singleton
class PokeLedger @Inject constructor() {
    private val seen = LinkedHashSet<String>()
    private val toAck = LinkedHashSet<String>()

    /** 처음 보는 id면 true(보여 줘도 된다). 본 적이 있으면 false. 어느 쪽이든 확인 목록에 넣는다. */
    @Synchronized
    fun firstTime(id: String): Boolean {
        toAck += id
        val fresh = seen.add(id)
        while (seen.size > MAX_SEEN) seen.remove(seen.first())
        return fresh
    }

    /** 다음 요청에 실어 보낼 확인 id. */
    @Synchronized
    fun pendingAck(): List<String> = toAck.take(PokePayloads.MAX_ACK)

    /** 서버가 받았다(요청이 성공했다): 그 id는 더 보내지 않는다. */
    @Synchronized
    fun acked(ids: Collection<String>) {
        toAck.removeAll(ids.toSet())
    }

    private companion object {
        const val MAX_SEEN = 200
    }
}

/**
 * 잔소리 서버 구현(5-4, ADR-0014). 공유에 동의했고 시연 모드가 아닐 때만 서버를 쓰고, 아니면 [LocalPokeInbox](가상 친구)에 맡긴다.
 * - 보내기: 멱등 키를 붙여 연결이 끊겨 다시 보내도 한 번만 간다.
 * - 받기: 앱이 화면에 올 때(15분에 한 번까지), 사용자가 직접 새로고침할 때(30초에 한 번까지), WorkManager(12시간마다).
 *   받은 글은 보여 주기 전에 금지 표현을 한 번 더 검사하고, 받은 id를 다음 요청에 실어 확인한다. 푸시(FCM)는 쓰지 않는다.
 */
@Singleton
internal class RemotePokeInbox @Inject constructor(
    private val local: LocalPokeInbox,
    private val transport: SocialTransport,
    private val sharingRepository: SharingRepository,
    private val pokeRepository: PokeRepository,
    private val localStore: FriendLocalStore,
    private val ledger: PokeLedger,
    private val filter: PokeTextFilter,
    private val clock: AppClock,
) : PokeInbox {
    private val mutex = Mutex()
    private var lastFetch: Instant? = null

    override val entrusted: Flow<List<EntrustedPoke>> = localStore.entrusted

    private suspend fun remote(): Boolean = sharingRepository.state.first() == SharingState.CONSENTED

    override suspend fun entrust(friendId: String, text: String): EntrustResult {
        PokeRules.check(text)?.let { return it }
        if (!remote()) return local.entrust(friendId, text)
        val response = transport.call(
            "POST",
            "/api/v1/pokes",
            PokePayloads.send(friendId, text),
            idempotencyKey = UUID.randomUUID().toString(),
            retries = SocialLimits.MAX_RETRIES,
        )
        if (!response.ok) return PokePayloads.sendFailure(response)
        val id = (response.body as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            ?: return EntrustResult.Error(SocialError.Unknown)
        localStore.addEntrusted(EntrustedPoke(id, friendId, text.trim(), clock.now()))
        return EntrustResult.Entrusted
    }

    override suspend fun fetch(manual: Boolean): Int = mutex.withLock {
        if (!remote()) return 0
        val now = clock.now()
        val every = if (manual) SocialLimits.POKE_FETCH_MANUAL_EVERY else SocialLimits.POKE_FETCH_EVERY
        if (lastFetch?.let { Duration.between(it, now) < every } == true) return 0
        val ack = ledger.pendingAck()
        val path = if (ack.isEmpty()) "/api/v1/pokes" else "/api/v1/pokes?ack=" + ack.joinToString(",")
        val response = transport.call("GET", path)
        if (!response.ok) return 0
        lastFetch = now
        ledger.acked(ack)
        val fresh = PokePayloads.ids(response.body).filter(ledger::firstTime).toSet()
        // 실제 친구가 보낸 것이므로 REAL. 시연 모드에서는 보이지 않는다. 금지 표현이 있으면 저장하지 않는다(확인은 해서 서버에서 지운다).
        val pokes = PokePayloads.inbox(response.body)
            .filter { it.id in fresh && it.expiresAt.isAfter(now) && filter.allowed(it.text) }
        if (pokes.isNotEmpty()) pokeRepository.add(pokes, DataSource.REAL)
        pokes.size
    }
}
