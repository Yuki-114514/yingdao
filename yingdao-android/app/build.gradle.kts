import groovy.json.JsonOutput
import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun configValue(name: String, fallback: String): String {
    return providers
        .gradleProperty(name)
        .orElse(localProperties.getProperty(name) ?: fallback)
        .get()
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yuki.yingdao"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yuki.yingdao"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "AI_BASE_URL", "\"https://example.invalid/\"")
        buildConfigField("String", "AI_APP_TOKEN", "\"\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            val debugAiBaseUrl = configValue("YINGDAO_DEBUG_AI_BASE_URL", "http://10.0.2.2:8787")
            val debugAiAppToken = configValue("YINGDAO_DEBUG_AI_APP_TOKEN", "")
            buildConfigField("String", "AI_BASE_URL", JsonOutput.toJson(debugAiBaseUrl))
            buildConfigField("String", "AI_APP_TOKEN", JsonOutput.toJson(debugAiAppToken))
        }

        release {
            val releaseAiBaseUrl = configValue("YINGDAO_RELEASE_AI_BASE_URL", "https://example.invalid/")
            val releaseAiAppToken = configValue("YINGDAO_RELEASE_AI_APP_TOKEN", "")
            require(releaseAiAppToken.isNotBlank() || releaseAiBaseUrl == "https://example.invalid/") {
                "YINGDAO_RELEASE_AI_APP_TOKEN must be set when YINGDAO_RELEASE_AI_BASE_URL is configured."
            }
            buildConfigField("String", "AI_BASE_URL", JsonOutput.toJson(releaseAiBaseUrl))
            buildConfigField("String", "AI_APP_TOKEN", JsonOutput.toJson(releaseAiAppToken))
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.okhttp)
    implementation(libs.gson)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
