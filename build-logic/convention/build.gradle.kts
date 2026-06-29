import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // `java-gradle-plugin` gives us the `gradlePlugin { }` registration DSL.
    // We deliberately use the standalone Kotlin JVM plugin (pinned to the same
    // Kotlin version as the app) instead of `kotlin-dsl`, because Gradle's
    // embedded kotlin-dsl compiler (2.2.0) cannot read the 2.4.0 metadata in
    // the Kotlin Gradle plugin's DSL types (e.g. KotlinMultiplatformExtension).
    `java-gradle-plugin`
    alias(libs.plugins.kotlinJvm)
}

group = "com.hopcape.odo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    // compileOnly: these Gradle plugins are on the build classpath of the
    // consuming build (supplied when the convention plugin is applied), so the
    // convention module only needs them to compile against their DSL types.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.composeMultiplatform.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    // Provides the org.gradle.kotlin.dsl helpers (configure<>, getByType<>).
    implementation(gradleKotlinDsl())
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatformLibrary") {
            id = "odo.kmp.library"
            implementationClass =
                "com.hopcape.odo.buildlogic.KotlinMultiplatformLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "odo.android.application"
            implementationClass =
                "com.hopcape.odo.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("composeMultiplatform") {
            id = "odo.compose.multiplatform"
            implementationClass =
                "com.hopcape.odo.buildlogic.ComposeMultiplatformConventionPlugin"
        }
    }
}
