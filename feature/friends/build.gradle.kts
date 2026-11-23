plugins {
    alias(libs.plugins.pause.android.feature)
    alias(libs.plugins.pause.android.screenshot)
}

dependencies {
    implementation(project(":core:social"))
    implementation(project(":core:ai"))
    implementation(libs.zxing.core)
}
