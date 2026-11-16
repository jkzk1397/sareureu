package com.capstone.pause.feature.settings

import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRecord
import com.capstone.pause.core.data.profile.ConsentRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** 정밀 모드 스위치 → 고지 → 동의/거절 → 시스템 설정 흐름(ADR-0004 보완). */
@OptIn(ExperimentalCoroutinesApi::class)
class PreciseModeViewModelTest {
    private class FakeConsents : ConsentRepository {
        val records = mutableListOf<ConsentRecord>()
        override val consents = MutableStateFlow<Map<ConsentItem, ConsentRecord>>(emptyMap())

        override suspend fun record(item: ConsentItem, version: Int, granted: Boolean) {
            val r = ConsentRecord(item, version, granted, Instant.EPOCH)
            records += r
            consents.value = consents.value + (item to r)
        }
    }

    private class FakeStatus(var on: Boolean = false) : AccessibilityStatus {
        override fun isOn() = on
    }

    private val consents = FakeConsents()
    private val status = FakeStatus()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun switchOnShowsDisclosureWithoutRecordingConsent() = runTest {
        val vm = PreciseModeViewModel(consents, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(PreciseAction.Toggle(true))
        assertTrue(vm.uiState.value.showDisclosure)
        assertTrue(consents.records.isEmpty(), "스위치만으로는 동의가 아니다")
    }

    @Test
    fun agreeRecordsConsentAndOpensSystemSettings() = runTest {
        val vm = PreciseModeViewModel(consents, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(PreciseAction.Toggle(true))
        vm.onAction(PreciseAction.Answer(agree = true))
        val s = vm.uiState.value
        assertFalse(s.showDisclosure)
        assertTrue(s.consented)
        assertTrue(s.openSystemSettings)
        assertFalse(s.active, "시스템에서 켜기 전에는 앱 단위")
        assertEquals(listOf(true), consents.records.map { it.granted })
        assertEquals(PRECISE_CONSENT_VERSION, consents.records.single().version)
        vm.onAction(PreciseAction.SettingsOpened)
        status.on = true
        vm.onAction(PreciseAction.Refresh)
        assertTrue(vm.uiState.first().active)
    }

    @Test
    fun declineKeepsAppLevelAndSwitchOff() = runTest {
        val vm = PreciseModeViewModel(consents, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(PreciseAction.Toggle(true))
        vm.onAction(PreciseAction.Answer(agree = false))
        val s = vm.uiState.value
        assertFalse(s.consented)
        assertFalse(s.openSystemSettings)
        assertFalse(s.showDisclosure)
    }

    @Test
    fun switchOffWithdrawsConsent() = runTest {
        status.on = true
        val vm = PreciseModeViewModel(consents, status)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(PreciseAction.Answer(agree = true))
        assertTrue(vm.uiState.value.active)
        vm.onAction(PreciseAction.Toggle(false))
        assertFalse(vm.uiState.value.consented)
        assertEquals(listOf(true, false), consents.records.map { it.granted })
    }
}
