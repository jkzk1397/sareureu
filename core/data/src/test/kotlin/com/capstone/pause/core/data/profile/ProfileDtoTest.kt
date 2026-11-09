package com.capstone.pause.core.data.profile

import com.capstone.pause.core.data.settings.AppSettings
import com.capstone.pause.core.data.settings.DefaultThresholds
import com.capstone.pause.core.model.AgeBand
import com.capstone.pause.core.model.ContextSlot
import com.capstone.pause.core.model.DailyWindow
import com.capstone.pause.core.model.DayRhythm
import com.capstone.pause.core.model.Goal
import com.capstone.pause.core.model.Occupation
import com.capstone.pause.core.model.Profile
import com.capstone.pause.core.model.Tone
import com.capstone.pause.core.model.WeeklyBlock
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.junit.Test

class ProfileDtoTest {
    private val weekdays =
        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private val profile = Profile(
        nickname = "서연",
        ageBand = AgeBand.AGE_18_24,
        occupation = Occupation.UNIVERSITY,
        rhythm = DayRhythm(
            weekdayWake = LocalTime.of(8, 0),
            weekdaySleep = LocalTime.of(1, 0),
            weekendWake = LocalTime.of(10, 0),
            weekendSleep = LocalTime.of(2, 0),
            classOrWork = listOf(WeeklyBlock(weekdays, LocalTime.of(9, 0), LocalTime.of(17, 30))),
            commute = listOf(WeeklyBlock(weekdays, LocalTime.of(8, 15), LocalTime.of(9, 0))),
            meals = listOf(DailyWindow(LocalTime.of(12, 0), LocalTime.of(13, 0))),
        ),
        goals = setOf(Goal.SLEEP),
        interests = setOf("기타 연습", "러닝"),
        tone = Tone.PLAYFUL,
        dailyGoalMinutes = 45,
    )

    @Test
    fun roundTripThroughJson() {
        val json = Json.encodeToString(ProfileDto.serializer(), profile.toDto())
        assertEquals(profile, Json.decodeFromString(ProfileDto.serializer(), json).toModel())
    }

    @Test
    fun defaultThresholdsFollowPrd() {
        val settings = AppSettings()
        assertEquals(10, settings.thresholdFor(ContextSlot.LATE_NIGHT))
        assertEquals(10, settings.thresholdFor(ContextSlot.PRE_SLEEP))
        assertEquals(15, settings.thresholdFor(ContextSlot.COMMUTE))
        assertEquals(ContextSlot.entries.size, DefaultThresholds.ALL.size)
    }
}
