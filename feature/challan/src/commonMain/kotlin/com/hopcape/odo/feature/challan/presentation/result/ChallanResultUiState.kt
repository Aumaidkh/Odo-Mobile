package com.hopcape.odo.feature.challan.presentation.result

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.feature.challan.presentation.list.ChallanRow
import com.hopcape.odo.feature.challan.presentation.state.Loadable

/** Display state for a stranger's plate (mockup 8) — read-only, saved nowhere. */
@Immutable
internal data class ChallanResultUiState(
    /** Display form of the plate — the screen's title. */
    val regNo: String = "",
    val content: Loadable<ChallanResultContent> = Loadable.Loading,
    val refreshing: Boolean = false,
)

/** The lookup's answer, shaped for the screen. */
@Immutable
internal data class ChallanResultContent(
    /** "Checked just now" / "Checked 2 minutes ago" — the age of *this* answer. */
    val checkedAgo: UiText,
    /** The transfer warning; `null` when the vehicle is clean. */
    val transfer: TransferWarning?,
    val rows: List<ChallanRow>,
)

/** "3 PENDING · RS. 3,200 — these transfer with the vehicle." */
@Immutable
internal data class TransferWarning(
    val badge: UiText,
)
