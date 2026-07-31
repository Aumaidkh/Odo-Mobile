plugins {
    // Data layer: SQLDelight-backed repository implementations + mappers. The
    // local DB is the offline-first source of truth. Depends inward on
    // :core:domain only (ports + entities); domain never sees a generated row.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    alias(libs.plugins.sqldelight)
    // DTOs for the sync payload and for columns holding a structured value.
    alias(libs.plugins.kotlinSerialization)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
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
            // The sync seam. `:core:data` depends on `:core:sync`, never the reverse —
            // the engine receives its Syncables rather than reaching in for them, which
            // is what keeps the two modules from forming a cycle.
            implementation(projects.core.sync)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // Observability: repositories are the layer where a silent `Either.Left`
            // would otherwise be invisible. Spans for every DB/remote call, logs for
            // every failed write, non-fatals for the exceptions this layer swallows
            // into DomainError.PersistenceFailure. Interfaces only — the single
            // configuration is owned by the app bootstrap.
            implementation(projects.observability.logging)
            implementation(projects.observability.performance)
            implementation(projects.observability.crashreporting)
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
