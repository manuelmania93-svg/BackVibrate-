plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.backvibrate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.backvibrate"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.0"
    }


    signingConfigs {
        // CI signs with a permanent key stored only as a GitHub Actions secret.
        // Local/fork builds without the secret fall back to the default debug key.
        create("ci") {
            val storePath = System.getenv("BV_KEYSTORE_PATH")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = System.getenv("BV_STORE_PASSWORD")
                keyAlias = "backvibrate"
                keyPassword = System.getenv("BV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (System.getenv("BV_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
