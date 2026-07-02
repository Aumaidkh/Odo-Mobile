plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.logging"
    }
}