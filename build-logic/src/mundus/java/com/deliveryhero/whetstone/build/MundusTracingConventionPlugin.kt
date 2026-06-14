package com.deliveryhero.whetstone.build

import com.unpopulardev.mundus.gradle.MundusExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Present-mode twin of the `com.deliveryhero.whetstone.mundus` convention plugin: applies the real
 * Mundus auto-tracing compiler plugin and configures it for the sample testbed.
 *
 * Compiled into build-logic **only** when `-Pmundus.present` is not `false` (the default). When the
 * property is `false` — e.g. a public clone with no GitHub Packages access to `helios66/mundus` —
 * build-logic swaps in the no-op twin under `src/noMundus/java` instead, and never resolves the
 * Mundus gradle artifact. Both twins share this exact id/package/class name so the consuming
 * modules apply `id("com.deliveryhero.whetstone.mundus")` unconditionally.
 *
 * The whole-app tracing config (includePackages + suspend instrumentation + the framework presets)
 * lives here, identical for `:sample` and `:sample-library`. `startupPhases` is a no-op on the
 * library module (no Application/ContentProvider there), so a single uniform config is correct.
 */
class MundusTracingConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply("com.unpopulardev.mundus")
        // build-logic applies samWithReceiver for org.gradle.api.HasImplicitReceiver, so the Action
        // SAMs below are receiver lambdas — `this` is the extension, no named param.
        target.extensions.configure(MundusExtension::class.java) {
            // Top-level prefix captures sample classes AND the generated Metro/Whetstone DI graph.
            includePackages.set(listOf("com.deliveryhero.whetstone"))
            // Trace suspend functions too (background coroutine work in the ViewModel).
            instrumentSuspendFunctions.set(true)
            // Instrument framework callbacks without widening includePackages.
            // 0.13.0 split the old `compose` preset into composeBodyTracing (@Composable function
            // bodies) and composeRuntimeTracing (the heavy androidx CompositionTracer). Both on for
            // debug; release/disabled/e2e drop the heavy one via -Pmundus.compose.tracing=false,
            // which overrides composeRuntimeTracing (the property wins over the preset).
            presets {
                composeBodyTracing.set(true)    // @Composable bodies
                composeRuntimeTracing.set(true) // heavy androidx CompositionTracer (prop-overridable)
                lifecycle.set(true)     // Activity/Fragment onCreate/onStart/onResume/onCreateView
                viewModel.set(true)     // androidx.lifecycle.ViewModel subclasses
                workers.set(true)       // androidx.work.ListenableWorker.doWork
                startupPhases.set(true) // Application.onCreate / ContentProvider / androidx.startup.Initializer
            }
        }
    }
}
