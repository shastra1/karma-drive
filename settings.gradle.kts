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
        // Pleos Playground SDK (Vehicle) — 공식 샘플과 같은 저장소
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}

rootProject.name = "karma-drive"
include(":incar-app")
include(":mobile-app")
