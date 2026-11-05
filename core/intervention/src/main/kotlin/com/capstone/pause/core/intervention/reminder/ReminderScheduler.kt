package com.capstone.pause.core.intervention.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.intervention.di.MonitoringStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 예약·미리·주간 알림과 보관 기간 정리(WorkManager). 정확한 알람 권한은 쓰지 않는다(몇 분 오차 허용, PRD 7.3).
 * - 예약 알림: 사용자가 고른 시각, 하루 최대 3개
 * - 위험 시간대 미리 알림: 오늘 요일의 가장 위험한 시각 10분 전, 하루 1번
 * - 주간 리포트 알림: 일요일 20시
 * - 정리: 하루 한 번(원시 이벤트 7일, 세션 90일, AI 감사 로그 30일)
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    @MonitoringStore private val store: DataStore<Preferences>,
    private val clock: AppClock,
) {
    private val work: WorkManager get() = WorkManager.getInstance(context)

    /** 예약 알림 시각들(최대 3개, 시각 순). */
    val reminderTimes: Flow<List<LocalTime>> = store.data.map { prefs -> decode(prefs[REMINDERS]) }

    /** 예약 알림 시각 저장 후 다시 건다. 3개를 넘으면 앞의 3개만. */
    suspend fun setReminderTimes(times: List<LocalTime>) {
        val clean = times.distinct().sorted().take(MAX_REMINDERS)
        store.edit { it[REMINDERS] = clean.joinToString(",") }
        scheduleReminders(clean)
    }

    /** 앱 시작·부팅 때: 모든 작업을 (없으면) 건다. */
    suspend fun rescheduleAll() {
        scheduleReminders(reminderTimes.first())
        scheduleWeekly()
        work.enqueueUniquePeriodicWork(
            WORK_PLANNER,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PredictivePlannerWorker>(1, TimeUnit.DAYS).build(),
        )
        work.enqueueUniquePeriodicWork(
            WORK_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build(),
        )
        // 오늘 몫의 미리 알림도 바로 계산해 둔다.
        work.enqueueUniqueWork(
            WORK_PLANNER_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PredictivePlannerWorker>().build(),
        )
    }

    /** [index]번 예약 알림을 다음 시각에 건다(알림을 보낸 뒤 다음 날 몫을 다시 걸 때도 쓴다). */
    suspend fun scheduleReminder(index: Int) {
        val time = reminderTimes.first().getOrNull(index)
        if (time == null) {
            work.cancelUniqueWork(reminderName(index))
            return
        }
        work.enqueueUniqueWork(
            reminderName(index),
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScheduledReminderWorker>()
                .setInitialDelay(delayUntil(nextAt(time)).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(ScheduledReminderWorker.KEY_INDEX to index))
                .build(),
        )
    }

    /** 다음 일요일 20시 주간 리포트 알림. */
    fun scheduleWeekly() {
        val now = clock.nowLocal()
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(WEEKLY_TIME)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        work.enqueueUniqueWork(
            WORK_WEEKLY,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WeeklyReportWorker>()
                .setInitialDelay(delayUntil(next).toMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    /** 오늘 [at]에 미리 알림 하나(이미 지났으면 걸지 않는다). */
    fun schedulePredictive(at: ZonedDateTime, hour: Int) {
        val delay = delayUntil(at)
        if (delay.isZero) return
        work.enqueueUniqueWork(
            WORK_PREDICTIVE,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PredictiveReminderWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(PredictiveReminderWorker.KEY_HOUR to hour))
                .build(),
        )
    }

    private suspend fun scheduleReminders(times: List<LocalTime>) {
        for (i in 0 until MAX_REMINDERS) {
            if (i < times.size) scheduleReminder(i) else work.cancelUniqueWork(reminderName(i))
        }
    }

    private fun nextAt(time: LocalTime): ZonedDateTime {
        val now = clock.nowLocal()
        val today = now.with(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun delayUntil(at: ZonedDateTime): Duration =
        Duration.between(clock.now(), at.toInstant()).let { if (it.isNegative) Duration.ZERO else it }

    private fun decode(raw: String?): List<LocalTime> =
        raw.orEmpty().split(',').mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }.sorted()

    /** 상수. */
    companion object {
        /** 예약 알림 최대 개수. */
        const val MAX_REMINDERS = 3

        /** 미리 알림을 위험 시각 몇 분 전에. */
        const val PREDICTIVE_LEAD_MIN = 10L

        private val REMINDERS = stringPreferencesKey("reminder_times")
        private val WEEKLY_TIME: LocalTime = LocalTime.of(20, 0)
        private const val WORK_PLANNER = "predictive_planner"
        private const val WORK_PLANNER_NOW = "predictive_planner_now"
        private const val WORK_PREDICTIVE = "predictive_today"
        private const val WORK_WEEKLY = "weekly_report"
        private const val WORK_CLEANUP = "retention_cleanup"

        private fun reminderName(index: Int) = "reminder_$index"
    }
}
