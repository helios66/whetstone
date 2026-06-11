package com.deliveryhero.whetstone

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Functional tests for Whetstone Gradle plugin configuration cache compatibility.
 *
 * These tests verify that the plugin properly supports Gradle's configuration cache,
 * ensuring builds can be cached and reused for improved performance.
 *
 * These tests run against the actual sample-library project in the repository.
 */
class ConfigurationCacheTest {

    private val projectRoot = File(System.getProperty("user.dir")).parentFile

    @Test
    fun `whetstone plugin is configuration cache compatible`() {
        // First build - should store or reuse configuration cache
        // Note: Not using :clean to avoid race conditions with parallel CI tasks
        val firstResult = GradleRunner.create()
            .withProjectDir(projectRoot)
            .withArguments(
                ":sample-library:compileMetroReleaseKotlin",
                "--configuration-cache"
            )
            .forwardOutput()
            .build()

        val firstOutput = firstResult.output
        val hasConfigCache = firstOutput.contains("Configuration cache entry stored") ||
                firstOutput.contains("Reusing configuration cache")

        assertTrue(
            hasConfigCache,
            "First build should either store or reuse configuration cache"
        )

        val firstOutcome = firstResult.task(":sample-library:compileMetroReleaseKotlin")?.outcome
        assertTrue(
            firstOutcome == TaskOutcome.SUCCESS || firstOutcome == TaskOutcome.UP_TO_DATE,
            "Kotlin compilation should succeed or be up-to-date (got: $firstOutcome)"
        )

        // Second build - should reuse configuration cache
        val secondResult = GradleRunner.create()
            .withProjectDir(projectRoot)
            .withArguments(
                ":sample-library:compileMetroReleaseKotlin",
                "--configuration-cache"
            )
            .forwardOutput()
            .build()

        assertTrue(
            secondResult.output.contains("Reusing configuration cache"),
            "Second build should reuse configuration cache"
        )

        val secondOutcome = secondResult.task(":sample-library:compileMetroReleaseKotlin")?.outcome
        assertTrue(
            secondOutcome == TaskOutcome.SUCCESS || secondOutcome == TaskOutcome.UP_TO_DATE,
            "Second build should succeed or be up-to-date (got: $secondOutcome)"
        )
    }

    @Test
    fun `whetstone KSP processor emits Metro contributions under configuration cache`() {
        // Build the AAR with configuration cache; this drives the KSP processor that translates
        // Whetstone annotations (e.g. @ContributesViewModel) into Metro @ContributesTo modules.
        // Note: Not using :clean to avoid race conditions with parallel CI tasks
        val result = GradleRunner.create()
            .withProjectDir(projectRoot)
            .withArguments(
                ":sample-library:bundleMetroReleaseAar",
                "--configuration-cache"
            )
            .forwardOutput()
            .build()

        val outcome = result.task(":sample-library:bundleMetroReleaseAar")?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
            "AAR bundling should succeed with configuration cache (got: $outcome)"
        )

        // Verify the KSP-generated Metro contribution exists for the contributed ViewModel.
        val generatedModule = File(
            projectRoot,
            "sample-library/build/generated/ksp/metroRelease/kotlin/" +
                "com/deliveryhero/whetstone/sample/library/MainViewModel_WhetstoneModule.kt"
        )
        assertTrue(
            generatedModule.exists(),
            "KSP should generate MainViewModel_WhetstoneModule.kt at ${generatedModule.path}"
        )

        val generated = generatedModule.readText()
        assertTrue(
            generated.contains("@ContributesTo") &&
                generated.contains("@ClassKey(MainViewModel::class)") &&
                generated.contains(": ViewModel"),
            "Generated module should bind MainViewModel into the Metro ViewModel multibinding. " +
                "Content: $generated"
        )
    }

    @Test
    fun `whetstone KSP processor emits the full set of Metro contribution shapes`() {
        val result = GradleRunner.create()
            .withProjectDir(projectRoot)
            .withArguments(":sample:assembleMetroDebug", "--configuration-cache")
            .forwardOutput()
            .build()

        val outcome = result.task(":sample:assembleMetroDebug")?.outcome
        assertTrue(
            outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE,
            "Sample app should assemble with configuration cache (got: $outcome)"
        )

        val genDir = File(
            projectRoot,
            "sample/build/generated/ksp/metroDebug/kotlin/com/deliveryhero/whetstone/sample"
        )

        // 1. Root @DependencyGraph generated for @ContributesAppInjector(generateAppComponent = true)
        val appGraph = File(genDir, "GeneratedApplicationComponent.kt").readText()
        assertTrue(
            appGraph.contains("@DependencyGraph(scope = ApplicationScope::class)") &&
                appGraph.contains(": ApplicationComponent") &&
                appGraph.contains("public fun interface Factory") &&
                appGraph.contains("@Provides") &&
                appGraph.contains("application: Application"),
            "GeneratedApplicationComponent should be a Metro @DependencyGraph with a @Provides factory. " +
                "Content: $appGraph"
        )

        // 2. Member-injector binding for an @AutoInjectorBinding annotation (@ContributesServiceInjector)
        val serviceModule = File(genDir, "MainService_WhetstoneModule.kt").readText()
        assertTrue(
            serviceModule.contains("@ContributesTo(ServiceScope::class)") &&
                serviceModule.contains("MembersInjector<MainService>") &&
                serviceModule.contains("MembersInjector<*>") &&
                serviceModule.contains("@ClassKey(MainService::class)"),
            "MainService module should bind MembersInjector<MainService> into the injector map. " +
                "Content: $serviceModule"
        )

        // 3. Instance binding for an @AutoInstanceBinding annotation (@ContributesWorker)
        val workerModule = File(genDir, "MainWorker_WhetstoneModule.kt").readText()
        assertTrue(
            workerModule.contains("@ContributesTo(WorkerScope::class)") &&
                workerModule.contains(": ListenableWorker") &&
                workerModule.contains("@ClassKey(MainWorker::class)"),
            "MainWorker module should bind MainWorker into the worker multibinding. " +
                "Content: $workerModule"
        )

        // 4. Activity member-injector binding (@ContributesActivityInjector)
        val activityModule = File(genDir, "MainActivity_WhetstoneModule.kt").readText()
        assertTrue(
            activityModule.contains("@ContributesTo(ActivityScope::class)") &&
                activityModule.contains("MembersInjector<MainActivity>"),
            "MainActivity module should bind MembersInjector<MainActivity> into ActivityScope. " +
                "Content: $activityModule"
        )

        // 5. Fragment instance binding (@ContributesFragment)
        val fragmentModule = File(genDir, "MainFragment_WhetstoneModule.kt").readText()
        assertTrue(
            fragmentModule.contains("@ContributesTo(FragmentScope::class)") &&
                fragmentModule.contains(": Fragment") &&
                fragmentModule.contains("@ClassKey(MainFragment::class)"),
            "MainFragment module should bind MainFragment into the fragment multibinding. " +
                "Content: $fragmentModule"
        )

        // 6. Generic @ContributesInjector(scope) member-injector binding
        val customModule = File(genDir, "CustomInjectable_WhetstoneModule.kt").readText()
        assertTrue(
            customModule.contains("@ContributesTo(ActivityScope::class)") &&
                customModule.contains("MembersInjector<CustomInjectable>"),
            "CustomInjectable module should bind MembersInjector<CustomInjectable> into ActivityScope. " +
                "Content: $customModule"
        )
    }
}
