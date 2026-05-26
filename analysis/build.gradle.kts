import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.ch0p.analysis"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 31
        ndk { abiFilters += "arm64-v8a" }   // sideload: arm64 only
        externalNativeBuild {
            cmake { cppFlags += "-std=c++17" }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(project(":edit-engine"))
    implementation(project(":ingest"))   // telemetry parsers + fusion
    implementation(libs.mediapipe.tasks.audio)   // YAMNet audio events
    implementation(libs.mediapipe.tasks.vision)  // face detection
    implementation(libs.mediapipe.tasks.genai)   // on-device LLM (Gemma)
    implementation(libs.onnxruntime.android)     // Silero VAD
    implementation(libs.tensorflow.lite)         // NIMA aesthetic

    testImplementation(libs.junit)
}
