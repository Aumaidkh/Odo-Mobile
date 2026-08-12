plugins {
    // Infrastructure adapter: the SQLDelight database and the LocalDataSource
    // ports :core:data declares implemented on top of it. Depends on
    // :core:data, :core:domain and :core:sync — never the reverse; that
    // direction is what keeps the storage adapter swappable behind the ports.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    alias(libs.plugins.sqldelight)
    // FairnessSnapshotJson serializes the frozen fairness verdict into a column.
    alias(libs.plugins.kotlinSerialization)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.database"
    }

    // DriverFactory is an expect/actual *class* (the platform driver needs a
    // Context on Android) — opt in to the stable-enough Beta feature so the
    // build stays warning-free.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // The LocalDataSource ports this module implements, the DTOs its
            // SyncTables map to, and the remote-data-source ports its Syncable
            // adapters push through.
            implementation(projects.core.data)
            // Entities + repository ports the local data sources reconstitute
            // rows into.
            implementation(projects.core.domain)
            // The sync seam (SyncEntity, Syncable, Synchronizer) this module's
            // SyncRunner and Syncable adapters implement against.
            implementation(projects.core.sync)
            // PlatformFileStore — the sign-out wipe deletes stored files
            // alongside the rows that named them.
            implementation(projects.core.platform)
            // TripSessionStoreImpl implements the :core:triptracker TripSessionStore port.
            implementation(projects.core.triptracker)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            // Observability: the local data sources and sync tables are the layer
            // where a silent failure would otherwise be invisible. Interfaces
            // only — the single configuration is owned by the app bootstrap.
            implementation(projects.observability.logging)
            implementation(projects.observability.performance)
            implementation(projects.observability.crashreporting)
            // The AnalyticsEventStore port SqlDelightAnalyticsEventStore implements.
            implementation(projects.observability.analytics)
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
        // Driver-backed local-data-source/sync tests run on the JVM host with an
        // in-memory database (JdbcSqliteDriver).
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("OdoDatabase") {
            packageName.set("com.hopcape.odo.infrastructure.database.db")
        }
    }
}
