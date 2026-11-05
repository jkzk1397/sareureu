plugins {
    alias(libs.plugins.pause.android.feature)
}

dependencies {
    implementation(project(":core:sensing"))
    implementation(project(":core:intervention"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
