plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.analytics"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // koin-test is added to commonTest by the odo.koin convention plugin.
        }
    }
}
