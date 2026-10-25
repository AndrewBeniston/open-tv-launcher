import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The release key lives in signing/ and never goes into git. scripts/make-keystore.sh creates it.
val signingProps = Properties().apply {
    val file = rootProject.file("signing/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Each builder's own values live in the git-ignored local.properties (README, "Build"): the
// Trakt API app's id and secret, and if wanted the app id and the Wikidata contact.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun quoted(key: String, default: String = "") =
    "\"" + localProps.getProperty(key, default).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// The id the app installs as. A different id installs a second app next to the first. The source
// package stays app.opentvlauncher, so the services' class names do not change with it.
val appId: String = localProps.getProperty("APPLICATION_ID")?.trim()?.takeIf { it.isNotEmpty() } ?: "app.opentvlauncher"

android {
    namespace = "app.opentvlauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "TRAKT_CLIENT_ID", quoted("TRAKT_CLIENT_ID"))
        buildConfigField("String", "TRAKT_CLIENT_SECRET", quoted("TRAKT_CLIENT_SECRET"))
        // Wikimedia asks each client to give a contact in its user agent (NetflixSource).
        buildConfigField("String", "WIKIDATA_CONTACT", quoted("WIKIDATA_CONTACT", "https://github.com/AndrewBeniston/open-tv-launcher"))
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // TvIdleCheckTest runs scripts/check-tv-idle.sh, so a change to a script runs the tests again.
        unitTests.all { test ->
            test.inputs.dir(rootProject.layout.projectDirectory.dir("scripts"))
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withPropertyName("scripts")
        }
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.tv.material)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
