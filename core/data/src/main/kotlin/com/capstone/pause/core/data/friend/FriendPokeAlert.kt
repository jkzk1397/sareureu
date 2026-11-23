package com.capstone.pause.core.data.friend

/**
 * 친구가 보낸 잔소리를 바로 알림으로 띄운다(ADR-0012). 시청 중 신호의 응답으로 받은 잔소리에만 쓴다.
 * 구현은 `core:intervention`의 알림 담당이 하고 그 모듈 `di/`에서 묶는다(core:social은 intervention에 의존하지 않는다).
 */
interface FriendPokeAlert {
    /** 알림 하나. [pokeId]가 같으면 같은 알림을 갱신한다. 띄웠으면 true(알림 권한이 없으면 false). */
    fun show(pokeId: String, fromNickname: String, text: String): Boolean
}
