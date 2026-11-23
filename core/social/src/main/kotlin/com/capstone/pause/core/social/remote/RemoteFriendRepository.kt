package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.friend.FriendsCache
import com.capstone.pause.core.data.monitoring.MonitoringGapRepository
import com.capstone.pause.core.data.monitoring.toSpans
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.social.AcceptResult
import com.capstone.pause.core.social.CodeCheck
import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.DemoFriendRepository
import com.capstone.pause.core.social.FriendRepository
import com.capstone.pause.core.social.FriendRequests
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.InviteCode
import com.capstone.pause.core.social.InviteCodes
import com.capstone.pause.core.social.MyStanding
import com.capstone.pause.core.social.ReceivedRequest
import com.capstone.pause.core.social.ReportReason
import com.capstone.pause.core.social.SentRequest
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.SocialError
import com.capstone.pause.core.social.SocialLimits
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** 서버가 내려 준 친구 목록. */
data class RemoteFriends(val friends: List<Standing>, val blocked: List<Pair<String, String>>)

/** 화면에 보일 친구 목록 한 장: 언제 받은 것인지, 지금 서버에 닿지 못해 저장본을 보이는 중인지. */
internal data class FriendsSnapshot(val data: RemoteFriends, val syncedAt: Instant, val offline: Boolean)

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

    /** `POST /api/v1/invites/{code}/requests` 결과: 요청만 생긴다. 코드 주인이 수락해야 친구가 된다(ADR-0014). */
    fun connect(response: ApiResponse): ConnectResult {
        if (response.ok) {
            val id = runCatching {
                response.body?.jsonObject?.get("request")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            return if (id != null) ConnectResult.Requested(id) else ConnectResult.Failed
        }
        return when {
            // 없는 코드·만료·차단은 서버가 같은 응답을 준다.
            response.code == NOT_FOUND -> ConnectResult.NotFound

            response.errorName == "own_invite" -> ConnectResult.OwnCode

            response.errorName == "already_friends" -> ConnectResult.AlreadyFriends

            response.errorName == "too_many_requests" && response.code == CONFLICT -> ConnectResult.TooManyRequests

            else -> ConnectResult.Error(response.error ?: SocialError.Unknown)
        }
    }

    /** `GET /api/v1/friend-requests`. 모양이 다르면 null. */
    fun requests(body: JsonElement?): FriendRequests? = runCatching {
        val o = body?.jsonObject ?: return null
        fun time(e: JsonObject, key: String) = Instant.ofEpochMilli(e.getValue(key).jsonPrimitive.longOrNull ?: 0)
        FriendRequests(
            received = o["received"]?.jsonArray.orEmpty().map { e ->
                val r = e.jsonObject
                ReceivedRequest(
                    id = r.getValue("id").jsonPrimitive.content,
                    fromNickname = r["fromNickname"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    createdAt = time(r, "createdAtMs"),
                    expiresAt = time(r, "expiresAtMs"),
                )
            },
            sent = o["sent"]?.jsonArray.orEmpty().map { e ->
                val r = e.jsonObject
                SentRequest(r.getValue("id").jsonPrimitive.content, time(r, "createdAtMs"), time(r, "expiresAtMs"))
            },
        )
    }.getOrNull()

    /** `POST /api/v1/friend-requests/{id}/accept` 결과. */
    fun accept(response: ApiResponse): AcceptResult {
        if (response.ok) {
            val nickname = runCatching {
                response.body?.jsonObject?.get("friend")?.jsonObject?.get("nickname")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            return AcceptResult.Accepted(nickname.orEmpty())
        }
        return when {
            response.code == NOT_FOUND -> AcceptResult.NotFound
            response.errorName == "too_many_friends" -> AcceptResult.TooManyFriends
            else -> AcceptResult.Error(response.error ?: SocialError.Unknown)
        }
    }

    /** `POST /api/v1/reports` 몸통. 대상은 친구 공개 ID나 받은 요청 ID 중 하나. */
    fun report(friendId: String?, requestId: String?, reason: ReportReason, text: String?): JsonObject = JsonObject(
        buildMap {
            friendId?.let { put("target", JsonPrimitive(it)) }
            requestId?.let { put("requestId", JsonPrimitive(it)) }
            put("reason", JsonPrimitive(reason.name))
            val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }?.take(SocialLimits.MAX_POKE_LENGTH)
            put("text", if (cleaned != null) JsonPrimitive(cleaned) else JsonNull)
        },
    )

    /** 사용자가 친 코드 다듬기: 빈칸·줄표 빼고 대문자. 모양이 맞지 않으면 null. */
    fun normalizeCode(input: String): String? = (InviteCodes.check(input) as? CodeCheck.Ok)?.code

    private const val NOT_FOUND = 404
    private const val CONFLICT = 409
}

/**
 * 친구 서버 구현(5-3, ADR-0014). 공유에 동의했고 시연 모드가 아닐 때만 서버를 쓴다. 아니면 가상 친구 구현([DemoFriendRepository])에 맡긴다.
 * - 순위는 서버가 아니라 앱이 [StreakRanking.build]로 매긴다(연속 일수, 모두 보임, ADR-0012).
 * - 받은 친구 목록은 기기에 저장해 두고, 서버에 닿지 못하면 그 목록을 "언제 기준"과 함께 보인다(시청 중 표시는 끈다).
 * - 서버 호출은 친구 탭이 화면에 있는 동안의 [refresh]와 사용자가 버튼을 눌렀을 때만.
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
    private val gapRepository: MonitoringGapRepository,
    private val recentPokes: RecentPokes,
    private val clock: AppClock,
) : FriendRepository {
    private val snapshot = MutableStateFlow<FriendsSnapshot?>(null)
    private val requestState = MutableStateFlow(FriendRequests())
    private var standingUploadedAt: Instant? = null
    private val json = Json { ignoreUnknownKeys = true }

    override fun observe(): Flow<FriendsOverview> = sharingRepository.state.flatMapLatest { state ->
        if (state == SharingState.CONSENTED) {
            remoteOverview()
        } else {
            // 철회·시연 모드로 바뀌면 받아 둔 친구 목록과 요청을 버린다.
            snapshot.value = null
            requestState.value = FriendRequests()
            demo.observe()
        }
    }

    override val requests: Flow<FriendRequests> = sharingRepository.state.flatMapLatest { state ->
        if (state == SharingState.CONSENTED) requestState else flowOf(FriendRequests())
    }

    private fun remoteOverview(): Flow<FriendsOverview> {
        val now = clock.now()
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(HISTORY_DAYS), clock.zone)
        return combine(
            sessionRepository.observeSessions(from, DayBoundary.endOf(today, clock.zone)),
            profileRepository.profile,
            combine(snapshot, localStore.friendsCache, ::pickSnapshot),
            localStore.reported,
            gapRepository.gaps,
        ) { sessions, profile, shown, reported, gaps ->
            val me = MyStanding.compute(
                sessions,
                today,
                clock.zone,
                now,
                profile?.nickname.orEmpty(),
                profile?.dailyGoalMinutes ?: 0,
                gaps.toSpans(clock.now()),
            )
            // 저장본을 보이는 중이면 '시청 중'은 믿을 수 없으니 끈다(잔소리 버튼도 같이 꺼진다).
            val friends = shown?.data?.friends.orEmpty().map {
                if (shown?.offline ==
                    true
                ) {
                    it.copy(watching = false)
                } else {
                    it
                }
            }
            val blocked = shown?.data?.blocked.orEmpty()
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
                syncedAt = shown?.syncedAt,
                offline = shown?.offline ?: false,
            )
        }
    }

    /** 이번 실행에서 받은 것이 있으면 그것, 없으면 기기에 저장해 둔 것(아직 서버에 확인하지 못했으니 저장본으로 표시). */
    private fun pickSnapshot(live: FriendsSnapshot?, cache: FriendsCache?): FriendsSnapshot? {
        if (live != null) return live
        val stored = cache ?: return null
        val parsed =
            runCatching { FriendPayloads.friends(json.parseToJsonElement(stored.json)) }.getOrNull() ?: return null
        return FriendsSnapshot(parsed, stored.savedAt, offline = true)
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
        if (parsed != null) {
            snapshot.value = FriendsSnapshot(parsed, now, offline = false)
            // 친구가 공유를 그만두거나 나를 끊으면 다음 목록에 없으므로 조용히 사라진다.
            response.body?.let { runCatching { localStore.saveFriendsCache(FriendsCache(it.toString(), now)) } }
        } else {
            // 서버에 닿지 못했다(오프라인·점검·한도): 조용히 저장본으로 돌아간다. 재시도는 다음 주기에 한 번만.
            val last = snapshot.value ?: pickSnapshot(null, localStore.friendsCache.first())
            snapshot.value = last?.copy(offline = true)
        }
        return parsed != null
    }

    override suspend fun createInvite(): InviteCode? {
        if (!remote()) return null
        val response = transport.call("POST", "/api/v1/invites", retries = SocialLimits.MAX_RETRIES)
        return if (response.ok) FriendPayloads.invite(response.body, server) else null
    }

    override suspend fun cancelInvite() {
        if (!remote()) return
        transport.call("DELETE", "/api/v1/invites", retries = SocialLimits.MAX_RETRIES)
    }

    override suspend fun connect(code: String): ConnectResult {
        if (!remote()) return ConnectResult.Failed
        // 모양이 틀린 코드는 서버에 보내지 않는다(틀린 코드 한도를 아낀다).
        val check = InviteCodes.check(code)
        val normalized = (check as? CodeCheck.Ok)?.code ?: return ConnectResult.InvalidCode(check)
        // 같은 사람에게 보내는 요청은 서버가 하나로 합치므로(멱등) 연결 실패 때 다시 해도 된다.
        val response = transport.call(
            "POST",
            "/api/v1/invites/$normalized/requests",
            retries = SocialLimits.MAX_RETRIES,
        )
        val result = FriendPayloads.connect(response)
        if (result is ConnectResult.Requested) refreshRequests()
        return result
    }

    override suspend fun refreshRequests(): Boolean {
        if (!remote()) return false
        val response = transport.call("GET", "/api/v1/friend-requests")
        val parsed = if (response.ok) FriendPayloads.requests(response.body) else null
        if (parsed != null) requestState.value = parsed
        return parsed != null
    }

    override suspend fun acceptRequest(requestId: String): AcceptResult {
        if (!remote()) return AcceptResult.Error(SocialError.Unknown)
        // 수락은 여러 번 가도 결과가 같다(이미 친구면 그대로 친구).
        val response = transport.call(
            "POST",
            "/api/v1/friend-requests/$requestId/accept",
            retries = SocialLimits.MAX_RETRIES,
        )
        val result = FriendPayloads.accept(response)
        if (result is AcceptResult.Accepted || result == AcceptResult.NotFound) {
            requestState.value =
                requestState.value.let { r -> r.copy(received = r.received.filter { it.id != requestId }) }
            refresh()
        }
        return result
    }

    override suspend fun declineRequest(requestId: String, block: Boolean) {
        if (!remote()) return
        val path = "/api/v1/friend-requests/$requestId" + if (block) "?block=1" else ""
        if (transport.call("DELETE", path, retries = SocialLimits.MAX_RETRIES).ok) {
            requestState.value =
                requestState.value.let { r -> r.copy(received = r.received.filter { it.id != requestId }) }
            if (block) refresh()
        }
    }

    override suspend fun cancelRequest(requestId: String) {
        if (!remote()) return
        if (transport.call("DELETE", "/api/v1/friend-requests/$requestId", retries = SocialLimits.MAX_RETRIES).ok) {
            requestState.value = requestState.value.let { r -> r.copy(sent = r.sent.filter { it.id != requestId }) }
        }
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

    /** 사유 없는 신고(예전 화면): 그 밖(OTHER)으로 서버에 남긴다. */
    override suspend fun report(friendId: String) {
        report(friendId, ReportReason.OTHER, null)
    }

    override suspend fun report(friendId: String, reason: ReportReason, text: String?): Boolean {
        // 이 폰에도 표시해 둔다(친구 관리에 '신고함').
        demo.report(friendId)
        if (!remote()) return false
        // 잔소리 내용 신고면 그 친구가 방금 보낸 문장을 함께 보낸다(운영자가 무엇을 신고했는지 볼 수 있게).
        val reported = text ?: recentPokes.lastFrom(friendId).takeIf { reason == ReportReason.POKE }
        return transport.call("POST", "/api/v1/reports", FriendPayloads.report(friendId, null, reason, reported)).ok
    }

    override suspend fun reportRequest(requestId: String, reason: ReportReason): Boolean {
        if (!remote()) return false
        return transport.call("POST", "/api/v1/reports", FriendPayloads.report(null, requestId, reason, null)).ok
    }

    private companion object {
        // 세션 보관 기간(90일)만큼: 연속 일수를 그만큼 셀 수 있다.
        const val HISTORY_DAYS = 90L
        val STANDING_EVERY: Duration = Duration.ofMinutes(10)
    }
}
