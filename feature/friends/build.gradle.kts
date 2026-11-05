plugins {
    alias(libs.plugins.pause.android.feature)
}

dependencies {
    implementation(project(":core:social"))
    implementation(project(":core:ai"))
}
