plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.composeMultiplatform)
    // NavigationManager is published as a Koin `single` (the Hilt @Binds analog),
    // so features inject the interface without knowing the implementation.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and test wiring all come
    // from the odo.kmp.library convention plugin — only identity lives here.
    androidLibrary {
        namespace = "com.hopcape.odo.core.navigation"
    }

    sourceSets {
        commonMain.dependencies {
            // Core Compose UI artifacts come from the odo.compose.multiplatform
            // convention plugin.
            //
            // Navigation 3, re-exported (`api`) so a `:feature:*` / `:app` module
            // depending on :core:navigation also sees NavKey, NavDisplay,
            // entryProvider, NavEntry — keeping Nav3 a single dependency surface.
            // navigation3-ui transitively provides the runtime/common types.
            api(libs.navigation3.ui)
            // Per-entry ViewModelStore. `api` because the decorator is part of how the
            // host is assembled, and :app builds the NavDisplay through OdoNavHost.
            api(libs.androidx.lifecycle.viewmodelNavigation3)
            // SharedFlow command bus. `api` so a feature that exposes the
            // NavigationManager flow keeps the coroutines types visible.
            api(libs.kotlinx.coroutines.core)
        }
    }
}
