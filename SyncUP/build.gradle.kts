plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dagger.hilt) apply false

    // UPDATE: Use a modern, compatible version of the google-services plugin
    id("com.google.gms.google-services") version "4.4.2" apply false

    // UPDATE: Use the latest version of the crashlytics plugin
    id("com.google.firebase.crashlytics") version "3.0.1" apply false


}

