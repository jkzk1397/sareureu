package com.capstone.pause.core.ai.engine.validate

/**
 * 금지 표현(05 문서 7절). 생성 문구·친구 잔소리·닉네임에 모두 적용한다.
 * 빈칸과 특수문자를 지우고 비교한다("정신 차려" = "정신.차려"). 친구 서버(`server/src/text.mjs`)와 같은 방식이다.
 * 목록 원본은 05 문서 7절이며(시험이 같은지 확인한다) 바꿀 때 평가 세트도 고친다.
 */
object BannedWords {
    /** 진단·훈계·수치심(05 문서 7절 그대로). */
    val SHAMING: List<String> = listOf(
        "중독", "장애", "진단", "환자", "한심", "정신 차려", "의지가 약", "쓰레기", "루저",
    )

    /** 욕설. */
    val PROFANITY: List<String> = listOf(
        "씨발", "시발", "ㅅㅂ", "병신", "ㅂㅅ", "개새끼", "새끼", "좆", "존나", "졸라", "미친놈", "미친년", "닥쳐", "꺼져", "지랄",
    )

    /** 외모·연애·성적·가족 비하 소재. */
    val TOPICS: List<String> = listOf(
        "못생", "뚱뚱", "돼지", "살쪄", "살 빼", "애인", "연애", "섹스", "야한", "몸매", "니 엄마", "느금",
    )

    /** 전체 목록. */
    val ALL: List<String> = SHAMING + PROFANITY + TOPICS

    // 아래 normalized보다 먼저 만들어져야 한다(object 초기화 순서).
    private val notLetterOrDigit = Regex("[^\\p{L}\\p{N}]")

    private val normalized = ALL.map { it to normalize(it) }

    /** 비교용: 호환 정규화(전각·반각 등) 뒤 소문자, 글자와 숫자만 남긴다(사이에 낀 빈칸·특수문자 우회를 잡는다). */
    private fun normalize(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).lowercase().replace(notLetterOrDigit, "")

    /** 처음 찾은 금지 표현(없으면 null). */
    fun find(text: String): String? {
        val n = normalize(text)
        return normalized.firstOrNull { (_, key) -> n.contains(key) }?.first
    }

    /** 금지 표현이 있는지. */
    fun contains(text: String): Boolean = find(text) != null
}
