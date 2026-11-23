package com.capstone.pause.core.social

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.friend.EntrustedPoke
import com.capstone.pause.core.data.friend.FriendLocalStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** 잔소리 맡기기 결과. */
sealed interface EntrustResult {
    /** 맡김(시연에서는 기기 안에만 기록). */
    data object Entrusted : EntrustResult

    /** 비었음. */
    data object Empty : EntrustResult

    /** 40자 넘음. */
    data object TooLong : EntrustResult

    /** 금지 표현(비하·수치심). */
    data object Banned : EntrustResult

    /** 서버에 전하지 못함(네트워크·하루 한도·친구가 아님). */
    data object Failed : EntrustResult

    /** 친구가 방금 숏폼을 멈춰서 보내지 않았다(ADR-0012: 시청 중인 친구에게만). */
    data object NotWatching : EntrustResult

    /** 하루 한도(전체 10번, 같은 친구 3번). [retryAfterSeconds] 뒤에 다시 보낼 수 있다(모르면 null). */
    data class Limited(val retryAfterSeconds: Long?) : EntrustResult

    /** 여러 사람에게 신고돼 잔소리 보내기가 멈춘 계정(ADR-0014). */
    data object Suspended : EntrustResult

    /** 서버에 닿지 못함·시계 등 종류가 있는 실패. */
    data class Error(val error: SocialError) : EntrustResult
}

/**
 * 잔소리 받은편지함·보낸 기록(03 문서 4절). 받은 잔소리는 `core:data` PokeRepository가 들고 있고,
 * 여기서는 내가 친구에게 보내는 쪽을 다룬다.
 */
interface PokeInbox {
    /** 내가 보낸 잔소리(최신순). */
    val entrusted: Flow<List<EntrustedPoke>>

    /**
     * 친구에게 잔소리 보내기(ADR-0012). 지금 시청 중인 친구에게만 가고, 친구 폰에 몇 초 안에 '보낸 사람 + 글' 알림이 뜬다.
     * 사람이 쓴 글이라 숫자는 허용하고 길이만 본다(금지 표현은 화면이 먼저 거른다).
     */
    suspend fun entrust(friendId: String, text: String): EntrustResult

    /**
     * 친구가 나에게 보낸 잔소리 중 놓친 것을 서버에서 받아 `core:data` PokeRepository에 넣는다(앱이 화면에 올 때·WorkManager).
     * 15분에 한 번까지만 부른다. [manual]은 사용자가 직접 새로고침했을 때로, 30초 간격까지 허용한다. 받은 개수.
     */
    suspend fun fetch(manual: Boolean = false): Int = 0
}

/** 잔소리 글 기본 검사(순수). 금지 표현 검사는 화면(feature:friends)이 `BannedWords`로 먼저 한다. */
object PokeRules {
    /** 최대 글자 수(05 문서 5.5절). */
    const val MAX_LENGTH = 40

    /** 검사. 통과하면 null. */
    fun check(text: String): EntrustResult? {
        val t = text.trim()
        return when {
            t.isEmpty() -> EntrustResult.Empty
            t.length > MAX_LENGTH -> EntrustResult.TooLong
            else -> null
        }
    }
}

/**
 * SOCIAL_BACKEND = off: 맡긴 잔소리는 기기 안에만 남긴다(가상 친구에게 실제로 전해지지 않음).
 * TODO(BUILD_REPORT#14): firebase면 `pokes/{toUid}/inbox`에 쓴다.
 */
@Singleton
internal class LocalPokeInbox @Inject constructor(
    private val localStore: FriendLocalStore,
    private val clock: AppClock,
) : PokeInbox {
    override val entrusted: Flow<List<EntrustedPoke>> = localStore.entrusted

    override suspend fun entrust(friendId: String, text: String): EntrustResult {
        PokeRules.check(text)?.let { return it }
        localStore.addEntrusted(EntrustedPoke(UUID.randomUUID().toString(), friendId, text.trim(), clock.now()))
        return EntrustResult.Entrusted
    }
}

/** 주간 리그(P1). 지금은 '곧 열려요'. */
interface LeagueRepository {
    /** 리그 상태. */
    fun observe(): Flow<LeagueState>
}

/** 리그가 열리기 전 구현. */
@Singleton
internal class ComingSoonLeagueRepository @Inject constructor() : LeagueRepository {
    override fun observe(): Flow<LeagueState> = kotlinx.coroutines.flow.flowOf(LeagueState.ComingSoon)
}
