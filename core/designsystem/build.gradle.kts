plugins {
    alias(libs.plugins.odo.kmpLibrary)
    // The design system is pure Compose UI (tokens + theme + reusable
    // composables); no DI, no navigation, no domain. It sits at the bottom of
    // the UI stack so any feature / app module can depend on it.
    alias(libs.plugins.odo.composeMultiplatform)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and test wiring all come
    // from the odo.kmp.library convention plugin — only identity lives here.
    androidLibrary {
        namespace = "com.hopcape.odo.core.designsystem"
    }

    sourceSets {
        commonMain.dependencies {
            // The Compose UI surface (runtime/foundation/ui/material3/resources)
            // is supplied by the odo.compose.multiplatform convention plugin and
            // re-exported transitively, so a module depending on :core:designsystem
            // gets Material3 + the tokens through this one dependency.
            //
            // Preview annotations (@Preview, @PreviewLightDark…) are `api` so
            // every feature module can write Odo multipreviews (@OdoThemePreviews)
            // without re-declaring this dependency.
            api(libs.compose.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    // The Android preview renderer (ui-tooling) for this module's own previews.
    androidRuntimeClasspath(libs.compose.uiTooling)
}
