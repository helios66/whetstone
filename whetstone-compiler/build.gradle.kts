plugins {
    kotlin("jvm")
    id("com.unpopulardev.whetstone.build")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kspApi)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoetKsp)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.kctforkCore)
    testImplementation(libs.kctforkKsp)
    // So generated code that references Metro annotations resolves during the test compilation.
    testImplementation(libs.metroRuntime)
}
