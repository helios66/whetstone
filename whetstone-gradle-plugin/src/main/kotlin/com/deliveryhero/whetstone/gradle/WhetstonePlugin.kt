package com.deliveryhero.whetstone.gradle

import com.android.build.gradle.api.AndroidBasePlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.hasPlugin
import org.gradle.kotlin.dsl.register

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
        target.enableMetroJavaxInterop()
        // Wire the processor + runtime eagerly. KSP decides whether its per-variant task has work
        // from the `ksp` configuration during its own afterEvaluate, so the dependency must be
        // present before then — adding it in our afterEvaluate is too late and KSP gets skipped.
        target.addCoreDependencies()
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
            target.addAddOnDependencies(extension)
            target.registerDepGraphTask()
        }
    }

    /**
     * Registers `whetstoneDepGraph`, which renders a Mermaid diagram of this module's Whetstone DI
     * contributions from the JSON fragments the processor emits under the KSP generated-resources dir.
     */
    private fun Project.registerDepGraphTask() {
        val fragments = fileTree(layout.buildDirectory.dir("generated/ksp").get().asFile)
        fragments.include("**/resources/whetstone/graph/*.json")
        val reports = layout.buildDirectory.dir("reports/whetstone")
        // Fragments are produced by KSP — make sure it runs first.
        val kspTasks = tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }
        tasks.register<WhetstoneDepGraphTask>("whetstoneDepGraph") {
            group = "whetstone"
            description = "Render a Mermaid diagram of the Whetstone DI graph for this module."
            graphFragments.from(fragments)
            reportDir.set(reports)
            dependsOn(kspTasks)
        }
    }

    /**
     * Whetstone consumers annotate their code with the JSR-330 `javax.inject.*` annotations
     * (`@Inject`, `@Singleton`, `@Qualifier`, …). Metro ignores those by default, so enable the
     * javax interop mode on the `metro` extension. Done reflectively because the Metro Gradle
     * plugin is only on this plugin's runtime classpath (not its compile classpath).
     */
    private fun Project.enableMetroJavaxInterop() {
        val metro = extensions.getByName("metro")
        val interop = metro.javaClass.getMethod("getInterop").invoke(metro)
        @Suppress("UNCHECKED_CAST")
        val includeJavax = interop.javaClass.getMethod("getIncludeJavaxAnnotations").invoke(interop)
            as org.gradle.api.provider.Property<Boolean>
        includeJavax.set(true)
    }

    private fun Project.dependency(moduleId: String): Any {
        val useLocal = findProperty("whetstone.internal.project-dependency").toString().toBoolean()
        return when {
            useLocal -> project(":$moduleId")
            else -> "${BuildConfig.GROUP}:$moduleId:${BuildConfig.VERSION}"
        }
    }

    private fun Project.addCoreDependencies() {
        dependencies {
            add("ksp", dependency("whetstone-compiler"))
            add("implementation", dependency("whetstone"))
        }
    }

    private fun Project.addAddOnDependencies(extension: WhetstoneExtension) {
        dependencies {
            if (extension.addOns.compose.get()) add("implementation", dependency("whetstone-compose"))
            if (extension.addOns.workManager.get()) add("implementation", dependency("whetstone-worker"))
        }
    }

    private companion object {
        const val METRO_PLUGIN_ID = "dev.zacsweers.metro"
        const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
        const val WHETSTONE_EXTENSION = "whetstone"
    }
}
