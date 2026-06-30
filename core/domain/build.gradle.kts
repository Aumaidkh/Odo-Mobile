plugins {
    // Pure domain module: entities, value objects, use cases, repository ports,
    // sealed DomainError. NO framework types ever (no Compose, DI, SQLDelight,
    // Supabase). Depends inward only — on :core:common.
    alias(libs.plugins.odo.kmpLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.core.domain"
    }

    sourceSets {
        commonMain.dependencies {
            // Either / EitherNel appear in public signatures (use case + repo
            // boundaries) → api. IdGenerator (from :core:common) is exposed in
            // AddCarUseCase's public constructor → api. Flow appears in the
            // repository port → api.
            api(libs.arrow.core)
            api(libs.kotlinx.coroutines.core)
            api(projects.core.common)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
