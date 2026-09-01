plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    // Same reason: the Kotlin JVM and KSP plugins are resolved once here so a module
    // can apply them by id without a version, and without a second classloader.
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ksp) apply false
}