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

rootProject.name = "LookTube"

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:database",
    ":core:designsystem",
    ":core:network",
    ":core:testing",
    ":feature:auth",
    ":feature:library",
    ":feature:player",
    ":feature:settings",
)
