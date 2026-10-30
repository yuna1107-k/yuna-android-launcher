// Gradle build for Launcher3 (non-Quickstep variant).
// AOSP dependencies are fetched into external/ by gradle-build/fetch_aosp_deps.sh.
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

rootProject.name = "Launcher3"

listOf(
    "app", "flags", "plugincore", "iconloader", "animationlib",
    "contextualedu", "msdl",
).forEach {
    include(":$it")
    project(":$it").projectDir = file("gradle-build/$it")
}
