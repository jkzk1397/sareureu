plugins {
    alias(libs.plugins.pause.android.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:insights"))
    api(project(":core:database"))
    api(project(":core:data"))
    api(project(":core:sensing"))
    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
}
