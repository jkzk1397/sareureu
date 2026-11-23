package com.capstone.pause.core.sensing.accessibility

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.Surface
import com.capstone.pause.core.sensing.ForegroundEvent
import com.capstone.pause.core.sensing.context.ContextResult
import com.capstone.pause.core.sensing.session.SessionTracker
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** 정밀 모드의 순수 부분: 규칙 읽기·판별, 넘김 세기, 사용 기록 이벤트와 합치기(ADR-0004 보완). */
class PreciseModeTest {
    private val yt = "com.google.android.youtube"
    private val rules = SurfaceRules.parse(File("src/main/assets/surface_rules.json").readText(Charsets.UTF_8))
    private val t0 = Instant.parse("2026-11-16T12:00:00Z")

    @Test
    fun bundledRulesCoverYoutubeInstagramTiktok() {
        assertEquals(Surface.SHORTS, rules.surfaceOf(yt))
        assertEquals(Surface.REELS, rules.surfaceOf("com.instagram.android"))
        assertEquals(Surface.TIKTOK_FEED, rules.surfaceOf("com.zhiliaoapp.musically"))
        assertEquals(Surface.APP_UNKNOWN, rules.surfaceOf("com.example"))
        // 뷰 ID 하나에 의존하지 않는다(ADR-0004).
        assertTrue(rules.ruleFor(yt)!!.markers.size >= 2)
    }

    @Test
    fun judgeShortsOtherScreenAndUnreadable() {
        val rule = rules.ruleFor(yt)!!
        assertEquals(true, SurfaceRules.judge(rule, setOf("toolbar", "reel_recycler")))
        assertEquals(false, SurfaceRules.judge(rule, setOf("toolbar", "results")))
        assertNull(SurfaceRules.judge(rule, emptySet()), "창을 못 읽으면 앱 단위로")
        assertEquals(true, SurfaceRules.judge(rules.ruleFor("com.zhiliaoapp.musically")!!, emptySet()))
    }

    @Test
    fun brokenRulesFallBackToAppLevel() {
        val broken = SurfaceRules.parse("{ not json")
        assertTrue(broken.covered.isEmpty())
        assertEquals(Surface.APP_UNKNOWN, broken.surfaceOf(yt))
    }

    @Test
    fun usageEventsForCoveredAppsAreReplaced() {
        val covered = rules.covered
        assertFalse(PreciseMerge.keepUsageEvent(ForegroundEvent.AppForeground(yt, t0), covered))
        assertTrue(PreciseMerge.keepUsageEvent(ForegroundEvent.AppForeground("com.instagram.lite", t0), covered))
        assertTrue(PreciseMerge.keepUsageEvent(ForegroundEvent.AppForeground("com.android.launcher3", t0), covered))
        assertTrue(PreciseMerge.keepUsageEvent(ForegroundEvent.ScreenOff(t0), covered))
    }

    @Test
    fun swipesAreDebouncedAndCountedWithMedianInterval() {
        val c = InteractionCounter()
        assertTrue(c.onScroll(t0))
        assertFalse(c.onScroll(t0.plusMillis(200)), "한 번 넘길 때 오는 스크롤 이벤트 여러 개는 한 번")
        assertTrue(c.onScroll(t0.plusSeconds(10)))
        assertTrue(c.onScroll(t0.plusSeconds(30)))
        c.onClick(t0.plusSeconds(31), like = true)
        c.onClick(t0.plusSeconds(32), like = false)
        val s = c.between(t0, t0.plusSeconds(60))
        assertEquals(3, s.swipes)
        assertEquals(15.0, s.medianSwipeIntervalSec)
        assertEquals(1, s.likes)
        assertEquals(1, s.otherClicks)
        assertEquals(0, c.between(t0.plusSeconds(61), t0.plusSeconds(90)).swipes)
    }

    @Test
    fun otherScreenOfYoutubeIsNotShortformSoSessionTrackerSplitsIt() {
        // 세션 만들기 코드는 그대로: 숏폼 화면 → 같은 앱 다른 화면 70초 → 세션 끝.
        val tracker = SessionTracker(
            isShortform = { it == yt },
            kinds = { AppKind.OTHER },
            classify = { _, _ -> ContextResult(ContextSlot.BREAK, Confidence.LOW) },
            zone = ZoneId.of("Asia/Seoul"),
            surfaceOf = rules::surfaceOf,
        )
        tracker.onEvent(ForegroundEvent.ScreenOn(t0))
        tracker.onEvent(ForegroundEvent.AppForeground(yt, t0))
        tracker.onEvent(ForegroundEvent.AppForeground(PreciseMerge.otherScreen(yt), t0.plusSeconds(300)))
        val finished = tracker.onTick(t0.plusSeconds(380))
        val session = finished.single()
        assertEquals(t0.plusSeconds(300), session.end)
        assertEquals(Surface.SHORTS, session.segments.single().surface)
    }

    @Test
    fun unreadableWindowKeepsLastJudgmentAndNeverAssumesShortform() {
        // 창을 읽었으면 그 판단 그대로.
        assertTrue(PreciseMerge.resolve(true, previous = null))
        assertFalse(PreciseMerge.resolve(false, previous = true))
        // 못 읽은 순간: 같은 앱의 마지막 판단을 이어 쓴다.
        assertTrue(PreciseMerge.resolve(null, previous = true))
        assertFalse(PreciseMerge.resolve(null, previous = false))
        // 한 번도 못 읽었으면 앱을 켠 것만으로 숏폼으로 치지 않는다.
        assertFalse(PreciseMerge.resolve(null, previous = null))
    }
}
