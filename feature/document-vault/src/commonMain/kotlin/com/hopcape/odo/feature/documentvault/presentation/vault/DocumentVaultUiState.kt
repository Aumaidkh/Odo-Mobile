package com.hopcape.odo.feature.documentvault.presentation.vault

import kotlinx.datetime.LocalDate

/** The papers Odo tracks for a car (drives the row's icon + title). */
internal enum class DocumentType { INSURANCE, PUC, RC, LICENCE }

/** A document's renewal status — the row's subtitle, pill, and action all derive from this. */
internal sealed interface DocStatus {
    /** Not captured yet — the owner is prompted to add it. */
    data object NotAdded : DocStatus
    /** On file and in date. A null [validTill] means it never expires (e.g. RC = lifetime). */
    data class Valid(val validTill: LocalDate?) : DocStatus
    /** In date but within the renewal window — flagged so a fine is avoided. */
    data class ExpiresSoon(val days: Int, val on: LocalDate) : DocStatus
    /** Lapsed — needs renewing now. */
    data class Expired(val on: LocalDate) : DocStatus
}

internal data class DocumentRow(val type: DocumentType, val status: DocStatus)

/**
 * Display state for the document vault overview. The overall header tone is derived:
 * anything needing attention wins (amber), else all-added-and-valid reads "covered"
 * (green), else it's the "add your documents" prompt (accent).
 */
internal data class DocumentVaultUiState(val documents: List<DocumentRow>) {
    val attention: List<DocumentRow>
        get() = documents.filter { it.status is DocStatus.ExpiresSoon || it.status is DocStatus.Expired }

    val allValid: Boolean
        get() = documents.isNotEmpty() && documents.all { it.status is DocStatus.Valid }
}

// --- Samples for previews + the pre-ViewModel route host (mirror the three mockups) ---

internal fun sampleVaultEmpty(): DocumentVaultUiState = DocumentVaultUiState(
    DocumentType.entries.map { DocumentRow(it, DocStatus.NotAdded) },
)

internal fun sampleVaultCovered(): DocumentVaultUiState = DocumentVaultUiState(
    listOf(
        DocumentRow(DocumentType.INSURANCE, DocStatus.Valid(LocalDate(2027, 7, 3))),
        DocumentRow(DocumentType.PUC, DocStatus.Valid(LocalDate(2026, 11, 12))),
        DocumentRow(DocumentType.RC, DocStatus.Valid(validTill = null)),
        DocumentRow(DocumentType.LICENCE, DocStatus.Valid(LocalDate(2031, 8, 14))),
    ),
)

internal fun sampleVaultAttention(): DocumentVaultUiState = DocumentVaultUiState(
    listOf(
        DocumentRow(DocumentType.INSURANCE, DocStatus.ExpiresSoon(days = 7, on = LocalDate(2026, 7, 3))),
        DocumentRow(DocumentType.PUC, DocStatus.Valid(LocalDate(2026, 11, 12))),
        DocumentRow(DocumentType.RC, DocStatus.Valid(validTill = null)),
        DocumentRow(DocumentType.LICENCE, DocStatus.NotAdded),
    ),
)
