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
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.SocialError
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class PokeInboxTest {
    private val now = Instant.parse("2026-11-16T03:00:00Z")

    @Test
    fun inboxDropsBrokenAndTooLongPokes() {
        val body = Json.parseToJsonElement(
            """
            {"pokes":[
              {"id":"p1","fromId":"a","fromNickname":"민지","text":"내일 1교시야","createdAtMs":1,"expiresAtMs":2},
              {"id":"p2","fromId":"a","fromNickname":"민지","text":"${"가".repeat(41)}","createdAtMs":1,"expiresAtMs":2},
              {"id":"p3","text":"빠진 칸"}
            ]}
            """,
        )
        assertEquals(listOf("p1"), PokePayloads.inbox(body).map { it.id })
        assertEquals(emptyList(), PokePayloads.inbox(null))
    }

    @Test
    fun notConsentedKeepsDemoBehaviourAndCallsNothing() = runTest {
        val f = Fixture(SharingState.UNAVAILABLE)
        assertEquals(EntrustResult.Entrusted, f.inbox.entrust("demo1", "자라"))
        assertEquals(1, f.store.entrusted.value.size)
        assertEquals(0, f.inbox.fetch())
        assertTrue(f.transport.calls.isEmpty())
    }

    @Test
    fun consentedSendsToServerAndRecordsOnlyOnSuccess() = runTest {
        val f = Fixture(SharingState.CONSENTED)
        assertEquals(EntrustResult.Empty, f.inbox.entrust("a", "  "))
        f.transport.next = ApiResponse(429, Json.parseToJsonElement("""{"error":"daily_limit"}"""), 3600)
        assertEquals(EntrustResult.Limited(3600), f.inbox.entrust("a", "자라"))
        assertTrue(f.store.entrusted.value.isEmpty())
        // 실패는 종류별로: 정지, 금지 표현(서버 재검사), 시청 중 아님, 인터넷 없음.
        fun fail(code: Int, error: String?) =
            ApiResponse(code, error?.let { Json.parseToJsonElement("""{"error":"$it"}""") })
        f.transport.next = fail(403, "suspended")
        assertEquals(EntrustResult.Suspended, f.inbox.entrust("a", "자라"))
        f.transport.next = fail(400, "banned_text")
        assertEquals(EntrustResult.Banned, f.inbox.entrust("a", "자라"))
        f.transport.next = fail(409, "not_watching")
        assertEquals(EntrustResult.NotWatching, f.inbox.entrust("a", "자라"))
        f.transport.next = fail(0, null)
        assertEquals(EntrustResult.Error(SocialError.Offline), f.inbox.entrust("a", "자라"))
        // 보낼 때마다 멱등 키가 붙는다(다시 보내도 서버가 한 번만 처리).
        assertTrue(f.transport.keys.all { !it.isNullOrBlank() })
        assertEquals(f.transport.keys.size, f.transport.keys.toSet().size)

        f.transport.next = ApiResponse(201, Json.parseToJsonElement("""{"id":"srv1"}"""))
        assertEquals(EntrustResult.Entrusted, f.inbox.entrust("a", " 자라 "))
        val (call, body) = f.transport.calls.last()
        assertEquals("POST /api/v1/pokes", call)
        assertEquals("자라", body?.getValue("text")?.jsonPrimitive?.content)
        assertEquals("srv1", f.store.entrusted.value.single().id)
    }

    @Test
    fun fetchStoresRealPokesAndWaitsFifteenMinutes() = runTest {
        val f = Fixture(SharingState.CONSENTED)
        val later = now.plusSeconds(86_400).toEpochMilli()
        f.transport.next = ApiResponse(
            200,
            Json.parseToJsonElement(
                """{"pokes":[{"id":"p1","fromId":"a","fromNickname":"민지","text":"자라","createdAtMs":1,"expiresAtMs":$later}]}""",
            ),
        )
        assertEquals(1, f.inbox.fetch())
        assertEquals(DataSource.REAL, f.pokes.added.single().second)
        assertEquals(0, f.inbox.fetch())
        assertEquals(1, f.transport.calls.size)
        // 직접 새로고침도 30초 안에는 다시 부르지 않는다.
        assertEquals(0, f.inbox.fetch(manual = true))
        assertEquals(1, f.transport.calls.size)
    }

    @Test
    fun receivedPokesAreAcknowledgedOnTheNextRequestAndNotShownTwice() = runTest {
        val f = Fixture(SharingState.CONSENTED)
        val later = now.plusSeconds(86_400).toEpochMilli()
        fun poke(id: String, text: String) =
            """{"id":"$id","fromId":"a","fromNickname":"민지","text":"$text","createdAtMs":1,"expiresAtMs":$later}"""
        // 서버는 확인 전까지 같은 잔소리를 다시 내려 준다. p2는 금지 표현이라 보여 주지 않지만 확인은 해서 서버에서 지운다.
        f.transport.next = ApiResponse(
            200,
            Json.parseToJsonElement("""{"pokes":[${poke("p1", "자라")},${poke("p2", "나쁜말이야")}]}"""),
        )
        assertEquals(1, f.inbox.fetch())
        assertEquals(listOf("p1"), f.pokes.added.map { it.first.id })
        assertEquals("GET /api/v1/pokes", f.transport.calls.last().first)
        assertEquals(listOf("p1", "p2"), f.ledger.pendingAck())

        // 시청 신호가 같은 장부를 쓴다: 다음 신호에 확인 id가 실린다.
        assertEquals(
            """{"watching":true,"ack":["p1","p2"]}""",
            PokePayloads.presence(true, f.ledger.pendingAck()).toString(),
        )
        assertEquals("""{"watching":false}""", PokePayloads.presence(false, emptyList()).toString())
        // 같은 것이 다시 와도 처음이 아니므로 보이지 않는다.
        assertFalse(f.ledger.firstTime("p1"))
        f.ledger.acked(listOf("p1", "p2"))
        assertTrue(f.ledger.pendingAck().isEmpty())
        assertFalse(f.ledger.firstTime("p1"))
    }

    private inner class Fixture(state: SharingState) {
        val transport = Transport()
        val store = Store()
        val pokes = Pokes()
        val ledger = PokeLedger()
        private val clock = AppClock(Clock.fixed(now, ZoneId.of("Asia/Seoul")))
        val inbox = RemotePokeInbox(
            local = LocalPokeInbox(store, clock),
            transport = transport,
            sharingRepository = object : SharingRepository {
                override val state: Flow<SharingState> = MutableStateFlow(state)
                override suspend fun grant() = true
                override suspend fun revoke() = true
                override suspend fun upload() = true
            },
            pokeRepository = pokes,
            localStore = store,
            ledger = ledger,
            filter = object : PokeTextFilter {
                override fun allowed(text: String) = "나쁜말" !in text
            },
            clock = clock,
        )
    }

    private class Transport : SocialTransport {
        var next = ApiResponse(0, null)
        val calls = mutableListOf<Pair<String, JsonObject?>>()
        val keys = mutableListOf<String?>()
        override suspend fun call(
            method: String,
            path: String,
            body: JsonObject?,
            idempotencyKey: String?,
            retries: Int,
        ): ApiResponse {
            calls += "$method $path" to body
            keys += idempotencyKey
            return next
        }
    }

    private class Pokes : PokeRepository {
        val added = mutableListOf<Pair<ReceivedPoke, DataSource>>()
        override fun observeUnused(now: Instant): Flow<List<ReceivedPoke>> = MutableStateFlow(emptyList())
        override suspend fun add(pokes: List<ReceivedPoke>, source: DataSource) {
            added += pokes.map { it to source }
        }
        override suspend fun markUsed(id: String) = Unit
        override suspend fun deleteAll(source: DataSource) = Unit
    }

    private class Store : FriendLocalStore {
        override val entrusted = MutableStateFlow<List<EntrustedPoke>>(emptyList())
        override val blocked: Flow<Set<String>> = MutableStateFlow(emptySet())
        override val reported: Flow<Set<String>> = MutableStateFlow(emptySet())
        override suspend fun addEntrusted(poke: EntrustedPoke) {
            entrusted.value = listOf(poke) + entrusted.value
        }
        override suspend fun block(friendId: String) = Unit
        override suspend fun unblock(friendId: String) = Unit
        override suspend fun report(friendId: String) = Unit
        override suspend fun clear() = Unit
    }
}
