plugins {
    id("com.android.library")
}

// Built-in guide, action cards and service contacts. Read only, works with no network.
// Must never depend on :personal.
android {
    namespace = "ua.sprotyv.content"
    compileSdk = 37
    // Unit tests run on the release build, as before; since Android Gradle plugin 9 only the tested build type has them
    testBuildType = "release"
    defaultConfig { minSdk = 31 }
    // Lint's findings printed in the CI log, as for the app
    lint {
        textReport = true
        textOutput = file("build/reports/lint.txt")
        // New versions come through Dependabot and a reviewed checksum refresh, not through lint
        disable += setOf("GradleDependency", "NewerVersionAvailable")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (Android provides it on the phone)
    testImplementation("org.json:json:20260814")
}
