plugins {
    alias(libs.plugins.androidApp)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("com.deliveryhero.whetstone.build")
    id("io.github.helios66.whetstone")
    // Mundus: auto-tracing Kotlin compiler plugin (version-locked to Kotlin 2.3.20).
    alias(libs.plugins.mundus)
}

whetstone {
    addOns {
        compose.set(true)
        workManager.set(true)
    }
}

mundus {
    // Trace the entire app: top-level prefix captures sample classes AND the
    // generated Metro/Whetstone DI graph code (all under com.deliveryhero.whetstone.*).
    includePackages.set(listOf("com.deliveryhero.whetstone"))
    // Trace suspend functions too (background coroutine work in the ViewModel).
    traceSuspendFunctions.set(true)
    // 0.4.0 presets: instrument framework callbacks without widening includePackages.
    presets {
        compose.set(true)       // @Composable bodies
        lifecycle.set(true)     // Activity/Fragment onCreate/onStart/onResume/onCreateView
        viewModel.set(true)     // androidx.lifecycle.ViewModel subclasses
        workers.set(true)       // androidx.work.ListenableWorker.doWork
        startupPhases.set(true) // 0.5.0: Application.onCreate / ContentProvider / androidx.startup.Initializer
    }
}

android {
    namespace = "com.deliveryhero.whetstone.sample"
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
        applicationId = "com.deliveryhero.whetstone.sample"
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
    }
}

dependencies {
    implementation(projects.sampleLibrary)

    implementation(libs.androidxActivity)
    implementation(libs.androidxCore)
    implementation(libs.androidxAppCompat)
    implementation(libs.androidxComposeMaterial)
    implementation(libs.androidxComposeUi)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.mundusRuntime)
    // NOTE: the Mundus plugin auto-adds mundus-compose-tracing when presets.compose is on, and
    // 0.6.0 auto-installs the Compose CompositionTracer from the classpath (the old
    // BuildConfig.DEBUG gate on install() is bypassed). To keep release traces lean we exclude
    // the compose-tracing module from the release classpath (see configurations block below).
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidxTestCore)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// Mundus 0.6.0 auto-installs the Compose CompositionTracer whenever mundus-compose-tracing is on
// the classpath (no install() call needed). Keep it out of release so release traces stay lean —
// debug still gets the full composition trace.
configurations.matching { it.name.startsWith("release") }.configureEach {
    exclude(group = "com.unpopulardev.mundus", module = "mundus-compose-tracing")
    exclude(group = "com.unpopulardev.mundus", module = "mundus-compose-tracing-android")
}
