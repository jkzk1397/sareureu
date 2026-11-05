plugins {
    alias(libs.plugins.pause.jvm.library)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
