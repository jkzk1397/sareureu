package com.capstone.pause.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.ai.report.ReportObservation
import com.capstone.pause.core.designsystem.component.durationLabel
import com.capstone.pause.core.designsystem.component.labelRes
import com.capstone.pause.core.model.ContextSlot
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 관찰 → 화면 문장. 숫자는 관찰에 있는 값을 그대로 쓴다(형식만 바꾼다). 모르는 관찰이면 null. */
@Composable
internal fun observationText(o: ReportObservation): String? {
    fun n(key: String): Double = o.numbers[key] ?: 0.0
    fun pct(key: String): Int = (n(key) * PERCENT).roundToInt()
    return when (o.kind) {
        "WEEK_TOTAL" -> stringResource(
            R.string.report_obs_week_total,
            dur(o, "totalMin"),
            dur(o, "avgMinPerDay"),
            n("sessions").toInt(),
        )

        "REDUCTION" -> {
            val rate = n("reductionRate")
            val res = if (rate >= 0) R.string.report_obs_reduction_down else R.string.report_obs_reduction_up
            stringResource(res, dur(o, "baselineMinPerDay"), dur(o, "avgMinPerDay"), (abs(rate) * PERCENT).roundToInt())
        }

        "TOP_CONTEXT", "SECOND_CONTEXT" -> stringResource(
            R.string.report_obs_context,
            contextLabel(o.labels["context"]),
            dur(o, "minutes"),
            pct("share"),
            n("sessions").toInt(),
        )

        "NIGHT_SHARE" -> stringResource(R.string.report_obs_night, dur(o, "minutes"), pct("share"))

        "RISK_WINDOW" -> stringResource(
            R.string.report_obs_risk,
            dayLabel(o.labels["dayOfWeek"]),
            n("hour").toInt(),
            dur(o, "avgMinutes"),
        )

        "TOP_ENTRY" -> stringResource(R.string.report_obs_top_entry, keyLabel(o.labels["entry"]), pct("share"))

        "TOP_FLOW" -> stringResource(
            R.string.report_obs_top_flow,
            keyLabel(o.labels["entry"]),
            appLabel(o.labels["app"]),
            keyLabel(o.labels["exit"]),
            n("count").toInt(),
            dur(o, "medianMin"),
        )

        "SESSION_LENGTH" -> stringResource(R.string.report_obs_length, dur(o, "medianMin"), n("longCount").toInt())

        "NUDGES" -> stringResource(
            R.string.report_obs_nudges,
            n("shown").toInt(),
            n("stopped").toInt(),
            pct("stopRate"),
            n("extensions").toInt(),
        )

        "COVERAGE" -> stringResource(R.string.report_obs_coverage, pct("coverage"))

        "TOP_CONTEXT_ENTRY" -> stringResource(
            R.string.report_obs_top_context_entry,
            contextLabel(o.labels["context"]),
            n("sessions").toInt(),
            n("count").toInt(),
            keyLabel(o.labels["entry"]),
        )

        else -> null
    }
}

@Composable
private fun contextLabel(name: String?): String =
    stringResource((ContextSlot.entries.firstOrNull { it.name == name } ?: ContextSlot.OTHER).labelRes())

private fun dayLabel(name: String?): String =
    DayOfWeek.entries.firstOrNull { it.name == name }?.getDisplayName(TextStyle.FULL, Locale.KOREAN).orEmpty()

/** 진입·이탈 키(FlowKeys 또는 앱 종류 이름) → 화면 말. */
@Composable
internal fun keyLabel(key: String?): String = stringResource(
    when (key) {
        "UNLOCK" -> R.string.report_key_unlock
        "HOME", "LAUNCHER" -> R.string.report_key_home
        "SHORTFORM" -> R.string.report_key_shortform
        "SCREEN_OFF" -> R.string.report_key_screen_off
        "NUDGE_STOP" -> R.string.report_key_nudge_stop
        "MESSENGER" -> R.string.report_key_messenger
        "SOCIAL" -> R.string.report_key_social
        "VIDEO" -> R.string.report_key_video
        "STUDY_WORK" -> R.string.report_key_study
        "BROWSER" -> R.string.report_key_browser
        "GAME" -> R.string.report_key_game
        "SHOPPING" -> R.string.report_key_shopping
        else -> R.string.report_key_unknown
    },
)

/** 흐름의 앱 칸(패키지 이름) → 화면 말. */
@Composable
internal fun appLabel(app: String?): String = when {
    app == null -> stringResource(R.string.report_key_unknown)

    app.startsWith("com.google.android.youtube") -> stringResource(R.string.report_app_youtube)

    app.startsWith("com.instagram") -> stringResource(R.string.report_app_instagram)

    app.startsWith(
        "com.ss.android.ugc",
    ) || app.startsWith("com.zhiliaoapp") -> stringResource(R.string.report_app_tiktok)

    else -> stringResource(R.string.report_key_unknown)
}

/** 유형 이름. */
internal fun viewerTypeRes(name: String): Int = when (name) {
    "NIGHT_OWL" -> R.string.report_type_night_owl
    "COMMUTER" -> R.string.report_type_commuter
    "MEAL_BUDDY" -> R.string.report_type_meal_buddy
    "DOMINO" -> R.string.report_type_domino
    "GAP_FILLER" -> R.string.report_type_gap_filler
    "MORNING" -> R.string.report_type_morning
    else -> R.string.report_type_balanced
}

@Composable
private fun dur(o: ReportObservation, key: String): String = durationLabel((o.numbers[key] ?: 0.0).roundToInt())
