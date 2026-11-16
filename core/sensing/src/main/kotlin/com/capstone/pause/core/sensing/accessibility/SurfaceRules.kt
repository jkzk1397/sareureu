package com.capstone.pause.core.sensing.accessibility

import com.capstone.pause.core.model.Surface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 숏폼 화면 판별 규칙(`assets/surface_rules.json`, ADR-0004). 뷰 ID 하나에 의존하지 않도록 앱마다 표지 여러 개를 둔다.
 * 화면 글자는 규칙에 쓰지 않는다(뷰 ID만).
 */
@Serializable
data class SurfaceRuleFile(val version: Int = 0, val rules: List<SurfaceRule> = emptyList())

/** 앱 하나의 규칙. [wholeApp]이면 앱 전체가 숏폼 피드(틱톡). */
@Serializable
data class SurfaceRule(
    val packageName: String,
    val surface: Surface,
    val markers: List<String> = emptyList(),
    val minMarkers: Int = 1,
    val likeIds: List<String> = emptyList(),
    val wholeApp: Boolean = false,
)

/** 규칙 묶음(순수). 규칙이 없는 앱은 앱 단위로 센다. */
class SurfaceRules(rules: List<SurfaceRule>) {
    private val byPackage = rules.associateBy { it.packageName }

    /** 규칙이 있는 앱(정밀 모드에서 사용 기록의 앱 단위 이벤트 대신 접근성 판단을 쓰는 앱). */
    val covered: Set<String> = byPackage.keys

    /** [packageName]의 규칙. */
    fun ruleFor(packageName: String): SurfaceRule? = byPackage[packageName]

    /** 정밀 모드에서 [packageName] 세션 구간에 붙일 화면 종류. 규칙이 없으면 [Surface.APP_UNKNOWN]. */
    fun surfaceOf(packageName: String): Surface = byPackage[packageName]?.surface ?: Surface.APP_UNKNOWN

    /** 읽기와 판별. */
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 비어 있는 규칙(읽기 실패 시: 모든 앱을 앱 단위로). */
        val EMPTY = SurfaceRules(emptyList())

        /** JSON을 읽는다. 모양이 틀리면 [EMPTY]. */
        fun parse(text: String): SurfaceRules = runCatching {
            SurfaceRules(json.decodeFromString(SurfaceRuleFile.serializer(), text).rules)
        }.getOrDefault(EMPTY)

        /**
         * 지금 창이 숏폼 화면인가. [viewIds]는 창 안 뷰 ID의 끝부분(`:id/` 뒤)만.
         * true = 숏폼 화면, false = 같은 앱의 다른 화면, null = 판단할 수 없음(창을 못 읽음) → 앱 단위로 센다.
         */
        fun judge(rule: SurfaceRule, viewIds: Set<String>): Boolean? = when {
            rule.wholeApp -> true
            viewIds.isEmpty() -> null
            else -> rule.markers.count { it in viewIds } >= rule.minMarkers.coerceAtLeast(1)
        }
    }
}
