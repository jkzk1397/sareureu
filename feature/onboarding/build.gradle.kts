plugins {
    alias(libs.plugins.pause.android.feature)
}

dependencies {
    implementation(project(":core:sensing"))
    implementation(project(":core:intervention"))
}
