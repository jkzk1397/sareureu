package com.capstone.pause.core.sensing.path

import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.EntryPoint
import com.capstone.pause.core.model.ExitPoint
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

internal const val YOUTUBE = "com.google.android.youtube"
internal const val INSTAGRAM = "com.instagram.android"
internal const val KAKAO = "com.kakao.talk"
internal const val LAUNCHER = "com.sec.android.app.launcher"
internal const val CHROME = "com.android.chrome"

internal val testKinds = AppKindLookup { pkg ->
    when (pkg) {
        YOUTUBE, INSTAGRAM -> AppKind.SHORTFORM
        KAKAO -> AppKind.MESSENGER
        LAUNCHER -> AppKind.LAUNCHER
        CHROME -> AppKind.BROWSER
        else -> AppKind.OTHER
    }
}

internal val isShortformTest: (String) -> Boolean = { it == YOUTUBE || it == INSTAGRAM }

class PathResolverTest {
    private val t0 = Instant.parse("2026-11-03T13:00:00Z")

    private fun entry(prev: String?, unlockSecondsBefore: Long? = null, current: String = YOUTUBE) = PathResolver.entry(
        start = t0,
        currentPackage = current,
        lastUnlockAt = unlockSecondsBefore?.let { t0.minusSeconds(it) },
        previousPackage = prev,
        isShortform = isShortformTest,
        kinds = testKinds,
    )

    @Test
    fun entryCases() {
        assertEquals(EntryPoint.Unlock, entry(LAUNCHER, unlockSecondsBefore = 10))
        assertEquals(EntryPoint.Home, entry(LAUNCHER, unlockSecondsBefore = 11))
        assertEquals(EntryPoint.FromApp(AppKind.MESSENGER), entry(KAKAO))
        assertEquals(EntryPoint.FromShortform(INSTAGRAM), entry(INSTAGRAM))
        assertEquals(EntryPoint.Unknown, entry(null))
        assertEquals(EntryPoint.FromApp(AppKind.BROWSER), entry(CHROME, unlockSecondsBefore = 600))
    }

    private fun exit(next: String?, screenOffAfter: Long? = null, stopSecondsBefore: Long? = null) = PathResolver.exit(
        leftAt = t0,
        nextPackage = next,
        screenOffAt = screenOffAfter?.let { t0.plusSeconds(it) },
        lastNudgeStopAt = stopSecondsBefore?.let { t0.minusSeconds(it) },
        kinds = testKinds,
    )

    @Test
    fun exitCases() {
        assertEquals(ExitPoint.ScreenOff, exit(null, screenOffAfter = 0))
        assertEquals(ExitPoint.ScreenOff, exit(KAKAO, screenOffAfter = 10))
        assertEquals(ExitPoint.ToApp(AppKind.MESSENGER), exit(KAKAO, screenOffAfter = 11))
        assertEquals(ExitPoint.Home, exit(LAUNCHER))
        assertEquals(ExitPoint.ToApp(AppKind.MESSENGER), exit(KAKAO))
        assertEquals(ExitPoint.AfterNudgeStop, exit(LAUNCHER, stopSecondsBefore = 60))
        assertEquals(ExitPoint.Home, exit(LAUNCHER, stopSecondsBefore = 61))
        assertEquals(ExitPoint.Unknown, exit(null))
    }

    @Test
    fun curatedTableBeatsLauncherAndCategory() {
        val curated = mapOf(KAKAO to AppKind.MESSENGER)
        assertEquals(AppKind.MESSENGER, AppKindRules.resolve(KAKAO, curated, homePackage = KAKAO, category = null))
        assertEquals(AppKind.LAUNCHER, AppKindRules.resolve("x.home", curated, homePackage = "x.home", category = null))
        assertEquals(AppKind.OTHER, AppKindRules.resolve("x.unknown", curated, homePackage = null, category = null))
    }
}
