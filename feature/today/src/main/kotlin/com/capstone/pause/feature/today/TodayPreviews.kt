package com.capstone.pause.feature.today

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capstone.pause.core.data.insight.LiveSummary
import com.capstone.pause.core.data.insight.RiskSummary
import com.capstone.pause.core.data.insight.TodaySummary
import com.capstone.pause.core.data.plan.ReceivedPoke
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.IfThenPlan
import com.capstone.pause.core.model.MeltStage
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 미리보기용 값(목업 01 수치). */
private fun previewState(live: Boolean): TodayUiState.Ready {
    val hours = List(24) { h ->
        when (h) {
            8 -> mapOf(ContextSlot.COMMUTE to 14)
            10 -> mapOf(ContextSlot.CLASS_WORK to 9)
            12 -> mapOf(ContextSlot.MEAL to 15)
            15 -> mapOf(ContextSlot.BREAK to 18)
            18 -> mapOf(ContextSlot.COMMUTE to 8)
            20 -> mapOf(ContextSlot.BREAK to 8)
            23 -> if (live) mapOf(ContextSlot.PRE_SLEEP to 12) else emptyMap()
            else -> emptyMap()
        }
    }
    val total = hours.sumOf { it.values.sum() }
    val summary = TodaySummary(
        now = ZonedDateTime.of(2026, 11, 3, if (live) 23 else 21, if (live) 8 else 40, 0, 0, ZoneId.of("Asia/Seoul")),
        isDemo = true,
        minutesByHour = hours,
        totalMinutes = total,
        byContext = mapOf(
            ContextSlot.COMMUTE to 22,
            ContextSlot.CLASS_WORK to 9,
            ContextSlot.MEAL to 15,
            ContextSlot.BREAK to 26,
        ) + if (live) mapOf(ContextSlot.PRE_SLEEP to 12) else emptyMap(),
        yesterdayMinutes = 95,
        baselineMinutes = 124.6,
        meltStage = MeltStage.SOFT,
        riskHours = listOf(22..23),
        topRisk = RiskSummary(DayOfWeek.TUESDAY, 22, 38),
        nudgedSessions = 3,
        stoppedSessions = 2,
        live = if (live) LiveSummary("com.google.android.youtube", ContextSlot.PRE_SLEEP, 12, 15) else null,
    )
    return TodayUiState.Ready(
        summary = summary,
        greeting = TodayGreeting.Stopped(3, 2),
        poke = ReceivedPoke("p", "minji", "민지", "", Instant.EPOCH, Instant.EPOCH),
        plan = IfThenPlan(1, ContextSlot.PRE_SLEEP, "누우면", "폰은 책상 충전기에", Instant.EPOCH, true),
        showMelt = true,
    )
}

@Preview(name = "light", heightDp = 1400)
@Preview(name = "dark", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayScreenPreview() {
    PauseTheme { TodayScreen(uiState = previewState(live = false), onAction = {}) }
}

@Preview(name = "light live", heightDp = 1400)
@Preview(name = "dark live", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayScreenLivePreview() {
    PauseTheme { TodayScreen(uiState = previewState(live = true), onAction = {}) }
}
