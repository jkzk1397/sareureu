package com.capstone.pause.core.data.trackedapp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.capstone.pause.core.data.di.TrackedAppsStore
import com.capstone.pause.core.model.AppKind
import com.capstone.pause.core.model.TrackedApp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 기본 추적 앱(ONE_SHOT 2단계). 이 목록을 `core:sensing` 매니페스트의 `<queries><package/>`와
 * (P1) 접근성 `packageNames`와 같게 유지한다. [TrackedApp.label]은 앱의 고유 이름(데이터)이다.
 */
object TrackedApps {
    /** 기본 7개: 유튜브, 인스타그램, 인스타그램 라이트, 틱톡(한국 스토어·글로벌·Go·Lite). */
    val DEFAULT: List<TrackedApp> = listOf(
        TrackedApp("com.google.android.youtube", "YouTube", AppKind.SHORTFORM, enabled = true),
        TrackedApp("com.instagram.android", "Instagram", AppKind.SHORTFORM, enabled = true),
        TrackedApp("com.instagram.lite", "Instagram Lite", AppKind.SHORTFORM, enabled = true),
        TrackedApp("com.ss.android.ugc.trill", "TikTok", AppKind.SHORTFORM, enabled = true),
        TrackedApp("com.zhiliaoapp.musically", "TikTok", AppKind.SHORTFORM, enabled = true),
        TrackedApp("com.zhiliaoapp.musically.go", "TikTok Lite", AppKind.SHORTFORM, enabled = true),
        TrackedApp("com.ss.android.ugc.tiktok.lite", "TikTok Lite", AppKind.SHORTFORM, enabled = true),
    )
}

/** 추적 앱 저장소. 사용자가 끈 앱만 저장하고 나머지는 기본값을 따른다. */
interface TrackedAppRepository {
    /** 추적 앱 목록(꺼진 앱 포함, [TrackedApp.enabled]로 구분). */
    val trackedApps: Flow<List<TrackedApp>>

    /** 앱 하나 켜기·끄기. */
    suspend fun setEnabled(packageName: String, enabled: Boolean)
}

/** DataStore(`tracked_apps`) 구현. */
@Singleton
internal class DataStoreTrackedAppRepository @Inject constructor(
    @TrackedAppsStore private val store: DataStore<Preferences>,
) : TrackedAppRepository {

    override val trackedApps: Flow<List<TrackedApp>> = store.data.map { prefs ->
        val disabled = prefs[DISABLED].orEmpty()
        TrackedApps.DEFAULT.map { it.copy(enabled = it.packageName !in disabled) }
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[DISABLED].orEmpty()
            prefs[DISABLED] = if (enabled) current - packageName else current + packageName
        }
    }

    private companion object {
        val DISABLED = stringSetPreferencesKey("disabled_packages")
    }
}
