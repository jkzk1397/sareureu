package com.capstone.pause.core.sensing.path

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.capstone.pause.core.model.AppKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** `assets/app_kinds.json` 모양. */
@Serializable
internal data class AppKindsFile(
    val packages: Map<String, AppKind> = emptyMap(),
    val ignored: List<String> = emptyList(),
)

/** 순수 규칙: 큐레이션 표 → 기본 런처 → 앱 카테고리 → OTHER. */
internal object AppKindRules {
    /** [category]는 `ApplicationInfo.category` 값(없으면 null). */
    fun resolve(packageName: String, curated: Map<String, AppKind>, homePackage: String?, category: Int?): AppKind {
        curated[packageName]?.let { return it }
        if (packageName == homePackage) return AppKind.LAUNCHER
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> AppKind.GAME
            ApplicationInfo.CATEGORY_VIDEO -> AppKind.VIDEO
            ApplicationInfo.CATEGORY_SOCIAL -> AppKind.SOCIAL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppKind.STUDY_WORK
            else -> AppKind.OTHER
        }
    }
}

/**
 * 앱 종류 판별(안드로이드). `QUERY_ALL_PACKAGES` 없이 `<queries>`의 MAIN/LAUNCHER로 보이는 앱만 카테고리를 읽는다.
 * 결과는 캐시한다.
 */
@Singleton
class AndroidAppKindResolver @Inject constructor(@ApplicationContext private val context: Context) : AppKindLookup {
    private val json = Json { ignoreUnknownKeys = true }
    private val file: AppKindsFile by lazy {
        runCatching {
            context.assets.open(ASSET).bufferedReader().use { json.decodeFromString<AppKindsFile>(it.readText()) }
        }.getOrDefault(AppKindsFile())
    }
    private val cache = ConcurrentHashMap<String, AppKind>()
    private val homePackage: String? by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    /** 시스템 UI처럼 앱 전환으로 세지 않는 패키지인지. */
    fun isIgnored(packageName: String): Boolean = packageName in file.ignored

    override fun kindOf(packageName: String): AppKind = cache.getOrPut(packageName) {
        val category = runCatching { context.packageManager.getApplicationInfo(packageName, 0).category }.getOrNull()
        AppKindRules.resolve(packageName, file.packages, homePackage, category)
    }

    private companion object {
        const val ASSET = "app_kinds.json"
    }
}
