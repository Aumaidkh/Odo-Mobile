package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.icons.IcStarFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** How many stars a rating runs to. */
const val ODO_MAX_STARS: Int = 5

/**
 * A row of stars the owner taps to rate something.
 *
 * Controlled: [rating] comes from the caller and taps report back through [onRate]. `0`
 * means nothing has been chosen yet, which is a real state — a sheet should not act on a
 * rating nobody gave.
 *
 * Every star is the same filled glyph, tinted differently rather than swapped for an
 * outline. The module ships no outlined star, and a filled shape in a dim colour reads as
 * "not chosen" at a glance while keeping all five the same size and weight — an outline
 * next to a fill is the version that looks uneven.
 *
 * **Each star is its own labelled target**, rather than the row being one merged control.
 * Merging reads more neatly — "Rated 3 of 5" in a single announcement — but it leaves a
 * screen-reader user able to hear the rating and unable to set one, because there is then
 * nothing to activate per value. Five targets labelled "Rate 1 of 5" … "Rate 5 of 5" are
 * how the control stays operable.
 *
 * @param rating the stars currently chosen, `0` for none, up to [ODO_MAX_STARS].
 * @param onRate called with the star that was tapped, `1`-based.
 * @param starContentDescription what each star announces, given its `1`-based position.
 */
@Composable
fun OdoStarRating(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    starContentDescription: ((Int) -> String)? = null,
) {
    Row(
        modifier = modifier.heightIn(min = OdoTheme.spacing.minTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(ODO_MAX_STARS) { index ->
            val star = index + 1
            val chosen = star <= rating
            OdoIcon(
                IcStarFilled,
                contentDescription = starContentDescription?.invoke(star),
                tint = if (chosen) OdoTheme.colors.warning else OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.large,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    // No ripple: five circular ripples in a tight row read as noise, and the
                    // colour change is already the feedback.
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onRate(star) },
            )
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoStarRatingPreview() = OdoPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoStarRating(rating = 0, onRate = {})
        OdoStarRating(rating = 3, onRate = {})
    }
}
