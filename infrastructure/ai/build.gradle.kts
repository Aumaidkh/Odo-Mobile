plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.ai"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.arrow.core)
            // The scan port this module implements (BillExtractor) and the read-result
            // models it answers with.
            implementation(projects.core.domain)
            // PlatformFileStore — a local storage key becomes bytes here.
            implementation(projects.core.platform)
            // LocalDate on the extraction results.
            implementation(libs.kotlinx.datetime)
            // Structured logging / APM spans / non-fatals behind AiTelemetry.
            implementation(projects.observability.logging)
            implementation(projects.observability.performance)
            implementation(projects.observability.crashreporting)
        }
        androidMain.dependencies {
            // The on-device OCR for the bill scanner. Bundled model, so a bill scans on
            // first run with no Play-Services model download.
            implementation(libs.mlkit.text.recognition)
            // Task.await() for ML Kit's Play-Services-style API.
            implementation(libs.kotlinx.coroutines.play.services)
            // CameraX stores rotation in EXIF; the bitmap must be upright for OCR.
            implementation(libs.androidx.exifinterface)
        }
    }
}
