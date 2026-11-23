package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.friend.FriendLocalStore
import com.capstone.pause.core.data.insight.PeerGroupStat
import com.capstone.pause.core.data.insight.PeerStats
import com.capstone.pause.core.data.insight.PeerStatsRepository
import com.capstone.pause.core.data.monitoring.MonitoringGapRepository
import com.capstone.pause.core.data.monitoring.toSpans
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.insights.GoalStreak
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.social.MyStanding
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
import com.capstone.pause.core.social.SocialLimits
import com.capstone.pause.core.social.Standing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 하루 합계 올리기 예약(WorkManager). 테스트에서는 가짜로 바꾼다. */
interface UploadScheduler {
    /** 하루 한 번 올리기 예약 + 지금 한 번. */
    fun schedule()

    /** 예약과 대기 중인 올리기·지우기 취소. */
    fun cancel()

    /** 서버 기록 지우기를 네트워크가 될 때 다시 해 본다(철회 때 실패하면). */
    fun scheduleDelete()
}

/** 서버로 보낼 몸통 만들기(순수). 키 이름은 server/README.md "앱 연결 계약"과 같다. */
object SharingPayloads {
    /**
     * 공유 동의 버전(서버 `consentVersion`). 보내는 항목이 바뀌면 올린다.
     * 2(ADR-0012): 연속 일수와 '지금 시청 중' 신호가 더해졌다. 버전 1로 동의한 사람은 다시 동의해야 한다.
     */
    const val CONSENT_VERSION = 2

    /** 닉네임 최대 글자(서버와 같음). */
    const val MAX_NICKNAME = SocialLimits.MAX_NICKNAME

    /** 동의: 닉네임(친구에게 보임), 연령대·신분(또래 그룹 집계용). 실명·연락처는 없다. */
    fun sharing(profile: Profile?): JsonObject = JsonObject(
        buildMap {
            put("consentVersion", JsonPrimitive(CONSENT_VERSION))
            profile?.nickname?.trim()?.takeIf { it.isNotEmpty() }?.let {
                put("nickname", JsonPrimitive(it.take(MAX_NICKNAME)))
            }
            profile?.let {
                put("ageBand", JsonPrimitive(it.ageBand.name))
                put("occupation", JsonPrimitive(it.occupation.name))
            }
        },
    )

    /** 하루 합계. 기록된 비율은 아직 재지 않아 null. */
    fun daily(minutes: Int, measuredAt: Instant, zone: ZoneId): JsonObject = JsonObject(
        mapOf(
            "shortformMinutes" to JsonPrimitive(minutes.coerceIn(0, MAX_DAY_MINUTES)),
            "coverage" to JsonNull,
            "measuredAtMs" to JsonPrimitive(measuredAt.toEpochMilli()),
            "timeZone" to JsonPrimitive(zone.id),
        ),
    )

    /** 랭킹 숫자: 감소율·기록된 비율·유효한 날(ADR-0005), 목표 이하 연속 일수(ADR-0012). */
    fun standing(s: Standing, measuredAt: Instant): JsonObject = JsonObject(
        mapOf(
            "reductionRate" to (s.reductionRate?.coerceIn(MIN_RATE, 1.0)?.let(::JsonPrimitive) ?: JsonNull),
            "coverage" to (s.coverage?.coerceIn(0.0, 1.0)?.let(::JsonPrimitive) ?: JsonNull),
            "validDays" to JsonPrimitive(s.validDays.coerceIn(0, WEEK)),
            "streakDays" to JsonPrimitive(s.streakDays.coerceIn(0, GoalStreak.MAX_DAYS)),
            "measuredAtMs" to JsonPrimitive(measuredAt.toEpochMilli()),
        ),
    )

    /** 지금 시청 중 신호(ADR-0012). 앱·영상·시간은 담지 않는다. 받은 잔소리 확인은 [PokePayloads.presence]. */
    fun presence(watching: Boolean): JsonObject = PokePayloads.presence(watching, emptyList())

    /**
     * 올릴 하루: 어제(새벽 4시 경계). **실제 기록만** 쓰고, 첫 기록보다 앞선 날이면 null(설치 전 날을 0분으로 올리지 않는다).
     */
    fun yesterday(
        sessions: List<ShortformSession>,
        today: LocalDate,
        zone: ZoneId,
        now: Instant,
    ): Pair<LocalDate, Int>? {
        val real = sessions.filter { it.source == DataSource.REAL }
        val first = real.minOfOrNull { it.start }?.let { DayBoundary.logicalDate(it, zone) } ?: return null
        val day = today.minusDays(1)
        if (day.isBefore(first)) return null
        return day to ShortformMinutes.onDay(real, day, zone, now)
    }

    /**
     * `GET /api/v1/peers` → 또래 그룹 집계(5-5). 서버가 이미 10명 미만을 빼지만 앱도 한 번 더 거른다.
     * 모양이 이상하면 null.
     */
    fun peers(body: JsonElement?, fetchedAt: Instant): PeerStats? = runCatching {
        val groups = body?.jsonObject?.get("groups")?.jsonArray ?: return null
        PeerStats(
            groups = groups.mapNotNull { e ->
                val g = e.jsonObject
                val n = g["n"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                PeerGroupStat(
                    ageBand = g.getValue("ageBand").jsonPrimitive.content,
                    occupation = g.getValue("occupation").jsonPrimitive.content,
                    n = n,
                    p50 = g["p50"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                    p75 = g["p75"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                ).takeIf { it.n >= MIN_GROUP }
            },
            fetchedAt = fetchedAt,
        )
    }.getOrNull()

    /** 또래 그룹 최소 인원. */
    const val MIN_GROUP = 10

    private const val MAX_DAY_MINUTES = 1500
    private const val MIN_RATE = -10.0
    private const val WEEK = 7
}

/**
 * 공유 동의와 하루 합계 올리기. 동의 기록은 `ConsentItem.FRIENDS`(버전 [SharingPayloads.CONSENT_VERSION]).
 * 예전 버전으로 동의한 사람은 [SharingState.NOT_CONSENTED]로 보여 새 내용으로 다시 동의를 받는다.
 * 서버 주소가 없거나 시연 모드면 아무것도 보내지 않는다.
 */
@Singleton
internal class RemoteSharingRepository @Inject constructor(
    private val server: SocialServer,
    private val transport: SocialTransport,
    private val auth: SocialAuth,
    private val scheduler: UploadScheduler,
    private val settingsRepository: SettingsRepository,
    private val consentRepository: ConsentRepository,
    private val profileRepository: ProfileRepository,
    private val sessionRepository: SessionRepository,
    private val peerStats: PeerStatsRepository,
    private val gapRepository: MonitoringGapRepository,
    private val localStore: FriendLocalStore,
    private val clock: AppClock,
    private val log: AppLog,
) : SharingRepository {

    override val deletingRemote: Flow<Boolean> = localStore.remoteDeletePending

    override val state: Flow<SharingState> = combine(
        settingsRepository.settings,
        consentRepository.consents,
    ) { settings, consents ->
        val record = consents[ConsentItem.FRIENDS]
        when {
            !server.enabled || settings.demoMode -> SharingState.UNAVAILABLE
            record?.granted == true && record.version >= SharingPayloads.CONSENT_VERSION -> SharingState.CONSENTED
            else -> SharingState.NOT_CONSENTED
        }
    }.distinctUntilChanged()

    override suspend fun grant(): Boolean {
        if (state.first() == SharingState.UNAVAILABLE) return false
        // 예전 철회에서 남은 지우기 작업이 새 동의를 지우지 않게 먼저 취소한다.
        scheduler.cancel()
        localStore.setRemoteDeletePending(false)
        val body = SharingPayloads.sharing(profileRepository.profile.first())
        val response = transport.call("PUT", "/api/v1/sharing", body)
        if (!response.ok) {
            log.w(TAG, "sharing grant failed: ${response.code}")
            return false
        }
        consentRepository.record(ConsentItem.FRIENDS, SharingPayloads.CONSENT_VERSION, granted = true)
        scheduler.schedule()
        return true
    }

    override suspend fun revoke(): Boolean {
        scheduler.cancel()
        val record = consentRepository.consents.first()[ConsentItem.FRIENDS]
        if (record?.granted == true) {
            consentRepository.record(ConsentItem.FRIENDS, SharingPayloads.CONSENT_VERSION, granted = false)
        }
        peerStats.clear()
        localStore.clearFriendsCache()
        // 한 번도 동의한 적 없으면 서버에 내 기록이 없다(지우려고 익명 로그인을 새로 만들지 않는다).
        if (!server.enabled || record == null) return true
        // 지울 때까지 "서버 기록 지우는 중"으로 보이고, 실패하면 성공할 때까지 WorkManager가 계속 시도한다.
        localStore.setRemoteDeletePending(true)
        val ok = deleteRemote()
        if (!ok) scheduler.scheduleDelete()
        return ok
    }

    /**
     * 서버가 사용자 행을 지우면 일일 합계·랭킹 숫자·친구 관계·요청·잔소리가 함께 지워진다.
     * 지운 뒤에는 Firebase 익명 계정도 지운다(되찾을 수 없는 계정이라 남길 이유가 없다).
     */
    suspend fun deleteRemote(): Boolean {
        val ok = transport.call("DELETE", "/api/v1/sharing").ok
        if (ok) {
            runCatching { auth.deleteAccount() }.onFailure { auth.signOut() }
            localStore.setRemoteDeletePending(false)
        }
        return ok
    }

    override suspend fun upload(): Boolean {
        if (state.first() != SharingState.CONSENTED) return false
        val now = clock.now()
        val zone = clock.zone
        val today = DayBoundary.logicalDate(now, zone)
        val from = DayBoundary.startOf(today.minusDays(HISTORY_DAYS), zone)
        val sessions = sessionRepository.observeSessions(from, DayBoundary.endOf(today, zone)).first()
            .filter { it.source == DataSource.REAL }
        // 사이에 시연 모드로 바뀌었으면 보내지 않는다.
        if (settingsRepository.settings.first().demoMode) return false
        var ok = true
        SharingPayloads.yesterday(sessions, today, zone, now)?.let { (day, minutes) ->
            ok = transport.call("PUT", "/api/v1/daily/$day", SharingPayloads.daily(minutes, now, zone)).ok
        }
        ok = putStanding(sessions, today, now) && ok
        // 또래 통계(5-5): 서버가 하루 한 번 계산한 그룹 집계만 받는다. 개인별 값은 없다.
        val peers = transport.call("GET", "/api/v1/peers")
        if (peers.ok) SharingPayloads.peers(peers.body, now)?.let { peerStats.save(it) }
        if (!ok) log.w(TAG, "upload failed")
        return ok
    }

    /**
     * 랭킹 숫자만 다시 올리기(친구 탭 새로 고침·숏폼을 멈췄을 때). 연속 일수가 하루 한 번보다 빨리 바뀌게 한다.
     * 동의했고 시연이 아닐 때만. 올렸으면 true.
     */
    suspend fun uploadStanding(): Boolean {
        if (state.first() != SharingState.CONSENTED) return false
        val now = clock.now()
        val today = DayBoundary.logicalDate(now, clock.zone)
        val from = DayBoundary.startOf(today.minusDays(HISTORY_DAYS), clock.zone)
        val sessions = sessionRepository.observeSessions(from, DayBoundary.endOf(today, clock.zone)).first()
            .filter { it.source == DataSource.REAL }
        if (settingsRepository.settings.first().demoMode) return false
        return putStanding(sessions, today, now)
    }

    private suspend fun putStanding(sessions: List<ShortformSession>, today: LocalDate, now: Instant): Boolean {
        val profile = profileRepository.profile.first()
        val me = MyStanding.compute(
            sessions,
            today,
            clock.zone,
            now,
            profile?.nickname.orEmpty(),
            profile?.dailyGoalMinutes ?: 0,
            gapRepository.gaps.first().toSpans(now),
        )
        return transport.call("PUT", "/api/v1/standing", SharingPayloads.standing(me, now)).ok
    }

    private companion object {
        const val TAG = "Sharing"

        // 세션 보관 기간(90일)만큼: 연속 일수를 그만큼 셀 수 있다.
        const val HISTORY_DAYS = 90L
    }
}
