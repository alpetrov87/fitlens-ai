plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.fitlens.ai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.fitlens.ai"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("FITLENS_VERSION_CODE")?.toInt() ?: 31
        versionName = "0.22.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val persistentKey = System.getenv("FITLENS_KEYSTORE_PATH").takeUnless { System.getenv("BAIR_WORKOUT_TEST_BUILD") == "true" }
    if (!persistentKey.isNullOrBlank()) {
        signingConfigs.getByName("debug") {
            storeFile = file(persistentKey)
            storePassword = requireNotNull(System.getenv("FITLENS_KEYSTORE_PASSWORD"))
            keyAlias = requireNotNull(System.getenv("FITLENS_KEY_ALIAS"))
            keyPassword = requireNotNull(System.getenv("FITLENS_KEY_PASSWORD"))
        }
    }
    if (System.getenv("BAIR_WORKOUT_TEST_BUILD") == "true") {
        buildTypes.getByName("debug") {
            applicationIdSuffix = ".workoutstest"
            resValue("string", "app_name", "bAIr Workouts Test")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
