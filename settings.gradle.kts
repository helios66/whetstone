enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "pd-whetstone"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        // Mundus is no longer applied via a module `plugins {}` alias — the
        // `com.unpopulardev.whetstone.mundus` build-logic convention applies it. But build-logic's
        // own `mundus-gradle` dependency is resolved against THIS pluginManagement repo set when its
        // plugin classpath is consumed by the modules, so GHP must be declared here too. Gated on
        // -Pmundus.present so a clone without GHP access (the no-op convention twin) doesn't need it.
        if ((providers.gradleProperty("mundus.present").orNull ?: "true").toBoolean()) {
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

    includeBuild("whetstone-gradle-plugin")
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mundus runtime + compose-tracing + compiler — private GitHub Packages registry.
        // Declared only when -Pmundus.present is not false (default true), so a clone without GHP
        // access builds with -Pmundus.present=false against the local runtime stubs instead.
        if ((providers.gradleProperty("mundus.present").orNull ?: "true").toBoolean()) {
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
}

include(":sample")
include(":whetstone")
include(":whetstone-compiler")
include(":whetstone-compose")
include(":whetstone-worker")
include(":sample-library")
include(":configuration-cache-test")
