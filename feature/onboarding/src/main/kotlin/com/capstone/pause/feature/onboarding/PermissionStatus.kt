package com.capstone.pause.feature.onboarding

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** 권한 상태 읽기와 설정 화면 인텐트. 다른 앱을 조작하지 않고 설정 화면만 연다. */
internal object PermissionStatus {
    /** 지금 상태. */
    fun read(context: Context): PermissionStates = PermissionStates(
        usage = hasUsageAccess(context),
        notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        overlay = Settings.canDrawOverlays(context),
    )

    /** 사용 기록 접근(AppOps)으로 확인한다. */
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 사용 기록 접근 설정. */
    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** 다른 앱 위에 표시 설정(이 앱). */
    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    /** 이 앱 알림 설정(13 미만이나 거절 뒤). */
    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** 배터리 최적화 목록 화면(직접 예외 요청은 하지 않는다, 03 문서 9절). */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
