package com.hopcape.odo.feature.documentvault.presentation.detail

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.presentation.state.Submission
import kotlinx.datetime.LocalDate

/**
 * Display state for one document's detail.
 *
 * [submission] belongs to the deletes and replaces started from this screen. The document
 * keeps loading independently of them.
 */
@Immutable
internal data class DocumentDetailUiState(
    val content: Loadable<DocumentDetailContent> = Loadable.Loading,
    val submission: Submission = Submission.Idle,
)

/**
 * The document as the screen shows it.
 *
 * Insurance specifics — provider, policy number, sum insured, cover type, premium, what is
 * covered — are deliberately absent. Nothing in the app can read them from a document yet,
 * and showing invented figures about someone's insurance is worse than showing none. They
 * arrive with the scanner that can extract them.
 */
@Immutable
internal data class DocumentDetailContent(
    val id: DocumentId,
    val type: DocumentType,
    /** The owner's own label, or `null` to fall back to the type's name. */
    val title: String?,
    val validity: DocumentValidity,
    val issuedOn: LocalDate?,
    /** How much of the document's life has run (0..1), or `null` when its span is unknown. */
    val validityProgress: Float?,
    val reminderDaysBefore: Int?,
    /** True only for a copy that came from the issuer. */
    val isVerified: Boolean,
    /** False when the stored file has gone missing; the screen then hides "View". */
    val isFileAvailable: Boolean,
    /**
     * Where the file is kept.
     *
     * The rest of this state is booleans and ids on purpose, and a path in UI state is the
     * exception rather than a new habit: drawing the document's own thumbnail means the
     * composable has to name the file it is drawing, and nothing else can do that for it.
     */
    val storagePath: String?,
)

// --- Samples for previews ---------------------------------------------------------

internal fun sampleDocumentDetail() = DocumentDetailUiState(
    Loadable.Ready(
        DocumentDetailContent(
            id = DocumentId("d1"),
            type = DocumentType.INSURANCE,
            title = "SafeDrive comprehensive",
            validity = DocumentValidity.ExpiringSoon(LocalDate(2026, 8, 4), daysLeft = 7),
            issuedOn = LocalDate(2025, 8, 4),
            validityProgress = 0.98f,
            reminderDaysBefore = 7,
            isVerified = true,
            isFileAvailable = true,
            storagePath = "documents/c1/d1.pdf",
        ),
    ),
)

internal fun sampleLifetimeDocumentDetail() = DocumentDetailUiState(
    Loadable.Ready(
        DocumentDetailContent(
            id = DocumentId("d2"),
            type = DocumentType.RC,
            title = null,
            validity = DocumentValidity.NoExpiry,
            issuedOn = null,
            validityProgress = null,
            reminderDaysBefore = null,
            isVerified = false,
            isFileAvailable = true,
            storagePath = "documents/c1/d2.jpg",
        ),
    ),
)
