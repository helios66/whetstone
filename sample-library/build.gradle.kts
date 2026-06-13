plugins {
    alias(libs.plugins.androidLib)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("com.deliveryhero.whetstone.build")
    id("io.github.helios66.whetstone")
    // Mundus auto-tracing — multi-module: trace this library module too.
    alias(libs.plugins.mundus)
}

mundus {
    // Trace the entire app: top-level prefix captures all library classes too.
    includePackages.set(listOf("com.deliveryhero.whetstone"))
    // Trace suspend functions too (background coroutine work in the ViewModel).
    // 0.11.1 renamed traceSuspendFunctions -> instrumentSuspendFunctions.
    instrumentSuspendFunctions.set(true)
    // 0.4.0 presets: instrument framework callbacks without widening includePackages.
    presets {
        compose.set(true)    // @Composable bodies
        lifecycle.set(true)  // Activity/Fragment lifecycle callbacks
        viewModel.set(true)  // androidx.lifecycle.ViewModel subclasses
        workers.set(true)    // androidx.work.ListenableWorker.doWork
    }
}

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
}

dependencies {

    implementation(libs.androidxCore)
    implementation(libs.androidxAppCompat)
    implementation(libs.material)
    implementation(libs.androidxComposeRuntime)
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeMaterial)
    implementation(libs.androidxLifecycleViewModelCompose)
    implementation(libs.mundusRuntime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
