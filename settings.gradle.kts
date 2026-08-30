pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "infinity-design"

// Pure-JVM libraries (testable without Android SDK)
include(":core")
include(":design")
include(":graphics")
include(":generation")
include(":backend")

// Android-only modules
include(":data")
include(":export")
include(":app")
