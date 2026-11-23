package com.capstone.pause.feature.onboarding

import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.Goal
import com.capstone.pause.core.model.Occupation
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Tone
import java.time.LocalTime

/** 온보딩 단계(5화면). [section]은 진행 표시(1~5)의 칸. */
enum class OnboardingStep(val section: Int) {
    /** 연령대 + 약관·개인정보 동의. */
    START(1),

    /** 만 18세 미만 안내(ADR-0009, 순서 밖). */
    UNDERAGE(1),

    /** 별명·신분·자는 시간. */
    ME(2),

    /** 목표·하루 목표 분. */
    GOALS(3),

    /** 사용 기록·알림·다른 앱 위 표시 한 화면. */
    PERMISSIONS(4),

    /** AI 친구 쓰기 / 나중에 → 시작. */
    AI(5),
    ;

    companion object {
        /** 진행 표시 칸 수. */
        const val SECTIONS = 5
    }
}

/** 온보딩에서 묻는 권한. [PRECISE]는 선택(정밀 모드: 접근성으로 쇼츠·릴스 화면만 셈, ADR-0004 보완). */
enum class PermissionKind { USAGE, NOTIFICATIONS, OVERLAY, PRECISE }

/** 권한 상태(설정에서 돌아올 때마다 다시 읽는다). */
data class PermissionStates(
    val usage: Boolean = false,
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    /** 시스템 접근성 설정에서 이 앱 서비스가 켜져 있는지(동의가 없으면 서비스가 스스로 꺼진다). */
    val precise: Boolean = false,
) {
    /** 그 권한이 허용됐는지. */
    fun granted(kind: PermissionKind): Boolean = when (kind) {
        PermissionKind.USAGE -> usage
        PermissionKind.NOTIFICATIONS -> notifications
        PermissionKind.OVERLAY -> overlay
        PermissionKind.PRECISE -> precise
    }
}

/** 온보딩 화면 상태. */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.START,
    val ageBand: AgeBand? = null,
    val nickname: String = "",
    val occupation: Occupation = Occupation.UNIVERSITY,
    /** 사용자가 고친 평일 취침 시각. null이면 신분 기본값. */
    val sleep: LocalTime? = null,
    val goals: Set<Goal> = setOf(Goal.SLEEP),
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL,
    val permissions: PermissionStates = PermissionStates(),
    val saving: Boolean = false,
    /** 정밀 모드 고지 화면을 보이는 중(다른 고지와 묶지 않는 별도 화면, ADR-0004 보완). */
    val showPreciseDisclosure: Boolean = false,
) {
    /** 화면에 보이는 평일 취침 시각. */
    val shownSleep: LocalTime get() = sleep ?: DefaultRhythms.sleepOf(occupation)

    /**
     * 저장할 프로필. 닉네임이 비면 [defaultNickname]. 리듬은 신분 기본값 + 자는 시간,
     * 관심사는 비우고 말투는 기본(친구처럼). 둘 다 설정에서 바꾼다.
     */
    fun toProfile(defaultNickname: String): Profile = Profile(
        nickname = nickname.trim().ifEmpty { defaultNickname },
        ageBand = ageBand ?: AgeBand.AGE_18_24,
        occupation = occupation,
        rhythm = DefaultRhythms.of(occupation, sleep).toRhythm(),
        goals = goals,
        interests = emptySet(),
        tone = Tone.PLAYFUL,
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
    /** 연령대 고르기(null = 만 18세 미만 → 안내 화면). */
    data class SelectAge(val band: AgeBand?) : OnboardingAction

    /** 다음 단계. */
    data object Next : OnboardingAction

    /** 이전 단계(뒤로 가기는 동의가 아니다). */
    data object Back : OnboardingAction

    /** 약관·처리 안내 동의 후 다음(연령대를 골랐을 때만). */
    data object AcceptTerms : OnboardingAction

    /** 닉네임. */
    data class SetNickname(val value: String) : OnboardingAction

    /** 신분. */
    data class SetOccupation(val value: Occupation) : OnboardingAction

    /** 평일 취침 시각을 [deltaMinutes]만큼 옮긴다. */
    data class ShiftSleep(val deltaMinutes: Int) : OnboardingAction

    /** 목표 켜기·끄기. */
    data class ToggleGoal(val goal: Goal) : OnboardingAction

    /** 하루 목표. */
    data class SetDailyGoal(val minutes: Int) : OnboardingAction

    /** 권한 줄의 [허용]: 고지 동의를 기록한다. 설정 화면은 Route가 연다. */
    data class AllowPermission(val kind: PermissionKind) : OnboardingAction

    /** 권한 상태 다시 읽기. */
    data class RefreshPermissions(val states: PermissionStates) : OnboardingAction

    /** 정밀 모드 고지의 답. 동의하면 기록하고 Route가 시스템 접근성 설정을 연다. 닫기·뒤로 가기는 거절. */
    data class AnswerPrecise(val agree: Boolean) : OnboardingAction
}
