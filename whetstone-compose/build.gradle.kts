plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.unpopulardev.whetstone.build")
    id("com.vanniktech.maven.publish")
}

android {
    buildFeatures.compose = true
    namespace = "com.unpopulardev.whetstone.compose"
}

dependencies {
    implementation(projects.whetstone)
    implementation(libs.androidxComposeRuntime)
    implementation(libs.androidxLifecycleViewModelCompose)
}
