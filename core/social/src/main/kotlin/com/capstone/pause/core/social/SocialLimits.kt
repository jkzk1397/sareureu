package com.capstone.pause.core.social

import java.time.Duration

/**
 * 친구 기능의 숫자를 앱 쪽에서 한곳에 모은다(ADR-0014에 이유). 서버 쪽 짝은 `server/src/limits.mjs`.
 * 서버가 최종 판단을 하고, 앱은 같은 숫자로 미리 걸러 불필요한 요청을 줄인다.
 */
object SocialLimits {
    /** 앱이 `X-App-Version`으로 보내는 친구 서버 계약 번호. 2 = 요청 뒤 수락, 잔소리 확인 뒤 삭제. */
    const val API_VERSION = 2

    /** 초대 코드 길이. */
    const val CODE_LENGTH = 8

    /** 초대 코드 글자표: 헷갈리는 글자(0·O·1·I·L)를 뺀 31글자. */
    const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** 닉네임 최대 글자. */
    const val MAX_NICKNAME = 12

    /** 잔소리 최대 글자. */
    const val MAX_POKE_LENGTH = 40

    /** 자동 재시도 최대 횟수(연결 실패·서버 오류만. 429와 그 밖의 4xx는 다시 하지 않는다). */
    const val MAX_RETRIES = 3

    /** 자동 재시도 첫 간격. 시도마다 두 배가 되고 0.5~1.5배로 무작위로 흩어진다. */
    val RETRY_BASE: Duration = Duration.ofMillis(500)

    /** 내 QR 화면을 띄워 둔 동안 받은 연결 요청을 확인하는 간격. */
    val QR_POLL: Duration = Duration.ofSeconds(5)

    /** 내 QR 화면에서 받은 요청을 자동으로 확인하는 최대 시간. 지나면 멈춘다. */
    val QR_POLL_MAX: Duration = Duration.ofMinutes(2)

    /** 놓친 잔소리 받기 간격(앱이 화면에 올 때). */
    val POKE_FETCH_EVERY: Duration = Duration.ofMinutes(15)

    /** 사용자가 직접 새로고침할 때 허용하는 잔소리 받기 간격. */
    val POKE_FETCH_MANUAL_EVERY: Duration = Duration.ofSeconds(30)

    /** 저장해 둔 친구 목록을 이 시간 넘게 못 새로 받았으면 시청 중 표시와 잔소리 버튼을 끈다. */
    val STALE_AFTER: Duration = Duration.ofMinutes(1)
}

/** 사용자가 친 초대 코드를 다듬고 검사한다(순수). 서버에 보내기 전에 걸러 틀린 코드 한도를 아낀다. */
object InviteCodes {
    /** 빈칸·줄표 등 글자·숫자가 아닌 것을 지우고 대문자로. 붙여 넣은 값도 이렇게 다듬는다. */
    fun clean(input: String): String = input.filter { it.isLetterOrDigit() }.uppercase()

    /** 다듬은 뒤 검사. */
    fun check(input: String): CodeCheck {
        val code = clean(input)
        val bad = code.filter { it !in SocialLimits.CODE_ALPHABET }.toList().distinct()
        return when {
            code.isEmpty() -> CodeCheck.Empty
            bad.isNotEmpty() -> CodeCheck.BadCharacters(bad)
            code.length != SocialLimits.CODE_LENGTH -> CodeCheck.WrongLength(code.length)
            else -> CodeCheck.Ok(code)
        }
    }
}

/** 초대 코드 입력 검사 결과. */
sealed interface CodeCheck {
    /** 보낼 수 있는 코드(다듬은 값). */
    data class Ok(val code: String) : CodeCheck

    /** 비었음. */
    data object Empty : CodeCheck

    /** 글자표에 없는 글자(0·O·1·I·L 등)가 있음. */
    data class BadCharacters(val characters: List<Char>) : CodeCheck

    /** 길이가 다름. */
    data class WrongLength(val length: Int) : CodeCheck
}
