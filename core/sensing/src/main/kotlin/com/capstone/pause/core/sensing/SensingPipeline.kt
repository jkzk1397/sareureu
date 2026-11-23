package com.capstone.pause.core.sensing

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.data.monitoring.LiveSessionSource
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.trackedapp.TrackedAppRepository
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.ShortformSession
import com.capstone.pause.core.model.Surface
import com.capstone.pause.core.sensing.accessibility.AccessibilitySource
import com.capstone.pause.core.sensing.context.ContextClassifier
import com.capstone.pause.core.sensing.context.DeviceSignals
import com.capstone.pause.core.sensing.device.DayRhythmProvider
import com.capstone.pause.core.sensing.device.DeviceSignalReader
import com.capstone.pause.core.sensing.path.AndroidAppKindResolver
import com.capstone.pause.core.sensing.session.SessionTracker
import com.capstone.pause.core.sensing.usage.UsageStatsSource
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 감지 → 세션 → 저장을 잇는다. 스스로 시작하지 않는다: 3단계 `MonitorService`(사용자가 화면에서 감지를 켤 때)가
 * [start]/[stop]을 부른다. 끝난 세션은 `source`(기본 REAL)로 저장한다.
 */
@Singleton
class SensingPipeline @Inject constructor(
    private val usageStatsSource: UsageStatsSource,
    private val accessibility: AccessibilitySource,
    private val kinds: AndroidAppKindResolver,
    private val trackedApps: TrackedAppRepository,
    private val rhythm: DayRhythmProvider,
    private val signals: DeviceSignalReader,
    private val sessionRepository: SessionRepository,
    private val clock: AppClock,
    private val log: AppLog,
) : LiveSessionSource {
    private val live = MutableStateFlow<ShortformSession?>(null)
    private val pending = MutableStateFlow<ShortformSession?>(null)
    private val lock = Mutex()
    private var job: Job? = null
    private var tracker: SessionTracker? = null

    @Volatile
    private var precise = false

    /** 지금 숏폼 화면에 있는 진행 중 세션. */
    val liveSession: StateFlow<ShortformSession?> = live.asStateFlow()

    /** 저장 전 진행 중 세션(잠깐 다른 앱에 가 있는 동안 포함). 오늘 탭 합계용. */
    override val current: StateFlow<ShortformSession?> = pending.asStateFlow()

    /** 감지 중인지. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * 감지를 시작한다. 이미 돌고 있으면 멈추고 다시 시작한다.
     * [source]를 주지 않으면 실제 감지: 정밀 모드가 쓰이는 동안(동의 + 접근성 서비스 연결)은 접근성 판단을 합친 이벤트,
     * 아니면 사용 기록만(ADR-0004). 정밀 모드가 켜지거나 꺼지면 그 자리에서 바뀐다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope, source: ForegroundSource? = null, dataSource: DataSource = DataSource.REAL) {
        stop()
        val events: Flow<ForegroundEvent> = source?.events ?: accessibility.active.flatMapLatest { on ->
            precise = on
            if (on) accessibility.preciseEvents(usageStatsSource) else usageStatsSource.events
        }
        job = scope.launch {
            val tracked =
                MutableStateFlow(trackedApps.trackedApps.first().filter { it.enabled }.map { it.packageName }.toSet())
            launch {
                trackedApps.trackedApps.collectLatest { list ->
                    tracked.value =
                        list.filter { it.enabled }.map { it.packageName }.toSet()
                }
            }
            val currentRhythm = rhythm.current()
            val t = SessionTracker(
                isShortform = { it in tracked.value },
                kinds = kinds,
                classify = { start, firstUnlock ->
                    ContextClassifier.classify(
                        ZonedDateTime.ofInstant(start, clock.zone),
                        currentRhythm,
                        DeviceSignals(signals.charging(), signals.headphones(), firstUnlock),
                    )
                },
                zone = clock.zone,
                source = dataSource,
                // 정밀 모드일 때만 쇼츠·릴스·틱톡 피드로 표시. 앱 단위면 '앱 전체'(APP_UNKNOWN).
                surfaceOf = { pkg -> if (precise) accessibility.surfaceOf(pkg) else Surface.APP_UNKNOWN },
            )
            tracker = t
            launch {
                t.liveSession.collect { session ->
                    live.value = session
                    usageStatsSource.fastPolling = session != null
                }
            }
            launch { t.currentSession.collect { pending.value = it } }
            launch {
                while (true) {
                    // 틱은 '잠깐 떠난 세션을 60초 뒤에 끝내기'에만 쓴다. 이어지는 세션이 없으면 생길 때까지 잔다(6-C).
                    t.currentSession.first { it != null }
                    delay(TICK_MS)
                    save(lock.withLock { t.onTick(clock.now()) })
                }
            }
            events.collect { event -> save(lock.withLock { t.onEvent(event) }) }
        }
    }

    /** 감지를 멈춘다. 진행 중 세션은 저장하지 않고 버린다(화면이 꺼지면 이미 끝나 저장된다). */
    fun stop() {
        job?.cancel()
        job = null
        precise = false
        tracker = null
        live.value = null
        pending.value = null
    }

    /**
     * 감지를 멈추되 진행 중 세션은 [at]에서 끝내 돌려준다(저장은 부르는 쪽이 한다). 사용자가 보는 중에 감지를 꺼도
     * 그때까지 본 시간이 기록에서 빠지지 않게 하려는 것이다. 감지와 같은 스레드(메인)에서 부른다.
     */
    fun stopKeepingLive(at: Instant = clock.now()): List<ShortformSession> {
        val closed = tracker?.close(at).orEmpty().map(::withInteraction)
        stop()
        return closed
    }

    /** '그만'을 눌렀다고 알린다(이탈 경로 계산용). */
    fun onNudgeStop(at: Instant = clock.now()) {
        tracker?.onNudgeStop(at)
    }

    private suspend fun save(finished: List<ShortformSession>) {
        for (session in finished) {
            runCatching { sessionRepository.insert(withInteraction(session), emptyList()) }
                .onFailure { log.e(TAG, "session save failed", it) }
        }
    }

    /** 정밀 모드로 센 세션(구간에 화면 종류가 있음)에만 넘김·누름 횟수를 붙인다. */
    private fun withInteraction(session: ShortformSession): ShortformSession {
        if (session.interaction != null || session.source != DataSource.REAL) return session
        if (session.segments.none { it.surface != Surface.APP_UNKNOWN }) return session
        val end = session.end ?: session.segments.maxOfOrNull { it.end } ?: return session
        return session.copy(interaction = accessibility.interactionBetween(session.start, end))
    }

    private companion object {
        const val TAG = "SensingPipeline"
        const val TICK_MS = 5_000L
    }
}
