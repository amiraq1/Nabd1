plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.localqwen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.localqwen"
        minSdk = 26
        targetSdk = 33
        versionCode = 4
        versionName = "1.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.mediapipe:tasks-text:latest.release")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
}
