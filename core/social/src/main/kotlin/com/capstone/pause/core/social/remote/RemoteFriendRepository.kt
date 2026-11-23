package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.DemoFriendRepository
import com.capstone.pause.core.social.FriendRepository
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.InviteCode
import com.capstone.pause.core.social.MyStanding
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.Standing
import com.capstone.pause.core.social.StreakRanking
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 서버가 내려 준 친구 목록(친구 탭을 열 때마다 새로). */
data class RemoteFriends(val friends: List<Standing>, val blocked: List<Pair<String, String>>)

/** 서버 응답 읽기(순수). 친구에게서 받는 것은 닉네임·감소율·기록된 비율·유효한 날·연속 일수·시청 중뿐이다. */
object FriendPayloads {
    /** `GET /api/v1/friends`. 모양이 다르면 null. */
    fun friends(body: JsonElement?): RemoteFriends? = runCatching {
        val o = body?.jsonObject ?: return null
        val friends = o.getValue("friends").jsonArray.map { e ->
            val f = e.jsonObject
            Standing(
                id = f.getValue("id").jsonPrimitive.content,
                nickname = f["nickname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                reductionRate = f["reductionRate"]?.jsonPrimitive?.doubleOrNull,
                coverage = f["coverage"]?.jsonPrimitive?.doubleOrNull,
                validDays = f["validDays"]?.jsonPrimitive?.intOrNull ?: 0,
                isMe = false,
                streakDays = (f["streakDays"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
                watching = f["watching"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
        val blocked = o["blocked"]?.jsonArray.orEmpty().map { e ->
            val b = e.jsonObject
            b.getValue("id").jsonPrimitive.content to b["nickname"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        RemoteFriends(friends, blocked)
    }.getOrNull()

    /** `POST /api/v1/invites`. [server]가 있으면 QR 주소(`/invite/{코드}`)를 붙인다. */
    fun invite(body: JsonElement?, server: SocialServer? = null): InviteCode? = runCatching {
        val o = body?.jsonObject ?: return null
        val code = o.getValue("code").jsonPrimitive.content
        val expires = o.getValue("expiresAtMs").jsonPrimitive.longOrNull ?: return null
        InviteCode(code, Instant.ofEpochMilli(expires), link = server?.let { InviteLinks.link(it, code) })
    }.getOrNull()

    /** `POST /api/v1/invites/{code}/accept` 결과. */
    fun connect(response: ApiResponse): ConnectResult {
        if (response.ok) {
            val nickname = runCatching {
                response.body?.jsonObject?.get("friend")?.jsonObject?.get("nickname")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            return ConnectResult.Connected(nickname.orEmpty())
        }
        val error = (response.body as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
        return when {
            response.code == NOT_FOUND -> ConnectResult.NotFound
            error == "own_invite" -> ConnectResult.OwnCode
            error == "already_friends" -> ConnectResult.AlreadyFriends
            error == "unavailable" || error == "too_many_friends" -> ConnectResult.Unavailable
            else -> ConnectResult.Failed
        }
    }

    /** 사용자가 친 코드 다듬기: 공백·하이픈 빼고 대문자. 6글자가 아니면 null. */
    fun normalizeCode(input: String): String? =
        input.filter { it.isLetterOrDigit() }.uppercase().takeIf { it.length == CODE_LENGTH }

    private const val NOT_FOUND = 404
    private const val CODE_LENGTH = 6
}

/**
 * 친구 서버 구현(5-3). 공유에 동의했고 시연 모드가 아닐 때만 서버를 쓴다. 아니면 가상 친구 구현([DemoFriendRepository])에 맡긴다.
 * 순위는 서버가 아니라 앱이 [StreakRanking.build]로 매긴다(연속 일수, 모두 보임, ADR-0012).
 * 서버 호출은 친구 탭이 화면에 있는 동안의 [refresh](몇 초마다, 시청 중 표시)와 사용자가 버튼을 눌렀을 때만.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class RemoteFriendRepository @Inject constructor(
    private val demo: DemoFriendRepository,
    private val transport: SocialTransport,
    private val sharingRepository: SharingRepository,
    private val sessionRepository: SessionRepository,
    private val profileRepository: ProfileRepository,
    private val localStore: FriendLocalStore,
    private val server: SocialServer,
    private val sharing: RemoteSharingRepository,
    private val clock: AppClock,
) : FriendRepository {
    private val cache = MutableStateFlow<RemoteFriends?>(null)
    private var standingUploadedAt: Instant? = null

    override fun observe(): Flow<FriendsOverview> = sharingRepository.state.flatMapLatest { state ->
        if (state == SharingState.CONSENTED) {
            remoteOverview()
        } else {
            // 철회·시연 모드로 바뀌면 받아 둔 친구 목록을 버린다.
            cache.value = null
            demo.observe()
        }
    }

    private fun remoteOverview(): Flow<FriendsOverview> {
        val now = clock.now()
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(HISTORY_DAYS), clock.zone)
        return combine(
            sessionRepository.observeSessions(from, DayBoundary.endOf(today, clock.zone)),
            profileRepository.profile,
            cache,
            localStore.reported,
        ) { sessions, profile, remote, reported ->
            val me = MyStanding.compute(
                sessions,
                today,
                clock.zone,
                now,
                profile?.nickname.orEmpty(),
                profile?.dailyGoalMinutes ?: 0,
            )
            val friends = remote?.friends.orEmpty()
            val blocked = remote?.blocked.orEmpty()
            val blockedStandings = blocked.map { (id, name) -> Standing(id, name, null, null, 0, isMe = false) }
            FriendsOverview(
                isDemo = false,
                available = true,
                me = me,
                ranking = StreakRanking.build(friends + me),
                friends = friends,
                allFriends = friends + blockedStandings,
                blocked = blocked.map { it.first }.toSet(),
                reported = reported,
                // 친구의 시간대별 기록은 공유하지 않아서 함께 챌린지는 가상 친구에서만.
                challenge = emptyList(),
                sharing = SharingState.CONSENTED,
            )
        }
    }

    private suspend fun remote(): Boolean = sharingRepository.state.first() == SharingState.CONSENTED

    override suspend fun refresh(): Boolean {
        if (!remote()) return false
        // 친구에게 보이는 내 연속 일수도 가끔 새로(오늘 목표를 넘기면 0이 되게). 하루 한 번 올리기와 별도.
        val now = clock.now()
        if (standingUploadedAt?.let { Duration.between(it, now) < STANDING_EVERY } != true) {
            standingUploadedAt = now
            runCatching { sharing.uploadStanding() }
        }
        val response = transport.call("GET", "/api/v1/friends")
        val parsed = if (response.ok) FriendPayloads.friends(response.body) else null
        if (parsed != null) cache.value = parsed
        return parsed != null
    }

    override suspend fun createInvite(): InviteCode? {
        if (!remote()) return null
        val response = transport.call("POST", "/api/v1/invites")
        return if (response.ok) FriendPayloads.invite(response.body, server) else null
    }

    override suspend fun connect(code: String): ConnectResult {
        if (!remote()) return ConnectResult.Failed
        val normalized = FriendPayloads.normalizeCode(code) ?: return ConnectResult.NotFound
        val result = FriendPayloads.connect(transport.call("POST", "/api/v1/invites/$normalized/accept"))
        if (result is ConnectResult.Connected) refresh()
        return result
    }

    override suspend fun disconnect(friendId: String) {
        if (!remote()) return
        if (transport.call("DELETE", "/api/v1/friends/$friendId").ok) refresh()
    }

    override suspend fun block(friendId: String) {
        if (!remote()) return demo.block(friendId)
        if (transport.call("PUT", "/api/v1/blocks/$friendId").ok) refresh()
    }

    override suspend fun unblock(friendId: String) {
        if (!remote()) return demo.unblock(friendId)
        if (transport.call("DELETE", "/api/v1/blocks/$friendId").ok) refresh()
    }

    /** 신고는 지금처럼 이 폰에만 기록한다(서버 신고 창구는 아직 없음). */
    override suspend fun report(friendId: String) = demo.report(friendId)

    private companion object {
        // 세션 보관 기간(90일)만큼: 연속 일수를 그만큼 셀 수 있다.
        const val HISTORY_DAYS = 90L
        val STANDING_EVERY: Duration = Duration.ofMinutes(10)
    }
}
