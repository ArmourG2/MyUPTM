import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)

}

android {
    namespace = "com.myuptm"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.myuptm"
        minSdk = 29
        this.targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sprint 8 Task 3: Cloudinary config is injected from local.properties so the
        // upload preset name is never committed into version control.
        val cloudinaryProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${cloudinaryProps.getProperty("cloudinaryCloudName") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${cloudinaryProps.getProperty("cloudinaryUploadPreset") ?: ""}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.datastore.preferences)

    // OkHttp — multipart upload client for the real Cloudinary seam (Sprint 8 Task 3)
    implementation(libs.okhttp)
    // Coil 3 — async image loading (avatars in Profile)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Import the Firebase BoM (Bill of Materials)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)

    // Firebase Auth (version is automatically matched to the BoM)
    implementation(libs.firebase.auth)

    // --- Credential Manager & Google Sign-In ---
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
}