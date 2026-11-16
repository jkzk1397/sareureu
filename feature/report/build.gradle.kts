plugins {
    alias(libs.plugins.pause.android.feature)
    alias(libs.plugins.pause.android.screenshot)
}

dependencies {
    implementation(project(":core:ai"))
}
