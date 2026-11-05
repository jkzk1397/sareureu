package com.capstone.pause.core.sensing

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** 감지 소스가 내보내는 이벤트(ADR-0004). 화면 글자·영상 제목은 담지 않는다. */
sealed interface ForegroundEvent {
    /** 이벤트 시각. */
    val at: Instant

    /** [packageName] 앱이 맨 앞에 왔다(앱 단위). */
    data class AppForeground(val packageName: String, override val at: Instant) : ForegroundEvent

    /** 화면 켜짐. */
    data class ScreenOn(override val at: Instant) : ForegroundEvent

    /** 화면 꺼짐. */
    data class ScreenOff(override val at: Instant) : ForegroundEvent

    /** 잠금 해제(키가드 사라짐). */
    data class Unlock(override val at: Instant) : ForegroundEvent
}

/** 감지 소스 세 가지(사용 기록 / 접근성(P1) / 시연)가 같은 인터페이스를 쓴다(ADR-0004). */
interface ForegroundSource {
    /** 이벤트 흐름. 모으는 동안만 감지하고, 모으기를 멈추면 감지도 멈춘다. */
    val events: Flow<ForegroundEvent>
}
