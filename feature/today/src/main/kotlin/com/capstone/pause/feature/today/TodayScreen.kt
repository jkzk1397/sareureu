package com.capstone.pause.feature.today

import android.content.Intent
import android.provider.Settings
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.designsystem.buddy.BuddyAvatar
import com.capstone.pause.core.designsystem.chart.DayClock
import com.capstone.pause.core.designsystem.chart.DayClockData
import com.capstone.pause.core.designsystem.component.BuddyBubble
import com.capstone.pause.core.designsystem.component.ChangeDirection
import com.capstone.pause.core.designsystem.component.ContextLegendRow
import com.capstone.pause.core.designsystem.component.DemoBadge
import com.capstone.pause.core.designsystem.component.PauseCard
import com.capstone.pause.core.designsystem.component.PauseScreenColumn
import com.capstone.pause.core.designsystem.component.PrimaryButton
import com.capstone.pause.core.designsystem.component.StatHero
import com.capstone.pause.core.designsystem.component.Wordmark
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.theme.PauseSizes
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.core.designsystem.theme.isLargeFont
import com.capstone.pause.core.model.BuddyExpression
import com.capstone.pause.core.model.MeltStage

/** 오늘 탭 진입점. '지금 그만할래'는 홈 화면 인텐트(다른 앱을 조작하지 않는다). */
@Composable
fun TodayRoute(
    onOpenMessages: () -> Unit,
    onOpenReport: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    TodayScreen(uiState = uiState, onAction = { action ->
        when (action) {
            TodayAction.OpenMessages -> onOpenMessages()

            TodayAction.OpenReport -> onOpenReport()

            TodayAction.OpenFriends -> onOpenFriends()

            TodayAction.StartDemo, TodayAction.StopDemo -> viewModel.onDemoAction(action)

            TodayAction.TurnOnMonitoring -> viewModel.turnOnMonitoring {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }

            TodayAction.StopNow -> context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    })
}

/**
 * 오늘 탭(상태 없음). 결론부터: 상단 바 → 오늘 본 시간(큰 숫자, 어제와 비교) → 르르 한마디/보는 중 → 하루 시계
 * → 자주 길어지는 시간 → 잔소리·계획. 잔소리 카드를 누르면 친구 탭, 계획 카드를 누르면 리포트 탭으로 간다.
 */
@Composable
fun TodayScreen(uiState: TodayUiState, onAction: (TodayAction) -> Unit, modifier: Modifier = Modifier) {
    val ready = uiState as? TodayUiState.Ready
    PauseScreenColumn(title = null, modifier = modifier) {
        TodayTopBar(ready = ready, onMessages = { onAction(TodayAction.OpenMessages) })
        if (ready == null) return@PauseScreenColumn
        val summary = ready.summary
        if (summary.isDemo) {
            // 시연 배지와 시연 버튼을 한 줄에. 이 화면의 주 버튼 자리를 차지하지 않게 작은 보조 버튼으로 그린다.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DemoBadge()
                Box(Modifier.weight(1f))
                DemoPlayButton(running = ready.demoRunning, onAction = onAction)
            }
        }
        TodayTotal(ready)
        if (ready.monitoringOff) {
            PauseCard(title = stringResource(R.string.today_monitoring_off_title)) {
                Text(
                    text = stringResource(R.string.today_monitoring_off_body),
                    style = PauseTheme.type.bodySmall,
                    color = PauseTheme.colors.ink2,
                )
                PrimaryButton(
                    text = stringResource(R.string.today_monitoring_turn_on),
                    onClick = { onAction(TodayAction.TurnOnMonitoring) },
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
            val openFriends = { onAction(TodayAction.OpenFriends) }
            val openReport = { onAction(TodayAction.OpenReport) }
            if (isLargeFont()) {
                // 큰 글자에서는 두 카드를 세로로 쌓는다(좁은 칸에서 한 글자씩 줄바꿈되지 않게).
                Column(verticalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm)) {
                    ready.poke?.let { PokeCard(it, openFriends, Modifier.fillMaxWidth()) }
                    ready.plan?.let { PlanCard(it, openReport, Modifier.fillMaxWidth()) }
                }
            } else {
                // 두 카드 높이를 맞춘다(키 큰 쪽에 맞춤).
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PauseTheme.spacing.sm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    ready.poke?.let { PokeCard(it, openFriends, Modifier.weight(1f).fillMaxHeight()) }
                    ready.plan?.let { PlanCard(it, openReport, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
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

    // 본 시간은 바로 위 큰 숫자에 있어서 한마디에서는 되풀이하지 않는다.
    is TodayGreeting.Watched -> stringResource(R.string.today_greeting_watched)
}

/** 맨 위 큰 숫자: 오늘 본 시간 한 번, 그 아래 어제와 비교. */
@Composable
private fun TodayTotal(ready: TodayUiState.Ready) {
    val summary = ready.summary
    val less = summary.lessThanYesterday
    StatHero(
        value = durationLabel(summary.totalMinutes),
        caption = stringResource(R.string.today_total_caption),
        change = when {
            less == null -> null
            less == 0 -> stringResource(R.string.today_same_as_yesterday)
            less > 0 -> stringResource(R.string.today_less_than_yesterday, durationLabel(less))
            else -> stringResource(R.string.today_more_than_yesterday, durationLabel(-less))
        },
        direction = when {
            less == null || less == 0 -> ChangeDirection.NONE
            less > 0 -> ChangeDirection.DOWN
            else -> ChangeDirection.UP
        },
    )
}

@Composable
private fun ClockCard(ready: TodayUiState.Ready) {
    val summary = ready.summary
    PauseCard(title = stringResource(R.string.today_clock_title)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            DayClock(
                data = DayClockData(
                    minutesByHour = summary.minutesByHour,
                    totalLabel = durationLabel(summary.totalMinutes),
                    caption = stringResource(R.string.today_clock_caption),
                    nowMinuteOfDay = summary.now.hour * MINUTES_PER_HOUR + summary.now.minute,
                    riskHours = summary.riskHours,
                    // 오늘 본 시간은 화면 맨 위에 크게 있다. 시계 가운데에는 되풀이하지 않는다.
                    showTotal = false,
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
