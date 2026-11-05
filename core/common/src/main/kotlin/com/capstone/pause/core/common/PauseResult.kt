package com.capstone.pause.core.common

/** 실패할 수 있는 작업의 결과. */
sealed interface PauseResult<out T> {
    /** 성공. */
    data class Success<T>(val value: T) : PauseResult<T>

    /** 실패. [reason]은 로그·감사용 짧은 설명(개인정보 금지). */
    data class Failure(val reason: String, val cause: Throwable? = null) : PauseResult<Nothing>
}

/** 성공 값 또는 null. */
fun <T> PauseResult<T>.getOrNull(): T? = (this as? PauseResult.Success)?.value
