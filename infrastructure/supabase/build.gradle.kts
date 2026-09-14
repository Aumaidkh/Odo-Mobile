import com.hopcape.odo.buildlogic.resolvedBuildFlavor
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
            // AppInfo.versionName for the log_uploads index row's app_version (docs/LOGGING_PLAN.md §7.3).
            implementation(projects.core.platform)
            implementation(projects.core.config)
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
 * Two projects exist, and the build type picks between them: **debug and stage talk to the
 * development project, release talks to production.** That mirrors `google-services.json`, which
 * is already per-variant, so a debug build points its Firebase *and* its Supabase at the
 * development copy without anyone remembering to set anything.
 *
 * The dev project's keys carry a `.dev` suffix on the same property names:
 *
 * ```properties
 * supabase.url.dev=https://<dev-ref>.supabase.co
 * supabase.anonKey.dev=<dev anon key>
 * supabase.url=https://<prod-ref>.supabase.co
 * supabase.anonKey=<prod anon key>
 * ```
 *
 * This module is a KMP `androidLibrary` and has no build types of its own to read, so the type
 * comes from `resolvedBuildFlavor()` — the same inference `:core:common` uses for `BuildInfo`,
 * shared rather than repeated so the two can never disagree about which build this is. It reads
 * task names, which means an IDE sync or a bare `./gradlew test` resolves to release. That is
 * the safe direction: an unrecognised build gets production, never somebody's scratch database.
 *
 * `supabase.env=dev|prod` in `local.properties` overrides the mapping, for the occasional debug
 * build that has to reproduce something against production. It applies to every build type at
 * once, so it is a thing to set for one build and unset again, not to leave on.
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

    // Which project to build against: the build type decides, unless something says otherwise.
    // Release ships against production; debug and stage are both development builds and get the
    // development project.
    val buildFlavor = resolvedBuildFlavor()
    val environmentForFlavor = if (buildFlavor == "release") "prod" else "dev"
    val override = (properties.getProperty("supabase.env") ?: System.getenv("SUPABASE_ENV"))
        .orEmpty().trim().lowercase()
    require(override.isEmpty() || override == "dev" || override == "prod") {
        "supabase.env must be 'dev' or 'prod', not '$override'. Leave it unset to let the build " +
            "type decide: debug and stage use the dev project, release uses production."
    }
    val environment = override.ifEmpty { environmentForFlavor }
    val isDev = environment == "dev"

    // Same property names for both projects, with `.dev` appended for development. One suffix
    // rather than two unrelated key names keeps a mistyped property visibly wrong.
    val propertySuffix = if (isDev) ".dev" else ""
    val envSuffix = if (isDev) "_DEV" else ""
    val url = (
        properties.getProperty("supabase.url$propertySuffix")
            ?: System.getenv("SUPABASE_URL$envSuffix")
        ).orEmpty()
    val anonKey = (
        properties.getProperty("supabase.anonKey$propertySuffix")
            ?: System.getenv("SUPABASE_ANON_KEY$envSuffix")
        ).orEmpty()
    // Off unless asked for. On, sign-in goes through Firebase phone auth and the
    // firebase-session Edge Function; off, it uses the fixed development account. Turning it
    // on before the Firebase side is set up (Blaze plan, Phone provider, this build's SHA-256
    // registered) means nobody can sign in — see infrastructure/firebase/auth/README.md.
    val phoneAuth =
        (properties.getProperty("supabase.phoneAuth") ?: System.getenv("SUPABASE_PHONE_AUTH")) == "true"

    inputs.property("url", url)
    inputs.property("anonKey", anonKey)
    inputs.property("phoneAuth", phoneAuth)
    // Both are written into the generated file, so both are inputs in their own right — the
    // credentials alone could stay identical while the description of them changed.
    inputs.property("environment", environment)
    inputs.property("buildFlavor", buildFlavor)
    outputs.dir(outputDir)

    doLast {
        // Nothing at runtime says which project a build is talking to, and the task only reruns
        // when the answer changes, so this is a rare line that answers a question that is
        // otherwise unanswerable after the fact.
        val overriding = override.isNotEmpty() && override != environmentForFlavor
        logger.lifecycle(
            when {
                url.isBlank() ->
                    "Supabase: no $environment credentials — the offline fakes stay in place."
                overriding ->
                    "Supabase: $environment project ($url) — supabase.env=$override overrides " +
                        "$buildFlavor's default of $environmentForFlavor. Unset it when done."
                else -> "Supabase: $environment project for a $buildFlavor build ($url)."
            },
        )

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
             * A **$buildFlavor** build, so it talks to the **$environment** project. Debug and
             * stage use development; release uses production. `supabase.env` in
             * `local.properties` overrides that for one build.
             *
             * Blank when the checkout has no credentials, which is the signal `supabaseModule`
             * reads to leave the offline fakes in place.
             */
            internal object BuildSupabaseConfig {
                const val URL: String = "${url.escaped()}"
                const val ANON_KEY: String = "${anonKey.escaped()}"

                /**
                 * Whether to sign in with a real phone number.
                 *
                 * True routes sign-in through Firebase phone auth and the firebase-session
                 * Edge Function. False uses the fixed development account instead, so the
                 * whole flow — and everything downstream of it — can be exercised on a
                 * checkout with no Firebase billing set up.
                 *
                 * Flip `supabase.phoneAuth=true` in local.properties once the Firebase side
                 * is ready (infrastructure/firebase/auth/README.md); no code changes.
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
