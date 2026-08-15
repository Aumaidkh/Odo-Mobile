package com.hopcape.odo.feature.documentvault.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import com.hopcape.odo.core.platform.file.StorageKey
import com.hopcape.odo.core.platform.share.EXPORT_DIRECTORY

/**
 * Copies a document's file into the export directory, ready to be handed to another app.
 *
 * A copy rather than the file itself, because sharing means granting the receiving app read
 * access to a directory, and the one Odo exports holds nothing but these copies. Sharing
 * straight out of the vault's own directory would mean granting a stranger app every policy
 * and bill the owner has ever filed.
 *
 * The copy is named after the document rather than after its id: the name travels with the
 * file, and "Insurance.pdf" is what the person on the other end needs to see.
 */
internal class ExportDocumentFileUseCase(
    private val files: PlatformFileStore,
) {
    /** The exported copy's storage key. */
    suspend operator fun invoke(document: Document): Either<DomainError, String> = either {
        val bytes = files.bytes(document.storagePath).bind()
        val key = StorageKey.of(
            // Per car, matching every other export: one car's papers do not sit in another
            // car's folder even for the moment they are being shared.
            directory = "$EXPORT_DIRECTORY/${document.carId.value}",
            fileName = document.exportName(),
            rawExtension = document.storagePath.substringAfterLast('.', missingDelimiterValue = ""),
        )
        files.write(key, bytes).bind()
    }
}

/**
 * What the document is called once it leaves the app, without an extension.
 *
 * The owner's own label when there is one, because that is what they know the paper as.
 * Anything a file name cannot carry is dropped rather than escaped — a name is not the place
 * to preserve punctuation, and `/` in one would write the copy into a directory nobody meant
 * to create.
 */
internal fun Document.exportName(): String {
    val fromTitle = title?.value.orEmpty()
        .map { if (it.isLetterOrDigit() || it in ALLOWED_NAME_CHARACTERS) it else ' ' }
        .joinToString(separator = "")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(separator = " ")
    return fromTitle.ifBlank { type.exportName() }
}

/** The document's file name with the extension its stored file carries. */
internal fun Document.exportFileName(): String {
    val extension = storagePath.substringAfterLast('.', missingDelimiterValue = "")
    return if (extension.isBlank()) exportName() else "${exportName()}.$extension"
}

/**
 * The type's name for a file. Short names stay as they are — "PUC" and "RC" are how the
 * papers are known, and title-casing them into "Puc" would only look like a typo.
 */
private fun DocumentType.exportName(): String =
    if (name.length <= ACRONYM_LENGTH) name else name.lowercase().replaceFirstChar { it.uppercase() }

private const val ACRONYM_LENGTH = 3

private val ALLOWED_NAME_CHARACTERS = setOf(' ', '-', '_')
