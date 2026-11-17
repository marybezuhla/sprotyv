plugins {
    id("com.android.application") version "9.5.1" apply false
    id("com.android.library") version "9.5.1" apply false
    // Kotlin is built into the Android plugin since 9.0; the Compose compiler plugin sets the Kotlin version (2.4.0)
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
