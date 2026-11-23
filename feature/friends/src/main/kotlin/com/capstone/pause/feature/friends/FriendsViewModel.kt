package com.capstone.pause.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.ai.coach.PokeDrafter
import com.capstone.pause.core.ai.engine.validate.BannedWords
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.social.ConnectResult
import com.capstone.pause.core.social.EntrustResult
import com.capstone.pause.core.social.FriendRepository
import com.capstone.pause.core.social.FriendsOverview
import com.capstone.pause.core.social.InviteLinkInbox
import com.capstone.pause.core.social.LeagueRepository
import com.capstone.pause.core.social.LeagueState
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.PokeRules
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
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

    /** 신고. */
    data class Report(val id: String) : FriendsAction

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
    private var polling: Job? = null

    /** 화면 상태. */
    val uiState: StateFlow<FriendsUiState> = combine(
        friendRepository.observe(),
        leagueRepository.observe(),
        poke,
        combine(sharing, invite, inviteLinkInbox.pending, pokeFeedback, ::InviteParts),
    ) { overview, league, p, parts ->
        // 창을 연 친구가 목록에서 빠지면(차단·끊기) 창을 닫는다.
        val open = p.friendId?.takeIf { id -> overview.friends.any { it.id == id } }
        FriendsUiState(
            overview = overview,
            league = league,
            poke = p.copy(friendId = open),
            sharing = parts.sharing,
            invite = parts.invite.copy(pending = parts.pending),
            pokeFeedback = parts.feedback,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FriendsUiState())

    private data class InviteParts(
        val sharing: SharingUiState,
        val invite: InviteUiState,
        val pending: String?,
        val feedback: PokeFeedback?,
    )

    init {
        // 초대 QR로 열렸으면: 공유에 동의돼 있을 때 그 코드로 바로 연결한다(동의 전이면 동의할 때까지 기다린다).
        viewModelScope.launch {
            combine(
                inviteLinkInbox.pending,
                sharingRepository.state.distinctUntilChanged(),
                invite.map { it.busy }.distinctUntilChanged(),
                ::Triple,
            ).collect { (code, state, busy) ->
                // 다른 연결이 진행 중이면 꺼내지 않고 둔다(끝나면 다시 본다).
                if (code != null && state == SharingState.CONSENTED && !busy) inviteLinkInbox.take()?.let(::connect)
            }
        }
    }

    /**
     * 친구 탭이 화면에 보이는지. 보이는 동안만 [POLL_MS]마다 친구·순위·시청 중을 새로 받는다
     * (시연·동의 전이면 저장소가 아무것도 부르지 않는다).
     */
    fun setVisible(visible: Boolean) {
        if (!visible) {
            polling?.cancel()
            polling = null
            return
        }
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (isActive) {
                runCatching { friendRepository.refresh() }
                delay(POLL_MS)
            }
        }
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

            is FriendsAction.Report -> viewModelScope.launch { friendRepository.report(action.id) }

            FriendsAction.DismissFeedback -> {
                poke.update { it.copy(result = null) }
                pokeFeedback.value = null
            }

            FriendsAction.GrantSharing -> changeSharing(stop = false) { sharingRepository.grant() }

            FriendsAction.RevokeSharing -> changeSharing(stop = true) { sharingRepository.revoke() }

            FriendsAction.CreateInvite -> createInvite()

            is FriendsAction.EditCode -> invite.update {
                it.copy(input = action.text.take(CODE_INPUT_MAX), result = null)
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
                    inviteFailed = code == null,
                )
            }
        }
    }

    private fun connect(code: String) {
        if (invite.value.busy || code.isBlank()) return
        invite.update { it.copy(busy = true, result = null) }
        viewModelScope.launch {
            val result = runCatching { friendRepository.connect(code) }.getOrDefault(ConnectResult.Failed)
            invite.update {
                it.copy(busy = false, result = result, input = if (result is ConnectResult.Connected) "" else it.input)
            }
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
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CODE_INPUT_MAX = 12

        /** 친구 탭이 보이는 동안 새로 받는 간격(서버의 '시청 중' 판정 5초와 맞춘다). */
        const val POLL_MS = 3_000L
    }
}
