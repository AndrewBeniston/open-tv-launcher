buildscript {
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin). This raises its Kotlin version to the catalog's.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// One build folder for the whole repository: build/<module>.
subprojects {
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(name))
}
