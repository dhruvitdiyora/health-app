pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // build.gradle.kts applies KSP without a version, so the version is pinned here.
        id("com.google.devtools.ksp") version "2.3.11"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// The root project is the application module: src/ and schemas/ sit beside this file.
rootProject.name = "health-app"
