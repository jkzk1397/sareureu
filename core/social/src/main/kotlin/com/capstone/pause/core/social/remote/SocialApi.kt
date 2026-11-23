package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.social.SocialError
import com.capstone.pause.core.social.SocialLimits
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 서버 응답. [code]가 0이면 연결 실패. [retryAfterSeconds]는 `Retry-After`(한도에 걸렸을 때). */
data class ApiResponse(val code: Int, val body: JsonElement?, val retryAfterSeconds: Long? = null) {
    /** 2xx. */
    val ok: Boolean get() = code in OK_RANGE

    /** 서버가 준 오류 이름(`{"error":"…"}`). 없으면 null. */
    val errorName: String?
        get() = runCatching { (body as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** 실패를 종류로 바꾼다(화면이 종류별로 다른 말을 한다). 성공이면 null. */
    val error: SocialError?
        get() = when {
            ok -> null
            code == 0 -> SocialError.Offline
            code == UNAUTHORIZED -> SocialError.SessionExpired
            code == UPGRADE_REQUIRED -> SocialError.UpgradeRequired
            code == TOO_MANY -> SocialError.TooMany(retryAfterSeconds)
            errorName == "clock_skew" -> SocialError.ClockSkew
            code >= SERVER_ERROR_FROM -> SocialError.Maintenance
            else -> SocialError.Unknown
        }

    /** 자동으로 다시 해 볼 만한 실패인지: 연결 실패와 서버 오류(5xx)만. 429와 그 밖의 4xx는 다시 하지 않는다. */
    val retryable: Boolean get() = code == 0 || code >= SERVER_ERROR_FROM

    /** 응답 코드. */
    companion object {
        /** 로그인 만료. */
        const val UNAUTHORIZED = 401

        /** 앱이 오래됨. */
        const val UPGRADE_REQUIRED = 426

        /** 한도 초과. */
        const val TOO_MANY = 429
        private const val SERVER_ERROR_FROM = 500
        private val OK_RANGE = 200..299
    }
}

/** 친구 서버 호출 한 겹(server/README.md "앱 연결 계약"). 테스트에서는 가짜로 바꾼다. */
interface SocialTransport {
    /**
     * [path]에 [method]로 [body]를 보낸다. 토큰과 계약 번호(`X-App-Version`)는 구현이 붙인다.
     * - [idempotencyKey]: 같은 요청이 두 번 가도 서버가 한 번만 처리하게 하는 키(잔소리 보내기).
     * - [retries]: 연결 실패·서버 오류일 때 자동으로 다시 해 볼 횟수(0이면 안 함). 몇 초마다 부르는 신호·새로고침은 0으로 둔다.
     */
    suspend fun call(
        method: String,
        path: String,
        body: JsonObject? = null,
        idempotencyKey: String? = null,
        retries: Int = 0,
    ): ApiResponse
}

/** 자동 재시도 간격(순수): 시도마다 두 배, 0.5~1.5배로 무작위로 흩어져 여러 폰이 한꺼번에 몰리지 않는다. */
object RetryBackoff {
    /** [attempt](0부터)번째 재시도 전에 쉴 시간(ms). [jitter]는 0~1 난수. */
    fun delayMs(attempt: Int, jitter: Double): Long {
        val base = SocialLimits.RETRY_BASE.toMillis() shl attempt.coerceIn(0, MAX_SHIFT)
        return (base * (MIN_FACTOR + jitter.coerceIn(0.0, 1.0))).toLong()
    }

    private const val MAX_SHIFT = 6
    private const val MIN_FACTOR = 0.5
}

/**
 * HttpURLConnection 구현(새 의존성 없음). Firebase 익명 로그인 토큰을 `Authorization`에 붙인다.
 * 401이면 토큰을 강제로 새로 받아 한 번만 다시 한다. 요청·응답 내용은 로그에 남기지 않는다.
 */
@Singleton
internal class HttpSocialTransport @Inject constructor(
    private val server: SocialServer,
    private val auth: SocialAuth,
    @Dispatcher(PauseDispatchers.IO) private val io: CoroutineDispatcher,
) : SocialTransport {
    override suspend fun call(
        method: String,
        path: String,
        body: JsonObject?,
        idempotencyKey: String?,
        retries: Int,
    ): ApiResponse {
        if (!server.enabled) return ApiResponse(0, null)
        var response = once(method, path, body, idempotencyKey, forceRefresh = false)
        // 로그인 만료: 토큰을 새로 받아 한 번만 다시.
        if (response.code == ApiResponse.UNAUTHORIZED) {
            response = once(method, path, body, idempotencyKey, forceRefresh = true)
        }
        var attempt = 0
        while (response.retryable && attempt < retries.coerceAtMost(SocialLimits.MAX_RETRIES)) {
            delay(RetryBackoff.delayMs(attempt, Random.nextDouble()))
            response = once(method, path, body, idempotencyKey, forceRefresh = false)
            attempt++
        }
        return response
    }

    private suspend fun once(
        method: String,
        path: String,
        body: JsonObject?,
        idempotencyKey: String?,
        forceRefresh: Boolean,
    ): ApiResponse {
        val token = auth.idToken(forceRefresh) ?: return ApiResponse(0, null)
        return withContext(io) {
            runCatching {
                val connection = URL(server.url(path)).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = method
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("X-App-Version", SocialLimits.API_VERSION.toString())
                    idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
                    if (body != null) {
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                    val code = connection.responseCode
                    val stream = if (code < ERROR_FROM) connection.inputStream else connection.errorStream
                    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    ApiResponse(
                        code = code,
                        body = text.takeIf {
                            it.isNotBlank()
                        }?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() },
                        retryAfterSeconds = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull(),
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
