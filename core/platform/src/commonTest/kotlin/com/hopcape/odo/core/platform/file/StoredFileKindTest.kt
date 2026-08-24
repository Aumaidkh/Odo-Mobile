package com.hopcape.odo.core.platform.file

import kotlin.test.Test
import kotlin.test.assertEquals

class StoredFileKindTest {

    @Test
    fun `recognises the extensions the app writes`() {
        assertEquals(StoredFileKind.IMAGE, StoredFileKinds.of("documents/car-1/doc-1.jpg"))
        assertEquals(StoredFileKind.IMAGE, StoredFileKinds.of("bills/car-1/log-1.png"))
        assertEquals(StoredFileKind.PDF, StoredFileKinds.of("documents/car-1/doc-1.pdf"))
    }

    @Test
    fun `is case insensitive because the extension travels from a picked filename`() {
        assertEquals(StoredFileKind.IMAGE, StoredFileKinds.of("documents/car-1/doc-1.JPEG"))
        assertEquals(StoredFileKind.PDF, StoredFileKinds.of("documents/car-1/doc-1.PDF"))
    }

    @Test
    fun `anything else is unsupported including the fallback extension`() {
        assertEquals(
            StoredFileKind.UNSUPPORTED,
            StoredFileKinds.of("documents/car-1/doc-1.${StorageKey.FALLBACK_EXTENSION}"),
        )
        assertEquals(StoredFileKind.UNSUPPORTED, StoredFileKinds.of("documents/car-1/doc-1.docx"))
    }

    @Test
    fun `a dot in a directory is not an extension`() {
        assertEquals(StoredFileKind.UNSUPPORTED, StoredFileKinds.of("documents/car.1/doc-1"))
    }

    @Test
    fun `a key with no extension at all is unsupported rather than a crash`() {
        assertEquals(StoredFileKind.UNSUPPORTED, StoredFileKinds.of(""))
        assertEquals(StoredFileKind.UNSUPPORTED, StoredFileKinds.of("documents/car-1/doc-1"))
    }
}
