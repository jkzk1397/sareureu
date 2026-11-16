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
import com.capstone.pause.core.social.LeagueRepository
import com.capstone.pause.core.social.LeagueState
import com.capstone.pause.core.social.PokeInbox
import com.capstone.pause.core.social.PokeRules
import com.capstone.pause.core.social.SharingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 잔소리 맡기기 입력 상태. */
data class PokeDraftState(
    val friendId: String? = null,
    val text: String = "",
    val drafts: List<String> = emptyList(),
    val drafting: Boolean = false,
    val result: PokeFeedback? = null,
)

/** 맡기기·초안 결과 안내. */
sealed interface PokeFeedback {
    /** 맡김. */
    data class Entrusted(val nickname: String, val remote: Boolean = false) : PokeFeedback

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
)

/** 친구 탭 동작. */
sealed interface FriendsAction {
    /** 잔소리 받을 친구 고르기. */
    data class SelectFriend(val id: String) : FriendsAction

    /** 잔소리 글 고치기. */
    data class EditText(val text: String) : FriendsAction

    /** AI 초안 받기. */
    data object RequestDraft : FriendsAction

    /** 초안 하나 고르기(입력칸에 채움). */
    data class PickDraft(val text: String) : FriendsAction

    /** 맡기기. */
    data object Entrust : FriendsAction

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

/** 친구 탭 ViewModel. 순위·감소율 계산은 core:social이 한다. */
@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val pokeInbox: PokeInbox,
    private val pokeDrafter: PokeDrafter,
    private val profileRepository: ProfileRepository,
    private val sharingRepository: SharingRepository,
    private val clock: AppClock,
    leagueRepository: LeagueRepository,
) : ViewModel() {
    private val poke = MutableStateFlow(PokeDraftState())
    private val sharing = MutableStateFlow(SharingUiState())
    private val invite = MutableStateFlow(InviteUiState())

    /** 화면 상태. */
    val uiState: StateFlow<FriendsUiState> = combine(
        friendRepository.observe(),
        leagueRepository.observe(),
        poke,
        combine(sharing, invite, ::Pair),
    ) { overview, league, p, (sh, inv) ->
        val selected = p.friendId?.takeIf { id -> overview.friends.any { it.id == id } }
            ?: overview.friends.firstOrNull()?.id
        FriendsUiState(overview, league, p.copy(friendId = selected), sh, inv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FriendsUiState())

    init {
        // 친구 서버 조회는 친구 탭을 열 때만(무료 한도). 시연·동의 전이면 아무것도 부르지 않는다.
        viewModelScope.launch { runCatching { friendRepository.refresh() } }
    }

    /** 동작 처리. */
    fun onAction(action: FriendsAction) {
        when (action) {
            is FriendsAction.SelectFriend -> poke.update {
                it.copy(friendId = action.id, drafts = emptyList(), result = null)
            }

            is FriendsAction.EditText -> poke.update {
                it.copy(text = action.text.take(PokeRules.MAX_LENGTH * 2), result = null)
            }

            is FriendsAction.PickDraft -> poke.update { it.copy(text = action.text, result = null) }

            FriendsAction.RequestDraft -> requestDraft()

            FriendsAction.Entrust -> entrust()

            is FriendsAction.Block -> viewModelScope.launch { friendRepository.block(action.id) }

            is FriendsAction.Unblock -> viewModelScope.launch { friendRepository.unblock(action.id) }

            is FriendsAction.Report -> viewModelScope.launch { friendRepository.report(action.id) }

            FriendsAction.DismissFeedback -> poke.update { it.copy(result = null) }

            FriendsAction.GrantSharing -> changeSharing(stop = false) { sharingRepository.grant() }

            FriendsAction.RevokeSharing -> changeSharing(stop = true) { sharingRepository.revoke() }

            FriendsAction.CreateInvite -> createInvite()

            is FriendsAction.EditCode -> invite.update {
                it.copy(input = action.text.take(CODE_INPUT_MAX), result = null)
            }

            FriendsAction.Connect -> connect()

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
                    inviteFailed = code == null,
                )
            }
        }
    }

    private fun connect() {
        val code = invite.value.input
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

    private fun entrust() {
        val state = uiState.value
        val friendId = state.poke.friendId ?: return
        val nickname = selectedNickname() ?: return
        val text = state.poke.text
        checkPoke(text)?.let { reason ->
            poke.update { it.copy(result = PokeFeedback.Rejected(reason)) }
            return
        }
        viewModelScope.launch {
            val result = pokeInbox.entrust(friendId, text)
            poke.update {
                if (result == EntrustResult.Entrusted) {
                    it.copy(
                        text = "",
                        drafts = emptyList(),
                        result = PokeFeedback.Entrusted(nickname, remote = state.overview?.isDemo == false),
                    )
                } else {
                    it.copy(result = PokeFeedback.Rejected(result))
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CODE_INPUT_MAX = 12
    }
}
