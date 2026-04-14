plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

val supportedBaselineReleaseAbis = setOf("arm64-v8a", "x86_64")
val requestedBaselineReleaseAbi = providers.gradleProperty("looktubeBaselineReleaseAbi")
    .orNull
    ?.trim()
    ?.takeIf(String::isNotBlank)

check(
    requestedBaselineReleaseAbi == null ||
        requestedBaselineReleaseAbi in supportedBaselineReleaseAbis,
) {
    "looktubeBaselineReleaseAbi must be one of ${supportedBaselineReleaseAbis.joinToString()} when provided."
}

android {
    namespace = "com.looktube.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.looktube.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "targetTier"

    productFlavors {
        create("baseline") {
            dimension = "targetTier"
            minSdk = 28
            ndk {
                if (requestedBaselineReleaseAbi == null) {
                    abiFilters += listOf("arm64-v8a", "x86_64")
                } else {
                    abiFilters += requestedBaselineReleaseAbi
                }
            }
        }
        create("highspec") {
            dimension = "targetTier"
            applicationIdSuffix = ".highspec"
            versionNameSuffix = "-highspec"
            minSdk = 35
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    lint {
        disable += "PropertyEscape"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }


    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.enabledSdks", "35")
            }
        }

        managedDevices {
            localDevices {
                create("pixel6Api36") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "google"
                    testedAbi = "x86_64"
                }
            }
        }
    }
}

roborazzi {
    outputDir.set(file("src/screenshots"))
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:heuristics"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.cast)
    implementation(libs.media3.session)
    add("highspecImplementation", libs.moonshine.voice)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
