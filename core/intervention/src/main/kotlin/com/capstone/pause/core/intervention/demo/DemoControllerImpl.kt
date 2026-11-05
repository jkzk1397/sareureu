package com.capstone.pause.core.intervention.demo

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.data.demo.DemoController
import com.capstone.pause.core.intervention.nudge.NudgeCoordinator
import com.capstone.pause.core.intervention.nudge.NudgeMode
import com.capstone.pause.core.intervention.service.MonitoringController
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.demo.DemoEventSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 시연 숏폼(PRD 11절 3번). 권한 없이 `DemoEventSource`로 유튜브 세션을 열고 개입 타이머만 60배속으로 돌린다
 * (WorkManager에는 배속을 쓰지 않는다). 오버레이 권한이 없으면 화면 구현이 `NudgePreviewActivity`로 대신 보여 준다.
 * 시연 중에는 실제 감지가 잠깐 멈추고, 끝나면 켜 둔 경우에만 다시 돈다.
 */
@Singleton
class DemoControllerImpl @Inject constructor(
    private val coordinator: NudgeCoordinator,
    private val demoSource: DemoEventSource,
    private val monitoringController: MonitoringController,
    private val clock: AppClock,
    @ApplicationScope private val appScope: CoroutineScope,
) : DemoController {

    private val running = MutableStateFlow(false)

    override val isRunning: StateFlow<Boolean> = running.asStateFlow()

    override suspend fun startShortformDemo() {
        coordinator.onDemoFinished = { appScope.launch { stop() } }
        coordinator.start(appScope, NudgeMode.DEMO)
        running.value = true
        // 감지 파이프라인이 구독을 시작할 틈을 준 뒤 '잠금 해제 → 유튜브'를 보낸다.
        delay(START_GAP_MS)
        val now = clock.now()
        demoSource.send(ForegroundEvent.ScreenOn(now))
        demoSource.send(ForegroundEvent.Unlock(now))
        demoSource.send(ForegroundEvent.AppForeground(YOUTUBE, now))
    }

    override suspend fun stop() {
        if (!running.value) return
        val now = clock.now()
        demoSource.send(ForegroundEvent.AppForeground(NudgeCoordinator.DEMO_LAUNCHER, now))
        demoSource.send(ForegroundEvent.ScreenOff(now))
        // 세션이 저장될 틈(감지 틱)을 준 뒤 멈춘다.
        delay(SAVE_GAP_MS)
        coordinator.stop()
        running.value = false
        monitoringController.resumeIfEnabled()
    }

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val START_GAP_MS = 300L
        const val SAVE_GAP_MS = 6_000L
    }
}
