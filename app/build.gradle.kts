plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.pleos.contest"
    compileSdk = 36
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "ai.pleos.contest"
        minSdk = 28
        // Pleos Connect Emulator v2.1.0 = API 34
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
        testInstrumentationRunner = "ai.pleos.contest.SyncAlarmInstrumentation"
    }

    signingConfigs {
        create("submission") {
            storeFile = System.getenv("CHAEGIM_STORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
            storePassword = System.getenv("CHAEGIM_STORE_PASSWORD")
            keyAlias = System.getenv("CHAEGIM_KEY_ALIAS")
            keyPassword = System.getenv("CHAEGIM_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("submission")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Pleos Connect Vehicle SDK (가이드의 2.3.1은 저장소에 없음 → 실제 최신 2.3.2)
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}
