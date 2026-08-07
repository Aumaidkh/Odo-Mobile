plugins {
    // Infrastructure adapter: the SQLDelight database and the LocalDataSource
    // ports :core:data declares implemented on top of it. Depends on
    // :core:data, :core:domain and :core:sync — never the reverse; that
    // direction is what keeps the storage adapter swappable behind the ports.
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.database"
    }
}
