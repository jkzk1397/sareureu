package com.capstone.pause.feature.friends.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.capstone.pause.feature.friends.FriendsRoute
import kotlinx.serialization.Serializable

/** 친구 탭 경로 키. */
@Serializable
data object FriendsKey : NavKey

/** 친구 탭 화면을 entry provider에 붙인다. */
fun EntryProviderScope<NavKey>.friendsEntries() {
    entry<FriendsKey> { FriendsRoute() }
}
