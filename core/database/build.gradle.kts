plugins {
    alias(libs.plugins.pause.android.library)
    alias(libs.plugins.pause.android.hilt)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

android {
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
