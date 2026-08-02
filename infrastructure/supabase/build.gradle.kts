plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    // Only this module's identity lives here; targets, SDK levels, JVM target,
    // the iOS framework and test wiring all come from the odo.kmp.library
    // convention plugin.
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.supabase"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.arrow.core)
            implementation(projects.core.data)
            implementation(projects.core.platform)
            implementation(projects.core.domain)
            // Structured logging — build-type-aware Logger wired via loggingModule.
            implementation(projects.observability.logging)
            // APM tracer (spans/traces) wired via performanceModule.
            implementation(projects.observability.performance)
            // CrashRecorder wired via crashReportingModule — :core:data's telemetry
            // records non-fatals through it, so the graph must publish one.
            implementation(projects.observability.crashreporting)
        }
    }
}
