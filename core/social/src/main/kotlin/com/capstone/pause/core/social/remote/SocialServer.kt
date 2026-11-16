package com.capstone.pause.core.social.remote

import com.capstone.pause.core.social.BuildConfig

/**
 * 친구 서버 설정(server/README.md "앱 연결 계약"). 주소는 `local.properties`의 `socialBaseUrl`에서 온다.
 * 비어 있으면 [enabled]가 false이고, 앱은 지금처럼 가상 친구로 동작한다.
 */
data class SocialServer(val baseUrl: String) {
    /** 원격 친구 기능을 쓸 수 있는지. */
    val enabled: Boolean get() = baseUrl.startsWith("https://")

    /** 경로를 붙인 주소(`/api/v1/...`). */
    fun url(path: String): String = baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    /** 빌드 설정에서 읽은 값. */
    companion object {
        /** `local.properties`의 `socialBaseUrl`(없으면 빈 값). */
        val FROM_BUILD = SocialServer(BuildConfig.SOCIAL_BASE_URL)
    }
}
