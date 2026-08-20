import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    // Not `odo.kmp.library`: that convention exists to give a shared module the
    // Android and iOS targets, and this module has neither. It is a browser
    // application with its own entry point, the way :androidApp is an Android one.
    alias(libs.plugins.kotlinMultiplatform)
    // The Compose UI surface (runtime/foundation/ui/material3) and the @Preview
    // annotations, into commonMain. Wasm inherits them from there.
    alias(libs.plugins.odo.composeMultiplatform)
    // kotlin-test in commonTest. The URL round trip is the part of this module
    // that breaks silently — a link that parses but never formats is a page
    // nobody can reach — so it is the part with tests.
    alias(libs.plugins.odo.kmpTest)
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
        val wasmJsMain by getting {
            dependencies {
                // window / document / history. Kotlin's stdlib carries no DOM
                // bindings on Wasm, so even mounting Compose needs this.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
