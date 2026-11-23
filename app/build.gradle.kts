import java.util.Properties

plugins {
    alias(libs.plugins.pause.android.application)
    alias(libs.plugins.pause.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// google-services.json이 있을 때만 Firebase 플러그인을 적용한다(없어도 빌드된다).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// 공용 서명 키(6-B): 이 PC에서 빌드한 앱과 GitHub가 빌드한 앱이 같은 키로 서명돼야 서로 덮어 설치된다.
// 키 파일과 비밀번호는 저장소에 두지 않고 local.properties(각자 PC)와 GitHub 비밀값(워크플로가 local.properties로 옮김)에만 둔다.
//   signingStoreFile=…/sareureu-shared.jks   signingStorePassword=…   signingKeyAlias=…   signingKeyPassword=…
// 네 값이 모두 있고 파일이 있으면 디버그·릴리스 모두 그 키로 서명한다. 하나라도 없으면 지금처럼 기본 디버그 키로 빌드된다.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(name: String): String? = localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }

val sharedStoreFile: File? = signingValue("signingStoreFile")?.let(::file)?.takeIf { it.exists() }
val sharedStorePassword: String? = signingValue("signingStorePassword")
val sharedKeyAlias: String? = signingValue("signingKeyAlias")
val sharedKeyPassword: String? = signingValue("signingKeyPassword")
val hasSharedKey: Boolean =
    sharedStoreFile != null && sharedStorePassword != null && sharedKeyAlias != null && sharedKeyPassword != null

android {
    defaultConfig {
        applicationId = "com.capstone.pause"
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        if (hasSharedKey) {
            create("shared") {
                storeFile = sharedStoreFile
                storePassword = sharedStorePassword
                keyAlias = sharedKeyAlias
                keyPassword = sharedKeyPassword
            }
        }
    }
    buildTypes {
        // 공용 키가 없으면 디버그는 기본 디버그 키, 릴리스도 설치해 볼 수 있게 디버그 키로 서명한다.
        val signing = if (hasSharedKey) signingConfigs.getByName("shared") else signingConfigs.getByName("debug")
        debug {
            signingConfig = signing
        }
        release {
            // 용량과 속도: 쓰지 않는 코드와 리소스를 뺀다. keep 규칙은 proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:intervention"))
    implementation(project(":core:model"))
    implementation(project(":core:sensing"))
    implementation(project(":core:social"))

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
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
}
