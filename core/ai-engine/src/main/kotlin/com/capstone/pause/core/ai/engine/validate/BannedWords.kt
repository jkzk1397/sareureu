package com.capstone.pause.core.ai.engine.validate

/**
 * 금지 표현(05 문서 7절). 생성 문구·친구 잔소리 모두 적용한다.
 * 띄어쓰기를 지우고 비교한다("정신 차려" = "정신차려"). 목록 원본은 05 문서이며 바꿀 때 평가 세트도 고친다.
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

    private val normalized = ALL.map { it to normalize(it) }

    private fun normalize(text: String): String = text.replace(Regex("\\s+"), "").lowercase()

    /** 처음 찾은 금지 표현(없으면 null). */
    fun find(text: String): String? {
        val n = normalize(text)
        return normalized.firstOrNull { (_, key) -> n.contains(key) }?.first
    }

    /** 금지 표현이 있는지. */
    fun contains(text: String): Boolean = find(text) != null
}
