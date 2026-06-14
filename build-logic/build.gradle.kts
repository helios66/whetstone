plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSam)
    alias(libs.plugins.detekt)
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
}

// Optional Mundus auto-tracing convention. Two source-compatible twins of the same plugin class
// live under src/mundus/java (applies the real Mundus plugin — needs the GHP artifact) and
// src/noMundus/java (pure no-op). We compile exactly one, picked by -Pmundus.present (default true),
// so a clone without GitHub Packages access can still build by passing -Pmundus.present=false.
val mundusPresent = (providers.gradleProperty("mundus.present").orNull ?: "true").toBoolean()
kotlin.sourceSets.named("main") {
    kotlin.srcDir(if (mundusPresent) "src/mundus/java" else "src/noMundus/java")
}

gradlePlugin {
    plugins.register("buildPlugin") {
        id = "com.deliveryhero.whetstone.build"
        implementationClass = "com.deliveryhero.whetstone.build.BuildPlugin"
    }
    plugins.register("mundusTracing") {
        id = "com.deliveryhero.whetstone.mundus"
        implementationClass = "com.deliveryhero.whetstone.build.MundusTracingConventionPlugin"
    }
}

kotlin.compilerOptions {
    optIn.add("kotlin.ExperimentalStdlibApi")
    // -Xjvm-default=all deprecated in Kotlin 2.2 → typed jvmDefault (NO_COMPATIBILITY == old `all`).
    jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
}

dependencies {
    implementation(gradleKotlinDsl())
    compileOnly(libs.kotlinGradle)
    compileOnly(libs.androidGradle)
    implementation(libs.detekt.gradle)
    // Mundus gradle plugin: on build-logic's runtime classpath so the present-mode convention twin
    // can apply + typed-configure it. Omitted entirely when -Pmundus.present=false (no GHP needed).
    if (mundusPresent) {
        implementation(libs.mundusGradle)
    }
}
