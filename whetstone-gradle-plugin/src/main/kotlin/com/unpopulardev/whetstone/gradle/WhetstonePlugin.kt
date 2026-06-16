package com.unpopulardev.whetstone.gradle

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
        // Wired lazily from the extension flag (set later in the consumer's `whetstone {}` block), so
        // Metro reads the resolved value at its own configuration time regardless of ordering.
        target.enableMetroDaggerInterop(extension.addOns.daggerInterop)
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
        val fragments = files()
        val kspDependencies = mutableListOf<Any>()

        // Collect a project's KSP graph fragment(s) + the ksp tasks that produce them.
        fun collectFrom(source: Project) {
            val tree = source.fileTree(source.layout.buildDirectory.dir("generated/ksp").get().asFile)
            tree.include("**/resources/whetstone/graph/*.json")
            fragments.from(tree)
            kspDependencies.add(
                source.tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }
            )
        }

        collectFrom(this)
        // Also fold in project-dependency modules so an app module renders the WHOLE-app graph
        // (e.g. :sample picks up :sample-library's ViewModel contributions).
        listOf("implementation", "api").forEach { configurationName ->
            configurations.findByName(configurationName)?.dependencies
                ?.withType(org.gradle.api.artifacts.ProjectDependency::class.java)
                ?.forEach { dependency ->
                    // ProjectDependency.path (Gradle 8.11+) replaces the deprecated dependencyProject;
                    // resolve it via the non-deprecated Project.project(path).
                    collectFrom(project(dependency.path))
                }
        }

        val reports = layout.buildDirectory.dir("reports/whetstone")
        tasks.register<WhetstoneDepGraphTask>("whetstoneDepGraph") {
            group = "whetstone"
            description = "Render a Mermaid diagram of the Whetstone DI graph for this module."
            graphFragments.from(fragments)
            reportDir.set(reports)
            dependsOn(kspDependencies)
        }
    }

    /**
     * Whetstone consumers annotate their code with the JSR-330 `javax.inject.*` annotations
     * (`@Inject`, `@Singleton`, `@Qualifier`, …). Metro ignores those by default, so enable the
     * javax interop mode on the `metro` extension. Done reflectively because the Metro Gradle
     * plugin is only on this plugin's runtime classpath (not its compile classpath).
     *
     * NOTE: Whetstone's Anvil-compatible `injector.*` contribution annotations are NOT handled via
     * Metro's annotation interop — that path doesn't emit cross-module contribution hints, so a
     * library-module contribution wouldn't merge into the app graph. They are instead translated to
     * native Metro `@ContributesTo` modules by the `whetstone-compiler` KSP processor, which does.
     */
    private fun Project.enableMetroJavaxInterop() {
        metroInteropBoolProperty("getIncludeJavaxAnnotations").set(true)
    }

    /**
     * Opt-in Dagger interop, driven by `whetstone { addOns { daggerInterop.set(true) } }`. Mirrors
     * Metro's `interop { includeDagger() }`: it flips both the annotation-recognition flag (so
     * `@Module`, `@Provides`, `dagger.MapKey`, … are understood) and the runtime-interop flag (so
     * `dagger.Lazy` and friends resolve at injection sites). This lets a codebase migrating off
     * Dagger keep those imports instead of rewriting hundreds of files.
     *
     * The flag is wired as a provider so Metro reads the consumer-supplied value at its own
     * configuration time — no afterEvaluate ordering assumptions.
     */
    private fun Project.enableMetroDaggerInterop(enabled: org.gradle.api.provider.Provider<Boolean>) {
        metroInteropBoolProperty("getIncludeDaggerAnnotations").set(enabled)
        metroInteropBoolProperty("getEnableDaggerRuntimeInterop").set(enabled)
    }

    /**
     * Reflectively fetch a `Property<Boolean>` off the `metro` extension's `interop` handler. Done
     * reflectively because the Metro Gradle plugin is only on this plugin's runtime classpath (not
     * its compile classpath).
     */
    @Suppress("UNCHECKED_CAST")
    private fun Project.metroInteropBoolProperty(getter: String): org.gradle.api.provider.Property<Boolean> {
        val metro = extensions.getByName("metro")
        val interop = metro.javaClass.getMethod("getInterop").invoke(metro)
        return interop.javaClass.getMethod(getter).invoke(interop)
            as org.gradle.api.provider.Property<Boolean>
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
