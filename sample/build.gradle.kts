plugins {
    alias(libs.plugins.androidApp)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("com.unpopulardev.whetstone.build")
    id("com.unpopulardev.whetstone")
    // Mundus auto-tracing convention (build-logic). Applies + configures the Mundus compiler plugin
    // when -Pmundus.present is not false (default true); a pure no-op otherwise. Whole-app tracing
    // config (includePackages + suspend + presets) lives in the convention, not here.
    id("com.unpopulardev.whetstone.mundus")
}

// Whether the private Mundus artifacts are available (GitHub Packages). Default true for the owner's
// tracing testbed; pass -Pmundus.present=false on a clone with no GHP access (the convention no-ops
// and :sample-library compiles against local runtime stubs).
val mundusPresent = (providers.gradleProperty("mundus.present").orNull ?: "true").toBoolean()

whetstone {
    addOns {
        compose.set(true)
        workManager.set(true)
    }
}

android {
    namespace = "com.unpopulardev.whetstone.sample"
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
        applicationId = "com.unpopulardev.whetstone.sample"
        targetSdk = 36
    }

    buildTypes {
        getByName("release") {
            isDefault = true
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Sample-only: the app dexes :sample-library, whose Compose UI uses Iterable#forEach
        // (API 24) under the JDK-21 toolchain. Desugaring keeps it safe on minSdk 23. Not enabled
        // in the shared convention, so it never lands in the published library AARs.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(projects.sampleLibrary)
    coreLibraryDesugaring(libs.desugarJdkLibs)

    implementation(libs.androidxActivity)
    implementation(libs.androidxCore)
    implementation(libs.androidxAppCompat)
    implementation(libs.androidxComposeMaterial)
    implementation(libs.androidxComposeUi)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    // Mundus runtime: required when present because the compiler injects calls to it into the app's
    // bytecode (the sample's own source has no Mundus symbols). Omitted when -Pmundus.present=false.
    // The Mundus plugin auto-adds mundus-compose-tracing (and the heavy Compose CompositionTracer)
    // only when presets.compose AND mundus.composeTracing are both on; release is built with
    // -Pmundus.composeTracing=false so the plugin drops that dep entirely (release traces stay lean).
    if (mundusPresent) {
        implementation(libs.mundusRuntime)
    }
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidxTestCore)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
