package com.capstone.pause.core.sensing.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.capstone.pause.core.data.profile.ConsentItem
import com.capstone.pause.core.data.profile.ConsentRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 정밀 모드 접근성 서비스(ADR-0004 보완). 하는 일은 셋뿐이다:
 * 1) 추적 앱 창이 숏폼 화면인지 뷰 ID로 판단, 2) 넘김 횟수, 3) 누름 횟수.
 * 화면 글자·영상 제목·메시지는 읽지 않고(`text`·`contentDescription`을 쓰지 않음) 저장·로그에도 남기지 않는다.
 * 다른 앱을 누르거나 닫거나 넘기지 않는다: `performAction`·`dispatchGesture`·`performGlobalAction`을 쓰지 않는다.
 * 동의가 없거나 철회되면 스스로 꺼진다.
 */
@AndroidEntryPoint
class PauseAccessibilityService : AccessibilityService() {
    @Inject
    lateinit var source: AccessibilitySource

    @Inject
    lateinit var consents: ConsentRepository

    private var scope: CoroutineScope? = null
    private var lastJudgeMs = 0L
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val s = MainScope().also { scope = it }
        s.launch {
            consents.consents
                .map { it[ConsentItem.ACCESSIBILITY]?.granted == true }
                .distinctUntilChanged()
                .collect { ok ->
                    if (ok) {
                        source.onConnected()
                    } else {
                        // 동의 없이 켜졌거나 철회됨: 아무것도 세지 않고 꺼진다.
                        source.onDisconnected()
                        disableSelf()
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> judge(pkg, force = true)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> judge(pkg, force = false)

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> source.onScroll(pkg)

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val rule = source.rules.ruleFor(pkg)
                val id = e.source?.viewIdResourceName?.substringAfter(ID_SEPARATOR)
                source.onClick(pkg, like = rule != null && id != null && id in rule.likeIds)
            }
        }
    }

    /** 창 판단. 내용 바뀜 이벤트는 자주 오므로 [JUDGE_GAP_MS]에 한 번만. */
    private fun judge(pkg: String, force: Boolean) {
        val rule = source.rules.ruleFor(pkg) ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && pkg == lastPackage && now - lastJudgeMs < JUDGE_GAP_MS) return
        lastJudgeMs = now
        lastPackage = pkg
        val root = rootInActiveWindow?.takeIf { it.packageName?.toString() == pkg }
        source.onSurface(pkg, SurfaceRules.judge(rule, root?.let(::viewIds).orEmpty()))
    }

    /** 창 안 뷰 ID 끝부분만 모은다(글자는 읽지 않는다). 너무 큰 창은 [MAX_NODES]까지만. */
    private fun viewIds(root: AccessibilityNodeInfo): Set<String> {
        val ids = HashSet<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (queue.isNotEmpty() && seen < MAX_NODES) {
            val node = queue.removeFirst()
            seen++
            node.viewIdResourceName?.let { ids += it.substringAfter(ID_SEPARATOR) }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return ids
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        source.onDisconnected()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private companion object {
        const val ID_SEPARATOR = ":id/"
        const val JUDGE_GAP_MS = 700L
        const val MAX_NODES = 400
    }
}
