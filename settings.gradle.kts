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

rootProject.name = "TimeManager"
include(":app")
// Вендоренный WYSIWYG-редактор заметок (richeditor-compose с фиксом,
// см. README.md в модуле).
include(":richeditor-compose")
