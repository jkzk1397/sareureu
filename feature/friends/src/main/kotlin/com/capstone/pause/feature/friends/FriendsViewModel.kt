package com.capstone.pause.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.ai.coach.PokeDrafter
import com.capstone.pause.core.ai.engine.validate.BannedWords
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.social.AcceptResult
import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.FriendRepository
import com.capstone.pause.core.social.FriendRequests
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.InviteCodes
import com.capstone.pause.core.social.InviteLinkInbox
import com.capstone.pause.core.social.LeagueRepository
import com.capstone.pause.core.social.LeagueState
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.PokeRules
import com.capstone.pause.core.social.PollBackoff
import com.capstone.pause.core.social.ReportReason
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.SocialError
import com.capstone.pause.core.social.SocialLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 잔소리 보내기 창 상태. [friendId]가 있으면 그 친구에게 보내는 창이 열려 있다. */
data class PokeDraftState(
    val friendId: String? = null,
    val text: String = "",
    val drafts: List<String> = emptyList(),
    val drafting: Boolean = false,
    val sending: Boolean = false,
    val result: PokeFeedback? = null,
)

/** 보내기·초안 결과 안내. */
sealed interface PokeFeedback {
    /** 보냄([demo]면 가상 친구라 실제로 가지 않음). */
    data class Sent(val nickname: String, val demo: Boolean) : PokeFeedback

    /** 검사에 걸림. */
    data class Rejected(val reason: EntrustResult) : PokeFeedback

    /** AI 초안을 못 만듦. */
    data object DraftFailed : PokeFeedback
}

/** 친구 탭 상태. */
data class FriendsUiState(
    val overview: FriendsOverview? = null,
    val league: LeagueState = LeagueState.ComingSoon,
    val poke: PokeDraftState = PokeDraftState(),
    val sharing: SharingUiState = SharingUiState(),
    val invite: InviteUiState = InviteUiState(),
    /** 창을 닫은 뒤 친구 목록 아래에 보일 보내기 결과. */
    val pokeFeedback: PokeFeedback? = null,
    /** 연결 요청(받은 것·보낸 것)과 진행 상태. */
    val requests: RequestsUiState = RequestsUiState(),
    /** 사용자가 직접 새로고침하는 중(당겨서·버튼). */
    val refreshing: Boolean = false,
    /** 철회했지만 서버의 내 기록을 아직 지우는 중. */
    val deletingRemote: Boolean = false,
    /** 저장해 둔 친구 목록을 보이는 중일 때 "N분 전 기준"의 N. 서버에서 방금 받은 것이면 null. */
    val staleMinutes: Long? = null,
)

/** 친구 탭 동작. */
sealed interface FriendsAction {
    /** 말풍선: 그 친구에게 잔소리 보내기 창 열기(시청 중일 때만). */
    data class OpenPoke(val id: String) : FriendsAction

    /** 창 닫기. */
    data object ClosePoke : FriendsAction

    /** 잔소리 글 고치기. */
    data class EditText(val text: String) : FriendsAction

    /** AI 초안 받기. */
    data object RequestDraft : FriendsAction

    /** 초안 하나 고르기(입력칸에 채움). */
    data class PickDraft(val text: String) : FriendsAction

    /** 보내기. */
    data object SendPoke : FriendsAction

    /** 차단. */
    data class Block(val id: String) : FriendsAction

    /** 차단 풀기. */
    data class Unblock(val id: String) : FriendsAction

    /** 신고(사유와 함께 서버에 남는다). */
    data class Report(val id: String, val reason: ReportReason = ReportReason.OTHER) : FriendsAction

    /** 초대 링크·QR로 받은 코드로 연결을 요청한다(확인 창의 '요청하기'). */
    data object ConfirmLink : FriendsAction

    /** 초대 링크로 받은 코드를 버린다(확인 창의 '취소'). */
    data object CancelLink : FriendsAction

    /** 받은 연결 요청 수락. */
    data class AcceptRequest(val id: String) : FriendsAction

    /** 받은 연결 요청 거절([block]이면 거절하면서 차단). 상대에게 알리지 않는다. */
    data class DeclineRequest(val id: String, val block: Boolean = false) : FriendsAction

    /** 받은 연결 요청 신고(닉네임). 신고하면서 거절한다. */
    data class ReportRequest(val id: String) : FriendsAction

    /** 내가 보낸 요청 취소. */
    data class CancelRequest(val id: String) : FriendsAction

    /** 내 초대 코드 취소. */
    data object CancelInvite : FriendsAction

    /** 내 QR 화면 열기·닫기. 열려 있는 동안에만 받은 요청을 몇 초마다 확인한다. */
    data class ShowQr(val open: Boolean) : FriendsAction

    /** 직접 새로고침(당겨서·버튼): 친구·요청·놓친 잔소리를 바로 받는다. */
    data object Refresh : FriendsAction

    /** 요청 결과 안내 닫기. */
    data object DismissRequestResult : FriendsAction

    /** 안내 닫기. */
    data object DismissFeedback : FriendsAction

    /** 랭킹 공유 동의(버튼을 눌렀을 때만). */
    data object GrantSharing : FriendsAction

    /** 공유 그만하기(확인 창 뒤). */
    data object RevokeSharing : FriendsAction

    /** 내 초대 코드 만들기(이전 코드는 없어짐). */
    data object CreateInvite : FriendsAction

    /** 친구 코드 입력. */
    data class EditCode(val text: String) : FriendsAction

    /** 친구 코드로 연결. */
    data object Connect : FriendsAction

    /** 연결 끊기. */
    data class Disconnect(val id: String) : FriendsAction
}

/** 잔소리 글 검사: 길이(코어) + 금지 표현(AI 엔진 목록). 통과하면 null. */
internal fun checkPoke(text: String): EntrustResult? =
    PokeRules.check(text) ?: if (BannedWords.contains(text)) EntrustResult.Banned else null

/** 초안의 `{friend}` 자리표시자를 친구 이름으로 채운다. */
internal fun fillFriend(draft: String, nickname: String): String = draft.replace("{friend}", nickname).trim()

/**
 * 친구 탭 ViewModel. 순위·연속 일수 계산은 core:social·core:insights가 한다.
 * 친구 탭이 화면에 있는 동안([setVisible]) 몇 초마다 서버에서 친구·시청 중을 새로 받는다.
 */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val pokeInbox: PokeInbox,
    private val pokeDrafter: PokeDrafter,
    private val profileRepository: ProfileRepository,
    private val sharingRepository: SharingRepository,
    private val inviteLinkInbox: InviteLinkInbox,
    private val clock: AppClock,
    leagueRepository: LeagueRepository,
) : ViewModel() {
    private val poke = MutableStateFlow(PokeDraftState())
    private val pokeFeedback = MutableStateFlow<PokeFeedback?>(null)
    private val sharing = MutableStateFlow(SharingUiState())
    private val invite = MutableStateFlow(InviteUiState())
    private val requestUi = MutableStateFlow(RequestsUiState())
    private val refreshing = MutableStateFlow(false)
    private var polling: Job? = null
    private var qrPolling: Job? = null
    private var visible = false

    /** 화면 상태. */
    val uiState: StateFlow<FriendsUiState> = combine(
        friendRepository.observe(),
        leagueRepository.observe(),
        poke,
        combine(sharing, invite, inviteLinkInbox.pending, pokeFeedback, ::InviteParts),
        combine(friendRepository.requests, requestUi, refreshing, sharingRepository.deletingRemote, ::RequestParts),
    ) { overview, league, p, parts, more ->
        // 창을 연 친구가 목록에서 빠지면(차단·끊기) 창을 닫는다.
        val open = p.friendId?.takeIf { id -> overview.friends.any { it.id == id } }
        FriendsUiState(
            overview = overview,
            league = league,
            poke = p.copy(friendId = open),
            sharing = parts.sharing,
            // 초대 링크로 받은 코드는 동의한 뒤에만 확인 창으로 묻는다(동의 전이면 동의 화면이 먼저다).
            invite = parts.invite.copy(
                pending = parts.pending,
                linkToConfirm = parts.pending.takeIf {
                    overview.sharing == SharingState.CONSENTED && !parts.invite.busy
                },
            ),
            pokeFeedback = parts.feedback,
            requests = more.ui.copy(received = more.requests.received, sent = more.requests.sent),
            refreshing = more.refreshing,
            deletingRemote = more.deleting,
            staleMinutes = overview.syncedAt?.takeIf { overview.offline }
                ?.let { java.time.Duration.between(it, clock.now()).toMinutes().coerceAtLeast(0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FriendsUiState())

    private data class RequestParts(
        val requests: FriendRequests,
        val ui: RequestsUiState,
        val refreshing: Boolean,
        val deleting: Boolean,
    )

    private data class InviteParts(
        val sharing: SharingUiState,
        val invite: InviteUiState,
        val pending: String?,
        val feedback: PokeFeedback?,
    )

    // 초대 링크·QR로 열려도 바로 연결하지 않는다(ADR-0014): 누가 링크를 누르게만 해도 친구가 되면 안 되므로
    // 확인 창([InviteUiState.linkToConfirm])에서 '요청하기'를 눌렀을 때만 [FriendsAction.ConfirmLink]로 요청한다.

    /**
     * 친구 탭이 화면에 보이는지. 보이는 동안만 [SocialLimits.FRIENDS_POLL]마다 친구·순위·시청 중을 새로 받는다
     * (시연·동의 전이면 저장소가 아무것도 부르지 않는다).
     */
    fun setVisible(visible: Boolean) {
        this.visible = visible
        if (!visible) {
            // 화면이 꺼지거나 앱이 뒤로 가면 모든 주기 요청을 멈춘다.
            polling?.cancel()
            polling = null
            stopQrPolling()
            return
        }
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            // 화면에 올 때 한 번: 받은·보낸 연결 요청.
            runCatching { friendRepository.refreshRequests() }
            var friendIds: Set<String>? = null
            var failures = 0
            while (isActive) {
                // 서버가 거부하면(무료 한도 초과·점검·오프라인) 저장해 둔 목록을 보이면서 간격을 늘린다. 몰아서 다시 하지 않는다.
                val ok = runCatching { friendRepository.refresh() }.getOrDefault(false)
                val remote = uiState.value.overview?.sharing == SharingState.CONSENTED
                failures = if (ok || !remote) 0 else failures + 1
                // 친구 목록이 바뀌었으면(내 요청이 수락됨 등) 요청 목록도 새로 받아 끝난 요청을 치운다.
                val now = uiState.value.overview?.friends?.map { it.id }?.toSet()
                if (friendIds != null && now != friendIds) runCatching { friendRepository.refreshRequests() }
                friendIds = now
                delay(PollBackoff.next(SocialLimits.FRIENDS_POLL, failures).toMillis())
            }
        }
        if (invite.value.qrOpen) startQrPolling()
    }

    /**
     * 내 QR 화면을 띄워 둔 동안에만 받은 요청을 [SocialLimits.QR_POLL]마다 확인해 바로 수락할 수 있게 한다.
     * [SocialLimits.QR_POLL_MAX]가 지나면 멈춘다(그 뒤에는 새로고침 버튼으로).
     */
    private fun startQrPolling() {
        if (qrPolling?.isActive == true || !visible) return
        invite.update { it.copy(qrWatching = true) }
        qrPolling = viewModelScope.launch {
            val until = clock.now().plus(SocialLimits.QR_POLL_MAX)
            while (isActive && clock.now().isBefore(until)) {
                runCatching { friendRepository.refreshRequests() }
                delay(SocialLimits.QR_POLL.toMillis())
            }
            invite.update { it.copy(qrWatching = false) }
        }
    }

    private fun stopQrPolling() {
        qrPolling?.cancel()
        qrPolling = null
        invite.update { it.copy(qrWatching = false) }
    }

    /** 동작 처리. */
    fun onAction(action: FriendsAction) {
        when (action) {
            is FriendsAction.OpenPoke -> {
                pokeFeedback.value = null
                poke.value = PokeDraftState(friendId = action.id)
            }

            FriendsAction.ClosePoke -> poke.value = PokeDraftState()

            is FriendsAction.EditText -> poke.update {
                it.copy(text = action.text.take(PokeRules.MAX_LENGTH * 2), result = null)
            }

            is FriendsAction.PickDraft -> poke.update { it.copy(text = action.text, result = null) }

            FriendsAction.RequestDraft -> requestDraft()

            FriendsAction.SendPoke -> send()

            is FriendsAction.Block -> viewModelScope.launch { friendRepository.block(action.id) }

            is FriendsAction.Unblock -> viewModelScope.launch { friendRepository.unblock(action.id) }

            is FriendsAction.Report -> viewModelScope.launch {
                runCatching { friendRepository.report(action.id, action.reason) }
            }

            FriendsAction.ConfirmLink -> inviteLinkInbox.take()?.let(::connect)

            FriendsAction.CancelLink -> inviteLinkInbox.take()

            is FriendsAction.AcceptRequest -> accept(action.id)

            is FriendsAction.DeclineRequest -> requestWork(action.id) {
                friendRepository.declineRequest(action.id, action.block)
            }

            is FriendsAction.ReportRequest -> requestWork(action.id) {
                friendRepository.reportRequest(action.id, ReportReason.NICKNAME)
                friendRepository.declineRequest(action.id)
            }

            is FriendsAction.CancelRequest -> requestWork(action.id) { friendRepository.cancelRequest(action.id) }

            FriendsAction.CancelInvite -> viewModelScope.launch {
                stopQrPolling()
                runCatching { friendRepository.cancelInvite() }
                invite.update { it.copy(code = null, expires = null, qr = null, link = null, qrOpen = false) }
            }

            is FriendsAction.ShowQr -> {
                invite.update { it.copy(qrOpen = action.open) }
                if (action.open) startQrPolling() else stopQrPolling()
            }

            FriendsAction.Refresh -> refreshNow()

            FriendsAction.DismissRequestResult -> requestUi.update { it.copy(result = null) }

            FriendsAction.DismissFeedback -> {
                poke.update { it.copy(result = null) }
                pokeFeedback.value = null
            }

            FriendsAction.GrantSharing -> changeSharing(stop = false) { sharingRepository.grant() }

            FriendsAction.RevokeSharing -> changeSharing(stop = true) { sharingRepository.revoke() }

            FriendsAction.CreateInvite -> createInvite()

            // 붙여 넣은 값도 바로 다듬는다(빈칸·줄표 제거, 대문자).
            is FriendsAction.EditCode -> invite.update {
                it.copy(input = InviteCodes.clean(action.text).take(SocialLimits.CODE_LENGTH), result = null)
            }

            FriendsAction.Connect -> connect(invite.value.input)

            is FriendsAction.Disconnect -> viewModelScope.launch { friendRepository.disconnect(action.id) }
        }
    }

    private fun createInvite() {
        if (invite.value.busy) return
        invite.update { it.copy(busy = true, inviteFailed = false, result = null) }
        viewModelScope.launch {
            val code = runCatching { friendRepository.createInvite() }.getOrNull()
            invite.update {
                it.copy(
                    busy = false,
                    code = code?.code ?: it.code,
                    expires = code?.let { c -> LocalDateTime.ofInstant(c.expiresAt, clock.zone) } ?: it.expires,
                    qr = code?.let { c -> c.link?.let(InviteQr::encode) } ?: it.qr,
                    link = code?.link ?: it.link,
                    inviteFailed = code == null,
                )
            }
        }
    }

    private fun accept(id: String) {
        if (requestUi.value.busyId != null) return
        requestUi.update { it.copy(busyId = id, result = null) }
        viewModelScope.launch {
            val result = runCatching { friendRepository.acceptRequest(id) }
                .getOrDefault(AcceptResult.Error(SocialError.Unknown))
            requestUi.update { it.copy(busyId = null, result = result) }
        }
    }

    private fun requestWork(id: String, block: suspend () -> Unit) {
        if (requestUi.value.busyId != null) return
        requestUi.update { it.copy(busyId = id, result = null) }
        viewModelScope.launch {
            runCatching { block() }
            requestUi.update { it.copy(busyId = null) }
        }
    }

    /** 직접 새로고침: 주기를 기다리지 않고 친구·요청·놓친 잔소리를 바로 받는다. */
    private fun refreshNow() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            runCatching { friendRepository.refresh() }
            runCatching { friendRepository.refreshRequests() }
            // 놓친 잔소리 받기는 직접 새로고침할 때만 30초 간격으로 허용한다.
            runCatching { pokeInbox.fetch(manual = true) }
            refreshing.value = false
        }
    }

    private fun connect(code: String) {
        if (invite.value.busy || code.isBlank()) return
        invite.update { it.copy(busy = true, result = null) }
        viewModelScope.launch {
            val result = runCatching { friendRepository.connect(code) }.getOrDefault(ConnectResult.Failed)
            val done = result is ConnectResult.Connected || result is ConnectResult.Requested
            invite.update { it.copy(busy = false, result = result, input = if (done) "" else it.input) }
        }
    }

    private fun changeSharing(stop: Boolean, block: suspend () -> Boolean) {
        if (sharing.value.busy) return
        sharing.value = SharingUiState(busy = true)
        viewModelScope.launch {
            val ok = runCatching { block() }.getOrDefault(false)
            sharing.value = SharingUiState(failed = !ok && !stop, stopFailed = !ok && stop)
        }
    }

    private fun selectedNickname(): String? {
        val state = uiState.value
        return state.overview?.friends?.firstOrNull { it.id == state.poke.friendId }?.nickname
    }

    private fun requestDraft() {
        val nickname = selectedNickname() ?: return
        poke.update { it.copy(drafting = true, result = null) }
        viewModelScope.launch {
            val tone = profileRepository.profile.first()?.tone ?: Tone.PLAYFUL
            val drafts = runCatching { pokeDrafter.draft(tone) }.getOrDefault(emptyList())
                .map { fillFriend(it, nickname) }
                .filter { checkPoke(it) == null }
            poke.update {
                it.copy(
                    drafting = false,
                    drafts = drafts,
                    result = if (drafts.isEmpty()) PokeFeedback.DraftFailed else null,
                )
            }
        }
    }

    private fun send() {
        val state = uiState.value
        val friendId = state.poke.friendId ?: return
        val overview = state.overview ?: return
        val friend = overview.friends.firstOrNull { it.id == friendId } ?: return
        if (state.poke.sending || !friend.watching) return
        val text = state.poke.text
        checkPoke(text)?.let { reason ->
            poke.update { it.copy(result = PokeFeedback.Rejected(reason)) }
            return
        }
        poke.update { it.copy(sending = true, result = null) }
        viewModelScope.launch {
            val result = runCatching { pokeInbox.entrust(friendId, text) }.getOrDefault(EntrustResult.Failed)
            if (result == EntrustResult.Entrusted) {
                // 보냈으면 창을 닫고 목록 아래에 결과를 보인다.
                pokeFeedback.value = PokeFeedback.Sent(friend.nickname, demo = overview.isDemo)
                poke.value = PokeDraftState()
            } else {
                poke.update { it.copy(sending = false, result = PokeFeedback.Rejected(result)) }
                if (result == EntrustResult.NotWatching) runCatching { friendRepository.refresh() }
            }
        }
    }

    override fun onCleared() {
        polling?.cancel()
        qrPolling?.cancel()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
