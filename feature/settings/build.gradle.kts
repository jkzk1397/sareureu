plugins {
    alias(libs.plugins.pause.android.feature)
}

dependencies {
    implementation(project(":core:intervention"))
    implementation(project(":core:ai"))
    implementation(project(":core:social"))
}
