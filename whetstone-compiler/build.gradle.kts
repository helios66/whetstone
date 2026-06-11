plugins {
    kotlin("jvm")
    id("com.deliveryhero.whetstone.build")
    id("com.vanniktech.maven.publish")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kspApi)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoetKsp)
}
