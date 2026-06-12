enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pd-whetstone"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        // Mundus (auto-tracing Kotlin compiler plugin) is published to mavenLocal.
        mavenLocal()
    }

    includeBuild("whetstone-gradle-plugin")
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // mundus-runtime
        mavenLocal()
    }
}

include(":sample")
include(":whetstone")
include(":whetstone-compiler")
include(":whetstone-compose")
include(":whetstone-worker")
include(":sample-library")
include(":configuration-cache-test")
