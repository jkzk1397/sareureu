package com.capstone.pause.core.intervention.notify

import com.capstone.pause.core.data.friend.FriendPokeAlert
import javax.inject.Inject
import javax.inject.Singleton

/** [FriendPokeAlert] 구현: [PauseNotifier]의 메시지 알림으로 띄운다. */
@Singleton
internal class NotifierFriendPokeAlert @Inject constructor(private val notifier: PauseNotifier) : FriendPokeAlert {
    override fun show(pokeId: String, fromNickname: String, text: String): Boolean =
        notifier.postFriendPoke(pokeId, fromNickname, text)
}
