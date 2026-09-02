package com.hopcape.odo.feature.challan.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.RelativeAge
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_time_day
import com.hopcape.odo.feature.challan.resources.ch_time_days
import com.hopcape.odo.feature.challan.resources.ch_time_hour
import com.hopcape.odo.feature.challan.resources.ch_time_hours
import com.hopcape.odo.feature.challan.resources.ch_time_just_now
import com.hopcape.odo.feature.challan.resources.ch_time_minute
import com.hopcape.odo.feature.challan.resources.ch_time_minutes
import kotlin.time.Instant

/** "2 hours ago" — the age of the last records check, as this feature's copy. */
internal fun checkedAgo(at: Instant, now: Instant): UiText =
    when (val age = RelativeAge.between(at, now)) {
        RelativeAge.JustNow -> UiText(Res.string.ch_time_just_now)
        is RelativeAge.Minutes ->
            UiText(if (age.count == 1L) Res.string.ch_time_minute else Res.string.ch_time_minutes, listOf(age.count))

        is RelativeAge.Hours ->
            UiText(if (age.count == 1L) Res.string.ch_time_hour else Res.string.ch_time_hours, listOf(age.count))

        is RelativeAge.Days ->
            UiText(if (age.count == 1L) Res.string.ch_time_day else Res.string.ch_time_days, listOf(age.count))
    }

/** "MH12AB1234" → "MH 12 AB 1234"; anything unrecognisable stays as stored. */
internal fun formatPlate(normalized: String): String {
    val match = PLATE.matchEntire(normalized) ?: return normalized
    return match.groupValues.drop(1).filter { it.isNotEmpty() }.joinToString(" ")
}

private val PLATE = Regex("""([A-Z]{2})(\d{1,2})([A-Z]{1,3})(\d{4})""")
