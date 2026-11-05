package com.capstone.pause.core.data.privacy

import com.capstone.pause.core.common.AppLog

/** 지우기 단계 하나. [name]은 실패 보고·로그용(개인정보 없음). */
internal class EraseStep(val name: String, val action: suspend () -> Unit)

/**
 * '모든 기록 삭제' 단계를 차례로 돈다. 한 단계가 실패해도 나머지는 계속 지우고, 실패한 단계 이름을 돌려준다.
 * 단계 목록은 `di/PrivacyDataModule`이 만든다(Room 전체, DataStore 파일들, 캐시).
 */
internal class EraseOrchestrator(private val steps: List<EraseStep>, private val log: AppLog = AppLog.None) {
    /** 실행. 빈 목록이면 모두 성공. */
    suspend fun run(): List<String> = steps.mapNotNull { step ->
        runCatching { step.action() }
            .onFailure { log.w(TAG, "erase step failed: ${step.name}", it) }
            .exceptionOrNull()
            ?.let { step.name }
    }

    /** 단계 이름(테스트·로그용). */
    val stepNames: List<String> get() = steps.map { it.name }

    private companion object {
        const val TAG = "Erase"
    }
}
