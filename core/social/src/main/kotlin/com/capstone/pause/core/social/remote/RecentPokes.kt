package com.capstone.pause.core.social.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 친구마다 방금 받은 잔소리 한 줄(메모리). 그 친구를 '잔소리 내용'으로 신고할 때 문장을 함께 보내려고 잠깐 들고 있는다.
 * 저장하지 않으므로 앱이 꺼지면 사라진다.
 */
@Singleton
class RecentPokes @Inject constructor() {
    private val last = LinkedHashMap<String, String>()

    /** [friendId]가 보낸 글을 기억한다. */
    @Synchronized
    fun remember(friendId: String, text: String) {
        last.remove(friendId)
        last[friendId] = text
        while (last.size > MAX) last.remove(last.keys.first())
    }

    /** 그 친구가 마지막으로 보낸 글. 없으면 null. */
    @Synchronized
    fun lastFrom(friendId: String): String? = last[friendId]

    private companion object {
        const val MAX = 50
    }
}
