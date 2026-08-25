package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A phone shown inside the app: a dark bezel with a rounded screen cut out of it, holding
 * whatever [content] you put in.
 *
 * For showing a feature to someone who has not used it yet — a clip of the app working, in
 * something that reads as a phone rather than as a rectangle floating on a page. The bezel
 * is what makes it read that way, so it is drawn rather than hinted at: a real border, a
 * real notch, and a screen radius smaller than the outer one, which is how a physical
 * device is shaped.
 *
 * The frame is decoration and takes no interaction of its own. Whatever [content] does with
 * touches is between it and the caller.
 */
@Composable
fun OdoDeviceFrame(
    modifier: Modifier = Modifier,
    bezel: Dp = DefaultBezel,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(OdoTheme.shapes.device)
            // Near-black rather than the theme's surface: a bezel that changes with the
            // theme stops reading as hardware, and this is a picture of a phone.
            .background(BezelColor)
            .padding(bezel),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Inner radius is the outer one minus the bezel, which is what keeps the
                // two curves concentric. Equal radii make the bezel look thicker at the
                // corners than along the edges.
                .clip(RoundedCornerShape(ScreenRadius))
                .background(Color.Black),
        ) {
            content()
        }

        // The notch. Drawn over the screen rather than cut out of it, because the content
        // behind it is a video that should fill the whole panel — the same way a real
        // phone's island sits on top of what is playing.
        Box(
            modifier = Modifier
                .padding(top = NotchTopInset)
                .width(NotchWidth)
                .height(NotchHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(BezelColor),
        )
    }
}

private val BezelColor = Color(0xFF101012)
private val DefaultBezel = 6.dp
private val ScreenRadius = 28.dp
private val NotchWidth = 78.dp
private val NotchHeight = 22.dp
private val NotchTopInset = 12.dp
