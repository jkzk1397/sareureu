package com.capstone.pause.core.social

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class FriendCharacterIdsTest {
    @Test
    fun sameKeyAlwaysGivesTheSameCharacterOnEveryPhone() {
        // 고정된 값: 해시 방식이 바뀌면 두 폰(다른 앱 버전)에서 같은 친구가 다른 캐릭터로 보이게 되므로 바뀌면 안 된다.
        assertEquals(5, FriendCharacterIds.of(""))
        assertEquals(4, FriendCharacterIds.of("a"))
        assertEquals(
            FriendCharacterIds.of("0f3c2a9e-7b1d-4c5e-9a8f-1b2c3d4e5f60"),
            FriendCharacterIds.of("0f3c2a9e-7b1d-4c5e-9a8f-1b2c3d4e5f60"),
        )
        assertEquals(FriendCharacterIds.of("민지"), FriendCharacterIds.of("민지"))
    }

    @Test
    fun idsStayInRangeAndSpreadOverAllEightCharacters() {
        val ids = (0 until 400).map { FriendCharacterIds.of("friend-$it") }
        assertTrue(ids.all { it in 0 until FriendCharacterIds.COUNT })
        assertEquals(FriendCharacterIds.COUNT, ids.toSet().size)
        // 한 캐릭터로 몰리지 않는다(400명이면 캐릭터마다 50명 안팎).
        assertTrue(ids.groupingBy { it }.eachCount().values.all { it in 25..80 })
    }

    @Test
    fun serverFriendsUseThePublicIdAndDemoFriendsUseTheName() {
        val friend = Standing("pid-1", "민지", null, null, 0, isMe = false)
        assertEquals(FriendCharacterIds.of("pid-1"), FriendCharacterIds.of(friend, demo = false))
        assertEquals(FriendCharacterIds.of("민지"), FriendCharacterIds.of(friend, demo = true))
        // 닉네임을 바꿔도 서버 친구의 캐릭터는 그대로다.
        assertEquals(
            FriendCharacterIds.of(friend, demo = false),
            FriendCharacterIds.of(friend.copy(nickname = "새이름"), demo = false),
        )
    }
}
