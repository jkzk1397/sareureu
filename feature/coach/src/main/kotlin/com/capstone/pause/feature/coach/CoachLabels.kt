package com.capstone.pause.feature.coach

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.ai.coach.EvidenceView
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.component.labelRes
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** 하루 때 이름. */
internal fun periodRes(hour: Int): Int = when (hour) {
    in 0..5 -> R.string.coach_period_dawn
    in 6..11 -> R.string.coach_period_morning
    in 12..17 -> R.string.coach_period_afternoon
    in 18..20 -> R.string.coach_period_evening
    else -> R.string.coach_period_night
}

/** "밤 11:42". */
@Composable
internal fun timeLabel(at: ZonedDateTime): String {
    val h = (at.hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }
    return stringResource(R.string.coach_time, stringResource(periodRes(at.hour)), h, at.minute)
}

/** 날짜 구분선: 오늘·어제·"11월 3일". */
@Composable
internal fun dayLabel(at: ZonedDateTime, today: ZonedDateTime?): String {
    val d = at.toLocalDate()
    val t = today?.toLocalDate()
    return when {
        t != null && d == t -> stringResource(R.string.coach_today)
        t != null && d == t.minusDays(1) -> stringResource(R.string.coach_yesterday)
        else -> stringResource(R.string.coach_date, d.monthValue, d.dayOfMonth)
    }
}

/** 앱 코드(YOUTUBE 등) → 이름. */
internal fun appRes(app: String): Int = when (app) {
    "YOUTUBE" -> R.string.coach_app_youtube
    "INSTAGRAM" -> R.string.coach_app_instagram
    "TIKTOK" -> R.string.coach_app_tiktok
    else -> R.string.coach_app_other
}

/** 진입·이탈 키(`FlowKeys`, 앱 종류 이름) → 이름. */
internal fun keyRes(key: String): Int = when (key) {
    "UNLOCK" -> R.string.coach_key_unlock
    "HOME", "LAUNCHER" -> R.string.coach_key_home
    "SHORTFORM" -> R.string.coach_key_shortform
    "SCREEN_OFF" -> R.string.coach_key_screen_off
    "NUDGE_STOP" -> R.string.coach_key_nudge_stop
    "MESSENGER" -> R.string.coach_key_messenger
    "SOCIAL" -> R.string.coach_key_social
    "VIDEO" -> R.string.coach_key_video
    "STUDY_WORK" -> R.string.coach_key_study
    "BROWSER" -> R.string.coach_key_browser
    "GAME" -> R.string.coach_key_game
    "SHOPPING" -> R.string.coach_key_shopping
    else -> R.string.coach_key_other
}

/** "MON" 등 → "월". 모르면 그대로. */
internal fun shortDay(code: String): String = DayOfWeek.entries.firstOrNull { it.name.startsWith(code.uppercase()) }
    ?.getDisplayName(TextStyle.SHORT, Locale.KOREAN) ?: code

/** 근거 칩 문구. */
@Composable
internal fun evidenceLabel(e: EvidenceView): String = when (e) {
    is EvidenceView.Session -> stringResource(
        R.string.coach_ev_session,
        shortDay(e.dayOfWeek),
        e.hour,
        e.minute,
        stringResource(appRes(e.app)),
        durationLabel(e.minutes),
    )

    is EvidenceView.Summary -> stringResource(R.string.coach_ev_summary, durationLabel(e.avgMinPerDay))

    is EvidenceView.Context -> stringResource(
        R.string.coach_ev_context,
        stringResource(e.slot.labelRes()),
        durationLabel(e.minutes),
    )

    is EvidenceView.Flow -> stringResource(
        R.string.coach_ev_flow,
        stringResource(keyRes(e.entry)),
        stringResource(appRes(e.app)),
        e.count,
    )

    is EvidenceView.Risk -> stringResource(
        R.string.coach_ev_risk,
        shortDay(e.dayOfWeek),
        e.hour,
        durationLabel(e.averageMinutes),
    )

    is EvidenceView.Text -> e.text

    is EvidenceView.Peer -> {
        val unknown = stringResource(R.string.coach_ev_unknown)
        val peer = e.peerMinutes?.let { durationLabel(it) } ?: unknown
        val me = e.myMinutes?.let { durationLabel(it) } ?: unknown
        stringResource(if (e.isDemo) R.string.coach_ev_peer_demo else R.string.coach_ev_peer, peer, me)
    }
}

private const val HALF_DAY = 12
