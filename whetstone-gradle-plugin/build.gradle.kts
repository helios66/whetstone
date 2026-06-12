import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    id("java-gradle-plugin")
    `kotlin-dsl`
    alias(libs.plugins.mavenPublish)
}

loadParentProperties()

fun loadParentProperties() {
    val properties = Properties()
    file("../gradle.properties").inputStream().use { properties.load(it) }

    properties.forEach { (k, v) ->
        val key = k.toString()
        val value = providers.gradleProperty(key).getOrElse(v.toString())
        extra.set(key, value)
    }
}

kotlin {
    // Metro's Gradle plugin (a compile dependency below) ships Java 21 bytecode, so this plugin
    // must also target Java 21.
    jvmToolchain(21)
    explicitApi()
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

gradlePlugin {
    plugins {
        create("whetstone") {
            id = "com.deliveryhero.whetstone"
            implementationClass = "com.deliveryhero.whetstone.gradle.WhetstonePlugin"
        }
    }
}

tasks.named<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

dependencies {
    // The Metro + KSP Gradle plugins are applied by id at execution time; we never reference their
    // classes. Keep them off the compile classpath (their newer Kotlin metadata is unreadable by
    // kotlin-dsl's embedded compiler) but on the runtime classpath so `pluginManager.apply(id)`
    // can resolve them in consuming projects.
    runtimeOnly(libs.metroGradle)
    runtimeOnly(libs.kspGradle)
    compileOnly(libs.androidGradle)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}

val generateBuildConfig by tasks.registering(GenerateBuildConfigTask::class) {
    val props = mapOf(
        "GROUP" to project.property("GROUP").toString(),
        "VERSION" to project.property("VERSION_NAME").toString(),
    )
    properties.set(props)
    generatedSourceDir.set(layout.buildDirectory.dir("generated/wgp/kotlin/main"))
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(generateBuildConfig)
}

// Lazily add the generated source directory to the main source set.
sourceSets.main.get().java.srcDir(generateBuildConfig.flatMap { it.generatedSourceDir })

abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val properties: MapProperty<String, String>

    @get:OutputDirectory
    abstract val generatedSourceDir: DirectoryProperty

    @TaskAction
    fun taskAction() {
        val buildFile = generatedSourceDir.file("com/deliveryhero/whetstone/gradle/BuildConfig.kt")

        buildFile.get().asFile.run {
            parentFile.mkdirs()
            val content = buildString {
                appendLine("package com.deliveryhero.whetstone.gradle")
                appendLine()
                appendLine("internal object BuildConfig {")
                properties.get().forEach { (k, v) -> appendLine("  const val $k = \"$v\"") }
                appendLine("}")
            }
            writeText(content)
        }
    }
}
