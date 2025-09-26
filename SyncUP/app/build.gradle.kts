plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dagger.hilt)
    kotlin("kapt")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")



}

android {
    namespace = "com.syncup.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.syncup.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.13"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "mozilla/public-suffix.list"

        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx) // For lifecycle-aware components
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.core.android)
    implementation(libs.protolite.well.known.types)
    implementation(libs.volley)     // For integrating Compose with Activities
    kapt(libs.dagger.hilt.compiler)                   // For Hilt's annotation processing)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom)) // Compose Bill of Materials (BoM) - manages versions for Compose libraries
    implementation(libs.androidx.ui)                    // Core Compose UI
    implementation(libs.androidx.ui.graphics)           // Compose Graphics
    implementation(libs.androidx.material3)             // Material Design 3 Components for Compose
    implementation(libs.androidx.ui.tooling.preview)    // For Composable previews in Android Studio
    implementation(libs.dagger.hilt.compose)
    debugImplementation(libs.androidx.ui.tooling)       // For UI tooling like Layout Inspector (debug builds only)

    implementation("io.coil-kt:coil-compose:2.6.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7") // Jetpack Navigation for Compose

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.2.0")) // Firebase BoM - manages versions for Firebase libraries
    // Make sure "34.2.0" is the latest or desired version
    implementation("com.google.firebase:firebase-analytics")          // Firebase Analytics
    implementation("com.google.firebase:firebase-auth")               // Firebase Authentication (ktx version is included by default with BoM)
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-database")            // Firebase Realtime Database"
    implementation("com.google.firebase:firebase-storage")             // Firebase Storage"
    implementation("com.google.dagger:hilt-android:2.51") // Dagger Hilt for dependency injection

    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("com.google.firebase:firebase-messaging:24.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation ("androidx.compose.material:material-icons-extended:1.7.4")



    // Testing
    testImplementation(libs.junit) // JUnit 4 for unit tests
    androidTestImplementation(libs.androidx.junit) // AndroidX Test Library for JUnit
    androidTestImplementation(libs.androidx.espresso.core) // Espresso for UI tests
    androidTestImplementation(platform(libs.androidx.compose.bom)) // Compose BoM for testing libraries
    androidTestImplementation(libs.androidx.ui.test.junit4)      // Compose UI testing with JUnit4
    debugImplementation(libs.androidx.ui.test.manifest)        // For UI test manifest (debug builds only)

}

