package com.hopcape.odo.feature.documentvault.presentation.vault

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import kotlinx.datetime.LocalDate

/**
 * Display state for the vault overview.
 *
 * The rows and the header come from one [VaultContent], because they are one fact about the
 * car. A header counting two documents above three amber rows would be worse than a slow
 * screen.
 */
@Immutable
internal data class DocumentVaultUiState(
    val content: Loadable<VaultContent> = Loadable.Loading,
)

/** The vault's rows plus the header they add up to. */
@Immutable
internal data class VaultContent(
    val rows: List<DocumentRow>,
    val header: VaultHeader,
)

/**
 * One row: a document on file, or one the vault asks for and does not have.
 *
 * Domain [DocumentType] and [DocumentValidity] are used as they are, not copied into
 * presentation enums. The rule that turns an expiry date into "valid / expiring / expired"
 * lives in one place, and a parallel enum here would be a second copy of it.
 */
@Immutable
internal sealed interface DocumentRow {

    val type: DocumentType

    /** On file. [id] is what a tap opens; [reminderDaysBefore] is the next nudge, if any. */
    @Immutable
    data class OnFile(
        val id: DocumentId,
        override val type: DocumentType,
        /** The owner's own label, or `null` to fall back to the type's name. */
        val title: String?,
        val validity: DocumentValidity,
        val reminderDaysBefore: Int?,
    ) : DocumentRow {
        val needsAttention: Boolean get() = validity.needsAttention
    }

    /** Not added yet — the row offers "Add". */
    @Immutable
    data class Missing(override val type: DocumentType) : DocumentRow
}

/**
 * The card at the top. Decided once when the state is built, so the screen renders it
 * instead of working it out again.
 */
@Immutable
internal sealed interface VaultHeader {

    /** Nothing on file yet. */
    data object AddPrompt : VaultHeader

    /** Everything on file and in date. [count] is how many documents that is. */
    @Immutable
    data class Covered(val count: Int) : VaultHeader

    /** [count] documents are expiring or expired; [first] names the most urgent one. */
    @Immutable
    data class NeedsAttention(val count: Int, val first: DocumentType) : VaultHeader
}

// --- Samples for previews ---------------------------------------------------------

private fun onFile(
    id: String,
    type: DocumentType,
    validity: DocumentValidity,
    reminderDaysBefore: Int? = null,
) = DocumentRow.OnFile(
    id = DocumentId(id),
    type = type,
    title = null,
    validity = validity,
    reminderDaysBefore = reminderDaysBefore,
)

private val PREVIEW_TRACKED_TYPES = listOf(
    DocumentType.INSURANCE,
    DocumentType.PUC,
    DocumentType.RC,
    DocumentType.LICENCE,
)

internal fun sampleVaultEmpty() = DocumentVaultUiState(
    Loadable.Ready(
        VaultContent(
            rows = PREVIEW_TRACKED_TYPES.map { DocumentRow.Missing(it) },
            header = VaultHeader.AddPrompt,
        ),
    ),
)

internal fun sampleVaultCovered() = DocumentVaultUiState(
    Loadable.Ready(
        VaultContent(
            rows = listOf(
                onFile("d1", DocumentType.INSURANCE, DocumentValidity.Valid(LocalDate(2027, 7, 3), daysLeft = 340)),
                onFile("d2", DocumentType.PUC, DocumentValidity.Valid(LocalDate(2026, 11, 12), daysLeft = 107)),
                onFile("d3", DocumentType.RC, DocumentValidity.NoExpiry),
                onFile("d4", DocumentType.LICENCE, DocumentValidity.Valid(LocalDate(2031, 8, 14), daysLeft = 1_843)),
            ),
            header = VaultHeader.Covered(count = 4),
        ),
    ),
)

internal fun sampleVaultAttention() = DocumentVaultUiState(
    Loadable.Ready(
        VaultContent(
            rows = listOf(
                onFile(
                    id = "d1",
                    type = DocumentType.INSURANCE,
                    validity = DocumentValidity.ExpiringSoon(LocalDate(2026, 8, 4), daysLeft = 7),
                    reminderDaysBefore = 7,
                ),
                onFile("d2", DocumentType.PUC, DocumentValidity.Valid(LocalDate(2026, 11, 12), daysLeft = 107)),
                onFile("d3", DocumentType.RC, DocumentValidity.NoExpiry),
                DocumentRow.Missing(DocumentType.LICENCE),
            ),
            header = VaultHeader.NeedsAttention(count = 1, first = DocumentType.INSURANCE),
        ),
    ),
)
