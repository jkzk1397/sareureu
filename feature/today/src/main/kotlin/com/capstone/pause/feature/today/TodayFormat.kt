package com.capstone.pause.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** 시 → 하루 때 이름(새벽·오전·오후·저녁·밤). */
internal fun periodRes(hour: Int): Int = when (hour) {
    in 0..5 -> R.string.today_period_dawn
    in 6..11 -> R.string.today_period_morning
    in 12..17 -> R.string.today_period_afternoon
    in 18..20 -> R.string.today_period_evening
    else -> R.string.today_period_night
}

private fun twelveHour(hour: Int): Int = (hour % HALF_DAY).let { if (it == 0) HALF_DAY else it }

/** 요일 이름(한국어). */
internal fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.KOREAN)

/** "11월 3일 화요일 · 밤 9:40". */
@Composable
internal fun dateTimeLabel(now: ZonedDateTime): String {
    val time = stringResource(
        R.string.today_time,
        stringResource(periodRes(now.hour)),
        twelveHour(now.hour),
        now.minute,
    )
    return stringResource(R.string.today_date, now.monthValue, now.dayOfMonth, dayName(now.dayOfWeek), time)
}

/** "밤 10시". */
@Composable
internal fun hourLabel(hour: Int): String =
    stringResource(R.string.today_hour, stringResource(periodRes(hour)), twelveHour(hour))

/** 받침이 있으면 true(조사 '이/가' 고르기). 한글이 아니면 false. */
internal fun hasFinalConsonant(word: String): Boolean {
    val last = word.lastOrNull() ?: return false
    if (last !in '가'..'힣') return false
    return (last - '가') % JONGSEONG_COUNT != 0
}

/** 추적 앱 패키지 → 화면 이름. */
internal fun appNameRes(packageName: String?): Int = when {
    packageName == null -> R.string.today_app_other

    packageName.startsWith("com.google.android.youtube") -> R.string.today_app_youtube

    packageName.startsWith("com.instagram") -> R.string.today_app_instagram

    packageName.startsWith(
        "com.ss.android.ugc",
    ) || packageName.startsWith("com.zhiliaoapp") -> R.string.today_app_tiktok

    else -> R.string.today_app_other
}

private const val HALF_DAY = 12
private const val JONGSEONG_COUNT = 28
