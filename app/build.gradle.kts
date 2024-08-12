plugins {
    id("com.android.application")
}

android {
    namespace = "com.abdurazaaqmohammed.AntiSplit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abdurazaaqmohammed.AntiSplit"
        minSdk = 4
        targetSdk = 35
        versionCode = 22
        versionName = "1.6.3.7"
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = false
    }
    dependencies {
        implementation("com.android.support:multidex:1.0.3")
        //implementation("org.bouncycastle:bcprov-jdk15to18:1.78.1")
      // implementation("org.bouncycastle:bcpkix-jdk15to18:1.78.1")
      //  implementation("com.madgag.spongycastle:core:1.58.0.0")
        coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    }
}