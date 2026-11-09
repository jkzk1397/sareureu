import java.util.Properties

plugins {
    alias(libs.plugins.pause.android.library)
    alias(libs.plugins.pause.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// App Check 공유 디버그 비밀(작업 5): local.properties의 appCheckDebugSecret을 debug BuildConfig로만 넘긴다.
// 값이 없거나 UUID 모양이 아니면 빈 문자열 → 지금처럼 폰마다 토큰을 만든다. local.properties는 커밋하지 않는다.
val localProperties = Properties()
val localPropertiesFile: File = rootProject.file("local.properties")
if (localPropertiesFile.exists()) localPropertiesFile.inputStream().use { localProperties.load(it) }
val configuredSecret: String = (localProperties.getProperty("appCheckDebugSecret") ?: "").trim()
val uuidPattern = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
val appCheckDebugSecret: String = if (uuidPattern.matches(configuredSecret)) configuredSecret else ""

android {
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "APP_CHECK_DEBUG_SECRET", "\"$appCheckDebugSecret\"")
        }
    }
}

dependencies {
    api(project(":core:ai-engine"))
    implementation(project(":core:data"))
    implementation(project(":core:insights"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Firebase AI Logic(Gemini Developer API) + App Check. google-services.json이 없으면 쓰지 않는다(모의로).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
}
