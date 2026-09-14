package com.hopcape.odo.web.admin.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hopcape.odo.web.admin.ui.component.Shimmer
import com.hopcape.odo.web.admin.ui.theme.AdminTokens

/**
 * What the panel shows while its string table is still loading.
 *
 * **Not decoration — it replaces a screen that lied.** Compose Resources reads the
 * string table asynchronously, and until it lands every `stringResource` returns the
 * empty string. On a 12 MB Wasm bundle that is thirty to forty-five seconds of a
 * fully drawn panel with no words in it: a rail of unlabelled icons, a blank page
 * title, empty column headings. It reads as a broken deployment rather than as a
 * slow one, and the first person to hit it on production reported exactly that.
 *
 * So nothing is drawn from a string until every string is ready. What is here
 * instead is the mark, which is a Canvas, and shimmer bars in the shape of the rail
 * and the page — both of which say "assembling" without claiming to be anything.
 */
@Composable
fun BootScreen() {
    // Read before the Canvas: a draw lambda is a DrawScope, not a composable scope.
    val track = AdminTokens.border
    val dial = AdminTokens.text

    Box(
        modifier = Modifier.fillMaxSize().background(AdminTokens.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // The same two arcs the rail draws, at the size of a splash.
            Canvas(Modifier.size(46.dp)) {
                val stroke = Stroke(width = size.minDimension * 0.245f, cap = StrokeCap.Round)
                val inset = stroke.width / 2f
                val arc = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(
                    color = track,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = stroke,
                )
                drawArc(
                    color = dial,
                    startAngle = 135f,
                    sweepAngle = 202f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = stroke,
                )
            }
            // Three bars rather than a spinner. A spinner over forty seconds reads as
            // a hang; bars that are visibly a layout arriving read as progress.
            Shimmer(Modifier.width(160.dp).height(11.dp))
            Shimmer(Modifier.width(112.dp).height(9.dp))
            Shimmer(Modifier.width(136.dp).height(9.dp))
        }
    }
}
