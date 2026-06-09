import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Compose compiler plugin (bundled with Kotlin 2.x — replaces the standalone compose compiler).
    alias(libs.plugins.kotlin.compose)
}

// Read optional dev-only license values from local.properties so the in-app license
// entry screen can be pre-filled during development. These are NOT required to build.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key, "")

android {
    namespace = "com.benomad.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.benomad.sample"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Surfaced to the app via BuildConfig (see SdkConfig). Empty unless set in local.properties.
        buildConfigField("String", "MSDK_PURCHASE_UUID", "\"${localProp("MSDK_PURCHASE_UUID")}\"")
    }

    buildTypes {
        release {
            // Kept off for a readable sample; see proguard-rules.pro for the keep rules
            // you need if you enable minification in your own app.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        // Mandatory: every mSDK module enables core-library desugaring and targets Java 17.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    // Modern replacement for the deprecated `android { kotlinOptions { ... } }` DSL.
    // Keeps the Kotlin JVM target aligned with the Java target set in compileOptions above.
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ── BeNomad mSDK ──────────────────────────────────────────────────────────
    // The SDK declares its inter-module dependencies with `implementation` (not `api`),
    // so they are NOT transitive: declare every module whose classes you reference.
    implementation(libs.msdk.error.manager) // mandatory base (Error / ErrorType)
    implementation(libs.msdk.core)           // mandatory: Core init, license, map data, GeoPoint
    implementation(libs.msdk.maps)           // MapView, MapStyleLoader, POIStyle
    implementation(libs.msdk.geocoder)       // OnlineGeoCoder, Address, GeoDecoder
    implementation(libs.msdk.gps.manager)    // GPSManager, LocationFromBuiltInGPS, LocationFromRoute
    implementation(libs.msdk.vehicle.manager) // Profile, Vehicle, VehicleModel
    implementation(libs.msdk.planner)        // Planner, RoutePlan, Route, SymbolicManeuverStyle
    implementation(libs.msdk.navigation)     // Navigation, listeners, foreground service, TTS

    // ── AndroidX + Compose ────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
}
