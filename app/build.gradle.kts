plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.livingwithhippos.webble"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.livingwithhippos.webble"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing for CI: the build workflow decodes the keystore and passes these variables.
    // Without them (local builds) the release APK is left unsigned.
    val keystore = System.getenv("KEYSTORE")?.let(::file)?.takeIf { it.isFile && it.length() > 0 }
    if (keystore != null) {
        signingConfigs {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }

            proguardFiles(
                // Default file with automatically generated optimization rules.
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // AGP's Compose Preview tasks need compiled resources, or creating them fails
            // (and with it `./gradlew tasks`).
            isIncludeAndroidResources = true
        }
    }
}

// APKs are named webble-<versionName>-<buildType>.apk (e.g. webble-1.0-release.apk) instead of
// app-<buildType>.apk. Set after the android block so versionName is already known.
base.archivesName = "webble-${android.defaultConfig.versionName}"

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.javascriptengine)
    testImplementation(libs.junit)
    // Android's org.json is a stub in JVM tests.
    testImplementation(libs.org.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}