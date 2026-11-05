package com.capstone.pause.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.capstone.pause.core.designsystem.R

/** 분을 "1시간 12분" · "1시간" · "12분"으로 쓴다. */
@Composable
fun durationLabel(minutes: Int): String {
    val h = minutes / MINUTES_PER_HOUR
    val m = minutes % MINUTES_PER_HOUR
    return when {
        h == 0 -> stringResource(R.string.designsystem_minutes, m)
        m == 0 -> stringResource(R.string.designsystem_duration_h, h)
        else -> stringResource(R.string.designsystem_duration_hm, h, m)
    }
}

private const val MINUTES_PER_HOUR = 60
