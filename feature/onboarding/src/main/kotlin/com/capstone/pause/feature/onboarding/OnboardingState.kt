package com.capstone.pause.feature.onboarding

import com.capstone.pause.core.data.settings.AiMode
import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.DailyWindow
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.model.Goal
import com.capstone.pause.core.model.Occupation
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.model.TrackedApp
import com.capstone.pause.core.model.WeeklyBlock
import java.time.DayOfWeek
import java.time.LocalTime

/** 온보딩 단계. [section]은 진행 표시(1~6)의 칸. */
enum class OnboardingStep(val section: Int) {
    AGE(1),
    UNDERAGE(1),
    TERMS(1),
    PROFILE(2),
    RHYTHM(3),
    GOALS(4),
    APPS(4),
    PERM_USAGE(5),
    PERM_NOTIFICATIONS(5),
    PERM_OVERLAY(5),
    AI(6),
    BATTERY(6),
    DONE(6),
    ;

    companion object {
        /** 진행 표시 칸 수. */
        const val SECTIONS = 6
    }
}

/** 시작~끝 시각. */
data class TimeRange(val start: LocalTime, val end: LocalTime)

/** 하루 리듬 초안. 구간이 null이면 '없음'. */
data class RhythmDraft(
    val weekdayWake: LocalTime = LocalTime.of(8, 0),
    val weekdaySleep: LocalTime = LocalTime.of(1, 0),
    val weekendWake: LocalTime = LocalTime.of(10, 0),
    val weekendSleep: LocalTime = LocalTime.of(2, 0),
    val commuteMorning: TimeRange? = TimeRange(LocalTime.of(8, 15), LocalTime.of(9, 0)),
    val commuteEvening: TimeRange? = TimeRange(LocalTime.of(18, 0), LocalTime.of(18, 50)),
    val work: TimeRange? = TimeRange(LocalTime.of(9, 0), LocalTime.of(17, 30)),
    val lunch: TimeRange? = TimeRange(LocalTime.of(12, 0), LocalTime.of(13, 0)),
    val dinner: TimeRange? = TimeRange(LocalTime.of(19, 0), LocalTime.of(20, 0)),
) {
    /** 계약 타입으로. 수업·근무·통학은 평일(월~금)만. */
    fun toRhythm(): DayRhythm = DayRhythm(
        weekdayWake = weekdayWake,
        weekdaySleep = weekdaySleep,
        weekendWake = weekendWake,
        weekendSleep = weekendSleep,
        classOrWork = listOfNotNull(work).map { WeeklyBlock(WEEKDAYS, it.start, it.end) },
        commute = listOfNotNull(commuteMorning, commuteEvening).map { WeeklyBlock(WEEKDAYS, it.start, it.end) },
        meals = listOfNotNull(lunch, dinner).map { DailyWindow(it.start, it.end) },
    )

    companion object {
        /** 월~금. */
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}

/** 리듬 화면에서 고칠 수 있는 칸. */
enum class RhythmField { WAKE, SLEEP, COMMUTE_MORNING, COMMUTE_EVENING, WORK, LUNCH, DINNER }

/** 권한 상태(설정에서 돌아올 때마다 다시 읽는다). */
data class PermissionStates(
    val usage: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
)

/** 온보딩 화면 상태. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.AGE,
    val ageBand: AgeBand? = null,
    val nickname: String = "",
    val occupation: Occupation = Occupation.UNIVERSITY,
    val rhythm: RhythmDraft = RhythmDraft(),
    val showWeekend: Boolean = false,
    val goals: Set<Goal> = setOf(Goal.SLEEP),
    val interests: Set<String> = emptySet(),
    val tone: Tone = Tone.PLAYFUL,
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL,
    val apps: List<TrackedApp> = emptyList(),
    val permissions: PermissionStates = PermissionStates(),
    val aiMode: AiMode = AiMode.CLOUD,
    val saving: Boolean = false,
) {
    /** 저장할 프로필. 닉네임이 비면 기본 닉네임(화면이 넣어 준다). */
    fun toProfile(defaultNickname: String): Profile = Profile(
        nickname = nickname.trim().ifEmpty { defaultNickname },
        ageBand = ageBand ?: AgeBand.AGE_18_24,
        occupation = occupation,
        rhythm = rhythm.toRhythm(),
        goals = goals,
        interests = interests,
        tone = tone,
        dailyGoalMinutes = dailyGoalMinutes,
    )

    companion object {
        /** 하루 목표 기본값(분). */
        const val DEFAULT_DAILY_GOAL = 60

        /** 하루 목표 고를 수 있는 값. */
        val DAILY_GOAL_CHOICES = listOf(30, 45, 60, 90, 120)

        /** 닉네임 최대 길이. */
        const val NICKNAME_MAX = 12
    }
}

/** 온보딩 동작. */
sealed interface OnboardingAction {
    /** 연령대 고르기(null = 만 18세 미만). */
    data class SelectAge(val band: AgeBand?) : OnboardingAction

    /** 다음 단계. */
    data object Next : OnboardingAction

    /** 이전 단계(뒤로 가기는 동의가 아니다). */
    data object Back : OnboardingAction

    /** 약관·처리 안내 동의. */
    data object AcceptTerms : OnboardingAction

    /** 닉네임. */
    data class SetNickname(val value: String) : OnboardingAction

    /** 신분. */
    data class SetOccupation(val value: Occupation) : OnboardingAction

    /** 평일/주말 보기. */
    data class ShowWeekend(val weekend: Boolean) : OnboardingAction

    /** 리듬 시각을 [deltaMinutes]만큼 옮긴다. 구간이면 [end]가 true일 때 끝 시각. */
    data class ShiftTime(val field: RhythmField, val end: Boolean, val deltaMinutes: Int) : OnboardingAction

    /** 구간 켜기·끄기. */
    data class ToggleRange(val field: RhythmField, val enabled: Boolean) : OnboardingAction

    /** 목표 켜기·끄기. */
    data class ToggleGoal(val goal: Goal) : OnboardingAction

    /** 관심사 켜기·끄기. */
    data class ToggleInterest(val interest: String) : OnboardingAction

    /** 말투. */
    data class SetTone(val tone: Tone) : OnboardingAction

    /** 하루 목표. */
    data class SetDailyGoal(val minutes: Int) : OnboardingAction

    /** 추적 앱 켜기·끄기. */
    data class ToggleApp(val packageName: String, val enabled: Boolean) : OnboardingAction

    /** 권한 고지에서 '동의하고 설정으로'(true) 또는 '나중에'(false). 고지 동의만 기록한다. */
    data class PermissionChoice(val step: OnboardingStep, val accepted: Boolean) : OnboardingAction

    /** AI 사용 방식. */
    data class SetAiMode(val mode: AiMode) : OnboardingAction

    /** AI 방식 확정(동의 기록). */
    data object ConfirmAi : OnboardingAction

    /** 권한 상태 다시 읽기. */
    data class RefreshPermissions(val states: PermissionStates) : OnboardingAction
}
