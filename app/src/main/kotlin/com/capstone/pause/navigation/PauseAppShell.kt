package com.capstone.pause.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.capstone.pause.R
import com.capstone.pause.core.designsystem.theme.PauseTheme
import com.capstone.pause.feature.coach.navigation.CoachKey
import com.capstone.pause.feature.coach.navigation.coachEntries
import com.capstone.pause.feature.friends.navigation.FriendsKey
import com.capstone.pause.feature.friends.navigation.friendsEntries
import com.capstone.pause.feature.onboarding.navigation.OnboardingKey
import com.capstone.pause.feature.onboarding.navigation.onboardingEntries
import com.capstone.pause.feature.report.navigation.ReportKey
import com.capstone.pause.feature.report.navigation.reportEntries
import com.capstone.pause.feature.settings.navigation.SettingsKey
import com.capstone.pause.feature.settings.navigation.settingsEntries
import com.capstone.pause.feature.today.navigation.TodayKey
import com.capstone.pause.feature.today.navigation.todayEntries

/** 하단 탭 5개. 순서 고정(오늘·리포트·코치·친구·설정). */
private enum class TopTab(val root: NavKey, @StringRes val label: Int, @DrawableRes val icon: Int) {
    TODAY(TodayKey, R.string.app_tab_today, R.drawable.app_ic_tab_today),
    REPORT(ReportKey, R.string.app_tab_report, R.drawable.app_ic_tab_report),
    COACH(CoachKey, R.string.app_tab_coach, R.drawable.app_ic_tab_coach),
    FRIENDS(FriendsKey, R.string.app_tab_friends, R.drawable.app_ic_tab_friends),
    SETTINGS(SettingsKey, R.string.app_tab_settings, R.drawable.app_ic_tab_settings),
}

/** 앱 셸: 프로필이 없으면 온보딩, 있으면 탭별 백 스택을 가진 하단 탭. */
@Composable
fun PauseAppShell(needsOnboarding: Boolean) {
    if (needsOnboarding) {
        val onboardingStack = rememberNavBackStack(OnboardingKey)
        PauseNavDisplay(
            backStack = onboardingStack,
            onBack = {},
            modifier = Modifier
                .fillMaxSize()
                .background(PauseTheme.colors.bg)
                .safeDrawingPadding(),
        ) {
            onboardingEntries(onFinished = {})
        }
        return
    }

    val stacks: Map<TopTab, NavBackStack<NavKey>> = TopTab.entries.associateWith { rememberNavBackStack(it.root) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val selected = TopTab.entries[selectedIndex]
    val stack = stacks.getValue(selected)

    // 탭 루트에서 뒤로 가기: 오늘 탭이 아니면 오늘 탭으로(예측형 뒤로 가기, onBackPressed 안 씀).
    BackHandler(enabled = stack.size == 1 && selected != TopTab.TODAY) { selectedIndex = 0 }

    Scaffold(
        containerColor = PauseTheme.colors.bg,
        bottomBar = {
            PauseBottomBar(selected = selected, onSelect = { tab ->
                if (tab == selected) {
                    // 같은 탭을 다시 누르면 루트로.
                    while (stack.size > 1) stack.removeAt(stack.lastIndex)
                } else {
                    selectedIndex = tab.ordinal
                }
            })
        },
    ) { padding ->
        PauseNavDisplay(
            backStack = stack,
            onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            todayEntries(onOpenMessages = { selectedIndex = TopTab.COACH.ordinal })
            reportEntries()
            coachEntries()
            friendsEntries()
            settingsEntries()
        }
    }
}

@Composable
private fun PauseNavDisplay(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entries: androidx.navigation3.runtime.EntryProviderScope<NavKey>.() -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider(builder = entries),
    )
}

@Composable
private fun PauseBottomBar(selected: TopTab, onSelect: (TopTab) -> Unit) {
    val colors = PauseTheme.colors
    NavigationBar(containerColor = colors.surface) {
        TopTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painter = painterResource(tab.icon), contentDescription = null) },
                label = { Text(text = stringResource(tab.label), style = PauseTheme.type.caption) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.ink,
                    selectedTextColor = colors.ink,
                    indicatorColor = colors.accent,
                    unselectedIconColor = colors.ink3,
                    unselectedTextColor = colors.ink3,
                ),
            )
        }
    }
}
