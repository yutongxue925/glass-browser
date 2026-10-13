// ============ settings.gradle.kts ============
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
        mavenCentral()          // Haze / Cloudy / Coil 都在 Maven Central
        // 若要试 QmDeve / Mortd3kay 这类只在 JitPack 的库，再加：
        // maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GlassBrowser"
include(":app")
