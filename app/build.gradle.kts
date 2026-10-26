plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.printmonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.printmonitor"
        minSdk = 26
        targetSdk = 35
        // Numéro de compilation GitHub : chaque APK est plus récent que le précédent
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.$build"
    }
    // Clé de signature fixe : sans elle, chaque compilation GitHub produit une signature
    // différente et Android refuse d'installer la mise à jour par-dessus l'ancienne version.
    signingConfigs {
        create("fixe") {
            storeFile = file("printmonitor.keystore")
            storePassword = "printmonitor"
            keyAlias = "printmonitor"
            keyPassword = "printmonitor"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixe") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixe")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
