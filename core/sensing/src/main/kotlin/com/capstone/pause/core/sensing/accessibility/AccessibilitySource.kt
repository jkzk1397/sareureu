package com.capstone.pause.core.sensing.accessibility

import android.content.Context
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import com.capstone.pause.core.model.InteractionStats
import com.capstone.pause.core.model.Surface
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.ForegroundSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * 정밀 모드 감지 소스(ADR-0004). 접근성 서비스([PauseAccessibilityService])가 넣어 주는 판단을 이벤트로 바꾼다.
 * 규칙이 있는 앱은 숏폼 화면이면 그 앱의 [ForegroundEvent.AppForeground], 같은 앱의 다른 화면이면
 * 추적하지 않는 가짜 패키지([PreciseMerge.otherScreen])를 보낸다. 그래서 세션 만들기(`SessionTracker`)는 그대로 쓴다.
 * 화면 켜짐·꺼짐·잠금 해제와 규칙이 없는 앱은 사용 기록 소스의 이벤트를 그대로 쓴다([preciseEvents]).
 */
@Singleton
class AccessibilitySource @Inject constructor(
    @ApplicationContext private val context: Context,
    consentRepository: ConsentRepository,
    private val clock: AppClock,
    private val log: AppLog,
) : ForegroundSource {
    private val flow = MutableSharedFlow<ForegroundEvent>(extraBufferCapacity = BUFFER)
    private val connected = MutableStateFlow(false)
    private val counter = InteractionCounter()

    @Volatile
    private var shortformPackage: String? = null

    /** 앱마다 마지막으로 읽은 판단(창을 못 읽은 순간에 이어 쓴다). */
    private val lastJudged = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** 판별 규칙. 읽지 못하면 비어 있고, 그러면 모든 앱을 앱 단위로 센다. */
    val rules: SurfaceRules by lazy {
        runCatching { context.assets.open(RULES_ASSET).bufferedReader().use { SurfaceRules.parse(it.readText()) } }
            .onFailure { log.w(TAG, "surface rules unreadable", it) }
            .getOrDefault(SurfaceRules.EMPTY)
    }

    /** 정밀 모드 동의가 있는지(마지막 기록이 '동의'). */
    val consented: Flow<Boolean> = consentRepository.consents
        .map { it[ConsentItem.ACCESSIBILITY]?.granted == true }
        .distinctUntilChanged()

    /** 정밀 모드가 실제로 쓰이는지: 동의 + 시스템에서 서비스 연결. 둘 중 하나라도 아니면 앱 단위. */
    val active: Flow<Boolean> = combine(consented, connected) { c, on -> c && on }.distinctUntilChanged()

    override val events: Flow<ForegroundEvent> = flow.asSharedFlow()

    /** 사용 기록 이벤트와 합친 정밀 모드 이벤트. 규칙이 있는 앱의 앱 단위 이벤트만 접근성 판단으로 바꾼다. */
    fun preciseEvents(usage: ForegroundSource): Flow<ForegroundEvent> = merge(
        usage.events.filter { PreciseMerge.keepUsageEvent(it, rules.covered) },
        events,
    )

    /** 정밀 모드에서 [packageName] 세션 구간의 화면 종류. */
    fun surfaceOf(packageName: String): Surface = rules.surfaceOf(packageName)

    /** [start]~[end]의 넘김·누름 횟수. */
    fun interactionBetween(start: Instant, end: Instant): InteractionStats = counter.between(start, end)

    internal fun onConnected() {
        connected.value = true
    }

    internal fun onDisconnected() {
        connected.value = false
        shortformPackage = null
        lastJudged.clear()
    }

    /**
     * 창 판단 하나. [shortform]이 null이면(창을 못 읽음) 그 앱의 마지막 판단을 이어 쓰고,
     * 아직 판단한 적이 없으면 세지 않는다(앱을 켠 것만으로 숏폼으로 치지 않는다).
     */
    internal fun onSurface(packageName: String, shortform: Boolean?) {
        val counted = PreciseMerge.resolve(shortform, lastJudged[packageName])
        if (shortform != null) lastJudged[packageName] = shortform
        shortformPackage = if (counted) packageName else null
        val pkg = if (counted) packageName else PreciseMerge.otherScreen(packageName)
        flow.tryEmit(ForegroundEvent.AppForeground(pkg, clock.now()))
    }

    internal fun onScroll(packageName: String) {
        if (shortformPackage == packageName) counter.onScroll(clock.now())
    }

    internal fun onClick(packageName: String, like: Boolean) {
        if (shortformPackage == packageName) counter.onClick(clock.now(), like)
    }

    private companion object {
        const val TAG = "AccessibilitySource"
        const val RULES_ASSET = "surface_rules.json"
        const val BUFFER = 64
    }
}

/** 정밀 모드 이벤트 합치기 규칙(순수). */
object PreciseMerge {
    private const val OTHER_SUFFIX = "/other"

    /** 같은 앱의 숏폼이 아닌 화면을 나타내는 가짜 패키지. 추적 앱 목록에 없으니 세션이 아니다. */
    fun otherScreen(packageName: String): String = packageName + OTHER_SUFFIX

    /** 이번 판단([judged], null = 창을 못 읽음)과 그 앱의 마지막 판단([previous])으로 숏폼 화면인지 정한다. 모르면 아니다. */
    fun resolve(judged: Boolean?, previous: Boolean?): Boolean = judged ?: previous ?: false

    /** 사용 기록 이벤트 중 그대로 쓸 것: 규칙이 있는 앱의 앱 단위 '앞에 옴'만 뺀다. */
    fun keepUsageEvent(event: ForegroundEvent, covered: Set<String>): Boolean =
        !(event is ForegroundEvent.AppForeground && event.packageName in covered)
}
