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
    compileSdk = 37

    defaultConfig {
        applicationId = "nodomain.greensnowapps.lightsignals"
        minSdk = 24
        targetSdk = 37
        versionCode = providers.gradleProperty("phoneVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()
        testInstrumentationRunner = "nodomain.greensnowapps.lightsignals.testing.LightSignalsTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        create("uitest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
        }
        release {
            // Enables code-related app optimization. For release - true, debug - false
            isMinifyEnabled = false
            // Enables resource shrinking. For release - true, debug - false
//            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    testBuildType = "uitest"
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

    sourceSets.getByName("test") {
        java.srcDir(rootProject.file("test-support/src/main/kotlin"))
        resources.srcDir(rootProject.file("test-support/src/main/resources"))
    }

    sourceSets.getByName("androidTest").java.srcDir(rootProject.file("instrumentation-support/src/main/kotlin"))
    testOptions.execution = "ANDROIDX_TEST_ORCHESTRATOR"

}

configurations.named("uitestImplementation") {
    extendsFrom(configurations.getByName("debugImplementation"))
}

dependencies {
    implementation(project(":core"))

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
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.multidex)
    kspAndroidTest(libs.hilt.android.compiler)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //Datastore
    implementation ("androidx.datastore:datastore-preferences:1.3.0")


    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0")
    implementation ("androidx.compose.material3:material3:1.4.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    // Hilt support for Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")

    val lifecycleVersion = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-compose-android:$lifecycleVersion")
    // optional - ProcessLifecycleOwner provides a lifecycle for the whole application process
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion")

    implementation("com.google.android.gms:play-services-wearable:20.0.1")

    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // Horologist library
    implementation("com.google.android.horologist:horologist-datalayer-phone:0.7.15")
    implementation("com.google.android.horologist:horologist-datalayer:0.7.15")
}
