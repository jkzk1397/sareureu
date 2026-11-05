package com.capstone.pause.core.model

import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** 사용자 프로필. 실명·생년월일·성별·위치는 묻지 않는다. */
data class Profile(
    val nickname: String,
    val ageBand: AgeBand,
    val occupation: Occupation,
    val rhythm: DayRhythm,
    val goals: Set<Goal>,
    val interests: Set<String>,
    val tone: Tone,
    /** 하루 숏폼 목표. 기준선이 생기기 전(첫 7일) 녹는 단계의 기준. */
    val dailyGoalMinutes: Int = 60,
)

/** 연령대. 만 18세 미만은 가입 불가(ADR-0009). */
enum class AgeBand { AGE_18_24, AGE_25_29, AGE_30_39, AGE_40_PLUS }

/** 신분. */
enum class Occupation { UNIVERSITY, GRAD_OR_JOBSEEKER, OFFICE_WORKER, SHIFT_OR_FREELANCE, OTHER }

/** 하루 리듬. */
data class DayRhythm(
    val weekdayWake: LocalTime,
    val weekdaySleep: LocalTime,
    val weekendWake: LocalTime,
    val weekendSleep: LocalTime,
    val classOrWork: List<WeeklyBlock>,
    val commute: List<WeeklyBlock>,
    val meals: List<DailyWindow>,
)

/** 말투: 친구처럼 / 다정하게 / 담백하게. */
@Serializable
enum class Tone { PLAYFUL, WARM, PLAIN }

/** 목표: 잠, 집중, 시간 되찾기, 기분. */
enum class Goal { SLEEP, FOCUS, TIME_BACK, MOOD, OTHER }

/** 요일별 반복 구간(수업·업무·이동). */
data class WeeklyBlock(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime)

/** 매일 반복 구간(식사). */
data class DailyWindow(val start: LocalTime, val end: LocalTime)
