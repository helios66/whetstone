package com.deliveryhero.whetstone.gradle

import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.hasPlugin

/**
 * Applies Metro (the dependency-injection compiler plugin) and KSP (which runs Whetstone's
 * `whetstone-compiler` processor to translate Whetstone annotations such as `@ContributesViewModel`
 * into Metro contributions), then wires up the Whetstone runtime dependencies.
 */
public class WhetstonePlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create<WhetstoneExtension>(WHETSTONE_EXTENSION)
        target.pluginManager.apply(METRO_PLUGIN_ID)
        target.pluginManager.apply(KSP_PLUGIN_ID)
        target.afterEvaluate {
            if (!target.plugins.hasPlugin(AndroidBasePlugin::class)) {
                throw GradleException(
                    """
                    Whetstone plugin was applied to project '${target.path}', but could not find
                    a corresponding Android plugin.
                    Whetstone can only be applied to Android projects!
                    """.trimIndent().replace('\n', ' ')
                )
            }
            target.addDependencies(extension)
        }
    }

    private fun Project.addDependencies(extension: WhetstoneExtension) {
        val useLocal = findProperty("whetstone.internal.project-dependency").toString().toBoolean()

        fun dependency(moduleId: String): Any = when {
            useLocal -> project(":$moduleId")
            else -> "${BuildConfig.GROUP}:$moduleId:${BuildConfig.VERSION}"
        }

        fun DependencyHandlerScope.ksp(moduleId: String) = add("ksp", dependency(moduleId))
        fun DependencyHandlerScope.implementation(moduleId: String) =
            add("implementation", dependency(moduleId))

        dependencies {
            ksp("whetstone-compiler")
            implementation("whetstone")

            if (extension.addOns.compose.get()) implementation("whetstone-compose")
            if (extension.addOns.workManager.get()) implementation("whetstone-worker")
        }
    }

    private companion object {
        const val METRO_PLUGIN_ID = "dev.zacsweers.metro"
        const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
        const val WHETSTONE_EXTENSION = "whetstone"
    }
}
