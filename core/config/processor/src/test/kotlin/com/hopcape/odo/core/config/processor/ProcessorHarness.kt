package com.hopcape.odo.core.config.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The outcome of running the processor over a snippet and compiling what it wrote. */
internal class ProcessorResult(
    private val exitCode: KotlinCompilation.ExitCode,
    val messages: String,
    private val sourcesDir: File,
) {

    val succeeded: Boolean get() = exitCode == KotlinCompilation.ExitCode.OK

    /** Every Kotlin file the processor generated, concatenated. */
    val generated: String by lazy {
        if (!sourcesDir.isDirectory) {
            ""
        } else {
            sourcesDir.walkTopDown()
                .filter { it.extension == "kt" }
                .joinToString("\n") { it.readText() }
        }
    }

    fun assertSucceeded() = assertTrue(succeeded, "expected success, got:\n$messages")

    /** Asserts the build failed, and that the message says what was wrong and where. */
    fun assertRejected(vararg mustMention: String) {
        assertTrue(!succeeded, "expected the build to fail, but it succeeded")
        mustMention.forEach { fragment ->
            assertTrue(
                messages.contains(fragment),
                "expected the error to mention '$fragment'. Actual:\n$messages",
            )
        }
    }

    fun assertGeneratedContains(vararg fragments: String) {
        assertEquals(true, succeeded, messages)
        fragments.forEach { fragment ->
            assertTrue(
                generated.contains(fragment),
                "expected the generated code to contain '$fragment'. Actual:\n$generated",
            )
        }
    }
}

/**
 * Runs the processor over a snippet and compiles everything it generated.
 *
 * The `:core:config` runtime is compiled from its real sources, not from a stub. A stub
 * would drift from the contract that ships, and proving the generated code compiles
 * against the real one is the whole point of these tests.
 */
internal fun process(source: String): ProcessorResult {
    val compilation = KotlinCompilation().apply {
        sources = runtimeSources() + SourceFile.kotlin("Subject.kt", source)
        configureKsp { symbolProcessorProviders += ConfigProcessorProvider() }
        inheritClassPath = true
        messageOutputStream = System.out
    }
    val result = compilation.compile()
    return ProcessorResult(result.exitCode, result.messages, compilation.kspSourcesDir.resolve("kotlin"))
}

private fun runtimeSources(): List<SourceFile> {
    val root = File(
        requireNotNull(System.getProperty("odo.config.runtimeSources")) {
            "odo.config.runtimeSources is not set; see this module's build.gradle.kts"
        },
    )
    check(root.isDirectory) { "Not a directory: $root" }
    return root.walkTopDown().filter { it.extension == "kt" }.map(SourceFile::fromPath).toList()
}
