import com.hopcape.odo.buildlogic.resolvedBuildFlavor
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
            // Its callbacks as suspend functions returning Either. Every port here answers
            // with Either already, so this is the shape the adapters would have written.
            implementation(libs.purchases.kmp.either)
            implementation(libs.arrow.core)
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
 * The values are RevenueCat's public SDK keys. They are not secrets — they identify an app to
 * RevenueCat and can only read offerings and make purchases the store has already authorised —
 * but they name a specific RevenueCat project, so they are read from a gitignored file rather
 * than committed. The secret key, which can grant entitlements, never goes near the app.
 *
 * **Which key a build gets is decided by its build type**, the same way `generateSupabaseConfig`
 * picks a project: debug and stage take the Test Store key, release takes the store's.
 *
 * ```properties
 * # Release — Google Play and the App Store. Real money.
 * revenuecat.apiKey.android=goog_xxx
 * revenuecat.apiKey.ios=appl_xxx
 * # Debug and stage — RevenueCat's Test Store. Simulated purchases, no Play Console setup.
 * revenuecat.apiKey.android.test=test_xxx
 * revenuecat.apiKey.ios.test=test_xxx
 * ```
 *
 * **Why this is split rather than one key for everything.** A Test Store key lets the whole
 * paywall — plans, prices, trial, entitlement — be walked on a debug build with nothing
 * configured in Play Console, which is the only way to work on it before the store products
 * exist. But RevenueCat's SDK deliberately **crashes a release build** that was configured with
 * one, to stop test purchases leaking into production. One shared property would put that crash
 * one careless AAB away, so the build type chooses and the release path cannot see the test key.
 *
 * A release configured with a `test_` key fails this task rather than producing the artifact.
 * The SDK would catch it too — at launch, on a tester's phone, after upload.
 *
 * A missing value generates an empty string rather than failing the build. Odo builds and runs
 * on a checkout with no credentials — `billingInfrastructureModule` sees the blank and leaves
 * the free-plan source from `coreDataModule` in place, so nothing sells and nothing crashes.
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

    // Debug and stage are development builds and get the Test Store; release ships to a real
    // store. Same inference `:core:common` uses for BuildInfo, so the two can never disagree
    // about which build this is.
    val buildFlavor = resolvedBuildFlavor()
    val isTestStore = buildFlavor != "release"
    val propertySuffix = if (isTestStore) ".test" else ""
    val envSuffix = if (isTestStore) "_TEST" else ""

    val androidKey = (
        properties.getProperty("revenuecat.apiKey.android$propertySuffix")
            ?: System.getenv("REVENUECAT_API_KEY_ANDROID$envSuffix")
        ).orEmpty()
    val iosKey = (
        properties.getProperty("revenuecat.apiKey.ios$propertySuffix")
            ?: System.getenv("REVENUECAT_API_KEY_IOS$envSuffix")
        ).orEmpty()

    // A secret key in any build, ever. It grants entitlements, issues refunds and reads
    // customer data, and an APK is a zip file anyone can open — so this is the one mistake
    // here that is not recoverable by shipping a fix. Checked before anything else because
    // it is the only one that matters whether the build is a release or not.
    check(!androidKey.startsWith(SECRET_KEY_PREFIX) && !iosKey.startsWith(SECRET_KEY_PREFIX)) {
        "A RevenueCat secret key (sk_...) was given to the app. Secret keys grant entitlements " +
            "and read customer data, and anything compiled into an APK can be read out of it. " +
            "Only the public SDK keys belong here: goog_/appl_ for release, test_ for debug. " +
            "Revoke that key in the RevenueCat dashboard — it has been written to a file."
    }

    // The one mistake that would reach a tester's phone rather than the build log. RevenueCat
    // crashes a release configured with a Test Store key on purpose; this catches it here.
    check(isTestStore || !(androidKey.startsWith(TEST_KEY_PREFIX) || iosKey.startsWith(TEST_KEY_PREFIX))) {
        "A release build was given a RevenueCat Test Store key (test_...). The SDK crashes on " +
            "launch when that ships. Put the store key in revenuecat.apiKey.android / .ios and " +
            "keep the test key in revenuecat.apiKey.android.test / .ios.test."
    }

    // The mistake that reached production rather than a tester's phone: 1.3.3.1 shipped with no
    // key at all. A blank key is a supported state everywhere else — a fresh checkout, a CI
    // compile gate, a unit-test run all build and run fine without one — so nothing here
    // complained, the bundle signed and uploaded like any other, and the paywall listed nothing
    // because `billingInfrastructureModule` had bound `UnconfiguredCatalog`.
    //
    // Narrowed to the packaging tasks on purpose. `resolvedBuildFlavor()` answers "release" for
    // anything it does not recognise — a bare `test`, a `lint` run, an IDE sync — so keying this
    // off the flavor alone would fail CI's unit-test job on a runner that has no key and does
    // not need one. Only an artifact that can be installed has to have one.
    val isPackagingRelease = gradle.startParameter.taskNames.any {
        it.contains("bundleRelease") || it.contains("assembleRelease")
    }
    check(!isPackagingRelease || androidKey.isNotBlank()) {
        "This release bundle would ship with no RevenueCat key, so nothing in it can be sold: " +
            "the paywall binds UnconfiguredCatalog, every plan request answers NothingForSale, " +
            "and an owner sees an empty screen with no way to buy. Set revenuecat.apiKey.android " +
            "in local.properties, or REVENUECAT_API_KEY_ANDROID in the environment on CI " +
            "(.github/workflows/deploy-internal.yml passes it from the play-internal secret)."
    }

    inputs.property("androidKey", androidKey)
    inputs.property("iosKey", iosKey)
    inputs.property("buildFlavor", buildFlavor)
    outputs.dir(outputDir)

    doLast {
        val store = if (isTestStore) "Test Store" else "live store"
        logger.lifecycle(
            if (androidKey.isBlank() && iosKey.isBlank()) {
                "RevenueCat: no $store key for a $buildFlavor build — nothing sells, and the " +
                    "free-plan entitlement stands."
            } else {
                "RevenueCat: $store keys for a $buildFlavor build " +
                    "(android=${androidKey.isNotBlank()}, ios=${iosKey.isNotBlank()})."
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
             * A **$buildFlavor** build, so these are **$store** keys. Debug and stage use
             * RevenueCat's Test Store — simulated purchases, no Play Console setup — and
             * release uses the real one.
             *
             * Blank when the checkout has no credentials, which is the signal
             * `billingInfrastructureModule` reads to leave the free-plan entitlement source
             * in place.
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

/** What a RevenueCat Test Store key looks like. */
private val TEST_KEY_PREFIX = "test_"

/** What a RevenueCat *secret* key looks like. Never belongs in a client. */
private val SECRET_KEY_PREFIX = "sk_"
