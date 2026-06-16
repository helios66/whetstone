package com.unpopulardev.whetstone.build

import com.android.build.api.dsl.CommonExtension
import com.unpopulardev.whetstone.build.Dependency.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.*


private const val DETEKT_PLUGINS = "detektPlugins"


class BuildPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.configureJava()
        target.configureKotlin()
        target.configureAndroid()
        target.configureDetekt()
        target.configureLint()
    }

    private fun Project.configureJava() = configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    private fun Project.configureKotlin() = configure<KotlinProjectExtension> {
        val config: KotlinJvmCompilerOptions.() -> Unit = {
            // -Xjvm-default=all was deprecated in Kotlin 2.2; the stable typed option is jvmDefault.
            // NO_COMPATIBILITY is the exact equivalent of the old `all` (default methods, no DefaultImpls).
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            freeCompilerArgs.add("-Xassertions=jvm")
            jvmTarget.set(JvmTarget.JVM_11)
        }
        when (this) {
            is KotlinJvmProjectExtension -> compilerOptions(config)
            is KotlinAndroidProjectExtension -> compilerOptions(config)
        }
        if (project.name != "sample") explicitApi()
        // Kotlin compiler JDK. Metro 1.2.1's compiler plugin ships Java 21 bytecode, so the Kotlin
        // daemon must run on JDK 21+ to load it (a JDK 17 daemon fails with UnsupportedClassVersionError
        // on a clean compile). `jvmTarget` above stays JVM_11, so emitted bytecode is unaffected.
        jvmToolchain(21)
    }

    private fun Project.configureAndroid() = plugins.withId("com.android.base") {
        extensions.configure(CommonExtension::class) {
            compileSdk = 36
            defaultConfig.minSdk = 23
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
        // NOTE: core library desugaring is intentionally NOT enabled here. Enabling it in the shared
        // convention would stamp `coreLibraryDesugaringEnabled=true` into every published AAR's
        // metadata, forcing all consumers to enable desugaring too. The library sources use no
        // desugared APIs; only the sample modules do (Compose UI Iterable#forEach), so they enable
        // it locally in their own build.gradle.kts.
    }

    private fun Project.configureDetekt() {
        apply { plugin("io.gitlab.arturbosch.detekt") }

        dependencies {
            add(DETEKT_PLUGINS, libs.findLibrary("detekt-formatting").get())
        }

        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
            buildUponDefaultConfig = true
            autoCorrect = false
            baseline = file("$rootDir/config/detekt/baseline.xml")
            ignoreFailures = false
        }

        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            jvmTarget = "17"
            ignoreFailures = true

            val reportDir = rootProject.layout.buildDirectory.dir("reports/detekt/${project.name}").get().asFile
            reports {
                html.required.set(true)
                html.outputLocation.set(reportDir.resolve("detekt.html"))
                xml.required.set(true)
                xml.outputLocation.set(reportDir.resolve("detekt.xml"))
                sarif.required.set(true)
                sarif.outputLocation.set(reportDir.resolve("detekt.sarif"))
            }

            doFirst {
                reportDir.mkdirs()
            }
        }
    }


    private fun Project.configureLint() {
        plugins.withId("com.android.base") {
            extensions.configure(CommonExtension::class.java) {
                lint {
                    xmlReport = true
                    htmlReport = true

                    val moduleName = project.name
                    val lintReportDir = rootProject.layout.buildDirectory.dir("reports/lint/$moduleName")

                    xmlOutput = lintReportDir.get().file("lint-report.xml").asFile
                    htmlOutput = lintReportDir.get().file("lint-report.html").asFile
                }
            }
        }
    }

}

