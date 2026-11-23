package com.capstone.pause

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.capstone.pause.core.common.ApplicationScope
import com.capstone.pause.core.intervention.reminder.ReminderScheduler
import com.capstone.pause.core.social.FriendPresence
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 애플리케이션. 기능 로직은 두지 않는다. WorkManager는 Hilt 워커 팩토리로 초기화한다. */
@HiltAndroidApp
class PauseApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderScheduler: ReminderScheduler

    @Inject lateinit var friendPresence: FriendPresence

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // 예약·미리·주간 알림과 보관 기간 정리를 (없으면) 건다. 서비스는 여기서 켜지 않는다.
        appScope.launch { reminderScheduler.rescheduleAll() }
        // 친구에게 '지금 시청 중' 알리기 + 잔소리 바로 받기(ADR-0012). 공유 동의 + 실제 시청 중일 때만 서버를 부른다.
        friendPresence.start()
    }
}
