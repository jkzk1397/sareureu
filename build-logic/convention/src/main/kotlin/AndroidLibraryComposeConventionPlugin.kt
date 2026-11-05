import com.android.build.api.dsl.LibraryExtension
import com.capstone.pause.buildlogic.configureCompose
import com.capstone.pause.buildlogic.libs
import com.capstone.pause.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `pause.android.library.compose`: Compose를 쓰는 안드로이드 라이브러리. */
class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("pause.android.library")
            pluginManager.apply(libs.pluginId("kotlin-compose"))
            extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            configureCompose()
        }
    }
}
