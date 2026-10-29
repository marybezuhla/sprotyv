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

rootProject.name = "Sprotyv"

// app: screens. content: built-in guide and cards (read only). personal: the encrypted database.
// reach: hands text to the phone's own apps (calls, SMS, messengers), never the network.
// Only :app may depend on :personal; CI checks this (scripts/check_modules.sh).
include(":app", ":content", ":personal", ":reach")
