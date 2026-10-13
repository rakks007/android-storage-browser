plugins { id("com.android.application") }

android {
    namespace = "com.rakesh.storagebrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rakesh.storagebrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
}
