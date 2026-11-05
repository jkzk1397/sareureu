plugins {
    alias(libs.plugins.pause.android.application)
    alias(libs.plugins.pause.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json이 있을 때만 Firebase 플러그인을 적용한다(없어도 빌드된다).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

android {
    defaultConfig {
        applicationId = "com.capstone.pause"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))

    implementation(project(":feature:coach"))
    implementation(project(":feature:friends"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:report"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:today"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
}
