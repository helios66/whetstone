plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.deliveryhero.whetstone.build")
    id("dev.zacsweers.metro")
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(projects.whetstone)
    api(libs.androidxWorkRuntime)

    testImplementation(kotlin("test-junit"))
}
android {
    namespace = "com.deliveryhero.whetstone.worker"
}
