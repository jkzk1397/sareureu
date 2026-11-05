plugins {
    alias(libs.plugins.pause.android.library)
    alias(libs.plugins.pause.android.hilt)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
