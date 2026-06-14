@file:Suppress("UnstableApiUsage")

rootProject.name = "build-logic"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mundus gradle plugin (for the optional auto-tracing convention) — private GitHub Packages.
        // Declared only when -Pmundus.present is not false, so a clone without GHP access can still
        // build build-logic (the no-op convention twin needs no Mundus artifact).
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
