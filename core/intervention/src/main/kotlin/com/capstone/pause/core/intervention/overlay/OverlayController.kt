package com.capstone.pause.core.intervention.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Velocity
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.notify.PauseNotifier
import com.capstone.pause.core.intervention.nudge.NudgeCommand
import com.capstone.pause.core.intervention.nudge.NudgeUi
import com.capstone.pause.core.intervention.nudge.NudgeUserAction
import com.capstone.pause.core.intervention.preview.NudgePreviewActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [NudgeUi] 구현. 다른 앱 위 표시 권한이 있으면 작은 오버레이 창(버블·카드)을,
 * 없으면 [NudgePreviewActivity]로 같은 카드를 앱 화면에 띄운다(버블은 생략).
 * 전체 화면 반투명 창으로 터치를 통과시키지 않는다(Android 12 신뢰하지 않는 창 규칙). 다른 앱이 오버레이를 숨겨도 크래시하지 않는다.
 *
 * 버블은 끌어서 옮기고, 놓으면 던진 속도대로 조금 더 미끄러지다 가장자리에 붙는다(스프링).
 * 끄는 동안 아래 가운데에 X 과녁이 뜨고, 거기에 놓으면 이 세션 동안 버블 대신 상단바 타이머([PauseNotifier.postBubbleTimer])가 뜬다.
 * 타이머가 끝나면 엔진이 평소처럼 카드를 띄운다(숨기기는 화면만 바꾸고 엔진은 모른다).
 */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: PauseNotifier,
    private val clock: AppClock,
    private val log: AppLog,
) : NudgeUi {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val _actions = MutableSharedFlow<NudgeUserAction>(extraBufferCapacity = ACTION_BUFFER)
    override val actions: SharedFlow<NudgeUserAction> = _actions.asSharedFlow()

    private val _current = MutableStateFlow<NudgeCommand?>(null)

    /** 미리보기 액티비티가 그릴 지금 카드·칭찬(오버레이 대신 쓸 때). */
    val current: StateFlow<NudgeCommand?> = _current.asStateFlow()

    private val bubbleState = mutableStateOf<NudgeCommand.ShowBubble?>(null)
    private val targetActive = mutableStateOf(false)
    private val targetVisible = mutableStateOf(false)
    private var bubble: OverlayWindow? = null
    private var target: OverlayWindow? = null
    private var settle: Job? = null

    /** 버블을 숨긴 세션(시작 시각). 같은 세션 동안은 버블 대신 상단바 타이머. */
    private var hiddenSession: Long? = null

    /** 마지막으로 올린 타이머 끝 시각(1초마다 알림을 다시 올리지 않게). */
    private var timerEndMs: Long? = null
    private var timerShown = false
    private var card: OverlayWindow? = null
    private var panel: OverlayWindow? = null
    private val closePanel = Runnable { removePanel() }

    override fun render(command: NudgeCommand) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { render(command) }
            return
        }
        when (command) {
            is NudgeCommand.ShowBubble -> showBubble(command)

            is NudgeCommand.ShowCard, is NudgeCommand.ShowPraise -> showCard(command)

            is NudgeCommand.PostBuddyMessage -> Unit

            // 알림은 서비스의 PauseNotifier가 보낸다.
            NudgeCommand.Hide -> hideAll()
        }
    }

    /** 미리보기 액티비티 등 화면에서 받은 동작을 흘려보낸다. 카드는 바로 내린다. */
    fun emit(action: NudgeUserAction) {
        // 먼저 카드를 내리고 나서 보낸다. 받는 쪽(Main.immediate)이 tryEmit 안에서 바로 칭찬을 그릴 수 있어서,
        // 순서가 반대면 방금 띄운 칭찬 카드를 지워 버린다.
        if (action is NudgeUserAction.BubbleTapped) {
            togglePanel()
        } else if (action is NudgeUserAction.BubbleDismissed) {
            hideBubble()
        } else {
            removePanel()
            removeCard()
            _current.value = null
        }
        _actions.tryEmit(action)
    }

    private fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    private fun showBubble(command: NudgeCommand.ShowBubble) {
        bubbleState.value = command
        if (hiddenSession != null && hiddenSession != command.sessionStartMs) {
            // 새 세션: 다시 버블로.
            hiddenSession = null
            clearTimer()
        }
        if (hiddenSession != null) {
            showTimer(command)
            return
        }
        if (!canDraw() || bubble != null) return
        // 과녁 창을 버블보다 먼저 붙여 둔다(나중에 붙이면 버블 위로 올라온다). 끄는 동안만 보인다.
        addTarget()
        val size = px(OverlayDimens.bubbleWithRing.value)
        val params = baseParams(focusable = false).apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            // 화면 가장자리에 반쯤 숨어 빼꼼.
            x = screenWidth() - size / 2
            y = (screenHeight() * BUBBLE_Y_RATIO).roundToInt()
        }
        val view = composeView {
            val state = bubbleState.value ?: return@composeView
            val scope = rememberCoroutineScope()
            FloatingBuddy(
                state = state,
                onTap = { emit(NudgeUserAction.BubbleTapped) },
                modifier = Modifier.pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            settle?.cancel()
                            removePanel()
                            tracker.resetTracking()
                            showTarget()
                        },
                        onDragEnd = { release(params, size, tracker.calculateVelocity(), scope) },
                        onDragCancel = { release(params, size, Velocity.Zero, scope) },
                        onDrag = { change, drag ->
                            change.consume()
                            params.x += drag.x.roundToInt()
                            params.y += drag.y.roundToInt()
                            // 창이 손가락을 따라 움직이므로 속도는 창의 화면 위치로 잰다.
                            tracker.addPosition(change.uptimeMillis, Offset(params.x.toFloat(), params.y.toFloat()))
                            targetActive.value = overTarget(params, size)
                            bubble?.let { update(it.view, params) }
                        },
                    )
                },
            )
        }
        bubble = add(view, params)
    }

    /**
     * 손을 뗐을 때: 과녁 위면 숨기고, 아니면 던진 속도만큼 조금 더 간 자리를 기준으로 가까운 가장자리에 스프링으로 붙인다.
     * 스프링에 놓을 때 속도를 그대로 넘겨서 관성이 이어진다.
     */
    private fun release(params: WindowManager.LayoutParams, size: Int, velocity: Velocity, scope: CoroutineScope) {
        val hide = overTarget(params, size)
        hideTarget()
        if (hide) {
            // 버블 자기 터치 처리 안에서 창을 떼지 않게 다음 차례로 미룬다.
            mainHandler.post { emit(NudgeUserAction.BubbleDismissed) }
            return
        }
        val projectedCenter = params.x + size / 2 + velocity.x * FLING_PROJECTION_SEC
        val edgeX = if (projectedCenter < screenWidth() / 2f) -size / 2 else screenWidth() - size / 2
        val margin = px(OverlayDimens.bubbleEdgeMargin.value)
        val edgeY = (params.y + velocity.y * FLING_PROJECTION_SEC).roundToInt()
            .coerceIn(margin, (screenHeight() - size - margin).coerceAtLeast(margin))
        val start = Offset(params.x.toFloat(), params.y.toFloat())
        settle = scope.launch {
            val position = Animatable(start, Offset.VectorConverter)
            position.animateTo(
                targetValue = Offset(edgeX.toFloat(), edgeY.toFloat()),
                animationSpec = spring(dampingRatio = SETTLE_DAMPING, stiffness = Spring.StiffnessLow),
                initialVelocity = Offset(velocity.x, velocity.y),
            ) {
                params.x = value.x.roundToInt()
                params.y = value.y.roundToInt()
                bubble?.let { update(it.view, params) }
            }
        }
    }

    /** 버블 중심이 과녁 중심 가까이 있는지. 둘 다 화면 왼쪽 위 기준 같은 좌표계. */
    private fun overTarget(params: WindowManager.LayoutParams, size: Int): Boolean {
        val (tx, ty) = targetCenter()
        val dx = params.x + size / 2f - tx
        val dy = params.y + size / 2f - ty
        return hypot(abs(dx), abs(dy)) <= px(OverlayDimens.dismissRadius.value)
    }

    private fun targetCenter(): Pair<Float, Float> {
        val box = px(OverlayDimens.dismissTargetActive.value)
        val bottom = px(OverlayDimens.dismissTargetBottom.value)
        return screenWidth() / 2f to (screenHeight() - bottom - box / 2f)
    }

    private fun showTarget() {
        targetActive.value = false
        targetVisible.value = true
    }

    /** 아래 가운데 X 과녁 창(비어 있다가 끄는 동안만 그린다). 터치는 받지 않는다. */
    private fun addTarget() {
        if (target != null) return
        val box = px(OverlayDimens.dismissTargetActive.value)
        val (cx, cy) = targetCenter()
        val params = baseParams(focusable = false).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = (cx - box / 2f).roundToInt()
            y = (cy - box / 2f).roundToInt()
        }
        val view = composeView {
            if (targetVisible.value) RiseIn { DismissTarget(active = targetActive.value) }
        }
        target = add(view, params)
    }

    /** 끄기가 끝나면 과녁만 감춘다(창은 버블과 함께 내린다). */
    private fun hideTarget() {
        targetActive.value = false
        targetVisible.value = false
    }

    private fun removeTarget() {
        hideTarget()
        target?.let(::remove)
        target = null
    }

    /** 버블을 숨기고 이 세션 동안 상단바 타이머로. 버블 패널의 '숨기기'도 여기로 온다. */
    private fun hideBubble() {
        val state = bubbleState.value ?: return
        settle?.cancel()
        removeTarget()
        removePanel()
        bubble?.let(::remove)
        bubble = null
        hiddenSession = state.sessionStartMs
        showTimer(state)
    }

    private fun showTimer(state: NudgeCommand.ShowBubble) {
        val end = state.realMsToNext?.let { clock.now().toEpochMilli() + it }
        val previous = timerEndMs
        // 끝 시각이 거의 그대로면 다시 올리지 않는다(1초마다 알림을 갱신하지 않게). 연장·오늘은 그만이면 바뀐다.
        val same = timerShown && (
            (end == null && previous == null) ||
                (end != null && previous != null && abs(end - previous) < TIMER_SLACK_MS)
            )
        if (same) return
        timerEndMs = end
        timerShown = true
        notifier.postBubbleTimer(end, state.sessionStartMs, state.appLabel)
    }

    private fun clearTimer() {
        if (timerShown) notifier.clearBubbleTimer()
        timerShown = false
        timerEndMs = null
    }

    /** 버블을 누르면 아래에 작은 패널(지금 몇 분째·다음 카드까지·그만 볼래). 다시 누르거나 몇 초 지나면 닫힌다. */
    private fun togglePanel() {
        if (panel != null) {
            removePanel()
            return
        }
        if (!canDraw() || bubbleState.value == null || card != null) return
        val params = baseParams(focusable = false).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
        }
        val view = composeView {
            val state = bubbleState.value ?: return@composeView
            BubblePanel(
                state = state,
                onStop = { emit(NudgeUserAction.StopNow) },
                onClose = { removePanel() },
                onHide = { emit(NudgeUserAction.BubbleDismissed) },
                modifier = Modifier.padding(OverlayDimens.cardOuterPadding),
            )
        }
        panel = add(view, params)
        mainHandler.postDelayed(closePanel, PANEL_MS)
    }

    private fun removePanel() {
        mainHandler.removeCallbacks(closePanel)
        panel?.let(::remove)
        panel = null
    }

    private fun showCard(command: NudgeCommand) {
        _current.value = command
        // 타이머가 끝나 카드가 떴다: 상단바 타이머는 내린다(연장하면 다음 버블 명령에서 다시 센다).
        clearTimer()
        removePanel()
        removeCard()
        if (!canDraw()) {
            launchPreview()
            return
        }
        val needsInput = command is NudgeCommand.ShowCard && command.card.level >= INPUT_LEVEL
        val params = baseParams(focusable = needsInput).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.BOTTOM
            if (needsInput) softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val view = composeView {
            val padding = Modifier.padding(OverlayDimens.cardOuterPadding)
            RiseIn {
                CardContent(command, padding)
            }
        }
        card = add(view, params)
        if (command is NudgeCommand.ShowPraise) {
            mainHandler.postDelayed({
                if (_current.value == command) {
                    removeCard()
                    _current.value = null
                }
            }, PRAISE_MS)
        }
    }

    @androidx.compose.runtime.Composable
    private fun CardContent(command: NudgeCommand, padding: Modifier) {
        when (command) {
            // 오버레이 창은 화면 높이까지만 커지므로, 큰 글자·키보드에서도 아래 버튼에 닿게 카드만 스크롤한다.
            // (NudgePreviewActivity는 화면 전체가 이미 스크롤된다 — 겹쳐 두면 무한 높이 오류.)
            is NudgeCommand.ShowCard -> NudgeCard(
                command.card,
                onAction = ::emit,
                modifier = padding.verticalScroll(rememberScrollState()),
            )

            is NudgeCommand.ShowPraise -> PraiseCard(command.text, onClose = { removeCard() }, modifier = padding)

            else -> Unit
        }
    }

    private fun launchPreview() {
        val intent = Intent(context, NudgePreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { context.startActivity(intent) }
            .onFailure { log.w(TAG, "미리보기 액티비티를 열 수 없음(백그라운드 시작 제한)", it) }
    }

    private fun hideAll() {
        bubbleState.value = null
        settle?.cancel()
        bubble?.let(::remove)
        bubble = null
        removeTarget()
        removePanel()
        // 숏폼을 떠났거나 세션이 끝났다: 타이머를 내린다. 같은 세션으로 돌아오면(60초 안) 숨긴 상태 그대로 다시 올린다.
        clearTimer()
        // '그만' 뒤 세션이 끝나며 오는 Hide가 칭찬을 바로 지우지 않게. 칭찬은 PRAISE_MS 뒤 스스로 내려간다.
        if (_current.value is NudgeCommand.ShowPraise) return
        removeCard()
        _current.value = null
    }

    private fun removeCard() {
        card?.let(::remove)
        card = null
    }

    private fun baseParams(focusable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    )

    private fun composeView(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { PauseTheme { content() } }
        }

    private fun add(view: ComposeView, params: WindowManager.LayoutParams): OverlayWindow? {
        val owner = OverlayLifecycleOwner()
        owner.attach(view)
        return runCatching {
            windowManager.addView(view, params)
            OverlayWindow(view, owner)
        }.onFailure {
            owner.destroy()
            log.w(TAG, "오버레이 창을 붙이지 못함", it)
        }.getOrNull()
    }

    private fun update(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun remove(window: OverlayWindow) {
        runCatching { windowManager.removeViewImmediate(window.view) }
        window.owner.destroy()
    }

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    private fun px(dp: Float): Int = (dp * context.resources.displayMetrics.density).roundToInt()

    private class OverlayWindow(val view: View, val owner: OverlayLifecycleOwner)

    private companion object {
        const val TAG = "OverlayController"
        const val ACTION_BUFFER = 8

        // 1단계부터 문장을 따라 써야 해서(ADR-0010) 모든 카드 창이 키보드를 받는다.
        const val INPUT_LEVEL = 1
        const val BUBBLE_Y_RATIO = 0.55f
        const val PRAISE_MS = 3_000L
        const val PANEL_MS = 8_000L

        /** 놓은 속도로 이만큼(초) 더 간 자리를 보고 붙을 가장자리를 고른다. */
        const val FLING_PROJECTION_SEC = 0.18f

        /** 붙을 때 살짝 튕기는 정도(1 = 튕김 없음). */
        const val SETTLE_DAMPING = 0.72f

        /** 타이머 끝 시각이 이만큼 안에서 바뀌면 알림을 다시 올리지 않는다. */
        const val TIMER_SLACK_MS = 2_000L
    }
}
