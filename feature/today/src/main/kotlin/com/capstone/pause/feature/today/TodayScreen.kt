package com.capstone.pause.feature.today

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.chart.DayClock
import com.capstone.pause.core.designsystem.chart.DayClockData
import com.capstone.pause.core.designsystem.component.BuddyBubble
import com.capstone.pause.core.designsystem.component.ChangeDirection
import com.capstone.pause.core.designsystem.component.ChangeText
import com.capstone.pause.core.designsystem.component.ContextLegendRow
import com.capstone.pause.core.designsystem.component.DemoBadge
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.Wordmark
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.designsystem.theme.isLargeFont
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.MeltStage

/** 오늘 탭 진입점. '지금 그만할래'는 홈 화면 인텐트(다른 앱을 조작하지 않는다). */
@Composable
fun TodayRoute(onOpenMessages: () -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    TodayScreen(uiState = uiState, onAction = { action ->
        when (action) {
            TodayAction.OpenMessages -> onOpenMessages()

            TodayAction.StartDemo, TodayAction.StopDemo -> viewModel.onDemoAction(action)

            TodayAction.StopNow -> context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    })
}

/** 오늘 탭(상태 없음). 목업 01 구성: 상단 바 → 르르 한마디/보는 중 → 하루 시계 → 위험 시간대 → 잔소리·계획. */
@Composable
fun TodayScreen(uiState: TodayUiState, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    val ready = uiState as? TodayUiState.Ready
    PauseScreenColumn(title = null, modifier = modifier) {
        TodayTopBar(ready = ready, onMessages = { onAction(TodayAction.OpenMessages) })
        if (ready == null) return@PauseScreenColumn
        val summary = ready.summary
        if (summary.isDemo) {
            DemoBadge()
            DemoPlayButton(running = ready.demoRunning, onAction = onAction)
        }
        if (ready.monitoringStopped) {
            PauseCard(title = stringResource(R.string.today_monitoring_off_title)) {
                Text(
                    text = stringResource(R.string.today_monitoring_off_body),
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
            }
        }
        val live = summary.live
        if (live != null) {
            LiveCard(live = live, melt = if (ready.showMelt) summary.meltStage else MeltStage.NONE, onStop = {
                onAction(TodayAction.StopNow)
            })
        } else {
            val cheer = ready.greeting is TodayGreeting.Stopped
            val expression = if (cheer) BuddyExpression.CHEER else BuddyExpression.NEUTRAL
            BuddyBubble(
                text = greetingText(ready.greeting),
                avatar = {
                    BuddyAvatar(
                        expression = expression,
                        size = PauseSizes.bubbleBuddy,
                        melt = if (ready.showMelt) summary.meltStage else MeltStage.NONE,
                    )
                },
            )
        }
        ClockCard(ready)
        summary.topRisk?.let { RiskCard(it) }
        if (ready.poke != null || ready.plan != null) {
            if (isLargeFont()) {
                // 큰 글자에서는 두 카드를 세로로 쌓는다(좁은 칸에서 한 글자씩 줄바꿈되지 않게).
                Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm)) {
                    ready.poke?.let { PokeCard(it, Modifier.fillMaxWidth()) }
                    ready.plan?.let { PlanCard(it, Modifier.fillMaxWidth()) }
                }
            } else {
                // 두 카드 높이를 맞춘다(키 큰 쪽에 맞춤).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    ready.poke?.let { PokeCard(it, Modifier.weight(1f).fillMaxHeight()) }
                    ready.plan?.let { PlanCard(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
        if (!summary.isDemo && summary.totalMinutes == 0) {
            Text(
                text = stringResource(R.string.today_demo_hint),
                style = PauseTheme.type.bodySmall,
                color = PauseTheme.colors.ink2,
            )
        }
    }
}

@Composable
private fun TodayTopBar(ready: TodayUiState.Ready?, onMessages: () -> Unit) {
    val colors = PauseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Wordmark()
        if (ready != null) {
            Text(
                text = dateTimeLabel(ready.summary.now),
                style = PauseTheme.type.bodySmall,
                color = colors.ink3,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = PauseTheme.spacing.xs),
            )
        } else {
            Box(Modifier.weight(1f))
        }
        IconButton(
            onClick = onMessages,
            modifier = Modifier
                .size(PauseTheme.spacing.minTouch)
                .border(PauseTheme.spacing.hairline, colors.line, PauseTheme.shapes.pill),
        ) {
            Icon(
                painter = painterResource(R.drawable.today_ic_bell),
                contentDescription = stringResource(R.string.today_messages, stringResource(DesignR.string.buddy_name)),
                tint = colors.ink,
            )
        }
    }
}

@Composable
private fun greetingText(greeting: TodayGreeting): String = when (greeting) {
    TodayGreeting.NothingYet -> stringResource(R.string.today_greeting_nothing)

    is TodayGreeting.Stopped -> if (greeting.stopped > 0) {
        stringResource(R.string.today_greeting_stopped, greeting.nudged, greeting.stopped)
    } else {
        stringResource(R.string.today_greeting_stopped_none, greeting.nudged)
    }

    is TodayGreeting.Watched -> stringResource(R.string.today_greeting_watched, durationLabel(greeting.minutes))
}

@Composable
private fun ClockCard(ready: TodayUiState.Ready) {
    val summary = ready.summary
    PauseCard(
        title = stringResource(R.string.today_clock_title),
        trailing = {
            val less = summary.lessThanYesterday
            when {
                less == null -> Unit

                less == 0 -> ChangeText(stringResource(R.string.today_same_as_yesterday), ChangeDirection.NONE)

                else -> ChangeText(
                    text = stringResource(R.string.today_vs_yesterday, durationLabel(kotlin.math.abs(less))),
                    direction = if (less > 0) ChangeDirection.DOWN else ChangeDirection.UP,
                )
            }
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            DayClock(
                data = DayClockData(
                    minutesByHour = summary.minutesByHour,
                    totalLabel = durationLabel(summary.totalMinutes),
                    caption = stringResource(R.string.today_clock_caption),
                    nowMinuteOfDay = summary.now.hour * MINUTES_PER_HOUR + summary.now.minute,
                    riskHours = summary.riskHours,
                ),
            )
        }
        val entries = summary.byContext.entries.toList()
        entries.chunked(if (isLargeFont()) 1 else 2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pair.forEach { (slot, minutes) ->
                    ContextLegendRow(slot = slot, value = durationLabel(minutes), modifier = Modifier.weight(1f))
                }
                if (pair.size == 1 && !isLargeFont()) Box(Modifier.weight(1f))
            }
        }
        ClockLegend()
    }
}

@Composable
private fun ClockLegend() {
    Column(Modifier.fillMaxSize()) {
        Text(
            text = listOf(
                stringResource(DesignR.string.designsystem_clock_now),
                stringResource(DesignR.string.designsystem_clock_risk),
                stringResource(DesignR.string.designsystem_clock_night),
            ).joinToString(LEGEND_SEPARATOR),
            style = PauseTheme.type.caption,
            color = PauseTheme.colors.ink3,
        )
    }
}

private const val MINUTES_PER_HOUR = 60
private const val LEGEND_SEPARATOR = "  ·  "
