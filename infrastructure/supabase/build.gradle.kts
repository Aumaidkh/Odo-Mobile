import java.util.Properties

plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // The adapters encode and decode PostgREST payloads with an explicit `Json`.
    alias(libs.plugins.kotlinSerialization)
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
            // The remote ports this module implements (RemoteDataSources + RemoteFileStorage)
            // and their DTOs. The repositories already talk to those ports, so nothing above
            // this module changes when the adapters replace the fakes.
            implementation(projects.core.data)
            implementation(projects.core.domain)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            // The fairness adapter fans one RPC call out per category concurrently.
            implementation(libs.kotlinx.coroutines.core)
            // Structured logging — build-type-aware Logger wired via loggingModule.
            implementation(projects.observability.logging)
            // APM tracer (spans/traces) wired via performanceModule.
            implementation(projects.observability.performance)
            // CrashRecorder wired via crashReportingModule — a request that throws is a
            // non-fatal, not an expected DomainError.
            implementation(projects.observability.crashreporting)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * Generates `BuildSupabaseConfig` from `local.properties` (or the environment on CI).
 *
 * The values are the project URL and the anon key. Neither is a secret in the sense the
 * service_role key is — RLS is what actually guards the data — but they identify a specific
 * project, so they are read from a gitignored file rather than committed. Everything genuinely
 * sensitive stays in Edge Function env (TDD §16).
 *
 * A missing value generates an empty string rather than failing the build. Odo is offline-first
 * and every remote port has a working fake, so a checkout with no credentials must still build
 * and run — `supabaseModule` sees the blanks and leaves the fakes in place.
 *
 * Kept in this module rather than a convention plugin: it is the only module that needs it.
 * Promote it when a second one does.
 */
val generateSupabaseConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/supabase/kotlin")
    val localProperties = rootProject.file("local.properties")

    // Read at configuration time into plain Strings so the task action captures values, not
    // the Project — which is what keeps it configuration-cache compatible.
    val properties = Properties().apply {
        if (localProperties.exists()) localProperties.inputStream().use(::load)
    }
    val url = (properties.getProperty("supabase.url") ?: System.getenv("SUPABASE_URL")).orEmpty()
    val anonKey =
        (properties.getProperty("supabase.anonKey") ?: System.getenv("SUPABASE_ANON_KEY")).orEmpty()
    // Off unless asked for. Real phone OTP needs a configured SMS provider and, in India,
    // TRAI DLT registration — until both exist, turning this on means nobody can sign in.
    val phoneAuth =
        (properties.getProperty("supabase.phoneAuth") ?: System.getenv("SUPABASE_PHONE_AUTH")) == "true"

    inputs.property("url", url)
    inputs.property("anonKey", anonKey)
    inputs.property("phoneAuth", phoneAuth)
    outputs.dir(outputDir)

    doLast {
        // The values land inside a Kotlin string literal, so anything that could end it early
        // or start a template is escaped. A JWT anon key contains none of these, but a typo in
        // local.properties should produce a wrong value, not an unparseable source file.
        fun String.escaped() = replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

        val target = outputDir.get().asFile.resolve("com/hopcape/odo/infrastructure/supabase")
        target.mkdirs()
        target.resolve("BuildSupabaseConfig.kt").writeText(
            """
            package com.hopcape.odo.infrastructure.supabase

            /**
             * Generated from `local.properties` by the `generateSupabaseConfig` Gradle task.
             * Do not edit — the file is rewritten on every build.
             *
             * Blank when the checkout has no credentials, which is the signal `supabaseModule`
             * reads to leave the offline fakes in place.
             */
            internal object BuildSupabaseConfig {
                const val URL: String = "${url.escaped()}"
                const val ANON_KEY: String = "${anonKey.escaped()}"

                /**
                 * Whether to sign in with real phone OTP.
                 *
                 * False means the development account is used instead, so the whole flow —
                 * and everything downstream of it — can be exercised while DLT registration
                 * is pending. Flip `supabase.phoneAuth=true` in local.properties once the
                 * SMS provider and DLT template are live; no code changes.
                 */
                const val PHONE_AUTH: Boolean = $phoneAuth
            }

            """.trimIndent(),
        )
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateSupabaseConfig)
}
