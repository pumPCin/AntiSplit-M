plugins {
    id("com.android.application")
}

android {
    namespace = "com.abdurazaaqmohammed.AntiSplit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abdurazaaqmohammed.AntiSplit"
        minSdk = 21
        targetSdk = 35
        versionCode = 30
        versionName = "2.0"
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = false
    }
    dependencies {
        implementation("org.apache.commons:commons-compress:1.24.0")
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
        implementation("androidx.appcompat:appcompat:1.7.0")
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")
        implementation("androidx.multidex:multidex:2.0.1")
        implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
        implementation("com.google.android.material:material:1.12.0")
    }
}
