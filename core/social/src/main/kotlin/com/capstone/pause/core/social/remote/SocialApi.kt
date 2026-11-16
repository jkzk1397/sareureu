package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 서버 응답. [code]가 0이면 연결 실패. */
data class ApiResponse(val code: Int, val body: JsonElement?) {
    /** 2xx. */
    val ok: Boolean get() = code in OK_RANGE

    private companion object {
        val OK_RANGE = 200..299
    }
}

/** 친구 서버 호출 한 겹(server/README.md "앱 연결 계약"). 테스트에서는 가짜로 바꾼다. */
interface SocialTransport {
    /** [path]에 [method]로 [body]를 보낸다. 토큰은 구현이 붙인다. */
    suspend fun call(method: String, path: String, body: JsonObject? = null): ApiResponse
}

/**
 * HttpURLConnection 구현(새 의존성 없음). Firebase 익명 로그인 토큰을 `Authorization`에 붙인다.
 * 요청·응답 내용은 로그에 남기지 않는다.
 */
@Singleton
internal class HttpSocialTransport @Inject constructor(
    private val server: SocialServer,
    private val auth: SocialAuth,
    @Dispatcher(PauseDispatchers.IO) private val io: CoroutineDispatcher,
) : SocialTransport {
    override suspend fun call(method: String, path: String, body: JsonObject?): ApiResponse {
        if (!server.enabled) return ApiResponse(0, null)
        val token = auth.idToken() ?: return ApiResponse(0, null)
        return withContext(io) {
            runCatching {
                val connection = URL(server.url(path)).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                    val code = connection.responseCode
                    val stream = if (code < ERROR_FROM) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    ApiResponse(
                        code,
                        text.takeIf {
                            it.isNotBlank()
                        }?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() },
                    )
                } finally {
                    connection.disconnect()
                }
            }.getOrElse { ApiResponse(0, null) }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val ERROR_FROM = 400
        val json = Json { ignoreUnknownKeys = true }
    }
}
