package com.capstone.pause.core.ai.engine.safety

/** 도움 전화 하나(문구는 화면이 strings.xml로 그린다. 여기서는 번호와 키만). */
data class HelpLine(val key: String, val number: String)

/** 안전 검사 결과. */
sealed interface SafetyResult {
    /** 위기 표현 발견: AI를 부르지 말고 도움 카드를 띄운다. */
    data class Crisis(val matched: String, val helpLines: List<HelpLine>) : SafetyResult

    /** 보내도 됨. [maskedText]는 전화번호·이메일·이름 패턴을 가린 글. */
    data class Safe(val maskedText: String, val maskedCount: Int) : SafetyResult
}

/**
 * 사용자 입력(채팅·메모·관심사) 안전 검사(05 문서 7절)와 개인정보 가림(04 문서 5절).
 * 위기 표현이 있으면 LLM을 부르지 않는다.
 */
object SafetyFilter {
    /** 자살예방 상담전화(24시간). */
    val SUICIDE_PREVENTION = HelpLine("suicide_prevention", "109")

    /** 정신건강 위기상담. */
    val MENTAL_HEALTH_CRISIS = HelpLine("mental_health_crisis", "1577-0199")

    /** 위기 표현(띄어쓰기 무시). 목록은 평가 세트(`eval/safety`)로 확인한다. */
    val CRISIS_PATTERNS: List<String> = listOf(
        "죽고싶", "죽고 싶", "자해", "살기싫", "살기 싫", "사라지고싶", "사라지고 싶", "끝내고싶", "끝내고 싶",
        "자살", "죽어버리", "죽어 버리", "목숨을끊", "목숨을 끊", "뛰어내리", "살고싶지", "살고 싶지", "없어지고싶", "없어지고 싶",
    )

    private val crisisKeys = CRISIS_PATTERNS.map { it.replace(" ", "") }.distinct()
    private val phone = Regex("(01[016789]|0[2-6][0-9]?)[-. ]?[0-9]{3,4}[-. ]?[0-9]{4}")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val namedAs = Regex("(제 이름은|내 이름은|이름은|이름이)\\s*([가-힣A-Za-z]{2,10})")

    /** 위기 표현 검사 후 가린 글을 돌려준다. */
    fun check(text: String): SafetyResult {
        val compact = text.replace(Regex("\\s+"), "")
        crisisKeys.firstOrNull { compact.contains(it) }?.let {
            return SafetyResult.Crisis(it, listOf(SUICIDE_PREVENTION, MENTAL_HEALTH_CRISIS))
        }
        var count = 0
        var masked = phone.replace(text) {
            count++
            PHONE_MASK
        }
        masked = email.replace(masked) {
            count++
            EMAIL_MASK
        }
        masked = namedAs.replace(masked) {
            count++
            "${it.groupValues[1]} $NAME_MASK"
        }
        return SafetyResult.Safe(masked, count)
    }

    /** 위기 표현이 있는지만. */
    fun isCrisis(text: String): Boolean = check(text) is SafetyResult.Crisis

    private const val PHONE_MASK = "[전화번호]"
    private const val EMAIL_MASK = "[이메일]"
    private const val NAME_MASK = "[이름]"
}
