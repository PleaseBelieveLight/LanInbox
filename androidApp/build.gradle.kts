import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.ui)
    implementation(libs.compose.components.resources)
    implementation(libs.ktor.client.core)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.zxing.scanner)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.uiTooling)
}

android {

    namespace = "com.luofelix.laninbox"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.luofelix.laninbox"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = rootProject.extra["laninbox.androidVersionCode"] as Int
        versionName = project.version.toString()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            // Missing credentials fail release signing without blocking ordinary Debug builds.
            storeFile = providers.environmentVariable("LANINBOX_KEYSTORE_PATH").orNull?.let { file(it) }
            storePassword = providers.environmentVariable("LANINBOX_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("LANINBOX_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("LANINBOX_KEY_PASSWORD").orNull
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
