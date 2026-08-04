package com.hopcape.odo.core.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A QR code: the three corner finder squares plus a few data cells.
 *
 * Drawn rather than traced from a real code, because at icon size a faithful QR is noise. The
 * finders are what make it readable as "QR" at 20dp.
 */
val IcQr: ImageVector
    get() {
        if (_IcQr != null) {
            return _IcQr!!
        }
        _IcQr = ImageVector.Builder(
            name = "IcQr",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            // The three finder squares, each an outline with a solid centre.
            listOf(0f to 0f, 10f to 0f, 0f to 10f).forEach { (x, y) ->
                path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                    moveTo(x, y)
                    lineTo(x + 6f, y)
                    lineTo(x + 6f, y + 6f)
                    lineTo(x, y + 6f)
                    close()
                    moveTo(x + 1f, y + 1f)
                    lineTo(x + 1f, y + 5f)
                    lineTo(x + 5f, y + 5f)
                    lineTo(x + 5f, y + 1f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(x + 2f, y + 2f)
                    lineTo(x + 4f, y + 2f)
                    lineTo(x + 4f, y + 4f)
                    lineTo(x + 2f, y + 4f)
                    close()
                }
            }
            // Data cells in the free quadrant.
            listOf(10f to 10f, 14f to 10f, 12f to 12f, 10f to 14f, 14f to 14f).forEach { (x, y) ->
                path(fill = SolidColor(Color.Black)) {
                    moveTo(x, y)
                    lineTo(x + 2f, y)
                    lineTo(x + 2f, y + 2f)
                    lineTo(x, y + 2f)
                    close()
                }
            }
        }.build()

        return _IcQr!!
    }

@Suppress("ObjectPropertyName")
private var _IcQr: ImageVector? = null
