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
}

/**
 * 잔소리 받은편지함·보낸 기록(03 문서 4절). 받은 잔소리는 `core:data` PokeRepository가 들고 있고,
 * 여기서는 내가 친구에게 맡기는 쪽을 다룬다.
 */
interface PokeInbox {
    /** 내가 맡긴 잔소리(최신순). */
    val entrusted: Flow<List<EntrustedPoke>>

    /** 친구에게 잔소리 맡기기. 사람이 쓴 글이라 숫자는 허용하고 길이만 본다(금지 표현은 화면이 먼저 거른다). */
    suspend fun entrust(friendId: String, text: String): EntrustResult
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
