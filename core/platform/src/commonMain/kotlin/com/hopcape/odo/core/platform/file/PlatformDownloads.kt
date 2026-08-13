package com.hopcape.odo.core.platform.file

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * Puts a copy of a stored file where the owner keeps their downloads.
 *
 * The counterpart of sharing rather than a flavour of it: a share hands the file to another
 * app the owner picks, this leaves a copy they can find later with Odo closed — at an RTO
 * counter, in a mail attachment picker, on a laptop after a cable transfer.
 *
 * A port rather than a composable ([com.hopcape.odo.core.platform.share.rememberFileSharer]
 * is one) because nothing here needs an Activity: the copy is written, not presented.
 *
 * Copying rather than moving. What the vault holds is the record; this is a duplicate that
 * leaves Odo's private storage for good, and losing the original to a failed copy would be
 * the worst possible trade.
 */
fun interface PlatformDownloads {

    /**
     * Copy the file at [storageKey] out of app storage, named [fileName] and declared as
     * [mimeType] — [StoredFileKinds.mimeTypeOf] answers the type for a stored key.
     *
     * [fileName] carries its own extension; it is what the owner will see in their file
     * list, so it is the document's name rather than the id the app stores it under.
     */
    suspend fun saveCopy(
        storageKey: String,
        fileName: String,
        mimeType: String,
    ): Either<DomainError, Unit>
}
