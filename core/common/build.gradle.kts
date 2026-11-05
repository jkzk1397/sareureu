plugins {
    alias(libs.plugins.pause.jvm.library)
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.core)
}
