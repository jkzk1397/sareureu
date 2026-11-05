package com.capstone.pause.feature.report.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.report.ReportRoute
import kotlinx.serialization.Serializable

/** 리포트 탭 경로 키. */
@Serializable
data object ReportKey : NavKey

/** 리포트 탭 화면을 entry provider에 붙인다. */
fun EntryProviderScope<NavKey>.reportEntries() {
    entry<ReportKey> { ReportRoute() }
}
