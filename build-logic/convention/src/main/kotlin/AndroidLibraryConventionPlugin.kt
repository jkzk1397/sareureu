import com.android.build.api.dsl.LibraryExtension
import com.capstone.pause.buildlogic.addUnitTestDependencies
import com.capstone.pause.buildlogic.configureLibrary
import com.capstone.pause.buildlogic.libs
import com.capstone.pause.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `pause.android.library`: 안드로이드 core 모듈. AGP 9 내장 Kotlin(kotlin-android 플러그인 적용 안 함). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-library"))
            extensions.configure<LibraryExtension> { configureLibrary(this) }
            addUnitTestDependencies()
        }
    }
}
