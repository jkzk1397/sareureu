package com.capstone.pause.core.ai.engine.validate

/** 검증 결과. [Valid]는 고친 값과 메모(바꾼 항목 등), [Invalid]는 실패 이유 목록. */
sealed interface Validation<out T> {
    /** 통과. */
    data class Valid<T>(val value: T, val notes: List<String> = emptyList()) : Validation<T>

    /** 실패. 이유는 재시도 프롬프트에 그대로 붙는다. */
    data class Invalid(val reasons: List<String>) : Validation<Nothing>
}

/** 생성 문구 공통 규칙(05 문서 4절 숫자 정책, 7절 금지 표현). */
object TextRules {
    /** 허용 자리표시자(05 문서 4절). */
    val PLACEHOLDERS: Set<String> = setOf(
        "nickname", "min", "app", "context", "plan_then", "reduction", "avgMin",
        "topContext", "topContextMin", "stopRate", "timeRange", "friend", "buddy",
    )

    // 닫는 중괄호도 이스케이프한다(안드로이드 ICU 정규식은 '}' 단독을 문법 오류로 본다).
    private val placeholder = Regex("\\{([A-Za-z_]+)\\}")
    private val digits = Regex("[0-9０-９]")

    // 한글 수사 + 시간 단위("열 분", "스무 분", "한 시간", "삼 분"). '한 번'처럼 횟수는 문구에 흔해서 막지 않는다.
    // 앞 글자가 한글이면(예: "구분", "시간 이분법") 수사가 아니므로 넘어간다.
    // '분' 뒤에 위·석·명 등이 오면 다른 낱말("분위기", "분석")이라 넘어간다. "열 분만"은 막는다.
    private const val UNIT = "(분(?![위석명량야리홍노류배])|시간)"
    private val nativeNumeral = Regex(
        "(?<![가-힣])(한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|스무|서른|마흔|쉰|예순)\\s*$UNIT",
    )
    private val sinoNumeral = Regex("(?<![가-힣])(일|이|삼|사|오|육|칠|팔|구|십|백)\\s+$UNIT")
    private val koreanTens = Regex("(이십|삼십|사십|오십|육십|스물)\\s*$UNIT")

    /** 자리표시자를 뺀 본문. */
    fun withoutPlaceholders(text: String): String = placeholder.replace(text, " ")

    /** 쓰인 자리표시자 이름. */
    fun placeholders(text: String): List<String> = placeholder.findAll(text).map { it.groupValues[1] }.toList()

    /** 생성 문구 한 줄의 문제 목록(빈 목록이면 통과). [allowed]는 이 기능에서 허용하는 자리표시자. */
    fun problems(
        text: String,
        maxLength: Int,
        allowed: Set<String> = PLACEHOLDERS,
        maxEmoji: Int = 1,
        field: String = "text",
    ): List<String> {
        val out = mutableListOf<String>()
        if (text.isBlank()) out += "$field: 비어 있음"
        if (text.length > maxLength) out += "$field: ${maxLength}자 넘음(${text.length}자)"
        placeholders(text).filter { it !in allowed }.forEach { out += "$field: 허용되지 않은 자리표시자 {$it}" }
        val body = withoutPlaceholders(text)
        if (digits.containsMatchIn(body)) out += "$field: 자리표시자 밖 숫자"
        if (listOf(nativeNumeral, sinoNumeral, koreanTens).any { it.containsMatchIn(body) }) {
            out += "$field: 한글 수사로 쓴 시간"
        }
        BannedWords.find(text)?.let { out += "$field: 금지 표현 '$it'" }
        if (emojiCount(text) > maxEmoji) out += "$field: 이모지 ${maxEmoji}개 초과"
        return out
    }

    /** 이모지 개수(보조 평면 기호 + 흔한 기호 블록). */
    fun emojiCount(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp >= EMOJI_PLANE_START || cp in MISC_SYMBOLS) count++
            i += Character.charCount(cp)
        }
        return count
    }

    /** 문장 수(. ! ? 와 줄바꿈 기준). */
    fun sentenceCount(text: String): Int = text.split(Regex("[.!?。\\n]+")).count { it.isNotBlank() }

    private const val EMOJI_PLANE_START = 0x1F000
    private val MISC_SYMBOLS = 0x2600..0x27BF
}
