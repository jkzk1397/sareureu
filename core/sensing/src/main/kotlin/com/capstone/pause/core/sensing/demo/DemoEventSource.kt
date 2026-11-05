package com.capstone.pause.core.sensing.demo

import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.ForegroundSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 시연 대본 한 줄: 대본 시작에서 [offset] 뒤에 [make]로 만든 이벤트를 낸다. */
data class DemoStep(val offset: Duration, val make: (Instant) -> ForegroundEvent)

/**
 * 시연용 감지 소스(권한 없이 동작). 대본을 [speed]배로 재생한다(예: 60 = 1초에 1분).
 * 이벤트 시각은 '시연 시계'(대본 시작 시각 + offset)로 찍는다. 3단계에서 `DemoController`가 몬다.
 */
@Singleton
class DemoEventSource @Inject constructor() : ForegroundSource {
    private val flow = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = BUFFER)

    override val events: Flow<ForegroundEvent> = flow.asSharedFlow()

    /** 이벤트 하나를 바로 낸다. */
    fun send(event: ForegroundEvent) {
        flow.tryEmit(event)
    }

    /** 대본을 [startAt]부터 [speed]배로 재생한다. 호출한 코루틴이 취소되면 멈춘다. */
    suspend fun play(steps: List<DemoStep>, startAt: Instant, speed: Double) {
        require(speed > 0.0) { "speed must be positive" }
        var elapsed = Duration.ZERO
        for (step in steps.sortedBy { it.offset }) {
            val wait = step.offset.minus(elapsed)
            if (!wait.isNegative && !wait.isZero) delay((wait.toMillis() / speed).toLong())
            elapsed = step.offset
            flow.emit(step.make(startAt.plus(step.offset)))
        }
    }

    private companion object {
        const val BUFFER = 64
    }
}
