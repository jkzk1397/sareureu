package com.capstone.pause.feature.report

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** 시 → "밤 10시" · "자정" · "정오". */
@Composable
internal fun reportHourLabel(hour: Int): String = when (hour) {
    0 -> stringResource(R.string.report_midnight)

    NOON -> stringResource(R.string.report_noon)

    else -> stringResource(
        R.string.report_hour,
        stringResource(
            when (hour) {
                in 1..5 -> R.string.report_period_dawn
                in 6..11 -> R.string.report_period_morning
                in 12..17 -> R.string.report_period_afternoon
                in 18..20 -> R.string.report_period_evening
                else -> R.string.report_period_night
            },
        ),
        hour % NOON,
    )
}

/** 흐름도 앱 키(YOUTUBE 등) → 화면 말. */
@Composable
internal fun reportAppLabel(key: String): String = stringResource(
    when (key) {
        "YOUTUBE" -> R.string.report_app_youtube
        "INSTAGRAM" -> R.string.report_app_instagram
        "TIKTOK" -> R.string.report_app_tiktok
        else -> R.string.report_key_unknown
    },
)

private const val NOON = 12
