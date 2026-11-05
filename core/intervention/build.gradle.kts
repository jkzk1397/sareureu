plugins {
    alias(libs.plugins.pause.android.library.compose)
    alias(libs.plugins.pause.android.hilt)
}

dependencies {
    implementation(project(":core:sensing"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
