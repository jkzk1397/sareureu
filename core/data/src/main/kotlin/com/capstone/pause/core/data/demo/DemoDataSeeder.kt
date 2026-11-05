package com.capstone.pause.core.data.demo

/** 시연 패턴(04 문서 8절): 밤형(기본, 목업 05와 같은 수치) · 출퇴근형 · 알림 도미노형. */
enum class DemoPattern { NIGHT_OWL, COMMUTER, DOMINO }

/**
 * 시연 데이터 넣기·지우기. 구현은 `core:sensing`(DemoScenario)에 있고 Hilt로 묶는다.
 * 시연 데이터는 `source = DEMO`로만 저장해 실제 기록과 섞지 않는다.
 */
interface DemoDataSeeder {
    /** 기존 시연 데이터를 지우고 지금 시각 기준 2주치 가상 기록을 넣는다. */
    suspend fun seed(pattern: DemoPattern)

    /** 시연 데이터를 모두 지운다. */
    suspend fun clear()
}
