import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Built-in guide, action cards and service contacts. Read only, works with no network.
// Must never depend on :personal.
android {
    namespace = "ua.sprotyv.content"
    compileSdk = 36
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (Android provides it on the phone)
    testImplementation("org.json:json:20240303")
}
