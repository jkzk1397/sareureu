package com.capstone.pause.feature.report.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.report.ReportRoute
import kotlinx.serialization.Serializable

/** 리포트 탭 경로 키. */
@Serializable
data object ReportKey : NavKey

/**
 * 리포트 탭 화면을 entry provider에 붙인다. [focusPlans]가 true면 '이번 주에 해 볼 것'까지 내려가고
 * [onFocusHandled]를 부른다(오늘 탭의 '내 계획' 카드에서 올 때).
 */
fun EntryProviderScope<NavKey>.reportEntries(focusPlans: Boolean = false, onFocusHandled: () -> Unit = {}) {
    entry<ReportKey> { ReportRoute(focusPlans = focusPlans, onFocusHandled = onFocusHandled) }
}
