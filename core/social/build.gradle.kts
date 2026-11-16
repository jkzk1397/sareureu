import java.util.Properties

plugins {
    alias(libs.plugins.pause.android.library)
    alias(libs.plugins.pause.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// 친구 서버 주소: 코드에 쓰지 않고 각자 local.properties의 socialBaseUrl에서(커밋하지 않음).
// 비어 있으면 지금처럼 가상 친구로 동작한다. 팀원 서버로 바꿀 때도 이 한 줄만 바꾼다(server/README.md).
val socialBaseUrl: String = rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }.getProperty("socialBaseUrl")
}.orEmpty().trim().trimEnd('/')

android {
    buildFeatures { buildConfig = true }
    defaultConfig { buildConfigField("String", "SOCIAL_BASE_URL", "\"$socialBaseUrl\"") }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:insights"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.android)
    // 하루 합계 올리기(5-2). 카탈로그에 이미 있는 항목만 쓴다(core:intervention과 같음).
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
