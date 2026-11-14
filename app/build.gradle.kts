plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The real signing key lives only in GitHub secrets (and with Shreyas). Without it the build is a
// "preview": a separate app id, so it can never be confused with or overwrite the real app.
val releaseKeyFile: File? = System.getenv("DUSK_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
val hasRealKey = releaseKeyFile != null && !System.getenv("DUSK_KEYSTORE_PASSWORD").isNullOrEmpty()

android {
    namespace = "com.dusk.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dusk.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 10
        versionName = "1.0-r1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("preview") {
            storeFile = file("../lab/dusk-lab.jks")
            storePassword = "dusklab"
            keyAlias = "dusklab"
            keyPassword = "dusklab"
        }
        if (hasRealKey) {
            create("real") {
                storeFile = releaseKeyFile
                storePassword = System.getenv("DUSK_KEYSTORE_PASSWORD")
                keyAlias = "dusk"
                keyPassword = System.getenv("DUSK_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasRealKey) {
                signingConfig = signingConfigs.getByName("real")
                resValue("string", "app_name", "Dusk")
                buildConfigField("boolean", "PREVIEW", "false")
            } else {
                applicationIdSuffix = ".preview"
                signingConfig = signingConfigs.getByName("preview")
                resValue("string", "app_name", "Dusk preview")
                buildConfigField("boolean", "PREVIEW", "true")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Dusk debug")
            buildConfigField("boolean", "PREVIEW", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
