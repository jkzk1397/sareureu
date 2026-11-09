package com.capstone.pause.core.sensing

import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.data.monitoring.LiveSessionSource
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.trackedapp.TrackedAppRepository
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.ShortformSession
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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

    /** 지금 숏폼 화면에 있는 진행 중 세션. */
    val liveSession: StateFlow<ShortformSession?> = live.asStateFlow()

    /** 저장 전 진행 중 세션(잠깐 다른 앱에 가 있는 동안 포함). 오늘 탭 합계용. */
    override val current: StateFlow<ShortformSession?> = pending.asStateFlow()

    /** 감지 중인지. */
    val isRunning: Boolean get() = job?.isActive == true

    /** [source](기본: 사용 기록)로 감지를 시작한다. 이미 돌고 있으면 멈추고 다시 시작한다. */
    fun start(
        scope: CoroutineScope,
        source: ForegroundSource = usageStatsSource,
        dataSource: DataSource = DataSource.REAL,
    ) {
        stop()
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
                    delay(TICK_MS)
                    save(lock.withLock { t.onTick(clock.now()) })
                }
            }
            source.events.collect { event -> save(lock.withLock { t.onEvent(event) }) }
        }
    }

    /** 감지를 멈춘다. 진행 중 세션은 저장하지 않고 버린다(화면이 꺼지면 이미 끝나 저장된다). */
    fun stop() {
        job?.cancel()
        job = null
        tracker = null
        live.value = null
        pending.value = null
    }

    /** '그만'을 눌렀다고 알린다(이탈 경로 계산용). */
    fun onNudgeStop(at: Instant = clock.now()) {
        tracker?.onNudgeStop(at)
    }

    private suspend fun save(finished: List<ShortformSession>) {
        for (session in finished) {
            runCatching { sessionRepository.insert(session, emptyList()) }
                .onFailure { log.e(TAG, "session save failed", it) }
        }
    }

    private companion object {
        const val TAG = "SensingPipeline"
        const val TICK_MS = 5_000L
    }
}
