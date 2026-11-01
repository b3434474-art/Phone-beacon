plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val phoneBeaconUrl = providers.gradleProperty("PHONEBEACON_SUPABASE_URL").orElse("").get()
val phoneBeaconAnonKey = providers.gradleProperty("PHONEBEACON_SUPABASE_ANON_KEY").orElse("").get()

android {
    namespace = "com.phonebeacon.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phonebeacon.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures { compose = true; buildConfig = true }
    buildConfigField("String", "SUPABASE_URL", "\"$phoneBeaconUrl\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"$phoneBeaconAnonKey\"")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
