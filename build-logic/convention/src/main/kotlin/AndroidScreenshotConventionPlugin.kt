import com.capstone.pause.buildlogic.lib
import com.capstone.pause.buildlogic.libs
import com.capstone.pause.buildlogic.pluginId
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `pause.android.screenshot`: 에뮬레이터 없이 Compose 화면을 PNG로 뽑는다(Roborazzi + Robolectric).
 * `./gradlew recordRoborazziDebug` 하나로 모든 모듈의 PNG가 루트 `build/screenshots/<모듈>/`에 모인다.
 * 기준 이미지 비교(verify)는 하지 않는다. PNG는 커밋하지 않는다(docs/TEAM_SETUP.md).
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("roborazzi"))
            // 모듈마다 다른 폴더: Gradle 9에서 여러 태스크가 한 출력 폴더를 같이 쓰면 실패한다.
            val folder = path.removePrefix(":").replace(':', '-')
            extensions.configure<RoborazziExtension> {
                outputDir.set(rootProject.layout.buildDirectory.dir("screenshots/$folder"))
            }
            dependencies {
                add("testImplementation", libs.lib("roborazzi"))
                add("testImplementation", libs.lib("roborazzi-compose"))
            }
        }
    }
}
