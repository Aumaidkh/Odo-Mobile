package com.hopcape.odo.core.domain.scan.model

import com.hopcape.odo.core.common.id.IdGenerator
import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * Typed identity for one scan attempt. A `value class` so it can never be mistaken for a
 * service-log or document id.
 */
@JvmInline
value class ScanId(val value: String) {
    companion object {
        /** Mint a fresh, client-side id. */
        fun new(ids: IdGenerator): ScanId = ScanId(ids.newId())
    }
}

/**
 * A photo the owner captured, waiting to be read.
 *
 * [storageKey] is the relative key the platform file store minted, never an absolute path —
 * the app's private directory moves between OS versions and restores, so a stored absolute
 * path becomes a file that will not open. It is an opaque string here for the same reason
 * `ServiceLogEntry.billPhotoRef` is: where the bytes live is infrastructure, not domain.
 *
 * [capturedAt] travels with it because the extractor may run much later than the shutter —
 * offline, the scan queues until there is a network — and a bill read next week is still a
 * bill photographed today.
 */
data class ScannedImage(
    val id: ScanId,
    val storageKey: String,
    val capturedAt: Instant,
)
