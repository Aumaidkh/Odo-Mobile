package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The text primitive every Odo screen uses instead of a raw Material [Text]. It
 * defaults to the brand type scale ([OdoTheme.typography] `body`) and inherits the
 * current content colour, so copy stays on-brand without each call site re-stating
 * a style + colour.
 *
 * Pick intent from the scale rather than sizing by hand:
 *
 * ```
 * OdoText("Apni gaadi", style = OdoTheme.typography.title)
 * OdoText("Based on 12 data points in Mumbai", style = OdoTheme.typography.caption,
 *         color = OdoTheme.colors.textDim)
 * ```
 *
 * [color] defaults to [Color.Unspecified] so the text takes the ambient
 * [LocalContentColor] — which [OdoScreen] sets to `colors.text` and a filled
 * [OdoButton] sets to `onAccent`. Pass an explicit brand token (`textDim`,
 * `textMuted`, `danger`…) only when the copy needs a different role.
 *
 * @param text the string to render.
 * @param style a role from the brand type scale; defaults to `body`.
 * @param color overrides the inherited content colour with a brand token.
 * @param maxLines truncates with [overflow] past this many lines.
 */
@Composable
fun OdoText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = OdoTheme.typography.body,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        minLines = minLines,
        softWrap = softWrap,
        overflow = overflow,
    )
}

/**
 * [AnnotatedString] overload of [OdoText], for copy with inline spans (a coloured
 * savings figure inside a sentence, a bolded workshop name). Shares the same
 * defaults as the plain-string version.
 */
@Composable
fun OdoText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = OdoTheme.typography.body,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
        minLines = minLines,
        softWrap = softWrap,
        overflow = overflow,
    )
}

@OdoThemePreviews
@Composable
private fun OdoTextPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText("Apni gaadi", style = OdoTheme.typography.title)
        OdoText("Har entry me odometer reading zaroori hai.", style = OdoTheme.typography.body)
        OdoText(
            "BASED ON 12 DATA POINTS IN MUMBAI",
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
    }
}
