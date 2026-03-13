plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.junit4)
    implementation(libs.kotlinx.coroutines.test)
}
