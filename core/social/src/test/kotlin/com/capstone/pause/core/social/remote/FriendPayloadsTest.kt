package com.capstone.pause.core.social.remote

import com.capstone.pause.core.social.AcceptResult
import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.FriendRequests
import com.capstone.pause.core.social.Ranking
import com.capstone.pause.core.social.ReportReason
import com.capstone.pause.core.social.SocialError
import com.capstone.pause.core.social.Standing
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            json.parseToJsonElement("""{"code":"ABC234XY","expiresAtMs":1000}"""),
            server,
        )!!
        assertEquals("https://sareureu.example.dev/invite/ABC234XY", invite.link)
        assertEquals("ABC234XY", InviteLinks.codeFrom(invite.link))
        assertEquals("ABC234XY", InviteLinks.codeFrom("sareureu://invite/abc234xy"))
        assertEquals("ABC234XY", InviteLinks.codeFrom("sareureu://invite/ABC234XY/?utm=x"))
        assertNull(InviteLinks.codeFrom("sareureu://invite/ABC"))
        // 예전 6자리 코드와 글자표에 없는 글자가 든 코드는 받지 않는다.
        assertNull(InviteLinks.codeFrom("sareureu://invite/ABC234"))
        assertNull(InviteLinks.codeFrom("sareureu://invite/ABC2340Y"))
        assertNull(InviteLinks.codeFrom("sareureu://other/ABC234XY"))
        assertNull(InviteLinks.codeFrom("https://evil.example/x/invite/ABC234XY"))
        assertNull(InviteLinks.codeFrom("http://sareureu.example.dev/invite/ABC234XY"))
        assertNull(InviteLinks.codeFrom(null))
    }

    @Test
    fun inviteAndCodeInput() {
        val invite = FriendPayloads.invite(json.parseToJsonElement("""{"code":"ABC234XY","expiresAtMs":1000}"""))!!
        assertEquals("ABC234XY", invite.code)
        assertEquals(1000L, invite.expiresAt.toEpochMilli())
        assertEquals("ABC234XY", FriendPayloads.normalizeCode(" abc2-34xy "))
        assertNull(FriendPayloads.normalizeCode("ABC23"))
        assertNull(FriendPayloads.normalizeCode("ABC2340Y"))
    }

    @Test
    fun connectResultsMatchServerErrors() {
        fun r(code: Int, body: String?) = FriendPayloads.connect(ApiResponse(code, body?.let(json::parseToJsonElement)))
        // 코드를 넣으면 요청만 생긴다(ADR-0014). 주인의 닉네임은 응답에 없다.
        assertEquals(ConnectResult.Requested("r1"), r(201, """{"request":{"id":"r1","expiresAtMs":5}}"""))
        assertEquals(ConnectResult.NotFound, r(404, """{"error":"invite_not_found"}"""))
        assertEquals(ConnectResult.OwnCode, r(409, """{"error":"own_invite"}"""))
        assertEquals(ConnectResult.AlreadyFriends, r(409, """{"error":"already_friends"}"""))
        assertEquals(ConnectResult.TooManyRequests, r(409, """{"error":"too_many_requests"}"""))
        // 실패는 종류별로 나뉜다.
        assertEquals(ConnectResult.Error(SocialError.Offline), r(0, null))
        assertEquals(ConnectResult.Error(SocialError.Maintenance), r(503, """{"error":"service_unavailable"}"""))
        assertEquals(ConnectResult.Error(SocialError.SessionExpired), r(401, """{"error":"invalid_token"}"""))
        assertEquals(ConnectResult.Error(SocialError.UpgradeRequired), r(426, """{"error":"upgrade_required"}"""))
        assertEquals(ConnectResult.Error(SocialError.ClockSkew), r(400, """{"error":"clock_skew"}"""))
        assertEquals(
            ConnectResult.Error(SocialError.TooMany(600)),
            FriendPayloads.connect(ApiResponse(429, json.parseToJsonElement("""{"error":"too_many_attempts"}"""), 600)),
        )
    }

    @Test
    fun requestsShowOnlyTheRequesterNickname() {
        val parsed = FriendPayloads.requests(
            json.parseToJsonElement(
                """
                {"received":[{"id":"r1","fromNickname":"준호","createdAtMs":10,"expiresAtMs":20}],
                 "sent":[{"id":"r2","createdAtMs":30,"expiresAtMs":40}]}
                """,
            ),
        )!!
        assertEquals("준호", parsed.received.single().fromNickname)
        assertEquals(20L, parsed.received.single().expiresAt.toEpochMilli())
        assertEquals("r2", parsed.sent.single().id)
        assertEquals(FriendRequests(), FriendPayloads.requests(json.parseToJsonElement("{}")))
        assertNull(FriendPayloads.requests(null))
    }

    @Test
    fun acceptAndReportPayloads() {
        fun a(code: Int, body: String?) = FriendPayloads.accept(ApiResponse(code, body?.let(json::parseToJsonElement)))
        assertEquals(AcceptResult.Accepted("준호"), a(201, """{"friend":{"id":"b","nickname":"준호"}}"""))
        assertEquals(AcceptResult.NotFound, a(404, """{"error":"request_not_found"}"""))
        assertEquals(AcceptResult.TooManyFriends, a(409, """{"error":"too_many_friends"}"""))
        assertEquals(AcceptResult.Error(SocialError.Offline), a(0, null))

        val byFriend = FriendPayloads.report("pid", null, ReportReason.POKE, "  기분 나쁜 말  ")
        assertEquals("""{"target":"pid","reason":"POKE","text":"기분 나쁜 말"}""", byFriend.toString())
        val byRequest = FriendPayloads.report(null, "rid", ReportReason.NICKNAME, null)
        assertEquals("""{"requestId":"rid","reason":"NICKNAME","text":null}""", byRequest.toString())
    }

    @Test
    fun retriesBackOffWithJitterAndOnlyForConnectionOrServerErrors() {
        // 0.5초에서 시작해 두 배씩, 0.5~1.5배로 흩어진다.
        assertEquals(250L, RetryBackoff.delayMs(0, 0.0))
        assertEquals(750L, RetryBackoff.delayMs(0, 1.0))
        assertEquals(1000L, RetryBackoff.delayMs(1, 0.5))
        assertEquals(2000L, RetryBackoff.delayMs(2, 0.5))
        assertTrue(ApiResponse(0, null).retryable)
        assertTrue(ApiResponse(503, null).retryable)
        // 429와 그 밖의 4xx는 자동으로 다시 하지 않는다.
        assertFalse(ApiResponse(429, null).retryable)
        assertFalse(ApiResponse(404, null).retryable)
        assertFalse(ApiResponse(401, null).retryable)
        assertNull(ApiResponse(204, null).error)
    }
}
