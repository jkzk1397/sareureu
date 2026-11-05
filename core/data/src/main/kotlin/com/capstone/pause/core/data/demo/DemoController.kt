package com.capstone.pause.core.data.demo

import kotlinx.coroutines.flow.StateFlow

/**
 * 시연용 숏폼 재생(오늘 탭의 "시연: 숏폼 보기 시작"). feature는 이 인터페이스만 부른다.
 * 구현은 `core:intervention`(DemoEventSource로 유튜브 세션을 시작하고 개입 타이머만 60배속)에 있고 Hilt로 묶는다.
 */
interface DemoController {
    /** 시연 숏폼이 재생 중인지. */
    val isRunning: StateFlow<Boolean>

    /** 시연 숏폼 시작. */
    suspend fun startShortformDemo()

    /** 시연 숏폼 멈추기. */
    suspend fun stop()
}
