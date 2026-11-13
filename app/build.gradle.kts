// roadtape v0.1.0 — app module build script
// Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
// Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
// Install path: app/build.gradle.kts
// Changelog:
//   v0.1.0 2026-11-13 Claude Fable 5.2 initial project setup

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chamomilegrey.roadtape"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chamomilegrey.roadtape"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // AAOS Car API (speed, gear) — an optional library of the platform SDK.
    useLibrary("android.car")

    buildTypes {
        release {
            isMinifyEnabled = false
            // Prototype build: signed with the local debug key so that the
            // release APK installs on an emulator without a private keystore
            // in this public repository. Replace before any store release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
