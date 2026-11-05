pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pause"

include(":app")

include(":core:ai")
include(":core:ai-engine")
include(":core:common")
include(":core:data")
include(":core:database")
include(":core:designsystem")
include(":core:insights")
include(":core:intervention")
include(":core:model")
include(":core:sensing")
include(":core:social")

include(":feature:coach")
include(":feature:friends")
include(":feature:onboarding")
include(":feature:report")
include(":feature:settings")
include(":feature:today")
