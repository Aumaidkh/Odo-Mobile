import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    // Not `odo.kmp.library`, for the same reason `:webApp` is not: this is a
    // browser application with its own entry point, not a shared library.
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.odo.composeMultiplatform)
    alias(libs.plugins.odo.koin)
    alias(libs.plugins.odo.kmpTest)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                // What index.html loads. Deliberately not `odo-blog.js` — the two
                // apps are served from the same Firebase site and a shared bundle
                // name would have one overwrite the other on deploy.
                outputFileName = "odo-admin.js"
            }
            testTask {
                useKarma { useChromeHeadless() }
            }
        }
        // Produces the browser distribution published under /admin.
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            // PostgREST, the Supabase session and its Firebase exchange, the token
            // store, and the state primitives. Everything here that is not about
            // administering something lives there.
            implementation(projects.webCore)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.composeViewmodel)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        val wasmJsMain by getting {
            dependencies {
                // window / document / history.
                implementation(libs.kotlinx.browser)
                // The engine. Picked up by `HttpClient { }` with no engine named,
                // because it is the only one on this module's classpath.
                implementation(libs.ktor.client.js)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Compose Multiplatform string resources. Same rule as everywhere else: no
// user-facing copy at a call site. `ad_` prefixes them, the way :webApp uses `bl_`.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.hopcape.odo.web.admin.resources"
    generateResClass = always
}

// Supabase credentials come from `:webCore`'s BuildWebConfig — both web apps talk
// to the same project, so neither of them owns the task that reads them.
