package com.capstone.pause.core.ai.coach

import com.capstone.pause.core.ai.engine.validate.BannedWords
import com.capstone.pause.core.data.friend.PokeTextFilter
import javax.inject.Inject
import javax.inject.Singleton

/** 친구가 보낸 글을 보여 주기 전에 금지 표현 목록(05 문서 7절)으로 한 번 더 거른다(ADR-0014). */
@Singleton
internal class BannedWordsPokeFilter @Inject constructor() : PokeTextFilter {
    override fun allowed(text: String): Boolean = !BannedWords.contains(text)
}
