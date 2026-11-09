package com.capstone.pause.feature.settings

import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.designsystem.component.rhythm.RhythmDraft
import com.capstone.pause.core.designsystem.component.rhythm.RhythmField
import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.Occupation
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Tone
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RhythmSettingsViewModelTest {
    private val stored = MutableStateFlow<Profile?>(
        Profile(
            nickname = "나",
            ageBand = AgeBand.AGE_18_24,
            occupation = Occupation.UNIVERSITY,
            rhythm = RhythmDraft().toRhythm(),
            goals = emptySet(),
            interests = emptySet(),
            tone = Tone.PLAYFUL,
        ),
    )
    private val repo = object : ProfileRepository {
        override val profile: Flow<Profile?> = stored
        override val hasProfile: Flow<Boolean> = stored.map { it != null }
        override suspend fun save(profile: Profile) {
            stored.value = profile
        }
        override suspend fun clear() {
            stored.value = null
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun shiftingSleepSavesProfileRightAway() = runTest {
        val vm = RhythmSettingsViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(RhythmSettingsAction.Shift(RhythmField.SLEEP, end = false, deltaMinutes = -60))
        assertEquals(LocalTime.of(0, 0), stored.value?.rhythm?.weekdaySleep)
        assertEquals(LocalTime.of(0, 0), vm.uiState.value.draft?.weekdaySleep)
    }

    @Test
    fun weekendViewShiftsWeekendTimes() = runTest {
        val vm = RhythmSettingsViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.onAction(RhythmSettingsAction.ShowWeekend(true))
        vm.onAction(RhythmSettingsAction.Shift(RhythmField.WAKE, end = false, deltaMinutes = 30))
        assertEquals(LocalTime.of(10, 30), stored.value?.rhythm?.weekendWake)
        assertEquals(LocalTime.of(8, 0), stored.value?.rhythm?.weekdayWake)
    }

    @Test
    fun turningLunchOffRemovesOneMeal() = runTest {
        val vm = RhythmSettingsViewModel(repo)
        vm.onAction(RhythmSettingsAction.Toggle(RhythmField.LUNCH, enabled = false))
        assertEquals(1, stored.value?.rhythm?.meals?.size)
    }

    @Test
    fun noProfileMeansNothingToEdit() = runTest {
        stored.value = null
        val vm = RhythmSettingsViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertNull(vm.uiState.value.draft)
        vm.onAction(RhythmSettingsAction.Shift(RhythmField.SLEEP, end = false, deltaMinutes = 30))
        assertNull(stored.value)
    }
}
