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
    fun friendsReadStreakAndWatching() {
        val body = json.parseToJsonElement(
            """
            {"friends":[
              {"id":"a","nickname":"민지","reductionRate":0.3,"coverage":0.9,"validDays":7,"streakDays":5,"watching":true},
              {"id":"b","nickname":"준호","reductionRate":null,"coverage":null,"validDays":0,"streakDays":-3}
            ],"blocked":[]}
            """,
        )
        val friends = FriendPayloads.friends(body)!!.friends
        assertEquals(5, friends[0].streakDays)
        assertTrue(friends[0].watching)
        // 예전 서버(칸 없음)·이상한 값은 0일·시청 중 아님.
        assertEquals(0, friends[1].streakDays)
        assertEquals(false, friends[1].watching)
    }

    @Test
    fun inviteLinkForQr() {
        val server = SocialServer("https://sareureu.example.dev/")
        val invite = FriendPayloads.invite(
            json.parseToJsonElement("""{"code":"ABC234","expiresAtMs":1000}"""),
            server,
        )!!
        assertEquals("https://sareureu.example.dev/invite/ABC234", invite.link)
        assertEquals("ABC234", InviteLinks.codeFrom(invite.link))
        assertEquals("ABC234", InviteLinks.codeFrom("sareureu://invite/abc234"))
        assertEquals("ABC234", InviteLinks.codeFrom("sareureu://invite/ABC234/?utm=x"))
        assertNull(InviteLinks.codeFrom("sareureu://invite/ABC"))
        assertNull(InviteLinks.codeFrom("sareureu://other/ABC234"))
        assertNull(InviteLinks.codeFrom("https://evil.example/x/invite/ABC234"))
        assertNull(InviteLinks.codeFrom("http://sareureu.example.dev/invite/ABC234"))
        assertNull(InviteLinks.codeFrom(null))
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
