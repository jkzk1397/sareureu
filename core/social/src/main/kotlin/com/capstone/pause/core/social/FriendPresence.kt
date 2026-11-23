package com.capstone.pause.core.social

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 지금 시청 중 신호(ADR-0012). 공유에 동의했고 실제로 숏폼을 보는 동안만 몇 초마다 서버에 알리고,
 * 그 응답으로 친구가 보낸 잔소리를 받아 바로 알림으로 띄운다. 앱 시작 때 한 번 [start]한다(여러 번 불러도 한 번만 돈다).
 */
interface FriendPresence {
    /** 시작. */
    fun start()
}

/**
 * 초대 QR로 앱이 열렸을 때 받은 코드(앱 셸이 넣고 친구 탭이 꺼내 연결한다). 공유 동의 전이면 동의할 때까지 기다린다.
 * 기기 안 메모리에만 있다.
 */
@Singleton
class InviteLinkInbox @Inject constructor() {
    private val code = MutableStateFlow<String?>(null)

    /** 아직 연결하지 않은 코드. */
    val pending: StateFlow<String?> = code.asStateFlow()

    /** 받은 코드 넣기(이전 것을 덮는다). */
    fun offer(inviteCode: String) {
        code.value = inviteCode
    }

    /** 꺼내기(한 번만). */
    fun take(): String? = code.getAndSetNull()

    private fun MutableStateFlow<String?>.getAndSetNull(): String? {
        while (true) {
            val current = value ?: return null
            if (compareAndSet(current, null)) return current
        }
    }
}
