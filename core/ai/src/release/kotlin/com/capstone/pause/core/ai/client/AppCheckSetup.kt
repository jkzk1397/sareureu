package com.capstone.pause.core.ai.client

import android.content.Context
import com.capstone.pause.core.common.AppLog
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** 릴리스 빌드: App Check Play Integrity 공급자. */
internal object AppCheckSetup {
    /** 한 번 설치. 실패해도 앱은 돈다(그 호출만 실패 → 모의로 대체). */
    fun install(context: Context, log: AppLog): Boolean = runCatching {
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
        true
    }.getOrElse {
        log.w("AppCheckSetup", "app check install failed: ${it.javaClass.simpleName}")
        false
    }
}
