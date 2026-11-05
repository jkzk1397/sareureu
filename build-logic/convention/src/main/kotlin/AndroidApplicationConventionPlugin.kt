import com.android.build.api.dsl.ApplicationExtension
import com.capstone.pause.buildlogic.addUnitTestDependencies
import com.capstone.pause.buildlogic.configureApplication
import com.capstone.pause.buildlogic.configureCompose
import com.capstone.pause.buildlogic.libs
import com.capstone.pause.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** `pause.android.application`: app 모듈. AGP 9 내장 Kotlin + Compose. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-application"))
            pluginManager.apply(libs.pluginId("kotlin-compose"))
            extensions.configure<ApplicationExtension> {
                configureApplication(this)
                buildFeatures.compose = true
            }
            configureCompose()
            addUnitTestDependencies()
        }
    }
}
