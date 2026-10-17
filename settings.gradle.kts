pluginManagement {
    repositories {
        // google() is https://dl.google.com/dl/android/maven2/ (Android Gradle Plugin, AndroidX).
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

rootProject.name = "AIUsageWidget"
include(":app")
