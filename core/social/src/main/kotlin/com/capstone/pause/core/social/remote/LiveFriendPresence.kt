package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.data.friend.FriendPokeAlert
import com.capstone.pause.core.data.friend.PokeTextFilter
import com.capstone.pause.core.data.monitoring.LiveSessionSource
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.social.FriendPresence
import com.capstone.pause.core.social.PollBackoff
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.SocialLimits
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 시청 중 신호 규칙(순수). */
object PresenceRules {
    /** 신호 간격([SocialLimits.PRESENCE_BEAT]). */
    val BEAT_MS: Long = SocialLimits.PRESENCE_BEAT.toMillis()

    /** 지금 숏폼 화면에 있는 실제 기록이면 시청 중(잠깐 다른 앱에 간 동안은 아님, 시연 기록은 아님). */
    fun isWatching(live: ShortformSession?): Boolean =
        live != null && live.end == null && live.source == DataSource.REAL
}

/**
 * [FriendPresence] 구현. 감지 서비스(포그라운드)가 프로세스를 살려 두는 동안 돈다.
 * 시청을 시작하면 [PresenceRules.BEAT_MS]마다 `PUT /api/v1/presence {"watching":true}`, 멈추면 `false`를 한 번 보내고
 * 랭킹 숫자(연속 일수)를 다시 올린다. 응답에 실린 잔소리는 [FriendPokeAlert]로 바로 띄운다(받은편지함에는 넣지 않는다:
 * 개입 카드가 같은 말을 또 꺼내지 않게).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class LiveFriendPresence @Inject constructor(
    private val liveSessionSource: LiveSessionSource,
    private val sharingRepository: SharingRepository,
    private val sharing: RemoteSharingRepository,
    private val transport: SocialTransport,
    private val alert: FriendPokeAlert,
    private val ledger: PokeLedger,
    private val filter: PokeTextFilter,
    private val recent: RecentPokes,
    private val clock: AppClock,
    @ApplicationScope private val scope: CoroutineScope,
) : FriendPresence {
    private val started = AtomicBoolean(false)

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var wasWatching = false
            combine(
                sharingRepository.state,
                liveSessionSource.current.map(PresenceRules::isWatching).distinctUntilChanged(),
            ) { state, watching -> state == SharingState.CONSENTED && watching }
                .distinctUntilChanged()
                .collectLatest { watching ->
                    if (watching) {
                        wasWatching = true
                        var failures = 0
                        while (true) {
                            // 서버가 거부하면(무료 한도 초과 등) 간격을 늘려 조용히 기다린다. 몰아서 다시 보내지 않는다.
                            failures = if (beat(true)) 0 else failures + 1
                            delay(PollBackoff.next(SocialLimits.PRESENCE_BEAT, failures).toMillis())
                        }
                    } else if (wasWatching) {
                        wasWatching = false
                        beat(false)
                        runCatching { sharing.uploadStanding() }
                    }
                }
        }
    }

    /** 신호 한 번. 서버가 받았으면 true. */
    private suspend fun beat(watching: Boolean): Boolean {
        // 지난 응답으로 받은 잔소리 id를 실어 보낸다: 서버는 이 확인을 받아야 지운다(응답이 끊겨도 사라지지 않게).
        val ack = ledger.pendingAck()
        val response = runCatching {
            transport.call("PUT", "/api/v1/presence", PokePayloads.presence(watching, ack))
        }.getOrNull() ?: return false
        if (!response.ok) return false
        ledger.acked(ack)
        val now = clock.now()
        // 확인 전까지 서버가 같은 것을 다시 내려 주므로 처음 본 것만 띄운다. 금지 표현이 있으면 띄우지 않는다.
        val fresh = PokePayloads.ids(response.body).filter(ledger::firstTime).toSet()
        PokePayloads.inbox(response.body)
            .filter { it.id in fresh && it.expiresAt.isAfter(now) && filter.allowed(it.text) }
            .forEach {
                recent.remember(it.fromId, it.text)
                alert.show(it.id, it.fromNickname, it.text)
            }
        return true
    }
}
