package com.capstone.pause.core.intervention.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.capstone.pause.core.intervention.notify.PauseNotifier
import com.capstone.pause.core.intervention.nudge.NudgeCommand
import com.capstone.pause.core.intervention.nudge.NudgeCoordinator
import com.capstone.pause.core.intervention.nudge.NudgeMode
import com.capstone.pause.core.intervention.nudge.NudgeUi
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 감지·개입 포그라운드 서비스. 유형은 `specialUse` 하나(ONE_SHOT 8절 3-③). 사용자가 켤 때만 시작하고,
 * 화면 켜짐·꺼짐 수신기는 여기서 런타임 등록한다. 폴링은 `UsageStatsSource`가 화면이 켜져 있을 때만 한다.
 */
@AndroidEntryPoint
class MonitorService : Service() {
    @Inject lateinit var coordinator: NudgeCoordinator

    @Inject lateinit var notifier: PauseNotifier

    @Inject lateinit var controller: MonitoringController

    @Inject lateinit var ui: NudgeUi

    private var scope: CoroutineScope? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 화면이 꺼지면 버블·카드를 내린다(창이 남지 않게). 세션 종료는 감지 쪽이 정한다.
            if (intent.action == Intent.ACTION_SCREEN_OFF) ui.render(NudgeCommand.Hide)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, PauseNotifier.STATUS_ID, notifier.statusNotification(), type)
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        controller.onServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 시연이 돌고 있으면 실제 감지는 시연이 끝난 뒤(ACTION_RESUME) 이어 간다.
        if (coordinator.mode.value != NudgeMode.DEMO || intent?.action == ACTION_RESUME) {
            val s = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }
            if (coordinator.mode.value != NudgeMode.REAL) coordinator.start(s, NudgeMode.REAL)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (coordinator.mode.value == NudgeMode.REAL) coordinator.stop()
        scope?.cancel()
        scope = null
        runCatching { unregisterReceiver(screenReceiver) }
        controller.onServiceRunning(false)
        super.onDestroy()
    }

    /** 인텐트 동작. */
    companion object {
        /** 시연이 끝난 뒤 실제 감지 재개. */
        const val ACTION_RESUME = "com.capstone.pause.action.RESUME_MONITORING"
    }
}
