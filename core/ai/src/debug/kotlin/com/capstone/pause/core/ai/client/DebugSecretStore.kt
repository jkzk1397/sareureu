package com.capstone.pause.core.ai.client

/**
 * App Check 디버그 SDK가 디버그 비밀을 읽는 SharedPreferences 이름과 키.
 * SDK 내부 방식이라 공개 API가 없다. firebase-appcheck-debug 20.0.0의 `internal.StorageHelper`를
 * `javap -c -constants`로 열어 확인한 값이다(PREFS_TEMPLATE, DEBUG_SECRET_KEY, `%s` = `FirebaseApp.getPersistenceKey()`,
 * 모드 0 = MODE_PRIVATE). Firebase BoM을 올리면 다시 확인한다.
 */
internal object DebugSecretStore {
    /** `String.format` 틀. */
    const val PREFS_TEMPLATE = "com.google.firebase.appcheck.debug.store.%s"

    /** 비밀 키. */
    const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    /** 그 FirebaseApp의 SharedPreferences 이름. */
    fun prefsName(persistenceKey: String): String = PREFS_TEMPLATE.format(persistenceKey)

    /** 넣을 비밀. 비었으면 null(넣지 않음 → SDK가 폰마다 새로 만든다). */
    fun secretOrNull(configured: String): String? = configured.trim().takeIf { it.isNotEmpty() }
}
