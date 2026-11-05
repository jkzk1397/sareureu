package com.capstone.pause.core.common

import javax.inject.Qualifier

/** 주입할 디스패처 종류. */
enum class PauseDispatchers { DEFAULT, IO }

/** 디스패처 한정자: `@Dispatcher(PauseDispatchers.IO) dispatcher: CoroutineDispatcher`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: PauseDispatchers)

/** 앱 수명 동안 사는 코루틴 범위 한정자. `GlobalScope` 대신 쓴다. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
