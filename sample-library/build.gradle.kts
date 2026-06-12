plugins {
    alias(libs.plugins.androidLib)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinCompose)
    id("com.deliveryhero.whetstone.build")
    id("io.github.helios66.whetstone")
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
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
