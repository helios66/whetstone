enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pd-whetstone"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        // Mundus (auto-tracing Kotlin compiler plugin) — private GitHub Packages registry.
        // Creds come from gpr.user/gpr.key (~/.gradle/gradle.properties) or GITHUB_ACTOR/TOKEN.
        maven {
            name = "MundusGitHubPackages"
            url = uri("https://maven.pkg.github.com/helios66/mundus")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    includeBuild("whetstone-gradle-plugin")
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mundus runtime + compose-tracing — private GitHub Packages registry (same creds).
        maven {
            name = "MundusGitHubPackages"
            url = uri("https://maven.pkg.github.com/helios66/mundus")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

include(":sample")
include(":whetstone")
include(":whetstone-compiler")
include(":whetstone-compose")
include(":whetstone-worker")
include(":sample-library")
include(":configuration-cache-test")
