package com.capstone.pause.core.social.remote

/**
 * 초대 QR 주소(순수). QR에는 친구 서버의 `https://…/invite/{코드}`를 담는다: 서버 페이지가 앱이 있으면
 * `sareureu://invite/{코드}`로 앱을 열고, 없으면 설치 파일로 보낸다(server/README.md "초대 QR").
 */
object InviteLinks {
    /** 앱이 받는 주소의 scheme(app 모듈 AndroidManifest의 intent-filter와 같다). */
    const val SCHEME = "sareureu"

    /** 앱이 받는 주소의 host. */
    const val HOST = "invite"

    /** QR에 담을 주소. */
    fun link(server: SocialServer, code: String): String = server.url("/invite/$code")

    /**
     * 받은 주소에서 코드 꺼내기: `sareureu://invite/{코드}` 또는 `https://…/invite/{코드}`.
     * 코드는 [FriendPayloads.normalizeCode]로 다듬는다. 아니면 null.
     */
    fun codeFrom(uri: String?): String? {
        val text = uri?.trim() ?: return null
        val path = when {
            text.startsWith("$SCHEME://$HOST/", ignoreCase = true) -> text.substring("$SCHEME://$HOST/".length)

            text.startsWith("https://", ignoreCase = true) -> text.substringAfter("://").substringAfter('/', "")
                .takeIf { it.startsWith("$HOST/") }?.removePrefix("$HOST/")

            else -> null
        } ?: return null
        val raw = path.substringBefore('?').substringBefore('#').trimEnd('/')
        if (raw.contains('/')) return null
        return FriendPayloads.normalizeCode(raw)
    }
}
