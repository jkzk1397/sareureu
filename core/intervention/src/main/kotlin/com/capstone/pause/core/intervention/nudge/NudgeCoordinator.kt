package com.capstone.pause.core.intervention.nudge

import android.content.Context
import android.content.Intent
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.common.DayBoundary
import com.capstone.pause.core.data.copy.CopyPackRepository
import com.capstone.pause.core.data.insight.InsightRepository
import com.capstone.pause.core.data.message.MessageRepository
import com.capstone.pause.core.data.plan.PokeRepository
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.data.session.SessionRepository
import com.capstone.pause.core.data.settings.SettingsRepository
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.notify.PauseNotifier
import com.capstone.pause.core.model.BuddyMessage
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.CopyPack
import com.capstone.pause.core.model.DataSource
import com.capstone.pause.core.model.MeltStage
import com.capstone.pause.core.model.NudgeResponse
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Sender
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.SensingPipeline
import com.capstone.pause.core.sensing.demo.DemoEventSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 개입을 어디서 돌리나: 실제 감지(포그라운드 서비스) / 시연(앱 안, 타이머 60배속). */
enum class NudgeMode { REAL, DEMO }

/**
 * 감지 → 개입 엔진 → 화면·알림을 잇는다. 한 번에 한 모드만 돈다(시연을 켜면 실제 감지는 잠깐 멈춘다).
 * 개입 순간에는 AI를 부르지 않고 문구 팩에서 고르기만 한다(ADR-0001).
 */
@Singleton
class NudgeCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pipeline: SensingPipeline,
    private val demoSource: DemoEventSource,
    private val ui: NudgeUi,
    private val notifier: PauseNotifier,
    private val copyPackRepository: CopyPackRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val pokeRepository: PokeRepository,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val insightRepository: InsightRepository,
    private val clock: AppClock,
    private val log: AppLog,
) {
    private val modeState = MutableStateFlow<NudgeMode?>(null)
    private var job: Job? = null

    /** 지금 도는 모드(null이면 멈춤). */
    val mode: StateFlow<NudgeMode?> = modeState.asStateFlow()

    /** 시연 세션이 '그만'으로 끝났을 때 부를 것(시연 컨트롤러가 정리·실제 감지 재개). */
    var onDemoFinished: (() -> Unit)? = null

    /** [mode]로 시작한다. 이미 돌고 있으면 멈추고 다시. */
    fun start(scope: CoroutineScope, mode: NudgeMode) {
        stop()
        modeState.value = mode
        job = scope.launch(Dispatchers.Main.immediate) { run(this, mode) }
    }

    /** 멈춘다. 화면은 내린다. */
    fun stop() {
        job?.cancel()
        job = null
        pipeline.stop()
        if (modeState.value != null) ui.render(NudgeCommand.Hide)
        modeState.value = null
    }

    private suspend fun run(scope: CoroutineScope, mode: NudgeMode) {
        val source = if (mode == NudgeMode.DEMO) DataSource.DEMO else DataSource.REAL
        if (mode == NudgeMode.DEMO) pipeline.start(scope, demoSource, DataSource.DEMO) else pipeline.start(scope)
        var settings = settingsRepository.settings.first()
        var profile: Profile? = profileRepository.profile.first()
        var pack: CopyPack = copyPackRepository.activePack.first()
        var melt = MeltStage.NONE
        var pokes = pokeRepository.observeUnused(clock.now()).first()
        scope.launch { settingsRepository.settings.collect { settings = it } }
        scope.launch { profileRepository.profile.collect { profile = it } }
        scope.launch { copyPackRepository.activePack.collect { pack = it } }
        scope.launch {
            insightRepository.observeToday().collect {
                melt =
                    if (settings.showMelt) it.meltStage else MeltStage.NONE
            }
        }
        scope.launch { pokeRepository.observeUnused(clock.now()).collect { pokes = it } }

        val texts = fixedTexts()
        val writer = object : NudgeWriter {
            private fun current() = CopyNudgeWriter(pack, profile?.tone ?: Tone.PLAYFUL, texts, profile?.nickname) {
                pokes.firstOrNull()?.let { PendingPoke(it.id, it.fromNickname, it.text) }
            }

            override fun card(level: Int, live: LiveInput, elapsedMinutes: Int) =
                current().card(level, live, elapsedMinutes)

            override fun message(live: LiveInput, elapsedMinutes: Int) = current().message(live, elapsedMinutes)

            override fun praise(live: LiveInput) = current().praise(live)
        }
        val speed = if (mode == NudgeMode.DEMO) DEMO_SPEED else 1.0
        val engine = NudgeEngine(clock.zone, writer, speed)
        val today = DayBoundary.logicalDate(clock.now(), clock.zone)
        val todayExtensions = runCatching {
            sessionRepository.observeNudges(DayBoundary.startOf(today, clock.zone), clock.now()).first()
                .count { it.response == NudgeResponse.EXTEND }
        }.getOrDefault(0)
        engine.seedToday(clock.now(), todayExtensions)

        fun liveInput(): LiveInput? = pipeline.liveSession.value?.let { s ->
            LiveInput(
                sessionStart = s.start,
                context = s.context,
                // 세션 안에서 앱을 옮겨 다니면 지금 보고 있는 앱(마지막 구간) 이름을 쓴다.
                appLabel = context.getString(appLabelRes(s.segments.lastOrNull()?.packageName)),
                thresholdMin = settings.thresholdFor(s.context),
                melt = melt,
            )
        }

        val pending = mutableListOf<Pair<FinishedNudges, Instant>>()
        scope.launch {
            ui.actions.collect { action ->
                dispatch(engine.onAction(clock.now(), action, liveInput()), source, pokes)
                if (action is NudgeUserAction.Stop || action is NudgeUserAction.StopNow) onStop(scope, mode)
            }
        }
        val tickMs = if (mode == NudgeMode.DEMO) DEMO_TICK_MS else TICK_MS
        while (scope.isActive) {
            val now = clock.now()
            dispatch(engine.tick(now, liveInput()), source, pokes)
            engine.drainFinished().forEach { pending += it to now }
            flush(pending, source, now)
            delay(tickMs)
        }
    }

    private suspend fun dispatch(commands: List<NudgeCommand>, source: DataSource, pokes: List<ReceivedPoke>) {
        for (command in commands) {
            if (command is NudgeCommand.PostBuddyMessage) {
                val m = command.message
                val delivered = notifier.postBuddyMessage(m)
                val poke = pokes.firstOrNull { it.id == m.pokeId }
                val sender = if (poke != null) Sender.Friend(poke.fromId, poke.fromNickname) else Sender.Buddy
                runCatching {
                    messageRepository.add(BuddyMessage(0, clock.now(), sender, m.text, m.copyId, null), source)
                    // 잔소리는 알림으로 실제 전해졌을 때만 '썼다'로 표시한다(알림이 막혀 있으면 다음에 다시).
                    if (delivered) m.pokeId?.let { pokeRepository.markUsed(it) }
                }.onFailure { log.w(TAG, "message save failed", it) }
            }
            ui.render(command)
        }
    }

    /** 끝난 세션이 저장되면 개입 기록을 붙인다. 10분 안에 못 찾으면 버린다(감지를 끈 경우 등). */
    private suspend fun flush(pending: MutableList<Pair<FinishedNudges, Instant>>, source: DataSource, now: Instant) {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val (finished, since) = iterator.next()
            val attached = runCatching {
                sessionRepository.attachNudges(finished.sessionStart, source, finished.events)
            }.getOrDefault(false)
            if (attached || Duration.between(since, now) > PENDING_TTL) iterator.remove()
        }
    }

    private fun onStop(scope: CoroutineScope, mode: NudgeMode) {
        val now = clock.now()
        pipeline.onNudgeStop(now)
        if (mode == NudgeMode.REAL) {
            // 오버레이 권한이 백그라운드 액티비티 시작 예외라 PendingIntent가 아니라 바로 연다(ONE_SHOT 8절 24번).
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_MAIN,
                    ).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { log.w(TAG, "home intent failed", it) }
        } else {
            // 시연: 홈으로 나가면 발표 화면이 사라지므로 앱에 남고, 대본으로 '홈 → 화면 끔'을 보내 세션을 끝낸다.
            demoSource.send(ForegroundEvent.AppForeground(DEMO_LAUNCHER, now))
            demoSource.send(ForegroundEvent.ScreenOff(now))
            scope.launch {
                delay(DEMO_PRAISE_MS)
                onDemoFinished?.invoke()
            }
        }
    }

    private fun fixedTexts(): FixedTexts {
        val buddy = context.getString(DesignR.string.buddy_name)
        return FixedTexts(
            buddyName = buddy,
            buddySender = context.getString(
                R.string.intervention_sender_buddy,
                buddy,
                context.getString(DesignR.string.app_name),
            ),
            friendSenderFormat = context.getString(R.string.intervention_sender_friend),
            contextLabels = ContextSlot.entries.associateWith { context.getString(contextLabelRes(it)) },
            reasonChips = listOf(
                ReasonChip("BORED", context.getString(R.string.intervention_chip_bored)),
                ReasonChip("TIRED", context.getString(R.string.intervention_chip_tired)),
                ReasonChip("PROCRASTINATING", context.getString(R.string.intervention_chip_procrastinating)),
                ReasonChip("FUN", context.getString(R.string.intervention_chip_fun)),
                ReasonChip("JUST", context.getString(R.string.intervention_chip_just)),
            ),
            typeToExtend = mapOf(
                1 to context.getString(R.string.intervention_type_to_extend_l1),
                2 to context.getString(R.string.intervention_type_to_extend_l2),
                3 to context.getString(R.string.intervention_type_to_extend_l3),
            ),
        )
    }

    /** 상수. */
    companion object {
        /** 시연 배속(개입 타이머만). */
        const val DEMO_SPEED = 60.0

        /** 시연에서 '홈'으로 쓰는 가짜 런처 패키지. */
        const val DEMO_LAUNCHER = "com.android.launcher3"

        private const val TAG = "NudgeCoordinator"
        private const val TICK_MS = 1_000L
        private const val DEMO_TICK_MS = 250L
        private const val DEMO_PRAISE_MS = 3_000L
        private val PENDING_TTL: Duration = Duration.ofMinutes(10)
    }
}

/** 추적 앱 패키지 → 화면 이름 리소스. */
internal fun appLabelRes(packageName: String?): Int = when {
    packageName == null -> R.string.intervention_app_other

    packageName.startsWith("com.google.android.youtube") -> R.string.intervention_app_youtube

    packageName.startsWith("com.instagram") -> R.string.intervention_app_instagram

    packageName.startsWith("com.ss.android.ugc") || packageName.startsWith("com.zhiliaoapp") ->
        R.string.intervention_app_tiktok

    else -> R.string.intervention_app_other
}

/** 맥락 이름 리소스(designsystem). */
internal fun contextLabelRes(slot: ContextSlot): Int = when (slot) {
    ContextSlot.WAKE_UP -> DesignR.string.designsystem_ctx_wake_up
    ContextSlot.COMMUTE -> DesignR.string.designsystem_ctx_commute
    ContextSlot.CLASS_WORK -> DesignR.string.designsystem_ctx_class_work
    ContextSlot.MEAL -> DesignR.string.designsystem_ctx_meal
    ContextSlot.BREAK -> DesignR.string.designsystem_ctx_break
    ContextSlot.PRE_SLEEP -> DesignR.string.designsystem_ctx_pre_sleep
    ContextSlot.LATE_NIGHT -> DesignR.string.designsystem_ctx_late_night
    ContextSlot.OTHER -> DesignR.string.designsystem_ctx_other
}
