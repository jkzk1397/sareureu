package com.capstone.pause.core.common

/**
 * 앱 로그 인터페이스. 사용 기록·채팅 내용·프로필을 남기지 않는다.
 * 안드로이드 구현은 `core:data`의 `di/`에서 묶는다.
 */
interface AppLog {
    /** 디버그 로그. */
    fun d(tag: String, message: String)

    /** 경고 로그. */
    fun w(tag: String, message: String, throwable: Throwable? = null)

    /** 오류 로그. */
    fun e(tag: String, message: String, throwable: Throwable? = null)

    /** 아무것도 남기지 않는 구현(테스트·순수 JVM 기본값). */
    object None : AppLog {
        override fun d(tag: String, message: String) = Unit

        override fun w(tag: String, message: String, throwable: Throwable?) = Unit

        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
