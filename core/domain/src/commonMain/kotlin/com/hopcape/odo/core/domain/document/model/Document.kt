package com.hopcape.odo.core.domain.document.model

import arrow.core.Either
import arrow.core.EitherNel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate
import arrow.core.right
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.LocalDate

/**
 * The Document aggregate root — one paper in a car's vault: insurance, PUC, RC, a
 * licence, a loan letter.
 *
 * Shared kernel, because a document is never only the vault's: the garage shows which
 * ones are on file, the timeline logs their renewals, the reminder engine watches their
 * expiry, and the resale passport lists them. Mirrors the `documents` table
 * (DB_SCHEMA §9.7) — every column that is domain-meaningful, none that are storage detail.
 *
 * Construct only via [create], which enforces every field invariant and accumulates *all*
 * failures (so a review-and-confirm step can show them at once). The private constructor
 * guarantees an invalid document can never exist.
 *
 * [storagePath] is required: a vault entry without a file is a reminder, not a document —
 * which is why the DB column is `NOT NULL` too. It stays an opaque string (like the
 * service log's `billPhotoRef`) because where the bytes live is infrastructure, not domain.
 */
class Document private constructor(
    val id: DocumentId,
    val ownerId: OwnerId,
    val carId: CarId,
    val type: DocumentType,
    /** The owner's own label ("SafeDrive comprehensive"); absent falls back to [type]. */
    val title: DocumentTitle?,
    /** Where the file is kept. A local path now; the storage object key once sync lands. */
    val storagePath: String,
    val source: DocumentSource,
    val issuedOn: LocalDate?,
    /** Null for papers that never lapse — see [DocumentValidity.NoExpiry]. */
    val expiresOn: LocalDate?,
) {
    /**
     * This document's standing on [today] — valid, due for renewal, or lapsed. Derived,
     * never stored, so it can never go stale in the way a persisted status would.
     */
    fun validity(today: LocalDate): DocumentValidity = DocumentValidity.of(expiresOn, today)

    /** Shorthand for the question most callers actually ask. */
    fun needsAttentionOn(today: LocalDate): Boolean = validity(today).needsAttention

    /**
     * Swap the stored file — the "Replace file" action, e.g. a clearer scan of the same
     * paper. Nothing the document *claims* changes, so no other field moves; a renewal is
     * a new document, not a replaced file.
     *
     * [source] travels with the file, because it describes where these bytes came from —
     * replacing a DigiLocker copy with a phone photo must lose the Verified badge.
     */
    fun withFile(storagePath: String, source: DocumentSource): Document =
        copy(storagePath = storagePath, source = source)

    /**
     * Copy with overrides. Private: a document changes only through the named transition
     * above, so no caller can assemble a state the invariants never approved. Editing the
     * details re-runs [create] with the same id, which keeps validation in one place.
     */
    private fun copy(
        storagePath: String = this.storagePath,
        source: DocumentSource = this.source,
    ): Document = Document(
        id = id,
        ownerId = ownerId,
        carId = carId,
        type = type,
        title = title,
        storagePath = storagePath,
        source = source,
        issuedOn = issuedOn,
        expiresOn = expiresOn,
    )

    companion object {
        /**
         * Validating factory — the single entry point for building a document. Takes raw,
         * domain-meaningful inputs and accumulates every field failure. [today] is injected
         * (the domain owns no clock) so the issue-date guard is deterministic and testable.
         *
         * Note what is deliberately *not* validated: an [expiresOn] in the past. Storing a
         * lapsed policy is the vault's whole point — [DocumentValidity.Expired] exists to
         * describe exactly that, and rejecting it would make the renewal flow impossible.
         */
        fun create(
            id: DocumentId,
            ownerId: OwnerId,
            carId: CarId,
            type: DocumentType,
            storagePath: String?,
            source: DocumentSource,
            today: LocalDate,
            title: String? = null,
            issuedOn: LocalDate? = null,
            expiresOn: LocalDate? = null,
        ): EitherNel<DomainError, Document> = either {
            zipOrAccumulate(
                { validateStoragePath(storagePath).bind() },
                { DocumentTitle.of(title).bind() },
                { validateIssuedOn(issuedOn, today).bind() },
                { validateExpiresOn(issuedOn, expiresOn).bind() },
            ) { validPath, validTitle, validIssuedOn, validExpiresOn ->
                Document(
                    id = id,
                    ownerId = ownerId,
                    carId = carId,
                    type = type,
                    title = validTitle,
                    storagePath = validPath,
                    source = source,
                    issuedOn = validIssuedOn,
                    expiresOn = validExpiresOn,
                )
            }
        }

        /**
         * Rehydrate a document from already-persisted, trusted data (the local DB, which
         * mirrors these invariants via its own constraints).
         *
         * Unlike [create], this does not accumulate errors: a value that fails to
         * reconstruct signals local data corruption — a programming/storage error, not user
         * input — and fails fast. Construction stays inside the domain, so the value
         * objects' private constructors remain private.
         */
        fun reconstitute(
            id: DocumentId,
            ownerId: OwnerId,
            carId: CarId,
            type: DocumentType,
            storagePath: String,
            source: DocumentSource,
            title: String? = null,
            issuedOn: LocalDate? = null,
            expiresOn: LocalDate? = null,
        ): Document = Document(
            id = id,
            ownerId = ownerId,
            carId = carId,
            type = type,
            title = DocumentTitle.of(title)
                .getOrElse { error("corrupt documents.title for ${id.value}") },
            storagePath = storagePath.ifBlank { error("corrupt documents.storage_path for ${id.value}") },
            source = source,
            issuedOn = issuedOn,
            expiresOn = expiresOn,
        )

        private fun validateStoragePath(path: String?): Either<DomainError, String> {
            val trimmed = path?.trim()
            return if (trimmed.isNullOrEmpty()) DomainError.MissingDocumentFile.left() else trimmed.right()
        }

        private fun validateIssuedOn(
            issuedOn: LocalDate?,
            today: LocalDate,
        ): Either<DomainError, LocalDate?> = when {
            issuedOn == null -> null.right()
            issuedOn > today -> DomainError.IssueDateInFuture.left()
            else -> issuedOn.right()
        }

        /**
         * A document cannot lapse before it was issued. Checked against the *raw* issue
         * date so this failure is still reported when that date is itself invalid — both
         * are the owner's to fix, and they should see both at once.
         */
        private fun validateExpiresOn(
            issuedOn: LocalDate?,
            expiresOn: LocalDate?,
        ): Either<DomainError, LocalDate?> = when {
            expiresOn == null -> null.right()
            issuedOn != null && expiresOn < issuedOn -> DomainError.ExpiryBeforeIssueDate.left()
            else -> expiresOn.right()
        }
    }
}
