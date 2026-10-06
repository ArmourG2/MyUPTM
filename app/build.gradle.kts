import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)

}

val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// Sprint 12: local.properties values may be pasted WITH surrounding quotes
// ("storeFile=/path/...") — Properties keeps the quotes literally, which broke
// validateSigningRelease (resolved against app/ with embedded "\""). Strip a
// symmetric wrapping pair from every signing value at load time, so quoting is
// optional and never fatal.
private fun Properties.signingValue(key: String): String? =
    getProperty(key)?.trim()?.trim('"', '\'')

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

        // Sprint 12: ML Kit ships native decoder libs for 4 ABIs — the POC
        // targets real devices only (arm64), so don't bloat the APK.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sprint 8 Task 3: Cloudinary config is injected from local.properties so the
        // upload preset name is never committed into version control.
        val cloudinaryProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${cloudinaryProps.getProperty("cloudinaryCloudName") ?: ""}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${cloudinaryProps.getProperty("cloudinaryUploadPreset") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.signingValue("storeFile") ?: "")
            storePassword = keystoreProps.signingValue("storePassword")
            keyAlias = keystoreProps.signingValue("keyAlias")
            keyPassword = keystoreProps.signingValue("keyPassword")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
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
    // Sprint 12 audit: material-icons-extended (≈35 MB of dex) was removed —
    // every used icon resolves from the core set; see icons in BottomNavBar,
    // ClassManagement, AdminDashboard, Attendance (QR/NoPhotography).
    // If a future icon is core-metadata-only, re-add extended OR a drawable.
    // implementation(libs.androidx.compose.material.icons.extended)
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

    // Sprint 11: QR attendance — zxing core generates the session QR (lecturer side).
    // Sprint 12: the student side scans in-app with CameraX + ML Kit (no zxing Activity).
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.viewfinder.compose)
    implementation(libs.mlkit.barcode.scanning)

    // Sprint 12: theme-colour picker — M3 scheme generation from a seed colour.
    implementation(libs.material.kolor)
}