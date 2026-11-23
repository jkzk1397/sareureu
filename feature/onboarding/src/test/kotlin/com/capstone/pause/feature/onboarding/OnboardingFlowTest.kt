package com.capstone.pause.feature.onboarding

import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.Occupation
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    @Test
    fun fiveStepsInOrder() {
        val seen = generateSequence(OnboardingStep.START) { s -> OnboardingFlow.next(s).takeIf { it != s } }.toList()
        assertEquals(
            listOf(
                OnboardingStep.START,
                OnboardingStep.ME,
                OnboardingStep.GOALS,
                OnboardingStep.PERMISSIONS,
                OnboardingStep.AI,
            ),
            seen,
        )
        assertEquals(5, OnboardingStep.SECTIONS)
        assertEquals(seen.map { it.section }, (1..5).toList())
    }

    @Test
    fun backGoesOneStepAndStopsAtStart() {
        assertEquals(OnboardingStep.GOALS, OnboardingFlow.previous(OnboardingStep.PERMISSIONS))
        assertEquals(OnboardingStep.START, OnboardingFlow.previous(OnboardingStep.START))
        assertEquals(OnboardingStep.AI, OnboardingFlow.next(OnboardingStep.AI))
    }

    @Test
    fun underageIsOutsideTheFlow() {
        // 만 18세 미만 안내는 다음으로 갈 수 없고, 뒤로 가면 시작 화면.
        assertEquals(OnboardingStep.UNDERAGE, OnboardingFlow.next(OnboardingStep.UNDERAGE))
        assertEquals(OnboardingStep.START, OnboardingFlow.previous(OnboardingStep.UNDERAGE))
    }
}

class DefaultRhythmsTest {
    @Test
    fun occupationDefaultsGoIntoProfile() {
        val office = OnboardingUiState(ageBand = AgeBand.AGE_25_29, occupation = Occupation.OFFICE_WORKER)
            .toProfile("친구")
        assertEquals(LocalTime.of(7, 0), office.rhythm.weekdayWake)
        assertEquals(LocalTime.of(0, 0), office.rhythm.weekdaySleep)
        assertEquals(LocalTime.of(1, 0), office.rhythm.weekendSleep, "주말 취침은 평일 +1시간")
        assertEquals(2, office.rhythm.commute.size)
        assertEquals(LocalTime.of(9, 0), office.rhythm.classOrWork.single().start)

        val freelance = OnboardingUiState(occupation = Occupation.SHIFT_OR_FREELANCE).toProfile("친구")
        assertTrue(freelance.rhythm.commute.isEmpty())
        assertTrue(freelance.rhythm.classOrWork.isEmpty())
        assertEquals(2, freelance.rhythm.meals.size)
    }

    @Test
    fun changedSleepOverridesDefaultAndWeekendFollows() {
        val state = OnboardingUiState(occupation = Occupation.UNIVERSITY, sleep = LocalTime.of(23, 30))
        val rhythm = state.toProfile("친구").rhythm
        assertEquals(LocalTime.of(23, 30), rhythm.weekdaySleep)
        assertEquals(LocalTime.of(0, 30), rhythm.weekendSleep)
        assertEquals(LocalTime.of(10, 0), rhythm.weekendWake, "주말 기상은 평일 +2시간")
    }

    @Test
    fun shownSleepFollowsOccupationUntilUserChangesIt() {
        val s = OnboardingUiState(occupation = Occupation.OFFICE_WORKER)
        assertNull(s.sleep)
        assertEquals(LocalTime.of(0, 0), s.shownSleep)
        assertEquals(LocalTime.of(1, 30), s.copy(occupation = Occupation.SHIFT_OR_FREELANCE).shownSleep)
    }

    @Test
    fun interestsAndToneUseDefaults() {
        val p = OnboardingUiState(nickname = "  ").toProfile("친구")
        assertEquals("친구", p.nickname)
        assertTrue(p.interests.isEmpty())
    }
}
