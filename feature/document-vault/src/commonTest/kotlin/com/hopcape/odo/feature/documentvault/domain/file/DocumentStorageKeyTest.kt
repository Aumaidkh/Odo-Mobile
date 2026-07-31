package com.hopcape.odo.feature.documentvault.domain.file

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.DocumentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentStorageKeyTest {

    private val carId = CarId("car-1")
    private val documentId = DocumentId("doc-1")

    private fun key(extension: String?) = DocumentStorageKey.of(carId, documentId, extension)

    @Test
    fun keyIsScopedByCarAndNamedByDocument() {
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
    fun pathSeparatorsCanNeverEscapeTheDocumentsRoot() {
        listOf("../../etc/passwd", "pdf/../..", "/pdf", "pdf.", "p df").forEach { raw ->
            val built = key(raw)
            assertEquals("documents/car-1/doc-1.bin", built, "raw=<$raw>")
            assertTrue(built.startsWith("${DocumentStorageKey.ROOT}/"), "raw=<$raw> escaped the root")
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
