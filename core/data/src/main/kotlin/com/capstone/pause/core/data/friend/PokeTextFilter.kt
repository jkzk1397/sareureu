package com.capstone.pause.core.data.friend

/**
 * 친구가 보낸 글(잔소리·닉네임)을 보여 주기 전에 한 번 더 거르는 창구(ADR-0014).
 * 서버도 검사하지만 서버 목록이 뒤처졌거나 다른 경로로 들어온 글을 받는 쪽에서 다시 막는다.
 * 구현은 금지 표현 목록(`BannedWords`)을 가진 `core:ai`가 묶는다. `core:social`은 이 창구로만 쓴다.
 */
interface PokeTextFilter {
    /** 보여 줘도 되는 글이면 true. */
    fun allowed(text: String): Boolean
}
