plugins {
    id("com.android.library")
}

// Calls, SMS and messengers through the phone's own apps. No network, no reading of messages.
// Must never depend on :personal: the screen passes it only the text and numbers to hand over.
android {
    namespace = "ua.sprotyv.reach"
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
}
