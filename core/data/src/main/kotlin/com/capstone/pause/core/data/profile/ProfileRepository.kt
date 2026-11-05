package com.capstone.pause.core.data.profile

import kotlinx.coroutines.flow.Flow

/** 프로필 저장소(DataStore). 프로필이 없으면 앱은 온보딩을 먼저 보여 준다. */
interface ProfileRepository {
    /** 온보딩을 끝내 프로필이 있는지. */
    val hasProfile: Flow<Boolean>

    /** 온보딩 완료 표시. TODO(BUILD_REPORT#1): 2단계 온보딩에서 실제 프로필 저장으로 바꾼다. */
    suspend fun markOnboardingDone()
}
