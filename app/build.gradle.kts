import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// AIMeter is an independent build of the CodexMeter code base (see NOTICE.md). The version is
// declared here instead of being derived from git tags: the upstream repository's tags must never
// leak into this app's versionCode, and the same commit must always produce the same version.
// Bump both values for every APK you install over a previous one (versionCode must increase).
val appVersionName = "1.1.0"
val appVersionCode = 4

// Release signing material is read from outside the repository: either the AIUW_RELEASE_* environment
// variables or a properties file (default ~/.android/aiusagewidget-release.properties, overridable via
// AIUW_RELEASE_PROPERTIES). Nothing secret is ever committed; see docs/BUILDING.md.
fun releaseSigningProperties(): Properties {
    val props = Properties()
    val path = System.getenv("AIUW_RELEASE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android/aiusagewidget-release.properties"
    val file = File(path)
    if (file.isFile) file.inputStream().use { props.load(it) }
    return props
}

val releaseSigning = releaseSigningProperties()

fun releaseSigningValue(env: String, key: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: releaseSigning.getProperty(key)?.takeIf { it.isNotBlank() }

// Without signing material the release build is produced unsigned (app-release-unsigned.apk), which is
// what CI does; scripts/build-release.sh refuses to run without it.
val releaseStoreFile: String? = releaseSigningValue("AIUW_RELEASE_STORE_FILE", "storeFile")

android {
    namespace = "com.kmnexus.codexmeter"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // The Kotlin namespace keeps the upstream package so the code stays diffable against
        // CodexMeter; only the installed identity changes.
        applicationId = "io.github.pnikkosis.aiusagewidget"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseStoreFile?.let { storePath ->
            create("release") {
                storeFile = file(storePath)
                storePassword = releaseSigningValue("AIUW_RELEASE_STORE_PASSWORD", "storePassword")
                keyAlias = releaseSigningValue("AIUW_RELEASE_KEY_ALIAS", "keyAlias")
                keyPassword = releaseSigningValue("AIUW_RELEASE_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // The dependency-metadata block is an encrypted blob meant for Google Play; a sideloaded personal
    // APK does not need it, and leaving it out keeps the APK contents inspectable.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        // CI prints a per-issue summary from the XML report; the HTML report is only an artifact.
        xmlReport = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.browser:browser:1.8.0")

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
