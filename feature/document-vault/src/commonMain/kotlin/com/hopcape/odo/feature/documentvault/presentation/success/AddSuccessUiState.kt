package com.hopcape.odo.feature.documentvault.presentation.success

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.domain.document.model.DocumentType
import kotlinx.datetime.LocalDate

/**
 * Display state for the screen shown after a document is saved.
 *
 * [reminder] is nullable, because not every document earns one: an RC never expires, and a
 * document added after it lapsed has no nudge left to schedule. The screen then confirms
 * the save without promising a reminder that will never arrive.
 */
@Immutable
internal data class AddSuccessUiState(
    val type: DocumentType,
    /** The owner's own label, or `null` to fall back to the type's name. */
    val title: String?,
    val reminder: ReminderPromise?,
)

/** When Odo will nudge: [daysBefore] the document expires, which falls [on] this day. */
@Immutable
internal data class ReminderPromise(
    val daysBefore: Int,
    val on: LocalDate,
)

// --- Samples for previews ---------------------------------------------------------

internal fun sampleAddSuccess() = AddSuccessUiState(
    type = DocumentType.INSURANCE,
    title = null,
    reminder = ReminderPromise(daysBefore = 7, on = LocalDate(2026, 6, 26)),
)

internal fun sampleAddSuccessNoReminder() = AddSuccessUiState(
    type = DocumentType.RC,
    title = null,
    reminder = null,
)
