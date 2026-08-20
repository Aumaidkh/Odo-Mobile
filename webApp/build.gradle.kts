import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import java.util.Properties

plugins {
    // Not `odo.kmp.library`: that convention exists to give a shared module the
    // Android and iOS targets, and this module has neither. It is a browser
    // application with its own entry point, the way :androidApp is an Android one.
    alias(libs.plugins.kotlinMultiplatform)
    // The Compose UI surface (runtime/foundation/ui/material3) and the @Preview
    // annotations, into commonMain. Wasm inherits them from there.
    alias(libs.plugins.odo.composeMultiplatform)
    // koin-core in commonMain, koin-test in commonTest. Same DI as every feature
    // module, so swapping the sample repositories for real ones is one file.
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest. The URL round trip is the part of this module
    // that breaks silently — a link that parses but never formats is a page
    // nobody can reach — so it is the part with tests.
    alias(libs.plugins.odo.kmpTest)
    // The Firebase Auth REST payloads. Applied directly, the way :core:data and
    // :core:navigation do — there is nothing to configure beyond applying it.
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                // What index.html loads. Without this the bundle is named after the
                // Gradle module and the host page has to track a build detail.
                outputFileName = "odo-blog.js"
            }
            // Wasm has no host test runner the way Android and iOS do: the tests
            // run in a real browser or not at all. Headless Chrome is the one
            // every machine here already has.
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        // Produces the browser distribution that gets published under /blog.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // Either<BlogError, T> on every repository call — the same shape the
            // app's ports have, so a Supabase implementation later reads like the
            // ones in :infrastructure:supabase rather than like a web experiment.
            implementation(libs.arrow.core)
            // viewModelScope, and the flows the ViewModels expose.
            implementation(libs.kotlinx.coroutines.core)
            // LocalDate on a post. Dates here are only ever displayed, but storing
            // them as text would put date formatting in the repository.
            implementation(libs.kotlinx.datetime)
            // koinViewModel() in the route hosts.
            implementation(libs.koin.composeViewmodel)
            // Firebase Auth over its REST API. No ContentNegotiation, matching
            // :infrastructure:supabase: the adapters decode with an explicit Json
            // so a non-2xx body is inspected before anything parses it as success.
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        val wasmJsMain by getting {
            dependencies {
                // window / document / history. Kotlin's stdlib carries no DOM
                // bindings on Wasm, so even mounting Compose needs this.
                implementation(libs.kotlinx.browser)
                // The engine. Picked up by `HttpClient { }` with no engine named,
                // because it is the only one on this module's classpath.
                implementation(libs.ktor.client.js)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // MockEngine — drives the Firebase adapter without a network or a
            // Firebase project, the same way the Supabase adapters are tested.
            implementation(libs.ktor.client.mock)
        }
    }
}

// Compose Multiplatform string resources. Same rule as every feature module: no
// user-facing copy is written at a call site. `bl_` prefixes them, the way
// :feature:servicelog uses `sl_`.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.web.blog.resources"
    generateResClass = always
}


// ── Supabase credentials ─────────────────────────────────────────────────────
/**
 * Generates `BuildBlogConfig` from `local.properties` (or the environment on CI).
 *
 * The same two values `:infrastructure:supabase` reads, from the same property
 * names, so one checkout configures both. Neither is a secret in the way the
 * service_role key is — RLS is what guards the data — but they name a specific
 * project, so they come from a gitignored file rather than the repository.
 *
 * Production unless `supabase.env=dev` says otherwise. The app picks its project
 * from the build type; a website has no build types, it has one deployment, and
 * that deployment is production. An override exists for the occasional local run
 * against the scratch project.
 *
 * A missing value generates an empty string rather than failing the build. A
 * clone with no credentials still has to compile and run — `blogModule` sees the
 * blanks and keeps the sample repositories.
 */
val generateBlogConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/blogconfig/kotlin")
    val localProperties = rootProject.file("local.properties")

    // Read at configuration time into plain Strings, so the task action captures
    // values rather than the Project — which is what keeps it compatible with the
    // configuration cache.
    val properties = Properties().apply {
        if (localProperties.exists()) localProperties.inputStream().use(::load)
    }
    val override = (properties.getProperty("supabase.env") ?: System.getenv("SUPABASE_ENV"))
        .orEmpty().trim().lowercase()
    require(override.isEmpty() || override == "dev" || override == "prod") {
        "supabase.env must be 'dev' or 'prod', not '$override'"
    }
    val isDev = override == "dev"
    val propertySuffix = if (isDev) ".dev" else ""
    val envSuffix = if (isDev) "_DEV" else ""

    val url = (
        properties.getProperty("supabase.url$propertySuffix")
            ?: System.getenv("SUPABASE_URL$envSuffix")
        ).orEmpty().trimEnd('/')
    val anonKey = (
        properties.getProperty("supabase.anonKey$propertySuffix")
            ?: System.getenv("SUPABASE_ANON_KEY$envSuffix")
        ).orEmpty()

    inputs.property("url", url)
    inputs.property("anonKey", anonKey)
    outputs.dir(outputDir)

    doLast {
        val directory = outputDir.get().asFile.resolve("com/hopcape/odo/web/blog/infrastructure/supabase")
        directory.mkdirs()
        directory.resolve("BuildBlogConfig.kt").writeText(
            """
            |// Generated by :webApp:generateBlogConfig from local.properties.
            |// Do not edit: the next build overwrites it.
            |package com.hopcape.odo.web.blog.infrastructure.supabase
            |
            |internal object BuildBlogConfig {
            |    const val SUPABASE_URL: String = "$url"
            |    const val SUPABASE_ANON_KEY: String = "$anonKey"
            |}
            |
            """.trimMargin(),
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateBlogConfig)
}
