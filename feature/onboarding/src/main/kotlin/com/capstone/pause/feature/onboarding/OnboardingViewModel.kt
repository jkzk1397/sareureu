package com.capstone.pause.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capstone.pause.core.data.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** 온보딩 상태. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val profileRepository: ProfileRepository) : ViewModel() {

    /** 온보딩을 끝내고 [onDone]을 부른다. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            profileRepository.markOnboardingDone()
            onDone()
        }
    }
}
