package com.capstone.pause.feature.coach

import com.capstone.pause.core.ai.coach.CoachConversation
import com.capstone.pause.core.ai.coach.CoachState
import com.capstone.pause.core.ai.coach.ReplyDetails
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.model.BuddyMessage
import com.capstone.pause.core.model.Sender
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 가짜 대화: 보내면 내 말과 르르 답을 쌓는다. */
private class FakeConversation : CoachConversation {
    val stored = MutableStateFlow<List<BuddyMessage>>(emptyList())
    val saved = mutableListOf<Long>()
    override val state = MutableStateFlow(CoachState())
    override val usingRealAi: Flow<Boolean> = flowOf(false)

    override fun messages(limit: Int): Flow<List<BuddyMessage>> = stored

    override suspend fun send(text: String) {
        val base = Instant.parse("2026-11-03T12:00:00Z")
        val me = BuddyMessage(stored.value.size + 1L, base, Sender.Me, text, null, null)
        val reply = BuddyMessage(stored.value.size + 2L, base.plusSeconds(1), Sender.Buddy, "답", null, null)
        // 저장소는 최신이 앞.
        stored.update { listOf(reply, me) + it }
        state.update {
            it.copy(
                details =
                    it.details + (reply.id to ReplyDetails(reply.id, 1, emptyList(), emptyList(), null, false)),
            )
        }
    }

    override suspend fun savePlan(messageId: Long) {
        saved += messageId
    }

    override suspend fun report(messageId: Long) = Unit

    override fun dismissCrisis() = state.update { it.copy(crisis = null) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun sendShowsOldestFirstAndAttachesDetailsToReplyOnly() = runTest(dispatcher) {
        val fake = FakeConversation()
        val vm =
            CoachViewModel(fake, AppClock(Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneId.of("Asia/Seoul"))))
        val job = launch { vm.uiState.collect {} }
        vm.onAction(CoachAction.Send("나 언제 제일 많이 봐?"))
        advanceUntilIdle()
        val items = vm.uiState.first { it.items.size == 2 }.items
        assertEquals(ChatItem.From.Me, items[0].from)
        assertNull(items[0].details)
        assertEquals(ChatItem.From.Buddy, items[1].from)
        assertEquals(items[1].id, items[1].details?.messageId)

        vm.onAction(CoachAction.SavePlan(items[1].id))
        advanceUntilIdle()
        assertEquals(listOf(items[1].id), fake.saved)
        job.cancel()
    }
}
