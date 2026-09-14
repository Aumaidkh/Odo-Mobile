package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * How much an answer rests on, as filled dots and a plain-language label.
 *
 * The PRD forbids showing a benchmark without saying what it is built on. Dots carry that in
 * the space a sentence would need, and the [label] beside them says it in words — the dots
 * alone would be decoration.
 *
 * The whole row reads as one thing to a screen reader: three dots announced separately are
 * noise, so [label] is the accessible name and the dots are cleared.
 *
 * @param filled how many of [total] are lit, coerced into range.
 * @param label what the strength means, e.g. "From your own record".
 */
@Composable
fun OdoEvidenceDots(
    filled: Int,
    label: String,
    modifier: Modifier = Modifier,
    total: Int = DEFAULT_TOTAL,
) {
    val lit = filled.coerceIn(0, total)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DOT_GAP)) {
            repeat(total) { index ->
                Spacer(
                    Modifier
                        .size(DOT_SIZE)
                        .background(
                            color = if (index < lit) OdoTheme.colors.textDim else OdoTheme.colors.border,
                            shape = CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.width(OdoTheme.spacing.sm))
        OdoText(
            text = label,
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textMuted,
        )
    }
}

private val DOT_SIZE = 5.dp
private val DOT_GAP = 3.dp
private const val DEFAULT_TOTAL = 3

@OdoThemePreviews
@Composable
private fun OdoEvidenceDotsPreview() = OdoPreview {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoEvidenceDots(filled = 3, label = "From your own record")
        OdoEvidenceDots(filled = 2, label = "From 14 real bills")
        OdoEvidenceDots(filled = 1, label = "Estimated from city rates")
    }
}
