plugins {
    alias(libs.plugins.odo.kmpLibrary)
    // The design system is pure Compose UI (tokens + theme + reusable
    // composables); no DI, no navigation, no domain. It sits at the bottom of
    // the UI stack so any feature / app module can depend on it.
    alias(libs.plugins.odo.composeMultiplatform)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Targets, SDK levels, JVM target, the iOS framework and test wiring all come
    // from the odo.kmp.library convention plugin — only identity lives here.
    androidLibrary {
        namespace = "com.hopcape.odo.core.designsystem"
    }

    sourceSets {
        commonMain.dependencies {
            // LocalDate — what OdoDateField hands back. A value type, not domain: the
            // alternative is every caller converting epoch millis at the call site.
            implementation(libs.kotlinx.datetime)
        }
    }

    // Otherwise no source-set dependencies: the Compose UI surface
    // (runtime/foundation/ui/material3/resources) and the tooling preview
    // (@Preview annotations + the Android renderer) both come from the
    // odo.compose.multiplatform convention plugin. Consumers get this module's
    // tokens/composables, and — because they apply the same Compose convention —
    // can write Odo multipreviews (@OdoThemePreviews) without any extra deps.
}
