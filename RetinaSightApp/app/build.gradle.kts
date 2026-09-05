plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.retinasight.ai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retinasight.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            // The iQOO 15 is arm64. ONNX Runtime ships four ABIs by default,
            // which triples the APK for architectures this app will never run on.
            abiFilters += "arm64-v8a"
        }

        // Keep every localized strings.xml in the APK. Without this, resource
        // shrinking can strip languages the user picks at runtime.
        resourceConfigurations += listOf(
            "en", "hi", "mr", "ta", "te", "kn", "bn", "gu", "ml", "pa", "or"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Required for BuildConfig.DEBUG, which guards MockInferenceEngine
        // from ever running in a release build.
        buildConfig = true
    }

    androidResources {
        // Keep the model uncompressed so it can be read straight from the APK
        // instead of being inflated into memory on every cold start.
        noCompress += "onnx"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // On-device inference for the trained dr-v2 grader (assets/dr-v2.onnx)
    implementation(libs.onnxruntime.android)

    // On-device LLM (Qwen2.5-1.5B-Instruct, Apache-2.0) used ONLY to restate a
    // result the CNN already decided. Never to decide anything.
    implementation(libs.mediapipe.tasks.genai)
}
