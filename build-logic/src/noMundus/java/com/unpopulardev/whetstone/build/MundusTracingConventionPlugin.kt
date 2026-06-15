package com.unpopulardev.whetstone.build

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * No-Mundus twin of the `com.unpopulardev.whetstone.mundus` convention plugin, compiled into
 * build-logic when `-Pmundus.present=false` (no GitHub Packages access to `helios66/mundus`).
 *
 * It does nothing: the Mundus compiler plugin and runtime artifact are absent, so no tracing is
 * instrumented. The testbed sources still compile and run because `:sample-library` pulls in the
 * local no-op runtime stubs under `src/mundusStub/java` instead of the real `mundus-runtime`.
 *
 * Kept byte-for-byte API-compatible with the present-mode twin (same id/package/class) so consumer
 * modules apply `id("com.unpopulardev.whetstone.mundus")` unconditionally regardless of the flag.
 */
class MundusTracingConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        // Intentionally empty — mundus.present=false. See the KDoc above.
    }
}
