package com.capstone.pause.feature.onboarding

import kotlin.test.assertEquals
import org.junit.Test

// 리듬 편집기 테스트가 core:designsystem으로 옮겨 가면서 같은 파일에 있던 흐름 테스트를 여기로 뺐다.
class OnboardingFlowTest {
    @Test
    fun flowSkipsUnderageAndBackGoesToAge() {
        assertEquals(OnboardingStep.TERMS, OnboardingFlow.next(OnboardingStep.AGE))
        assertEquals(OnboardingStep.AGE, OnboardingFlow.previous(OnboardingStep.UNDERAGE))
        assertEquals(OnboardingStep.AGE, OnboardingFlow.previous(OnboardingStep.AGE))
        assertEquals(OnboardingStep.DONE, OnboardingFlow.next(OnboardingStep.DONE))
    }
}
