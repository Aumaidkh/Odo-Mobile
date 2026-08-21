package com.hopcape.odo.feature.support.presentation.rating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.ODO_MAX_STARS
import com.hopcape.odo.core.designsystem.component.OdoStarRating
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.presentation.SupportSheet
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_close
import com.hopcape.odo.feature.support.resources.sp_fb_hint
import com.hopcape.odo.feature.support.resources.sp_rate_body_high
import com.hopcape.odo.feature.support.resources.sp_rate_body_low
import com.hopcape.odo.feature.support.resources.sp_rate_star_cd
import com.hopcape.odo.feature.support.resources.sp_rate_play
import com.hopcape.odo.feature.support.resources.sp_rate_prompt_high
import com.hopcape.odo.feature.support.resources.sp_rate_prompt_low
import com.hopcape.odo.feature.support.resources.sp_rate_send
import com.hopcape.odo.feature.support.resources.sp_rate_subtitle
import com.hopcape.odo.feature.support.resources.sp_rate_title
import org.jetbrains.compose.resources.stringResource

/** At or below this, the sheet leads with the message box instead of the store link. */
private const val LOW_RATING_MAX = 3

/**
 * "Rate Odo" — stars first, then both ways of acting on them.
 *
 * **This is not a review gate, on purpose.** The obvious build of this screen sends four and
 * five stars to the Play Store and quietly routes everything lower into a private email, so
 * the listing only ever hears from happy owners. Play's policy calls that discouraging
 * negative reviews and Apple bars it outright, and it is also just dishonest — a rating is
 * the owner's to publish.
 *
 * What it does instead: a low rating *leads* with the message box, because somebody who is
 * unhappy is usually stuck on something fixable and telling us is faster than a review. The
 * store link is on screen the whole time either way, and never withheld.
 *
 * @param onOpenPlayStore null where there is no listing to open, and the button is then
 *   absent rather than dead — the same rule the help sheet's own Rate row follows.
 * @param onSendFeedback called with the stars and the message, to be mailed.
 */
@Composable
internal fun RateSheetContent(
    onClose: () -> Unit,
    onOpenPlayStore: (() -> Unit)?,
    onSendFeedback: (rating: Int, message: String) -> Unit,
) {
    var rating by rememberSaveable { mutableIntStateOf(0) }
    val lowRatingBody = stringResource(Res.string.sp_rate_body_low)
    val highRatingBody = stringResource(Res.string.sp_rate_body_high)
    var message by rememberSaveable { mutableStateOf("") }
    // Whether the owner has edited the box themselves. Once they have, switching stars must
    // not overwrite what they wrote — the template is a starting point, not a live binding.
    var messageEdited by rememberSaveable { mutableStateOf(false) }

    // One label per star, resolved up front: stringResource cannot be called from the
    // lambda the star row invokes while drawing each icon.
    val starLabels = (1..ODO_MAX_STARS).map { stringResource(Res.string.sp_rate_star_cd, it) }
    val rated = rating > 0
    val low = rated && rating <= LOW_RATING_MAX
    val template = if (low) lowRatingBody else highRatingBody
    val shownMessage = if (messageEdited) message else template

    SupportSheet {
        Header(onClose = onClose)

        OdoStarRating(
            rating = rating,
            onRate = { rating = it },
            starContentDescription = { star -> starLabels[star - 1] },
            modifier = Modifier.fillMaxWidth(),
        )

        // Nothing below the stars until one is tapped. A message box and two buttons under an
        // unanswered question is a form, and the question takes one tap to answer.
        if (rated) {
            OdoText(
                stringResource(
                    if (low) Res.string.sp_rate_prompt_low else Res.string.sp_rate_prompt_high,
                ),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            OdoInputField(
                value = shownMessage,
                onValueChange = {
                    message = it
                    messageEdited = true
                },
                placeholder = stringResource(Res.string.sp_fb_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = MESSAGE_FIELD_MIN_HEIGHT),
            )

            // Both actions, every time. Which one leads changes with the rating; which ones
            // exist does not.
            val send: @Composable (OdoButtonVariant) -> Unit = { variant ->
                OdoButton(
                    text = stringResource(Res.string.sp_rate_send),
                    onClick = { onSendFeedback(rating, shownMessage.trim()) },
                    variant = variant,
                    enabled = shownMessage.isNotBlank() && shownMessage.trim() != template.trim(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val play: @Composable (OdoButtonVariant) -> Unit = { variant ->
                onOpenPlayStore?.let { open ->
                    OdoButton(
                        text = stringResource(Res.string.sp_rate_play),
                        onClick = open,
                        variant = variant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                if (low) {
                    send(OdoButtonVariant.Primary)
                    play(OdoButtonVariant.Tertiary)
                } else {
                    play(OdoButtonVariant.Primary)
                    send(OdoButtonVariant.Tertiary)
                }
            }
        }
    }
}

/** The question, with the circular close that pops the sheet. */
@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(stringResource(Res.string.sp_rate_title), style = OdoTheme.typography.title)
            OdoText(
                stringResource(Res.string.sp_rate_subtitle),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
        OdoCircularIconButton(
            IcClose,
            contentDescription = stringResource(Res.string.sp_close),
            onClick = onClose,
            variant = OdoCircularIconButtonVariant.Raised,
        )
    }
}

private val MESSAGE_FIELD_MIN_HEIGHT = 140.dp
