import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "dev.healthapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.healthapp"
        // Health Connect needs API 26+; on Android 14+ it is part of the OS.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // ONNX Runtime ships native libs per ABI; phones are arm64, x86_64 is for the emulator.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin.compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    testOptions { unitTests.isReturnDefaultValues = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    // Pulled in by Compose; pinned for its 16 KB-aligned native lib.
    implementation(libs.androidx.graphics.path)
    // Runs the CalorieK food-photo model on the phone (see docs/food-model-benchmark.md).
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
