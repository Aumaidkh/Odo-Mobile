plugins {
    // By id, not alias: the Kotlin Gradle plugin is already on the build classpath via
    // kotlinMultiplatform, so requesting a version here fails to resolve.
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(libs.ksp.symbolProcessingApi)
    implementation(libs.kotlinPoet)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinCompileTesting.core)
    testImplementation(libs.kotlinCompileTesting.ksp)
    // The generated code references these, so a test that compiles its output needs them.
    // The :core:config runtime itself is compiled from source — see configRuntimeSources.
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.koin.core)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

// kotlin-compile-testing drives the compiler directly, and every entry point it offers is
// behind the compiler's own experimental opt-in. Scoped to the test source set so nothing
// in the processor itself can quietly start using an unstable compiler API.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
}

tasks.withType<Test>().configureEach {
    // Tests compile :core:config's real commonMain sources rather than a stub of them.
    // A stub would drift from the runtime it is meant to represent, and the whole value
    // of these tests is that the generated code compiles against the actual contract.
    systemProperty(
        "odo.config.runtimeSources",
        project.rootDir.resolve("core/config/src/commonMain/kotlin").absolutePath,
    )
}
