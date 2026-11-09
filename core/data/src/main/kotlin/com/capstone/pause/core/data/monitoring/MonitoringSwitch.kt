package com.capstone.pause.core.data.monitoring

/**
 * 감지 켜기 창구. 오늘 탭처럼 `core:intervention`에 의존할 수 없는 화면이 쓴다.
 * 포그라운드 서비스는 사용자가 화면에서 누를 때만 시작한다(앱이 화면에 있을 때만 부른다).
 * 구현은 `core:intervention`의 `MonitoringController`이고 그 모듈 `di/`에서 묶는다.
 */
interface MonitoringSwitch {
    /** 사용 기록 접근 권한이 있는지. 없으면 켤 수 없다. */
    fun hasUsageAccess(): Boolean

    /** 감지를 켠다. 권한이 없으면 false(화면이 권한 설정으로 안내한다). */
    suspend fun turnOn(): Boolean
}
