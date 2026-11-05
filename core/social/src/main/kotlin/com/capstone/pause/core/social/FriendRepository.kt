package com.capstone.pause.core.social

import android.content.Context
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.common.Dispatcher
import com.capstone.pause.core.common.PauseDispatchers
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 친구와 랭킹(03 문서 4절). 친구에게는 감소율·기록률만 공유한다. */
interface FriendRepository {
    /** 친구 탭 값. */
    fun observe(): Flow<FriendsOverview>

    /** 차단(로컬). 차단한 친구는 랭킹·잔소리에서 빠진다. */
    suspend fun block(friendId: String)

    /** 차단 풀기. */
    suspend fun unblock(friendId: String)

    /** 신고(로컬 기록. 친구 기능이 서버에 연결되면 함께 보낸다). */
    suspend fun report(friendId: String)
}

/** 가상 친구 파일 `assets/demo/friends.json`(core:sensing 자산, 앱에 합쳐진다). */
@Serializable
internal data class DemoFriendsAsset(val friends: List<DemoFriendEntry> = emptyList())

/** 가상 친구 한 명. */
@Serializable
internal data class DemoFriendEntry(
    val id: String,
    val nickname: String,
    val reductionRate: Double? = null,
    val coverage: Double? = null,
    val validDays: Int = 0,
    val challengeLateNightZero: List<Boolean> = emptyList(),
)

/**
 * SOCIAL_BACKEND = off: 친구는 시연 모드의 가상 친구뿐이다(자산을 직접 읽는다).
 * TODO(BUILD_REPORT#14): SOCIAL_BACKEND = firebase(P1)면 Firestore `publicDaily`를 읽는 구현으로 바꾼다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class DemoFriendRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val localStore: FriendLocalStore,
    private val clock: AppClock,
    @Dispatcher(PauseDispatchers.IO) private val io: CoroutineDispatcher,
) : FriendRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private val demoFriends: Flow<List<DemoFriendEntry>> = flow {
        emit(withContext(io) { runCatching { readAsset() }.getOrDefault(emptyList()) })
    }

    override fun observe(): Flow<FriendsOverview> = settingsRepository.settings.flatMapLatest { settings ->
        val now = clock.now()
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(HISTORY_DAYS), clock.zone)
        val to = DayBoundary.endOf(today, clock.zone)
        combine(
            sessionRepository.observeSessions(from, to),
            demoFriends,
            profileRepository.profile,
            localStore.blocked,
            localStore.reported,
        ) { sessions, demo, profile, blocked, reported ->
            val me = MyStanding.compute(sessions, today, clock.zone, now, profile?.nickname.orEmpty())
            val friends = if (settings.demoMode) demo else emptyList()
            val standings = friends.map {
                Standing(it.id, it.nickname, it.reductionRate, it.coverage, it.validDays, isMe = false)
            }
            val visible = standings.filter { it.id !in blocked }
            FriendsOverview(
                isDemo = settings.demoMode,
                available = settings.demoMode,
                me = me,
                ranking = Ranking.build(visible + me),
                friends = visible,
                allFriends = standings,
                blocked = blocked,
                reported = reported,
                challenge = challengeRows(friends.filter { it.id !in blocked }, sessions, me, today, now),
            )
        }
    }

    private fun challengeRows(
        friends: List<DemoFriendEntry>,
        sessions: List<com.capstone.pause.core.model.ShortformSession>,
        me: Standing,
        today: java.time.LocalDate,
        now: java.time.Instant,
    ): List<ChallengeRow> {
        if (friends.isEmpty()) return emptyList()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val mine = MyStanding.lateNightZeroDays(sessions, today, clock.zone, now)
        val rows = friends.map { f ->
            val days = (0 until DAYS).map { i ->
                if (monday.plusDays(i.toLong()).isAfter(today)) null else f.challengeLateNightZero.getOrNull(i)
            }
            ChallengeRow(f.nickname, isMe = false, days = days)
        }
        return listOf(ChallengeRow(me.nickname, isMe = true, days = mine)) + rows
    }

    override suspend fun block(friendId: String) = localStore.block(friendId)

    override suspend fun unblock(friendId: String) = localStore.unblock(friendId)

    override suspend fun report(friendId: String) = localStore.report(friendId)

    private fun readAsset(): List<DemoFriendEntry> = context.assets.open(ASSET).bufferedReader().use {
        json.decodeFromString<DemoFriendsAsset>(it.readText()).friends
    }

    private companion object {
        const val ASSET = "demo/friends.json"
        const val HISTORY_DAYS = 35L
        const val DAYS = 7
    }
}
