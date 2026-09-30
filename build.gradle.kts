plugins {
    alias(libs.plugins.android.application) apply false
    // Also pins the Kotlin Gradle Plugin version used by AGP's built-in Kotlin support.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.ksp) apply false
}
