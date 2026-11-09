package com.capstone.pause.core.intervention.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.capstone.pause.core.data.monitoring.MonitoringState
import com.capstone.pause.core.data.monitoring.MonitoringStatus
import com.capstone.pause.core.data.monitoring.MonitoringSwitch
import com.capstone.pause.core.intervention.di.MonitoringStore
import com.capstone.pause.core.sensing.usage.UsageAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 감지 켜기·끄기(공개 API). 포그라운드 서비스는 **사용자가 화면에서 '감지 켜기'를 누를 때만** 시작한다
 * (ONE_SHOT 8절 3-①). 켜 둔 상태는 저장해 부팅 뒤 다시 켠다.
 */
@Singleton
class MonitoringController @Inject constructor(
    @ApplicationContext private val context: Context,
    @MonitoringStore private val store: DataStore<Preferences>,
) : MonitoringStatus,
    MonitoringSwitch {

    private val running = MutableStateFlow(false)

    /** 서비스가 돌고 있는지. */
    val isMonitoring: StateFlow<Boolean> = running.asStateFlow()

    /** 사용자가 감지를 켜 두었는지(저장된 값). */
    val userEnabled: Flow<Boolean> = store.data.map { it[ENABLED] ?: false }

    override val state: Flow<MonitoringState> = combine(running, userEnabled) { on, enabled ->
        when {
            on -> MonitoringState.ON
            enabled && stoppedByUser() -> MonitoringState.STOPPED_BY_USER
            else -> MonitoringState.OFF
        }
    }

    /** 사용 기록 권한이 있는지. 없으면 감지를 켤 수 없다. */
    override fun hasUsageAccess(): Boolean = UsageAccess.isGranted(context)

    /** 감지 켜기([start]와 같다). 오늘 탭·온보딩 끝에서 부른다. */
    override suspend fun turnOn(): Boolean = start()

    /**
     * 앱을 열 때 부른다: 켜 두었는데 서비스가 멈춰 있으면(제조사 절전·프로세스 종료 등) 다시 켠다.
     * 앱이 화면에 있으니 포그라운드 서비스를 시작할 수 있다. 시연 중이면 서비스가 실제 감지를 건너뛴다.
     */
    suspend fun ensureRunning() {
        if (running.value || !userEnabled.first() || !hasUsageAccess()) return
        runCatching { ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java)) }
    }

    /** 사용 기록 설정 화면으로 가는 인텐트(고지 화면을 먼저 보여 준 뒤에 쓴다). */
    fun usageAccessIntent(): Intent = UsageAccess.settingsIntent()

    /** 감지 켜기. 앱이 화면에 있을 때(사용자 탭)만 부른다. 권한이 없으면 false. */
    suspend fun start(): Boolean {
        if (!hasUsageAccess()) return false
        store.edit { it[ENABLED] = true }
        ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        return true
    }

    /** 감지 끄기. */
    suspend fun stop() {
        store.edit { it[ENABLED] = false }
        context.stopService(Intent(context, MonitorService::class.java))
    }

    /** 부팅 뒤: 사용자가 켜 두었고 권한이 있으면 다시 켠다(specialUse는 부팅 시작 금지 목록에 없음). */
    suspend fun restoreAfterBoot() {
        if (userEnabled.first() && hasUsageAccess()) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java)) }
        }
    }

    /** 시연이 끝난 뒤 실제 감지를 다시 돌린다(켜 둔 경우만). */
    suspend fun resumeIfEnabled() {
        if (userEnabled.first() && hasUsageAccess()) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_RESUME),
            )
        }
    }

    internal fun onServiceRunning(on: Boolean) {
        running.value = on
    }

    /** 마지막 프로세스 종료가 사용자의 '중지'였는지(작업 관리자 등, API 30+). */
    private fun stoppedByUser(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val last = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 1) }.getOrNull()
        return last?.firstOrNull()?.reason == ApplicationExitInfo.REASON_USER_REQUESTED
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("monitoring_enabled")
    }
}
