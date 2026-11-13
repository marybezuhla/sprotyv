plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

// The owner's own data: medical card, family plan, kit, contacts. Encrypted with SQLCipher,
// key kept in the phone's keystore. Only :app may use this module.
android {
    namespace = "ua.sprotyv.personal"
    compileSdk = 37
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val room = "2.8.5"
    // api: screens in :app receive the opened database
    api("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
    implementation("net.zetetic:sqlcipher-android:4.20.0@aar")
    implementation("androidx.sqlite:sqlite:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Argon2id for password backups and the family plan code (a reviewed version, its checksum pinned)
    implementation("org.bouncycastle:bcprov-jdk18on:1.87")
    // Streaming encryption of the backup file (AES-256-GCM-HKDF, in parts), from Google Tink
    implementation("com.google.crypto.tink:tink-android:1.24.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (Android provides it on the phone)
    testImplementation("org.json:json:20260814")
}
