plugins {
    alias(libs.plugins.pause.android.feature)
}

dependencies {
    implementation(project(":core:intervention"))
    implementation(project(":core:ai"))
    implementation(project(":core:ai-engine"))
    implementation(project(":core:social"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
}
