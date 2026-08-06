package com.hopcape.odo.feature.reminders.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.feature.reminders.presentation.state.Loadable
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_line_due_in_days
import com.hopcape.odo.feature.reminders.resources.rm_line_suggested_monthly
import com.hopcape.odo.feature.reminders.resources.rm_line_valid_till
import com.hopcape.odo.feature.reminders.resources.rm_new_default_tyre
import com.hopcape.odo.feature.reminders.resources.rm_row_insurance
import com.hopcape.odo.feature.reminders.resources.rm_row_puc
import kotlinx.datetime.LocalDate

/** Which glyph (and tint) a reminder row shows, keyed off its kind. */
internal enum class ReminderIcon { SHIELD, OIL, LEAF, TYRE }

/**
 * A row's text: a resource reference from the ViewModel, or the owner's own words (a
 * custom reminder's title is data, not copy — there is no resource to point at).
 */
@Immutable
internal sealed interface RowText {
    @Immutable
    data class Res(val value: UiText) : RowText

    @Immutable
    data class Plain(val value: String) : RowText
}

@Composable
internal fun RowText.asString(): String = when (this) {
    is RowText.Res -> value.asString()
    is RowText.Plain -> value
}

/**
 * What identifies a row to the actions sheet: the kind, the occurrence a dismissal keys
 * on (`null` for a distance target, which has no date), and the custom reminder when
 * there is one. Suggestions carry no identity — there is nothing to act on yet.
 */
@Immutable
internal data class ReminderRowId(
    val kind: ReminderKind,
    val dueOn: LocalDate?,
    val customId: ReminderId?,
)

/** The trailing end of a row: an urgency pill, or the suggestion opt-in. */
@Immutable
internal sealed interface RowStatus {
    data object DueThisWeek : RowStatus
    data object DueSoon : RowStatus
    data object OnTrack : RowStatus

    /** Not tracked yet — offers "Remind me", which creates the preset's reminder. */
    @Immutable
    data class Suggested(val preset: ReminderPreset) : RowStatus
}

/** One reminder row, ready to render. */
@Immutable
internal data class ReminderRow(
    val id: ReminderRowId?,
    val icon: ReminderIcon,
    val title: RowText,
    val line: UiText,
    val status: RowStatus,
)

/** The summary header — attention (amber) or all-caught-up (green). */
@Immutable
internal sealed interface RemindersHeader {
    @Immutable
    data class Attention(val count: Int) : RemindersHeader

    data object CaughtUp : RemindersHeader
}

/**
 * The list's rows plus the header they add up to — one value, from one emission, so the
 * header can never disagree with the rows below it.
 */
@Immutable
internal data class RemindersContent(
    val header: RemindersHeader,
    val thisWeek: List<ReminderRow>,
    val upcoming: List<ReminderRow>,
)

/** Display state for the Reminders screen. */
@Immutable
internal data class RemindersUiState(
    val content: Loadable<RemindersContent> = Loadable.Loading,
)

// --- Samples for previews ---------------------------------------------------------

private fun sampleRows() = listOf(
    ReminderRow(
        id = ReminderRowId(ReminderKind.PUC_EXPIRY, LocalDate(2026, 11, 12), customId = null),
        icon = ReminderIcon.LEAF,
        title = RowText.Res(UiText(Res.string.rm_row_puc)),
        line = UiText(Res.string.rm_line_valid_till, listOf("12 Nov 2026")),
        status = RowStatus.OnTrack,
    ),
    ReminderRow(
        id = null,
        icon = ReminderIcon.TYRE,
        title = RowText.Res(UiText(Res.string.rm_new_default_tyre)),
        line = UiText(Res.string.rm_line_suggested_monthly),
        status = RowStatus.Suggested(ReminderPreset.TYRE_ROTATION),
    ),
)

/** Sample with an urgent renewal this week (amber header). */
internal fun sampleRemindersAttention(): RemindersUiState = RemindersUiState(
    Loadable.Ready(
        RemindersContent(
            header = RemindersHeader.Attention(count = 1),
            thisWeek = listOf(
                ReminderRow(
                    id = ReminderRowId(ReminderKind.INSURANCE_EXPIRY, LocalDate(2026, 7, 3), customId = null),
                    icon = ReminderIcon.SHIELD,
                    title = RowText.Res(UiText(Res.string.rm_row_insurance)),
                    line = UiText(Res.string.rm_line_due_in_days, listOf(7, "03 Jul 2026")),
                    status = RowStatus.DueThisWeek,
                ),
            ),
            upcoming = sampleRows(),
        ),
    ),
)

/** Sample with nothing urgent (green "all caught up" header, no this-week section). */
internal fun sampleRemindersCaughtUp(): RemindersUiState = RemindersUiState(
    Loadable.Ready(
        RemindersContent(
            header = RemindersHeader.CaughtUp,
            thisWeek = emptyList(),
            upcoming = sampleRows(),
        ),
    ),
)
