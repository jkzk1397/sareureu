package com.capstone.pause.core.ai.engine.validate

/**
 * 주간 리포트 문장 전용 규칙(프롬프트 v3, 2026-11-09 실제 Gemini 검증에서 남은 것).
 * - `{buddy}`(캐릭터 이름)를 폰처럼 쓰기: "{buddy} 내려놓기", "{buddy}를 충전기에"
 * - `{avgMin}`(하루 평균 본 시간)을 줄어든 양처럼 쓰기: "{avgMin}만큼 가벼워졌어"
 * - 녹는 이야기 지어내기(입력에 녹는 단계가 없다): "녹았어", "녹아버리겠어"
 * - 입력에 없는 생활 사건을 이유로 지어내기: "시험 끝나서 해방감"
 */
object ReportRules {
    // {buddy} 바로 뒤(조사 하나까지)에 폰에 하는 동작이 오면 폰 취급이다. "{buddy}와 함께", "{buddy}가 신호를 줄게"는 괜찮다.
    private val buddyAsPhone = Regex(
        "\\{buddy\\}\\s*(을|를)?\\s*(내려놓|놓아|놓고|놓기|찾|충전|재워|재우|덮|끄|꺼|켜|잡|쥐|뒤집|치우|멀리)",
    )

    // 하루 평균 '본' 시간 뒤에 줄었다·아꼈다·가벼워졌다가 오면 줄어든 양으로 쓴 것이다. 줄어든 것은 {reduction}으로만.
    private val avgAsSaved = Regex("\\{avgMin\\}[^.!?\\n]{0,8}(줄었|줄어|줄였|가벼워|덜 봤|아꼈|아낀|되찾|벌었|여유)")

    // 녹다·녹이다 활용형. "녹음", "녹색", "녹차"는 다른 낱말이라 넘어간다.
    private val melting = Regex("녹(았|아|는|을|여|이|일|고|기|겠|던|으)")

    /** 입력에 없으면 지어낸 이유로 보는 생활 사건 낱말. */
    val LIFE_EVENTS: List<String> = listOf(
        "시험", "중간고사", "기말", "방학", "휴가", "해방감", "이별", "헤어", "야근", "회식", "과제", "마감", "알바",
    )

    /** 문장 한 줄의 문제 목록. [inputText]는 모델이 받은 입력(관찰·프로필·메모)을 이어 붙인 글. */
    fun problems(text: String, field: String, inputText: String): List<String> {
        val out = mutableListOf<String>()
        buddyAsPhone.find(text)?.let { out += "$field: {buddy}를 폰처럼 씀 '${it.value}'" }
        avgAsSaved.find(text)?.let { out += "$field: 하루 평균을 줄어든 양처럼 씀 '${it.value}'" }
        melting.find(text)?.let { out += "$field: 녹는 이야기 '${it.value}'" }
        LIFE_EVENTS.filter { it in text && it !in inputText }.forEach { out += "$field: 입력에 없는 이유 '$it'" }
        return out
    }
}
