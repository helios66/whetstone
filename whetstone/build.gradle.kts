plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.unpopulardev.whetstone.build")
    id("dev.zacsweers.metro")
    id("com.vanniktech.maven.publish")
}

dependencies {
    // Whetstone consumers use the JSR-330 javax.inject annotations; expose them transitively.
    // Metro is configured (by the Gradle plugin) to treat them as injection points.
    api(libs.javaxInject)

    implementation(libs.androidxLifecycleRuntime)
    implementation(libs.androidxLifecycleProcess)
    implementation(libs.androidxLifecycleViewModel)
    implementation(libs.androidxLifecycleSavedState)

    implementation(libs.androidxCore)
    implementation(libs.androidxActivity)
    api(libs.androidxFragment)

    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("reflect"))
}
android {
    namespace = "com.unpopulardev.whetstone"
}
