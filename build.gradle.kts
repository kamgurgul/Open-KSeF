import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidxRoom) apply false
    alias(libs.plugins.spotless)
}

configure<SpotlessExtension> {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "spotless/**/*.kt")
        ktfmt(libs.ktfmt.get().version).kotlinlangStyle()
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
}