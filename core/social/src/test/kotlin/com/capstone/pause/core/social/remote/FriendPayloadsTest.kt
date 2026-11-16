package com.capstone.pause.core.social.remote

import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.Ranking
import com.capstone.pause.core.social.Standing
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

class FriendPayloadsTest {
    private val json = Json

    @Test
    fun friendsKeepOnlySharedNumbers() {
        val body = json.parseToJsonElement(
            """
            {"friends":[
              {"id":"a","nickname":"민지","reductionRate":0.3,"coverage":0.9,"validDays":7},
              {"id":"b","nickname":"준호","reductionRate":null,"coverage":null,"validDays":0}
            ],"blocked":[{"id":"c","nickname":"하은"}]}
            """,
        )
        val parsed = FriendPayloads.friends(body)!!
        assertEquals(listOf("민지", "준호"), parsed.friends.map { it.nickname })
        assertEquals(0.3, parsed.friends[0].reductionRate)
        assertTrue(parsed.friends[1].measuring)
        assertEquals(listOf("c" to "하은"), parsed.blocked)
        assertNull(FriendPayloads.friends(json.parseToJsonElement("""{"nope":1}""")))
        assertNull(FriendPayloads.friends(null))
    }

    @Test
    fun rankingFromServerFriendsHidesLastAndKeepsMe() {
        val friends = listOf(0.4, 0.3, 0.2, 0.1).mapIndexed { i, r -> Standing("f$i", "친구$i", r, 0.9, 7, false) }
        val me = Standing("me", "나", 0.05, 0.9, 7, true)
        val view = Ranking.build(friends + me)
        assertEquals(listOf("f0", "f1", "f2", "me"), view.rows.map { it.standing.id })
    }

    @Test
    fun inviteAndCodeInput() {
        val invite = FriendPayloads.invite(json.parseToJsonElement("""{"code":"ABC234","expiresAtMs":1000}"""))!!
        assertEquals("ABC234", invite.code)
        assertEquals(1000L, invite.expiresAt.toEpochMilli())
        assertEquals("ABC234", FriendPayloads.normalizeCode(" abc-234 "))
        assertNull(FriendPayloads.normalizeCode("ABC23"))
    }

    @Test
    fun connectResultsMatchServerErrors() {
        fun r(code: Int, body: String?) = FriendPayloads.connect(ApiResponse(code, body?.let(json::parseToJsonElement)))
        assertEquals(ConnectResult.Connected("민지"), r(201, """{"friend":{"id":"a","nickname":"민지"}}"""))
        assertEquals(ConnectResult.NotFound, r(404, """{"error":"invite_not_found"}"""))
        assertEquals(ConnectResult.OwnCode, r(409, """{"error":"own_invite"}"""))
        assertEquals(ConnectResult.AlreadyFriends, r(409, """{"error":"already_friends"}"""))
        assertEquals(ConnectResult.Unavailable, r(409, """{"error":"unavailable"}"""))
        assertEquals(ConnectResult.Failed, r(0, null))
    }
}
