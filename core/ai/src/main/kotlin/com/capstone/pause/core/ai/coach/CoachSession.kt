package com.capstone.pause.core.ai.coach

import com.capstone.pause.core.ai.AiCallLogger
import com.capstone.pause.core.ai.AiRouter
import com.capstone.pause.core.ai.engine.coach.CoachEngine
import com.capstone.pause.core.ai.engine.coach.CoachTurn
import com.capstone.pause.core.ai.engine.llm.AiFeature
import com.capstone.pause.core.ai.engine.llm.ChatMessage
import com.capstone.pause.core.ai.engine.llm.Provider
import com.capstone.pause.core.ai.engine.run.AiQuota
import com.capstone.pause.core.ai.engine.safety.HelpLine
import com.capstone.pause.core.ai.engine.schema.PlanProposalOut
import com.capstone.pause.core.ai.engine.schema.ProfileSummary
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.message.MessageRepository
import com.capstone.pause.core.data.plan.PlanRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.model.BuddyMessage
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.IfThenPlan
import com.capstone.pause.core.model.Sender
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** AI 답 하나에 붙는 화면 정보(메시지 ID로 찾는다). 대화 글은 메시지 저장소에 있다. */
data class ReplyDetails(
    val messageId: Long,
    val aiCallId: Long,
    val evidence: List<EvidenceView>,
    val followUps: List<String>,
    val planProposal: PlanProposalOut?,
    val usedRealAi: Boolean,
    val planSaved: Boolean = false,
    val reported: Boolean = false,
)

/** 코치 대화 상태. */
data class CoachState(
    /** 기록 확인 중(도구 호출 루프가 도는 동안). */
    val thinking: Boolean = false,
    /** 위기 표현이 보여 AI를 멈췄다 → 도움 카드. */
    val crisis: List<HelpLine>? = null,
    /** 오늘 채팅 상한에 닿음. */
    val quotaReached: Boolean = false,
    /** 메시지 ID → 근거·제안. */
    val details: Map<Long, ReplyDetails> = emptyMap(),
)

/** 코치 대화방 창구(화면이 쓰는 것만). 테스트에서는 가짜로 바꾼다. */
interface CoachConversation {
    /** 대화 상태. */
    val state: StateFlow<CoachState>

    /** 지금 실제 AI를 쓰는지. */
    val usingRealAi: Flow<Boolean>

    /** 대화방 글(최신이 앞). */
    fun messages(limit: Int = 100): Flow<List<BuddyMessage>>

    /** 보내기. */
    suspend fun send(text: String)

    /** 계획으로 저장(버튼). */
    suspend fun savePlan(messageId: Long)

    /** 신고. */
    suspend fun report(messageId: Long)

    /** 도움 카드 닫기. */
    fun dismissCrisis()
}

/**
 * 코치 대화방(공개 API). 메시지 알림과 같은 대화방이라 글은 [MessageRepository]에 쌓이고,
 * 근거 칩·계획 제안·신고 같은 AI 답의 부가 정보는 이 세션이 메모리에 들고 있다(앱을 다시 켜면 글만 남는다).
 * 앱이 화면에 있을 때 ViewModel이 부른다.
 */
@Singleton
class CoachSession @Inject constructor(
    private val engine: CoachEngine,
    private val router: AiRouter,
    private val logger: AiCallLogger,
    private val messageRepository: MessageRepository,
    private val planRepository: PlanRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: AppClock,
) : CoachConversation {
    private val mutex = Mutex()
    private val history = mutableListOf<ChatMessage>()
    private val quota = AiQuota()
    private var turnsDay: LocalDate? = null
    private var turnsToday = 0

    private val _state = MutableStateFlow(CoachState())

    /** 대화 상태. */
    override val state: StateFlow<CoachState> = _state.asStateFlow()

    /** 대화방 글(르르 메시지 알림 + 채팅, 최신이 앞). */
    override fun messages(limit: Int): Flow<List<BuddyMessage>> = messageRepository.observeRecent(limit)

    /** 지금 실제 AI를 쓰는지("모의 AI" 표시). */
    override val usingRealAi: Flow<Boolean> get() = router.usingRealAi

    /** 사용자 말 보내기. 위기 표현이면 AI를 부르지 않고 도움 카드 상태로. */
    override suspend fun send(text: String) = mutex.withLock {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withLock
        val today = clock.today()
        if (turnsDay != today) {
            turnsDay = today
            turnsToday = 0
        }
        if (!quota.canChat(turnsToday)) {
            _state.update { it.copy(quotaReached = true) }
            return@withLock
        }
        val source = source()
        _state.update { it.copy(thinking = true, crisis = null) }
        try {
            val client = router.clientFor(AiFeature.COACH_CHAT)
            val profile = ProfileSummary.from(profileRepository.profile.first())
            when (val turn = engine.reply(client, history.toList(), trimmed, profile)) {
                is CoachTurn.Crisis -> {
                    // 위기 표현이 담긴 원문은 저장하지도, AI로 보내지도 않는다.
                    _state.update { it.copy(crisis = turn.helpLines) }
                }

                is CoachTurn.Reply -> {
                    turnsToday++
                    messageRepository.add(
                        BuddyMessage(0, clock.now(), Sender.Me, turn.maskedUserText, null, null),
                        source,
                    )
                    val answerId = messageRepository.add(
                        BuddyMessage(0, clock.now(), Sender.Buddy, turn.reply.answer, null, null),
                        source,
                    )
                    val callId = logger.log(turn.record)
                    history += ChatMessage.User(turn.maskedUserText)
                    history += ChatMessage.Model(turn.reply.answer)
                    while (history.size > MAX_HISTORY) history.removeAt(0)
                    val details = ReplyDetails(
                        messageId = answerId,
                        aiCallId = callId,
                        evidence = EvidenceResolver.resolve(turn.reply.evidence, turn.toolResults),
                        followUps = turn.reply.followUps,
                        planProposal = turn.reply.planProposal,
                        usedRealAi = turn.record.provider != Provider.MOCK,
                    )
                    _state.update { it.copy(details = it.details + (answerId to details)) }
                }
            }
        } finally {
            _state.update { it.copy(thinking = false) }
        }
    }

    /** "계획으로 저장" 버튼. 자동 저장하지 않는다(사용자가 눌렀을 때만). */
    override suspend fun savePlan(messageId: Long) {
        val details = _state.value.details[messageId] ?: return
        val proposal = details.planProposal ?: return
        if (details.planSaved) return
        planRepository.add(
            IfThenPlan(0, null, proposal.ifText, proposal.thenText, clock.now(), active = true),
            source(),
        )
        _state.update { it.copy(details = it.details + (messageId to details.copy(planSaved = true))) }
    }

    /** AI 답 '신고'. 감사 로그에 표시한다. */
    override suspend fun report(messageId: Long) {
        val details = _state.value.details[messageId] ?: return
        logger.report(details.aiCallId)
        _state.update { it.copy(details = it.details + (messageId to details.copy(reported = true))) }
    }

    /** 도움 카드 닫기. */
    override fun dismissCrisis() = _state.update { it.copy(crisis = null) }

    private suspend fun source(): DataSource =
        if (settingsRepository.settings.first().demoMode) DataSource.DEMO else DataSource.REAL

    private companion object {
        const val MAX_HISTORY = 20
    }
}
