package com.hopcape.odo.preview

import com.hopcape.odo.core.data.remote.RemoteBucket
import com.hopcape.odo.core.navigation.OdoDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `:core:navigation` names buckets as strings so a feature can ask for one without depending
 * on the data layer, and this module is the only one that sees both sides. Without this, a
 * renamed enum constant would compile everywhere and simply stop fetching files — the failure
 * being an empty screen on a second phone, which nobody would trace back to a rename.
 */
class FilePreviewBucketTest {

    @Test
    fun `every bucket a preview can name exists in the storage layer`() {
        val named = listOf(
            OdoDestination.FilePreview.BUCKET_DOCUMENTS,
            OdoDestination.FilePreview.BUCKET_BILL_PHOTOS,
        )
        named.forEach { name ->
            assertNotNull(
                RemoteBucket.entries.firstOrNull { it.name == name },
                "no RemoteBucket named $name",
            )
        }
    }

    @Test
    fun `the vault's documents and the service log's bills point at different buckets`() {
        assertEquals(RemoteBucket.DOCUMENTS.name, OdoDestination.FilePreview.BUCKET_DOCUMENTS)
        assertEquals(RemoteBucket.BILL_PHOTOS.name, OdoDestination.FilePreview.BUCKET_BILL_PHOTOS)
    }
}
