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

rootProject.name = "BodyTempGage"

include(":core")
// The :app module needs the Android SDK and Google Maven (dl.google.com).
// Set SKIP_ANDROID=1 to work on :core alone in restricted environments (e.g. CI without SDK).
if (System.getenv("SKIP_ANDROID") == null) {
    include(":app")
}
