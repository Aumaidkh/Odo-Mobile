package com.hopcape.odo.feature.documentvault.presentation.share

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import kotlinx.datetime.LocalDate

/**
 * Display state for the share sheet.
 *
 * There is no redaction toggle. The mockup offered to hide the policy number, but the app
 * does not hold a policy number, so the toggle would have hidden nothing while telling the
 * owner it had. It comes back with the fields it can act on.
 */
@Immutable
internal data class ShareDocumentUiState(
    val id: DocumentId,
    val type: DocumentType,
    /** The owner's own label, or `null` to fall back to the type's name. */
    val title: String?,
    val validity: DocumentValidity,
    /** False when the stored file has gone missing; sharing is then disabled. */
    val isFileAvailable: Boolean = true,
    /** What the last action did, shown under the buttons. `null` until one is taken. */
    val notice: UiText? = null,
)

/**
 * Where a document went.
 *
 * Two cases, because the sheet offers two things and neither of them names an app. The
 * system chooser is what picks WhatsApp or mail, and it does not say which was picked — so
 * recording anything more precise here would be recording a guess.
 */
internal enum class ShareTarget { SYSTEM, DOWNLOADS }

/** Sample for previews. */
internal fun sampleShareDocument() = ShareDocumentUiState(
    id = DocumentId("d1"),
    type = DocumentType.INSURANCE,
    title = "SafeDrive comprehensive",
    validity = DocumentValidity.ExpiringSoon(LocalDate(2026, 8, 4), daysLeft = 7),
)
