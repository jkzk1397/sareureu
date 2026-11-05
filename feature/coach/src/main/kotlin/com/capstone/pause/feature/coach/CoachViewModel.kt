package com.capstone.pause.feature.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.ai.coach.CoachConversation
import com.capstone.pause.core.ai.coach.ReplyDetails
import com.capstone.pause.core.ai.engine.safety.HelpLine
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.model.Sender
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 대화방 글 한 줄. */
data class ChatItem(
    val id: Long,
    val at: ZonedDateTime,
    val from: From,
    val text: String,
    /** 메시지 알림으로 왔던 글(르르·친구)인지. */
    val wasAlert: Boolean,
    /** AI 답이면 근거·제안. */
    val details: ReplyDetails?,
) {
    /** 보낸 쪽. */
    sealed interface From {
        /** 르르. */
        data object Buddy : From

        /** 친구가 맡긴 잔소리. */
        data class Friend(val nickname: String) : From

        /** 나. */
        data object Me : From
    }
}

/** 코치 화면 상태. [items]는 오래된 것부터. */
data class CoachUiState(
    val items: List<ChatItem> = emptyList(),
    val thinking: Boolean = false,
    val crisis: List<HelpLine>? = null,
    val quotaReached: Boolean = false,
    val usingRealAi: Boolean = false,
    val today: ZonedDateTime? = null,
)

/** 코치 화면 동작. */
sealed interface CoachAction {
    /** 보내기(입력 또는 추천 질문). */
    data class Send(val text: String) : CoachAction

    /** 계획으로 저장. */
    data class SavePlan(val messageId: Long) : CoachAction

    /** AI 답 신고. */
    data class Report(val messageId: Long) : CoachAction

    /** 도움 카드 닫기. */
    data object DismissCrisis : CoachAction
}

/** 코치 화면 ViewModel. AI는 화면이 떠 있을 때만(사용자가 보낼 때만) 부른다. */
@HiltViewModel
class CoachViewModel @Inject constructor(private val conversation: CoachConversation, private val clock: AppClock) :
    ViewModel() {

    /** 화면 상태. */
    val uiState: StateFlow<CoachUiState> = combine(
        conversation.messages(MESSAGE_LIMIT),
        conversation.state,
        conversation.usingRealAi,
    ) { messages, state, real ->
        CoachUiState(
            items = messages.sortedWith(compareBy({ it.at }, { it.id })).map { m ->
                ChatItem(
                    id = m.id,
                    at = local(m.at),
                    from = when (val s = m.sender) {
                        Sender.Buddy -> ChatItem.From.Buddy
                        is Sender.Friend -> ChatItem.From.Friend(s.nickname)
                        Sender.Me -> ChatItem.From.Me
                    },
                    text = m.text,
                    wasAlert = m.copyId != null || m.sender is Sender.Friend,
                    details = state.details[m.id],
                )
            },
            thinking = state.thinking,
            crisis = state.crisis,
            quotaReached = state.quotaReached,
            usingRealAi = real,
            today = local(clock.now()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CoachUiState())

    /** 동작 처리. */
    fun onAction(action: CoachAction) {
        viewModelScope.launch {
            when (action) {
                is CoachAction.Send -> conversation.send(action.text)
                is CoachAction.SavePlan -> conversation.savePlan(action.messageId)
                is CoachAction.Report -> conversation.report(action.messageId)
                CoachAction.DismissCrisis -> conversation.dismissCrisis()
            }
        }
    }

    private fun local(at: Instant) = ZonedDateTime.ofInstant(at, clock.zone)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MESSAGE_LIMIT = 100
    }
}
