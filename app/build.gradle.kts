plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.abdurazaaqmohammed.AntiSplit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.abdurazaaqmohammed.AntiSplit"
        minSdk = 24
        targetSdk = 36
        versionCode = 57
        versionName = "2.2.9"
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
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.material)
    implementation(files("libs/APKEditor.jar"))
}
