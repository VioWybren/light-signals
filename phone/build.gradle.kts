import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "nodomain.greensnowapps.lightsignals"
    compileSdk = 36

    defaultConfig {
        applicationId = "nodomain.greensnowapps.lightsignals"
        minSdk = 24
        targetSdk = 36
        versionCode = 363031
        versionName = "3.03"
        // Kotlin versions starts from  versionCode = 363001
        // 36 = targetSdk, 300 = version, 1 - watch
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Enables code-related app optimization. For release - true, debug - false
            isMinifyEnabled = false
            // Enables resource shrinking. For release - true, debug - false
//            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //Datastore
    implementation ("androidx.datastore:datastore-preferences:1.2.1")

    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
    implementation ("androidx.compose.material3:material3:1.4.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    // Hilt support for Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    val lifecycleVersion = "2.10.0"
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose-android:$lifecycleVersion")
    // optional - ProcessLifecycleOwner provides a lifecycle for the whole application process
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")

    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Horologist library
    implementation("com.google.android.horologist:horologist-datalayer-phone:0.7.15")
    implementation("com.google.android.horologist:horologist-datalayer:0.7.15")
}