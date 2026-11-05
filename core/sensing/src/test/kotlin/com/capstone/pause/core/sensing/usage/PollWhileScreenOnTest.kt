package com.capstone.pause.core.sensing.usage

import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PollWhileScreenOnTest {
    @Test
    fun pollsWhileOnStopsWhenOffAndResumesWhenOn() = runTest {
        val screen = MutableStateFlow(true)
        var polls = 0
        val job = launch {
            pollWhileScreenOn({ true }, screen, { 5_000L }, 60_000L) { polls++ }
        }
        runCurrent()
        assertEquals(1, polls)
        advanceTimeBy(10_001)
        assertEquals(3, polls)

        screen.value = false
        advanceTimeBy(5_000) // 꺼진 뒤 마지막 한 번
        assertEquals(4, polls)
        advanceTimeBy(600_000) // 꺼져 있는 10분 동안 폴링 없음
        assertEquals(4, polls)

        screen.value = true
        runCurrent()
        assertEquals(5, polls)
        job.cancel()
    }

    @Test
    fun withoutPermissionNeverPolls() = runTest {
        var polls = 0
        val job = launch { pollWhileScreenOn({ false }, MutableStateFlow(true), { 5_000L }, 60_000L) { polls++ } }
        advanceTimeBy(300_000)
        assertEquals(0, polls)
        job.cancel()
    }
}
