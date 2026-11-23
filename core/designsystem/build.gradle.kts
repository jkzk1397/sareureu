plugins {
    alias(libs.plugins.pause.android.library.compose)
    alias(libs.plugins.pause.android.screenshot)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
}
