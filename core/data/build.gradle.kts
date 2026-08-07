plugins {
    // Data layer: repository implementations + ports over the LocalDataSource
    // seam. The SQLDelight database and its implementations live in
    // :infrastructure:database — this module never imports SQLDelight, which
    // is what keeps the storage adapter swappable behind the ports it declares.
    // Depends inward on :core:domain only (ports + entities); domain never
    // sees a generated row.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // DTOs for the sync payload and for columns holding a structured value.
    alias(libs.plugins.kotlinSerialization)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.data"
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
            // PlatformFileStore — a blob has to be read off the device before it can be
            // uploaded, and reading a file is a platform capability.
            implementation(projects.core.platform)
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
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
