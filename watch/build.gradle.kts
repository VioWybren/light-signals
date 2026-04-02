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
        minSdk = 30
        targetSdk = 36
        versionCode = 363030
        versionName = "3.03"
        // Kotlin versions starts from  versionCode = 353000
        //35 = targetSdk, 300 = version, 0 - watch
    }

    buildTypes {
        release {
            // Enables code-related app optimization. For release - true, debug - false
            isMinifyEnabled = false
//            // Enables resource shrinking. For release - true, debug - false
//            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.runtime)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


    implementation ("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-android:1.10.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")

    // Compose for Wear OS dependencies
    implementation("androidx.wear.compose:compose-material3:1.6.0")
    // Foundation is additive, so you can use the mobile version in your Wear OS app.
    implementation("androidx.wear.compose:compose-foundation:1.6.0")
    // Wear OS preview annotations
    implementation("androidx.wear.compose:compose-ui-tooling:1.6.0")

    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation ("androidx.constraintlayout:constraintlayout-compose:1.1.1")

    //Datastore
    implementation ("androidx.datastore:datastore-preferences:1.2.1")

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
    implementation("com.google.android.horologist:horologist-datalayer-watch:0.7.15")
    implementation("com.google.android.horologist:horologist-datalayer:0.7.15")

    implementation("androidx.core:core-splashscreen:1.2.0")

//
//    // If you are using Compose Navigation, use the Wear OS version (NOT THE MOBILE VERSION).
//    // Uncomment the line below and update the version number.
//    // implementation("androidx.wear.compose:compose-navigation:1.5.0-beta03")
//
//    // Testing
//    testImplementation("junit:junit:4.13.2")
//    androidTestImplementation("androidx.test.ext:junit:1.1.3")
//    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
//    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.0.3")
//    debugImplementation("androidx.compose.ui:ui-tooling:1.4.1")

    // Curved support for sliders & text on a round device
//    implementation ("androidx.wear.compose:compose-material-curved:1.1.0")
}