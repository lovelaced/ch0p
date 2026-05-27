import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 ships Kotlin built-in: do NOT apply org.jetbrains.kotlin.android.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.ch0p"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.ch0p"
        minSdk = 31
        targetSdk = 36
        versionCode = 13
        versionName = "0.1.12"
        // Sideload builds: arm64 only keeps the APK ~3x smaller. Add ABIs back for Play.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 removed android.kotlinOptions {} — configure Kotlin at the top level instead.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":edit-engine"))
    implementation(project(":ingest"))
    implementation(project(":analysis"))
    implementation(project(":render"))
    implementation(project(":models"))
    implementation(libs.androidx.media3.exoplayer)  // preview playback
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
