plugins {
    // Data layer: SQLDelight-backed repository implementations + mappers. The
    // local DB is the offline-first source of truth. Depends inward on
    // :core:domain only (ports + entities); domain never sees a generated row.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.data"
    }

    // DriverFactory is an expect/actual *class* (the platform driver needs a
    // Context on Android) — opt in to the stable-enough Beta feature so the
    // build stays warning-free.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // Ports (CarRepository, VehicleCatalog) + Car aggregate. Brings
            // :core:common and Arrow transitively. `implementation`: nothing
            // here is re-exported — features depend on :core:domain directly.
            implementation(projects.core.domain)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            // One declaration covers iosArm64 + iosSimulatorArm64 via the
            // default hierarchy template's iosMain intermediate source set.
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Driver-backed repository/DB tests run on the JVM host with an
        // in-memory database (JdbcSqliteDriver).
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("OdoDatabase") {
            packageName.set("com.hopcape.odo.core.data.db")
        }
    }
}
