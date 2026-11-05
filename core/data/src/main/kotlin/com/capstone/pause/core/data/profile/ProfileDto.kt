package com.capstone.pause.core.data.profile

import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.DailyWindow
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.model.Goal
import com.capstone.pause.core.model.Occupation
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.model.WeeklyBlock
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** DataStore에 JSON으로 넣는 프로필 모양. 시각은 "HH:mm". */
@Serializable
internal data class ProfileDto(
    val nickname: String,
    val ageBand: AgeBand,
    val occupation: Occupation,
    val rhythm: RhythmDto,
    val goals: Set<Goal>,
    val interests: Set<String>,
    val tone: Tone,
    val dailyGoalMinutes: Int,
)

@Serializable
internal data class RhythmDto(
    val weekdayWake: String,
    val weekdaySleep: String,
    val weekendWake: String,
    val weekendSleep: String,
    val classOrWork: List<BlockDto>,
    val commute: List<BlockDto>,
    val meals: List<WindowDto>,
)

@Serializable
internal data class BlockDto(val days: Set<String>, val start: String, val end: String)

@Serializable
internal data class WindowDto(val start: String, val end: String)

@Serializable
internal data class ConsentDto(val item: String, val version: Int, val granted: Boolean, val atEpochMs: Long)

private fun LocalTime.text(): String = toString().take(TIME_LEN)

private const val TIME_LEN = 5

private fun WeeklyBlock.toDto() = BlockDto(days.map { it.name }.toSet(), start.text(), end.text())

private fun BlockDto.toModel() = WeeklyBlock(
    days.map {
        DayOfWeek.valueOf(it)
    }.toSet(),
    LocalTime.parse(start),
    LocalTime.parse(end),
)

internal fun Profile.toDto() = ProfileDto(
    nickname = nickname,
    ageBand = ageBand,
    occupation = occupation,
    rhythm = RhythmDto(
        weekdayWake = rhythm.weekdayWake.text(),
        weekdaySleep = rhythm.weekdaySleep.text(),
        weekendWake = rhythm.weekendWake.text(),
        weekendSleep = rhythm.weekendSleep.text(),
        classOrWork = rhythm.classOrWork.map { it.toDto() },
        commute = rhythm.commute.map { it.toDto() },
        meals = rhythm.meals.map { WindowDto(it.start.text(), it.end.text()) },
    ),
    goals = goals,
    interests = interests,
    tone = tone,
    dailyGoalMinutes = dailyGoalMinutes,
)

internal fun ProfileDto.toModel() = Profile(
    nickname = nickname,
    ageBand = ageBand,
    occupation = occupation,
    rhythm = DayRhythm(
        weekdayWake = LocalTime.parse(rhythm.weekdayWake),
        weekdaySleep = LocalTime.parse(rhythm.weekdaySleep),
        weekendWake = LocalTime.parse(rhythm.weekendWake),
        weekendSleep = LocalTime.parse(rhythm.weekendSleep),
        classOrWork = rhythm.classOrWork.map { it.toModel() },
        commute = rhythm.commute.map { it.toModel() },
        meals = rhythm.meals.map { DailyWindow(LocalTime.parse(it.start), LocalTime.parse(it.end)) },
    ),
    goals = goals,
    interests = interests,
    tone = tone,
    dailyGoalMinutes = dailyGoalMinutes,
)
