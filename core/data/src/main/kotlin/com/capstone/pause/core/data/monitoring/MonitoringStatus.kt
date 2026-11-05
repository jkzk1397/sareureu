package com.capstone.pause.core.data.monitoring

import kotlinx.coroutines.flow.Flow

/** 감지 상태. [STOPPED_BY_USER]는 켜 둔 감지를 작업 관리자·알림 스와이프로 멈춘 경우(ONE_SHOT 8절 3-⑤). */
enum class MonitoringState { OFF, ON, STOPPED_BY_USER }

/**
 * 감지 상태 읽기 전용 창구. 오늘 탭처럼 `core:intervention`에 의존할 수 없는 화면이 쓴다.
 * 구현은 `core:intervention`의 `MonitoringController`이고 그 모듈 `di/`에서 묶는다.
 */
interface MonitoringStatus {
    /** 지금 상태. */
    val state: Flow<MonitoringState>
}
