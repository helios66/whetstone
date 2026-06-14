plugins {
    alias(libs.plugins.androidLib)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("com.deliveryhero.whetstone.build")
    id("io.github.helios66.whetstone")
    // Mundus auto-tracing convention (build-logic) — multi-module: traces this library too. Applies
    // + configures the Mundus compiler plugin when -Pmundus.present is not false; no-op otherwise.
    id("com.deliveryhero.whetstone.mundus")
}

// Whether the private Mundus artifacts are available (GitHub Packages). Default true for the owner's
// tracing testbed; pass -Pmundus.present=false on a clone with no GHP access — the convention no-ops
// and the testbed sources compile against the local no-op runtime stubs under src/mundusStub/java.
val mundusPresent = (providers.gradleProperty("mundus.present").orNull ?: "true").toBoolean()

android {
    namespace = "com.deliveryhero.whetstone.sample.library"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    if (!mundusPresent) {
        // No real mundus-runtime on the classpath — compile the testbed sources against local
        // no-op stubs of the com.unpopulardev.mundus.runtime API instead.
        sourceSets.getByName("main").java.srcDir("src/mundusStub/java")
    }
}

dependencies {

    implementation(libs.androidxCore)
    implementation(libs.androidxAppCompat)
    implementation(libs.material)
    implementation(libs.androidxComposeRuntime)
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeMaterial)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxLifecycleViewModelCompose)
    // Mundus runtime: provides the tracing annotations + Mundus facade the testbed sources reference,
    // and the compiler injects calls into it. Omitted when -Pmundus.present=false (stubs cover the
    // source references; with the compiler off there are no injected calls to satisfy).
    if (mundusPresent) {
        implementation(libs.mundusRuntime)
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
