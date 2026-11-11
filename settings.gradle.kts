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

// 工程名带版本号，两个版本可以在 Android Studio 里同时打开、互不覆盖
rootProject.name = "QingchenRadio-4.0"
include(":app")
