plugins {
    id("com.android.application")
}

android {
    namespace = "com.local.yongsanimaxwatcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.local.yongsanimaxwatcher"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("VERSION_NAME").orNull ?: "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
