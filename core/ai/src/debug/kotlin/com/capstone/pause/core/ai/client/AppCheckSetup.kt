package com.capstone.pause.core.ai.client

import android.content.Context
import com.capstone.pause.core.ai.BuildConfig
import com.capstone.pause.core.common.AppLog
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 디버그 빌드: App Check 디버그 공급자.
 * `local.properties`에 `appCheckDebugSecret`(콘솔에 등록한 UUID)이 있으면 공급자를 설치하기 **전에** 그 값을
 * 디버그 SDK의 저장소에 써 둬서, 모든 폰이 같은 토큰을 쓴다(폰마다 콘솔에 등록할 필요 없음). 없으면 예전처럼 폰마다 만든다.
 * 비밀은 커밋하지 않고, 이 값이 들어간 APK는 공개 아티팩트로 만들지 않는다.
 */
internal object AppCheckSetup {
    /** 한 번 설치. 실패해도 앱은 돈다(그 호출만 실패 → 모의로 대체). */
    fun install(context: Context, log: AppLog): Boolean = runCatching {
        DebugSecretStore.secretOrNull(BuildConfig.APP_CHECK_DEBUG_SECRET)?.let { secret ->
            val name = DebugSecretStore.prefsName(FirebaseApp.getInstance().persistenceKey)
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .putString(DebugSecretStore.DEBUG_SECRET_KEY, secret)
                .commit()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        true
    }.getOrElse {
        log.w("AppCheckSetup", "app check debug install failed: ${it.javaClass.simpleName}")
        false
    }
}
