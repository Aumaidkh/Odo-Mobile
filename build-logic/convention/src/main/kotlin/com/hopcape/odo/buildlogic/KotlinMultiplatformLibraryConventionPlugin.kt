package com.hopcape.odo.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Convention for a shared Kotlin Multiplatform *library* module (Android + iOS).
 *
 * Centralises everything that would otherwise be copy-pasted into every KMP
 * module: the iOS targets + static framework, the Android library SDK levels,
 * the JVM target, and Android test/resource wiring. A consuming module only
 * declares its `namespace`, its source-set dependencies, and (optionally) extra
 * targets — never the boilerplate.
 */
class KotlinMultiplatformLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.kotlin.multiplatform.library")
        }

        extensions.configure(KotlinMultiplatformExtension::class.java) { kmp ->
            // iOS targets + a static framework. baseName is derived from the
            // module path so it stays unique without per-module config.
            kmp.iosArm64()
            kmp.iosSimulatorArm64()
            kmp.targets.withType(KotlinNativeTarget::class.java).configureEach { native ->
                native.binaries.framework { framework ->
                    framework.baseName = iosFrameworkBaseName
                    framework.isStatic = true
                }
            }

            // Android library config (new AGP KMP DSL). `namespace` is module
            // identity, so it is intentionally left to the consuming module.
            (kmp as ExtensionAware).extensions.configure(
                KotlinMultiplatformAndroidLibraryTarget::class.java
            ) { android ->
                android.compileSdk = libs.intVersion("android-compileSdk")
                android.minSdk = libs.intVersion("android-minSdk")
                android.compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
                }
                android.androidResources.enable = true
                android.withHostTest {
                    isIncludeAndroidResources = true
                    // A host test runs against the stub android.jar, where every method
                    // throws "not mocked" rather than doing anything. That is fine until a
                    // test has to construct a vendor SDK type whose constructor happens to
                    // call one — Firebase's auth exceptions validate their error code
                    // through android.text.TextUtils, which makes the error mapping in
                    // :infrastructure:firebase:auth untestable without this.
                    //
                    // Set here rather than per module because AGP's KMP DSL allows exactly
                    // one withHostTest block and this plugin owns it. It can only turn a
                    // call that currently throws into one that returns a default, so no
                    // passing test can start failing because of it; the cost is that a test
                    // which *should* have noticed it was touching the Android framework now
                    // quietly gets a zero.
                    isReturnDefaultValues = true
                }
            }
        }
    }
}
