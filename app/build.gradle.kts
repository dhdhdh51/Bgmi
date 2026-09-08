import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No Kotlin plugin on purpose: AGP 9.x compiles Kotlin out of the box
    // (built-in Kotlin). Applying org.jetbrains.kotlin.android here would fail.
}

// ---------------------------------------------------------------------------
// Backend base URL resolution order:
//   1. -Pbgmi.apiBaseUrl=... on the Gradle command line
//   2. bgmi.apiBaseUrl in local.properties (git-ignored, good for dev machines)
//   3. bgmi.apiBaseUrl in gradle.properties (checked in default)
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val apiBaseUrl: String = listOf(
    providers.gradleProperty("bgmi.apiBaseUrl").orNull,
    localProperties.getProperty("bgmi.apiBaseUrl"),
).firstOrNull { !it.isNullOrBlank() }?.trim()
    ?.let { if (it.endsWith("/")) it else "$it/" }
    ?: "https://example.com/api/"

// The CI workflow decodes KEYSTORE_BASE64 into app/release.keystore before
// calling assembleRelease. When that file is absent (local dev, forks without
// secrets) the release build falls back to the debug signing config so the
// build still succeeds — it is simply not distributable.
val releaseKeystore = file("release.keystore")
val hasReleaseKeystore = releaseKeystore.exists()

android {
    namespace = "com.bgmi.sensitivity"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bgmi.sensitivity"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Keep CI honest but do not fail the release build on lint warnings.
        abortOnError = false
        warningsAsErrors = false
    }
}

// With built-in Kotlin the compiler options live in a top-level `kotlin` block
// rather than the old android { kotlinOptions { } } block.
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
