package com.capstone.pause.feature.today.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.today.TodayRoute
import kotlinx.serialization.Serializable

/** 오늘 탭 경로 키. */
@Serializable
data object TodayKey : NavKey

/**
 * 오늘 탭 화면을 entry provider에 붙인다. 종 버튼은 [onOpenMessages](코치 탭의 르르 메시지),
 * '내 계획' 카드는 [onOpenReport](리포트 탭의 해 볼 것), '맡겨 둔 잔소리' 카드는 [onOpenFriends](친구 탭).
 */
fun EntryProviderScope<NavKey>.todayEntries(
    onOpenMessages: () -> Unit,
    onOpenReport: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
) {
    entry<TodayKey> {
        TodayRoute(onOpenMessages = onOpenMessages, onOpenReport = onOpenReport, onOpenFriends = onOpenFriends)
    }
}
