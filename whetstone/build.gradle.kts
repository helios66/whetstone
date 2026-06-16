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

    // Expose Metro's runtime so Whetstone's facade types that alias Metro annotations (e.g. the
    // `@MapKey` typealias) are usable downstream — including in modules that only define map keys
    // and depend on `whetstone` without applying the Whetstone Gradle plugin.
    api(libs.metroRuntime)

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
