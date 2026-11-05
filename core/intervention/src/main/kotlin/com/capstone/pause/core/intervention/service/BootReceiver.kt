package com.capstone.pause.core.intervention.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.intervention.reminder.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 부팅(잠금 해제 뒤) 후 예약 알림을 다시 걸고, 사용자가 감지를 켜 두었으면 서비스를 다시 켠다. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var controller: MonitoringController

    @Inject lateinit var scheduler: ReminderScheduler

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        appScope.launch {
            try {
                scheduler.rescheduleAll()
                controller.restoreAfterBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
