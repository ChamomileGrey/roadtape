// roadtape v0.1.0 — Gradle settings (plugin and dependency repositories)
// Created: 2026-11-13 by Claude Fable 5.2 via claude.ai
// Modified: 2026-11-13 by Claude Fable 5.2 via claude.ai
// Install path: settings.gradle.kts
// Changelog:
//   v0.1.0 2026-11-13 Claude Fable 5.2 initial project setup

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "roadtape"
include(":app")
