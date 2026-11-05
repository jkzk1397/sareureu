package com.capstone.pause.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** 프로젝트 공통 SDK 값 (ADR-0007, 03 문서 6절). */
object PauseSdk {
    const val MIN = 29
    const val TARGET = 36
    const val COMPILE = 37
    const val NAMESPACE = "com.capstone.pause"
}

/** `:core:ai-engine` → `com.capstone.pause.core.ai.engine` */
fun Project.pauseNamespace(): String = PauseSdk.NAMESPACE + path.replace(':', '.').replace('-', '.')

internal fun Project.configureApplication(ext: ApplicationExtension) {
    ext.compileSdk = PauseSdk.COMPILE
    ext.namespace = PauseSdk.NAMESPACE
    ext.defaultConfig.minSdk = PauseSdk.MIN
    ext.defaultConfig.targetSdk = PauseSdk.TARGET
    ext.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ext.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    ext.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    ext.testOptions.unitTests.isIncludeAndroidResources = true
    ext.testOptions.unitTests.isReturnDefaultValues = true
    configureTests()
}

internal fun Project.configureLibrary(ext: LibraryExtension) {
    ext.compileSdk = PauseSdk.COMPILE
    ext.namespace = pauseNamespace()
    ext.defaultConfig.minSdk = PauseSdk.MIN
    ext.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ext.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    ext.compileOptions.targetCompatibility = JavaVersion.VERSION_17
    ext.testOptions.unitTests.isIncludeAndroidResources = true
    ext.testOptions.unitTests.isReturnDefaultValues = true
    ext.lint.targetSdk = PauseSdk.TARGET
    configureTests()
}

internal fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "2g"
        // Hilt가 테스트 소스를 생성해서, 아직 테스트가 없는 모듈도 '테스트 소스 있음'으로 보인다.
        // 테스트를 끄는 설정이 아니다: 있는 테스트는 그대로 돌고 실패하면 빌드가 깨진다.
        failOnNoDiscoveredTests.set(false)
    }
}

/** JUnit4 + kotlin.test + 코루틴 테스트 + Turbine (03 문서 8절: 단언 라이브러리 추가 없음). */
internal fun Project.addUnitTestDependencies() {
    dependencies {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("kotlin-test"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("turbine"))
    }
}
