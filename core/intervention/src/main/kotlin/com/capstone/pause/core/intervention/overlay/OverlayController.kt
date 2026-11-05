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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.capstone.pause.core.common.AppLog
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.intervention.nudge.NudgeCommand
import com.capstone.pause.core.intervention.nudge.NudgeUi
import com.capstone.pause.core.intervention.nudge.NudgeUserAction
import com.capstone.pause.core.intervention.preview.NudgePreviewActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [NudgeUi] 구현. 다른 앱 위 표시 권한이 있으면 작은 오버레이 창(버블·카드)을,
 * 없으면 [NudgePreviewActivity]로 같은 카드를 앱 화면에 띄운다(버블은 생략).
 * 전체 화면 반투명 창으로 터치를 통과시키지 않는다(Android 12 신뢰하지 않는 창 규칙). 다른 앱이 오버레이를 숨겨도 크래시하지 않는다.
 */
@Singleton
class OverlayController @Inject constructor(
    @ApplicationContext private val context: Context,
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
    private var bubble: OverlayWindow? = null
    private var card: OverlayWindow? = null

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
        if (action !is NudgeUserAction.BubbleTapped) {
            removeCard()
            _current.value = null
        }
        _actions.tryEmit(action)
    }

    private fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    private fun showBubble(command: NudgeCommand.ShowBubble) {
        bubbleState.value = command
        if (!canDraw() || bubble != null) return
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
            FloatingBuddy(
                state = state,
                onTap = { emit(NudgeUserAction.BubbleTapped) },
                modifier = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { snapToEdge(params, size) },
                        onDrag = { change, drag ->
                            change.consume()
                            params.x += drag.x.roundToInt()
                            params.y += drag.y.roundToInt()
                            bubble?.let { update(it.view, params) }
                        },
                    )
                },
            )
        }
        bubble = add(view, params)
    }

    private fun snapToEdge(params: WindowManager.LayoutParams, size: Int) {
        val center = params.x + size / 2
        params.x = if (center < screenWidth() / 2) -size / 2 else screenWidth() - size / 2
        bubble?.let { update(it.view, params) }
    }

    private fun showCard(command: NudgeCommand) {
        _current.value = command
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
            when (command) {
                is NudgeCommand.ShowCard -> NudgeCard(command.card, onAction = ::emit, modifier = padding)
                is NudgeCommand.ShowPraise -> PraiseCard(command.text, onClose = { removeCard() }, modifier = padding)
                else -> Unit
            }
        }
        card = add(view, params)
        if (command is NudgeCommand.ShowPraise) {
            mainHandler.postDelayed({
                if (_current.value ==
                    command
                ) {
                    removeCard()
                }
            }, PRAISE_MS)
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
        bubble?.let(::remove)
        bubble = null
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
        const val INPUT_LEVEL = 3
        const val BUBBLE_Y_RATIO = 0.55f
        const val PRAISE_MS = 3_000L
    }
}
