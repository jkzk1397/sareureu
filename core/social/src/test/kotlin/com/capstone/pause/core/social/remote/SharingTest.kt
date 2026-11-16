package com.capstone.pause.core.social.remote

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.data.demo.DemoPattern
import com.capstone.pause.core.data.insight.PeerStats
import com.capstone.pause.core.data.insight.PeerStatsRepository
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRecord
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.data.settings.AppSettings
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.data.settings.ThemeMode
import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.NudgeEvent
import com.capstone.pause.core.model.Occupation
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.social.SharingState
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class SharingTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 16)
    private val now = today.atTime(LocalTime.of(12, 0)).atZone(zone).toInstant()

    private fun session(day: LocalDate, minutes: Long, source: DataSource = DataSource.REAL): ShortformSession {
        val start = day.atTime(LocalTime.of(21, 0)).atZone(zone).toInstant()
        return ShortformSession(
            0, start, start.plusSeconds(minutes * 60), emptyList(), ContextSlot.OTHER, Confidence.HIGH, false,
            EntryPoint.Unknown, null, null, 0, 0, false, null, source,
        )
    }

    @Test
    fun sharingBodyHasOnlyNicknameAgeBandOccupation() {
        val body = SharingPayloads.sharing(PROFILE.copy(nickname = "  아주아주긴닉네임이라서잘림  "))
        assertEquals(setOf("consentVersion", "nickname", "ageBand", "occupation"), body.keys)
        assertEquals(SharingPayloads.MAX_NICKNAME, body.getValue("nickname").jsonPrimitive.content.length)
        assertEquals("AGE_18_24", body.getValue("ageBand").jsonPrimitive.content)
    }

    @Test
    fun yesterdayUsesRealOnlyAndSkipsDaysBeforeFirstRecord() {
        val demoOnly = listOf(session(today.minusDays(1), 40, DataSource.DEMO))
        assertNull(SharingPayloads.yesterday(demoOnly, today, zone, now))
        assertNull(SharingPayloads.yesterday(listOf(session(today, 10)), today, zone, now))
        val mixed = listOf(session(today.minusDays(3), 5), session(today.minusDays(1), 30)) +
            session(today.minusDays(1), 50, DataSource.DEMO)
        assertEquals(today.minusDays(1) to 30, SharingPayloads.yesterday(mixed, today, zone, now))
        // 첫 기록 뒤 안 본 날은 0분으로 올린다.
        assertEquals(
            today.minusDays(1) to 0,
            SharingPayloads.yesterday(listOf(session(today.minusDays(3), 5)), today, zone, now),
        )
    }

    @Test
    fun dailyBodyMatchesServerContract() {
        val body = SharingPayloads.daily(37, now, zone)
        assertEquals(setOf("shortformMinutes", "coverage", "measuredAtMs", "timeZone"), body.keys)
        assertEquals("Asia/Seoul", body.getValue("timeZone").jsonPrimitive.content)
    }

    @Test
    fun grantRecordsConsentOnlyWhenServerAccepts() = runTest {
        val f = Fixture(responseCode = 503)
        assertEquals(SharingState.NOT_CONSENTED, f.repo.state.first())
        assertFalse(f.repo.grant())
        assertNull(f.consents.value[ConsentItem.FRIENDS])
        assertEquals(0, f.scheduler.scheduled)

        f.transport.code = 204
        assertTrue(f.repo.grant())
        assertEquals(SharingState.CONSENTED, f.repo.state.first())
        assertEquals(1, f.scheduler.scheduled)
        assertEquals("PUT /api/v1/sharing", f.transport.calls.last().first)
    }

    @Test
    fun demoModeIsUnavailableAndUploadsNothing() = runTest {
        val f = Fixture(responseCode = 204)
        f.repo.grant()
        f.transport.calls.clear()
        f.settings.value = AppSettings(demoMode = true)
        f.sessions.value = listOf(session(today.minusDays(2), 20), session(today.minusDays(1), 30, DataSource.DEMO))
        assertEquals(SharingState.UNAVAILABLE, f.repo.state.first())
        assertFalse(f.repo.upload())
        assertTrue(f.transport.calls.isEmpty())
        assertFalse(f.repo.grant())
    }

    @Test
    fun uploadSendsYesterdayAndStandingOfRealRecords() = runTest {
        val f = Fixture(responseCode = 204)
        f.repo.grant()
        f.transport.calls.clear()
        f.sessions.value = listOf(session(today.minusDays(2), 20), session(today.minusDays(1), 30))
        assertTrue(f.repo.upload())
        assertEquals(listOf("PUT /api/v1/daily/2026-11-15", "PUT /api/v1/standing"), f.transport.calls.map { it.first })
        assertEquals("30", f.transport.calls.first().second?.getValue("shortformMinutes")?.jsonPrimitive?.content)
    }

    @Test
    fun noServerMeansUnavailableAndNoCalls() = runTest {
        val f = Fixture(responseCode = 204, server = SocialServer(""))
        assertEquals(SharingState.UNAVAILABLE, f.repo.state.first())
        assertFalse(f.repo.grant())
        assertTrue(f.repo.revoke())
        assertTrue(f.transport.calls.isEmpty())
    }

    @Test
    fun revokeStopsUploadsDeletesServerAndRetriesWhenOffline() = runTest {
        val f = Fixture(responseCode = 204)
        f.repo.grant()
        f.transport.code = 0
        assertFalse(f.repo.revoke())
        assertEquals(SharingState.NOT_CONSENTED, f.repo.state.first())
        assertEquals(1, f.scheduler.deleteScheduled)
        assertTrue(f.scheduler.cancelled > 0)
        assertEquals(1, f.peer.cleared)
        assertEquals("DELETE /api/v1/sharing", f.transport.calls.last().first)
        assertEquals(0, f.auth.signedOut)

        f.transport.code = 204
        assertTrue(f.repo.revoke())
        assertEquals(1, f.auth.signedOut)
    }

    @Test
    fun revokeWithoutAnyConsentDoesNotCallServer() = runTest {
        val f = Fixture(responseCode = 204)
        assertTrue(f.repo.revoke())
        assertTrue(f.transport.calls.isEmpty())
    }

    private class Fixture(responseCode: Int, server: SocialServer = SocialServer("https://example.test")) {
        val transport = FakeTransport(responseCode)
        val scheduler = FakeScheduler()
        val auth = FakeAuth()
        val settings = MutableStateFlow(AppSettings())
        val consents = MutableStateFlow<Map<ConsentItem, ConsentRecord>>(emptyMap())
        val sessions = MutableStateFlow<List<ShortformSession>>(emptyList())
        val peer = FakePeer()
        val repo = RemoteSharingRepository(
            server = server,
            transport = transport,
            auth = auth,
            scheduler = scheduler,
            settingsRepository = FakeSettings(settings),
            consentRepository = object : ConsentRepository {
                override val consents: Flow<Map<ConsentItem, ConsentRecord>> = this@Fixture.consents
                override suspend fun record(item: ConsentItem, version: Int, granted: Boolean) {
                    this@Fixture.consents.value += item to ConsentRecord(item, version, granted, Instant.EPOCH)
                }
            },
            profileRepository = object : ProfileRepository {
                override val profile: Flow<Profile?> = MutableStateFlow(PROFILE)
                override val hasProfile: Flow<Boolean> = profile.map { it != null }
                override suspend fun save(profile: Profile) = Unit
                override suspend fun clear() = Unit
            },
            sessionRepository = object : SessionRepository {
                override fun observeSessions(from: Instant, to: Instant) = sessions
                override fun observeNudges(from: Instant, to: Instant): Flow<List<NudgeEvent>> =
                    MutableStateFlow(emptyList())
                override suspend fun insert(session: ShortformSession, nudges: List<NudgeEvent>) = 0L
                override suspend fun deleteAll(source: DataSource) = Unit
            },
            peerStats = peer,
            clock = AppClock(Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Seoul"))),
            log = AppLog.None,
        )
    }

    private class FakeTransport(var code: Int) : SocialTransport {
        val calls = mutableListOf<Pair<String, JsonObject?>>()
        override suspend fun call(method: String, path: String, body: JsonObject?): ApiResponse {
            calls += "$method $path" to body
            return ApiResponse(code, null)
        }
    }

    private class FakeScheduler : UploadScheduler {
        var scheduled = 0
        var cancelled = 0
        var deleteScheduled = 0
        override fun schedule() {
            scheduled++
        }
        override fun cancel() {
            cancelled++
        }
        override fun scheduleDelete() {
            deleteScheduled++
        }
    }

    private class FakeAuth : SocialAuth {
        var signedOut = 0
        override suspend fun idToken(): String = "token"
        override fun signOut() {
            signedOut++
        }
    }

    private class FakePeer : PeerStatsRepository {
        var cleared = 0
        override val stats: Flow<PeerStats?> = MutableStateFlow(null)
        override suspend fun save(stats: PeerStats) = Unit
        override suspend fun clear() {
            cleared++
        }
    }

    private class FakeSettings(override val settings: MutableStateFlow<AppSettings>) : SettingsRepository {
        override suspend fun setDemoMode(enabled: Boolean) {
            settings.value = settings.value.copy(demoMode = enabled)
        }
        override suspend fun setDemoPattern(pattern: DemoPattern) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setShowMelt(enabled: Boolean) = Unit
        override suspend fun setAiMode(mode: AiMode) = Unit
        override suspend fun setThreshold(slot: ContextSlot, minutes: Int) = Unit
        override suspend fun reset() = Unit
    }

    private companion object {
        val PROFILE = Profile(
            nickname = "지수",
            ageBand = AgeBand.AGE_18_24,
            occupation = Occupation.UNIVERSITY,
            rhythm = DayRhythm(
                LocalTime.of(7, 0),
                LocalTime.of(0, 0),
                LocalTime.of(9, 0),
                LocalTime.of(1, 0),
                emptyList(),
                emptyList(),
                emptyList(),
            ),
            goals = emptySet(),
            interests = emptySet(),
            tone = Tone.PLAYFUL,
        )
    }
}
