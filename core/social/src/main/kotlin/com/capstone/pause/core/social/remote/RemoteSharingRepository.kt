package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.insight.PeerGroupStat
import com.capstone.pause.core.data.insight.PeerStats
import com.capstone.pause.core.data.insight.PeerStatsRepository
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.insights.ShortformMinutes
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.social.MyStanding
import com.capstone.pause.core.social.SharingRepository
import com.capstone.pause.core.social.SharingState
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
    /** 공유 동의 버전(서버 `consentVersion`). 보내는 항목이 바뀌면 올린다. */
    const val CONSENT_VERSION = 1

    /** 닉네임 최대 글자(서버와 같음). */
    const val MAX_NICKNAME = 12

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

    /** 랭킹 숫자: 감소율·기록된 비율·유효한 날(ADR-0005). */
    fun standing(s: Standing, measuredAt: Instant): JsonObject = JsonObject(
        mapOf(
            "reductionRate" to (s.reductionRate?.coerceIn(MIN_RATE, 1.0)?.let(::JsonPrimitive) ?: JsonNull),
            "coverage" to (s.coverage?.coerceIn(0.0, 1.0)?.let(::JsonPrimitive) ?: JsonNull),
            "validDays" to JsonPrimitive(s.validDays.coerceIn(0, WEEK)),
            "measuredAtMs" to JsonPrimitive(measuredAt.toEpochMilli()),
        ),
    )

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
    private val clock: AppClock,
    private val log: AppLog,
) : SharingRepository {

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
        // 한 번도 동의한 적 없으면 서버에 내 기록이 없다(지우려고 익명 로그인을 새로 만들지 않는다).
        if (!server.enabled || record == null) return true
        val ok = deleteRemote()
        if (!ok) scheduler.scheduleDelete()
        return ok
    }

    /** 서버가 사용자 행을 지우면 일일 합계·랭킹 숫자·친구 관계·잔소리가 함께 지워진다. 지운 뒤에만 로그아웃한다. */
    suspend fun deleteRemote(): Boolean {
        val ok = transport.call("DELETE", "/api/v1/sharing").ok
        if (ok) auth.signOut()
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
        val nickname = profileRepository.profile.first()?.nickname.orEmpty()
        val me = MyStanding.compute(sessions, today, zone, now, nickname)
        ok = transport.call("PUT", "/api/v1/standing", SharingPayloads.standing(me, now)).ok && ok
        // 또래 통계(5-5): 서버가 하루 한 번 계산한 그룹 집계만 받는다. 개인별 값은 없다.
        val peers = transport.call("GET", "/api/v1/peers")
        if (peers.ok) SharingPayloads.peers(peers.body, now)?.let { peerStats.save(it) }
        if (!ok) log.w(TAG, "upload failed")
        return ok
    }

    private companion object {
        const val TAG = "Sharing"
        const val HISTORY_DAYS = 35L
    }
}
