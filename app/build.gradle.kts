plugins {
    id("com.android.application")
}

android {
    namespace = "com.abdurazaaqmohammed.AntiSplit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abdurazaaqmohammed.AntiSplit"
        minSdk = 24
        targetSdk = 36
        versionCode = 56
        versionName = "2.2.8"
        multiDexEnabled = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = false
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        viewBinding = false
    }
    dependencies {
        implementation("com.google.android.material:material:1.13.0")
        implementation(files("libs/APKEditor.jar"))
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}
