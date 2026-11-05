package com.capstone.pause.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** 버전 카탈로그 `libs`. 컨벤션 플러그인은 버전을 직접 쓰지 않고 여기서만 읽는다. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** 카탈로그의 라이브러리 별칭으로 의존성 좌표를 얻는다. */
fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/** 카탈로그의 플러그인 ID. */
fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId
