package com.capstone.pause.core.sensing.context

import com.capstone.pause.core.model.Confidence
import com.capstone.pause.core.model.ContextSlot
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

/**
 * 경계값 표(PRD 8절). 기본 리듬: 평일 07:30 기상·00:30 취침, 주말 09:30·01:30,
 * 평일 08~09·18~19 이동, 09~18 수업, 12~13·18:30~19:30 식사.
 * 2026-11-03은 화요일, 11-07 토, 11-06 금.
 */
class ContextClassifierTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val rhythm = DefaultDayRhythm.VALUE

    private fun classify(text: String, signals: DeviceSignals = DeviceSignals()) =
        ContextClassifier.classify(LocalDateTime.parse(text).atZone(zone), rhythm, signals)

    private data class Case(val time: String, val expected: ContextSlot, val signals: DeviceSignals = DeviceSignals())

    @Test
    fun boundaryTable() {
        val cases = listOf(
            // 자기 전: 취침(화→수 00:30) −60분 = 23:30부터, +30분 = 01:00 전까지
            Case("2026-11-03T23:29:59", ContextSlot.OTHER),
            Case("2026-11-03T23:30:00", ContextSlot.PRE_SLEEP),
            Case("2026-11-04T00:59:59", ContextSlot.PRE_SLEEP),
            // 한밤중: 01:00 ~ 기상 07:30 전
            Case("2026-11-04T01:00:00", ContextSlot.LATE_NIGHT),
            Case("2026-11-04T07:29:59", ContextSlot.LATE_NIGHT),
            // 기상 직후: 기상부터 45분(첫 잠금 해제를 모를 때)
            Case("2026-11-04T07:30:00", ContextSlot.WAKE_UP),
            Case("2026-11-04T08:14:59", ContextSlot.WAKE_UP),
            // 이동: 평일 08~09(기상 창이 끝난 뒤)
            Case("2026-11-04T08:15:00", ContextSlot.COMMUTE),
            Case("2026-11-04T08:59:59", ContextSlot.COMMUTE),
            // 수업·업무 09~18, 식사 12~13이 우선
            Case("2026-11-04T09:00:00", ContextSlot.CLASS_WORK),
            Case("2026-11-04T11:59:59", ContextSlot.CLASS_WORK),
            Case("2026-11-04T12:00:00", ContextSlot.MEAL),
            Case("2026-11-04T12:59:59", ContextSlot.MEAL),
            Case("2026-11-04T13:00:00", ContextSlot.CLASS_WORK),
            // 이동 18~19가 식사 18:30~19:30보다 우선
            Case("2026-11-04T18:45:00", ContextSlot.COMMUTE),
            Case("2026-11-04T19:00:00", ContextSlot.MEAL),
            // 쉬는 시간: 위에 없는 9~21시
            Case("2026-11-04T19:30:00", ContextSlot.BREAK),
            Case("2026-11-04T20:59:59", ContextSlot.BREAK),
            Case("2026-11-04T21:00:00", ContextSlot.OTHER),
            // 21시 이후 충전 중이면 자기 전(확신도 중간)
            Case("2026-11-04T21:00:00", ContextSlot.PRE_SLEEP, DeviceSignals(charging = true)),
            Case("2026-11-04T20:59:59", ContextSlot.BREAK, DeviceSignals(charging = true)),
            // 주말: 토요일엔 수업·이동 없음, 기상 09:30
            Case("2026-11-07T08:30:00", ContextSlot.LATE_NIGHT),
            Case("2026-11-07T09:30:00", ContextSlot.WAKE_UP),
            Case("2026-11-07T10:30:00", ContextSlot.BREAK),
            Case("2026-11-07T12:30:00", ContextSlot.MEAL),
            // 금요일 밤은 주말 취침(01:30): 00:30~02:00 자기 전, 02:00부터 한밤중
            Case("2026-11-07T00:29:59", ContextSlot.OTHER),
            Case("2026-11-07T00:30:00", ContextSlot.PRE_SLEEP),
            Case("2026-11-07T02:00:00", ContextSlot.LATE_NIGHT),
            // 첫 잠금 해제를 알면 그 뒤 45분, 기상 ±2시간 안에서만
            Case(
                "2026-11-04T09:20:00",
                ContextSlot.WAKE_UP,
                DeviceSignals(firstUnlockToday = LocalDateTime.parse("2026-11-04T09:00:00")),
            ),
            Case(
                "2026-11-04T09:45:00",
                ContextSlot.CLASS_WORK,
                DeviceSignals(firstUnlockToday = LocalDateTime.parse("2026-11-04T09:00:00")),
            ),
        )
        cases.forEach { c -> assertEquals(c.expected, classify(c.time, c.signals).slot, "${c.time} ${c.signals}") }
    }

    @Test
    fun confidence() {
        assertEquals(Confidence.HIGH, classify("2026-11-04T02:00:00").confidence)
        assertEquals(Confidence.LOW, classify("2026-11-04T12:30:00").confidence)
        assertEquals(Confidence.MEDIUM, classify("2026-11-04T08:30:00").confidence)
        assertEquals(Confidence.HIGH, classify("2026-11-04T08:30:00", DeviceSignals(headphones = true)).confidence)
        assertEquals(Confidence.MEDIUM, classify("2026-11-04T21:30:00", DeviceSignals(charging = true)).confidence)
        assertEquals(Confidence.LOW, classify("2026-11-04T21:30:00").confidence)
    }

    @Test
    fun everySlotIsReachable() {
        val all = listOf(
            "2026-11-04T07:40:00",
            "2026-11-04T08:30:00",
            "2026-11-04T10:00:00",
            "2026-11-04T12:30:00",
            "2026-11-04T20:00:00",
            "2026-11-04T23:45:00",
            "2026-11-04T03:00:00",
            "2026-11-04T21:30:00",
        ).map { classify(it).slot }.toSet()
        assertEquals(ContextSlot.entries.toSet(), all)
    }
}
