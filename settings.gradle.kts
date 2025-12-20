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
        // Shizuku/Sui repository
        maven("https://api.github.com/repos/RikkaApps/Shizuku-API/contents/maven")
        // Alternative Shizuku repository
        maven("https://raw.githubusercontent.com/RikkaApps/Shizuku-API/main/maven")
    }
}

rootProject.name = "AutoGLM"
include(":app")
 