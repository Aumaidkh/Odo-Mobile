package com.hopcape.odo.core.platform.file

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorageKeyTest {

    private fun key(extension: String?) = StorageKey.of("documents/car-1", "doc-1", extension)

    @Test
    fun keyIsTheDirectoryTheCallerNamedPlusItsFile() {
        assertEquals("documents/car-1/doc-1.pdf", key("pdf"))
    }

    @Test
    fun extensionIsLowercasedAndUndotted() {
        assertEquals("documents/car-1/doc-1.jpg", key(".JPG"))
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals("documents/car-1/doc-1.jpeg", key("  jpeg "))
    }

    @Test
    fun unknownExtensionFallsBack() {
        listOf(null, "", "   ").forEach { raw ->
            assertEquals("documents/car-1/doc-1.bin", key(raw), "raw=<$raw>")
        }
    }

    @Test
    fun pathSeparatorsCanNeverEscapeTheDirectory() {
        listOf("../../etc/passwd", "pdf/../..", "/pdf", "pdf.", "p df").forEach { raw ->
            val built = key(raw)
            assertEquals("documents/car-1/doc-1.bin", built, "raw=<$raw>")
            assertTrue(built.startsWith("documents/car-1/"), "raw=<$raw> escaped the directory")
        }
    }

    @Test
    fun overLongExtensionIsNotTrusted() {
        assertEquals("documents/car-1/doc-1.bin", key("superlongextension"))
    }

    @Test
    fun keyIsRelative_soAMovedDataDirectoryCannotOrphanTheFile() {
        assertTrue(!key("pdf").startsWith("/"), "the key must resolve against the platform's own root")
    }
}
