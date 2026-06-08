plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.localqwen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nabd.ai.local.mtp_engine"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "0.2.15-isolated"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // Dagger Hilt
    implementation("com.google.dagger:hilt-android:2.54")
    kapt("com.google.dagger:hilt-compiler:2.54")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // AI / ML
    implementation("com.google.ai.edge.litert:litert-api:1.1.0")
    implementation("com.google.ai.edge.litert:litert-support:1.1.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mediapipe:tasks-text:0.10.14") {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite")
    }

    // AndroidX
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // UI Utilities
    implementation("io.noties.markwon:core:4.6.2")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Room
    implementation("androidx.room:room-runtime:2.7.0-alpha13")
    implementation("androidx.room:room-ktx:2.7.0-alpha13")
    kapt("androidx.room:room-compiler:2.7.0-alpha13")

    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // Testing
    testImplementation("junit:junit:4.13.2")
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val copyApkTask = tasks.register<Copy>("copyApkToDownloads") {
            description = "Copies the debug APK to the Downloads directory."
            
            // Access the APK directory correctly using the Artifact API
            val apkDir = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
            
            from(apkDir) {
                include("**/*.apk")
                rename { "nabd-latest-dev.apk" }
            }
            
            val destDir = File("/storage/emulated/0/Download/")
            into(destDir)
            
            doFirst {
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
            }
            
            doLast {
                val copiedApk = File(destDir, "nabd-latest-dev.apk")
                if (copiedApk.exists()) {
                    println("APK copied successfully:\n${copiedApk.absolutePath}")
                } else {
                    println("APK not found:\n${copiedApk.absolutePath}")
                }
            }
        }
        
        project.tasks.configureEach {
            if (name == "assembleDebug") {
                finalizedBy(copyApkTask)
            }
        }
    }
}
