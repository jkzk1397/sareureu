package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.friend.EntrustedPoke
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.plan.PokeRepository
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.LocalPokeInbox
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
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
        f.transport.next = ApiResponse(429, Json.parseToJsonElement("""{"error":"daily_limit"}"""))
        assertEquals(EntrustResult.Failed, f.inbox.entrust("a", "자라"))
        assertTrue(f.store.entrusted.value.isEmpty())

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
    }

    private inner class Fixture(state: SharingState) {
        val transport = Transport()
        val store = Store()
        val pokes = Pokes()
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
            clock = clock,
        )
    }

    private class Transport : SocialTransport {
        var next = ApiResponse(0, null)
        val calls = mutableListOf<Pair<String, JsonObject?>>()
        override suspend fun call(method: String, path: String, body: JsonObject?): ApiResponse {
            calls += "$method $path" to body
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
