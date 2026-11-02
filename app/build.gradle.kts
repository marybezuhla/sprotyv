import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The version is set by hand in version.txt (x.y.z); the code follows from it: 1.2.4 → 10204
val appVersion = rootProject.file("version.txt").readText().trim().also {
    require(Regex("^\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}$").matches(it)) { "version.txt must be x.y.z" }
}
val appVersionCode = appVersion.split(".").map(String::toInt).let { (a, b, c) -> a * 10_000 + b * 100 + c }

android {
    namespace = "ua.sprotyv"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.sprotyv"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    buildTypes {
        release {
            // Unused library code (such as a map library's online download classes) is cut out of the
            // app; names are kept as they are (no obfuscation), so reports and reviews stay readable
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Left unsigned here; signed later in a separate step that runs no build code
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // No encrypted list of dependencies inside the APK (only Google Play can read it)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":content"))
    implementation(project(":personal"))
    implementation(project(":reach"))

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // QR codes, made and read on the phone (ZXing core: Apache 2.0, plain Java, no network, no Google services)
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    // Screens drawn on the computer (Robolectric) from the real content, for review
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
