import java.util.Properties

plugins {
    alias(libs.plugins.odo.kmpLibrary)
    alias(libs.plugins.odo.koin)
    // kotlin-test in commonTest comes from the odo.kmp.test convention plugin.
    alias(libs.plugins.odo.kmpTest)
}

kotlin {
    androidLibrary {
        namespace = "com.hopcape.odo.infrastructure.billing"
    }

    sourceSets {
        commonMain.dependencies {
            // The store SDK this module exists to adapt. Wraps Play Billing on Android and
            // StoreKit on iOS, and validates receipts on RevenueCat's servers — which is why
            // the client only ever mirrors entitlement rather than deciding it.
            implementation(libs.purchases.kmp.core)
            // The ports implemented here: EntitlementSource and, from S5, the subscription
            // catalog and purchaser.
            implementation(projects.core.domain)
            // BuildInfo — the SDK's log level follows the build type, so a debug build
            // prints why a purchase failed and a release build does not.
            implementation(projects.core.common)
            implementation(libs.kotlinx.coroutines.core)
            // Structured logging + non-fatals behind BillingTelemetry. A purchase that fails
            // silently is the one failure nobody can debug from a bug report.
            implementation(projects.observability.logging)
            implementation(projects.observability.crashreporting)
        }
    }
}

/**
 * Generates `BuildBillingConfig` from `local.properties` (or the environment on CI).
 *
 * The values are RevenueCat's public SDK keys, one per store. They are not secrets — they
 * identify an app to RevenueCat and can only read offerings and make purchases the store has
 * already authorised — but they name a specific RevenueCat project, so they are read from a
 * gitignored file rather than committed. The secret key, which can grant entitlements, never
 * goes near the app.
 *
 * ```properties
 * revenuecat.apiKey.android=goog_xxx
 * revenuecat.apiKey.ios=appl_xxx
 * ```
 *
 * One key per platform rather than per build type, because RevenueCat has no separate test
 * project: Play's licence testers and App Store sandbox accounts are what separate a test
 * purchase from a real one, and both are properties of the store account, not of the key.
 *
 * A missing value generates an empty string rather than failing the build. Odo builds and
 * runs on a checkout with no credentials — `billingInfrastructureModule` sees the blank and
 * leaves the free-plan source from `coreDataModule` in place, so nothing sells and nothing
 * crashes.
 *
 * Kept in this module rather than a convention plugin, for the same reason
 * `generateSupabaseConfig` is: it is the only other module that needs one. Promote them
 * together if a third appears.
 */
val generateBillingConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/billing/kotlin")
    val localProperties = rootProject.file("local.properties")

    // Read at configuration time into plain Strings so the task action captures values, not
    // the Project — which is what keeps it configuration-cache compatible.
    val properties = Properties().apply {
        if (localProperties.exists()) localProperties.inputStream().use(::load)
    }

    val androidKey = (
        properties.getProperty("revenuecat.apiKey.android")
            ?: System.getenv("REVENUECAT_API_KEY_ANDROID")
        ).orEmpty()
    val iosKey = (
        properties.getProperty("revenuecat.apiKey.ios")
            ?: System.getenv("REVENUECAT_API_KEY_IOS")
        ).orEmpty()

    inputs.property("androidKey", androidKey)
    inputs.property("iosKey", iosKey)
    outputs.dir(outputDir)

    doLast {
        logger.lifecycle(
            if (androidKey.isBlank() && iosKey.isBlank()) {
                "RevenueCat: no API key — nothing sells, and the free-plan entitlement stands."
            } else {
                "RevenueCat: configured (android=${androidKey.isNotBlank()}, ios=${iosKey.isNotBlank()})."
            },
        )

        // The values land inside a Kotlin string literal, so anything that could end it early
        // or start a template is escaped. A RevenueCat key contains none of these, but a typo
        // in local.properties should produce a wrong value, not an unparseable source file.
        fun String.escaped() = replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

        val target = outputDir.get().asFile.resolve("com/hopcape/odo/infrastructure/billing")
        target.mkdirs()
        target.resolve("BuildBillingConfig.kt").writeText(
            """
            package com.hopcape.odo.infrastructure.billing

            /**
             * Generated from `local.properties` by the `generateBillingConfig` Gradle task.
             * Do not edit — the file is rewritten on every build.
             *
             * RevenueCat's public SDK keys, one per store. Blank when the checkout has no
             * credentials, which is the signal `billingInfrastructureModule` reads to leave
             * the free-plan entitlement source in place.
             */
            internal object BuildBillingConfig {
                const val ANDROID_API_KEY: String = "${androidKey.escaped()}"
                const val IOS_API_KEY: String = "${iosKey.escaped()}"
            }

            """.trimIndent(),
        )
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(generateBillingConfig)
}
