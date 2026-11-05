package com.capstone.pause.core.intervention.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capstone.pause.core.common.AppClock
import com.capstone.pause.core.data.copy.CopyPackRepository
import com.capstone.pause.core.data.insight.InsightRepository
import com.capstone.pause.core.data.maintenance.RetentionRepository
import com.capstone.pause.core.data.profile.ProfileRepository
import com.capstone.pause.core.designsystem.R as DesignR
import com.capstone.pause.core.intervention.R
import com.capstone.pause.core.intervention.notify.PauseNotifier
import com.capstone.pause.core.intervention.nudge.CopyPicker
import com.capstone.pause.core.sensing.context.ContextClassifier
import com.capstone.pause.core.sensing.context.DefaultDayRhythm
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** 예약 알림 하나를 보내고 다음 날 몫을 다시 건다. */
@HiltWorker
class ScheduledReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: PauseNotifier,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val index = inputData.getInt(KEY_INDEX, 0)
        val buddy = applicationContext.getString(DesignR.string.buddy_name)
        notifier.postReminder(
            id = NOTIFICATION_BASE + index,
            title = applicationContext.getString(R.string.intervention_reminder_title, buddy),
            body = applicationContext.getString(R.string.intervention_reminder_body),
            tab = PauseNotifier.TAB_TODAY,
        )
        scheduler.scheduleReminder(index)
        return Result.success()
    }

    /** 입력 키. */
    companion object {
        /** 몇 번째 예약 알림인지. */
        const val KEY_INDEX = "index"
        private const val NOTIFICATION_BASE = 2000
    }
}

/** 하루 한 번: 오늘 요일의 가장 위험한 시각을 찾아 10분 전 미리 알림을 건다(하루 최대 1개). */
@HiltWorker
class PredictivePlannerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val insightRepository: InsightRepository,
    private val scheduler: ReminderScheduler,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val risk = insightRepository.observeToday().first().topRisk ?: return Result.success()
        val now = clock.nowLocal()
        var at = now.withHour(risk.hour).withMinute(0).withSecond(0).withNano(0)
            .minusMinutes(ReminderScheduler.PREDICTIVE_LEAD_MIN)
        // 새벽 칸(4시 전)은 같은 논리적 날짜의 다음 달력 날짜다.
        if (risk.hour < DAY_START_HOUR && now.hour >= DAY_START_HOUR) at = at.plusDays(1)
        if (at.isAfter(now)) scheduler.schedulePredictive(at, risk.hour)
        return Result.success()
    }

    private companion object {
        const val DAY_START_HOUR = 4
    }
}

/** 위험 시간대 미리 알림. 문구는 문구 팩의 `predictive`(맥락별)에서. */
@HiltWorker
class PredictiveReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: PauseNotifier,
    private val copyPackRepository: CopyPackRepository,
    private val profileRepository: ProfileRepository,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val hour = inputData.getInt(KEY_HOUR, -1).takeIf { it in 0..LAST_HOUR } ?: return Result.success()
        val rhythm = profileRepository.profile.first()?.rhythm ?: DefaultDayRhythm.VALUE
        val at = clock.nowLocal().withHour(hour).withMinute(0)
        val slot = ContextClassifier.classify(at, rhythm).slot
        val line = copyPackRepository.activePack.first().predictive[slot] ?: return Result.success()
        val ctx = applicationContext
        val timeRange = when (hour) {
            in NIGHT_START..LAST_HOUR -> ctx.getString(R.string.intervention_time_night, hour - HALF_DAY)
            in 0 until DAWN_END -> ctx.getString(R.string.intervention_time_dawn, if (hour == 0) HALF_DAY else hour)
            else -> ctx.getString(R.string.intervention_time_day, hour)
        }
        val buddy = ctx.getString(DesignR.string.buddy_name)
        notifier.postReminder(
            id = NOTIFICATION_ID,
            title = ctx.getString(R.string.intervention_predictive_title, buddy),
            body = CopyPicker.fill(line.text, mapOf("timeRange" to timeRange, "buddy" to buddy)),
            tab = PauseNotifier.TAB_TODAY,
        )
        return Result.success()
    }

    /** 입력 키. */
    companion object {
        /** 위험 시각(0~23). */
        const val KEY_HOUR = "hour"
        private const val NOTIFICATION_ID = 2100
        private const val LAST_HOUR = 23
        private const val NIGHT_START = 21
        private const val DAWN_END = 6
        private const val HALF_DAY = 12
    }
}

/** 일요일 저녁 주간 리포트 알림. 보내고 다음 주 몫을 건다. 리포트 생성(AI)은 앱을 열었을 때만. */
@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: PauseNotifier,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        notifier.postReminder(
            id = NOTIFICATION_ID,
            title = ctx.getString(R.string.intervention_weekly_title),
            body = ctx.getString(R.string.intervention_weekly_body, ctx.getString(DesignR.string.buddy_name)),
            tab = PauseNotifier.TAB_REPORT,
        )
        scheduler.scheduleWeekly()
        return Result.success()
    }

    private companion object {
        const val NOTIFICATION_ID = 2200
    }
}

/** 보관 기간이 지난 기록을 지운다(하루 한 번). */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val retentionRepository: RetentionRepository,
    private val clock: AppClock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        retentionRepository.cleanUp(clock.now())
        return Result.success()
    }
}
