import com.capstone.pause.buildlogic.lib
import com.capstone.pause.buildlogic.libs
import com.capstone.pause.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** `pause.android.feature`: Compose + Hilt + Navigation 3 + designsystem·data 기본 의존. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("pause.android.library.compose")
            pluginManager.apply("pause.android.hilt")
            pluginManager.apply(libs.pluginId("kotlin-serialization"))
            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:common"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-navigation3-runtime"))
                add("implementation", libs.lib("kotlinx-serialization-json"))
            }
        }
    }
}
