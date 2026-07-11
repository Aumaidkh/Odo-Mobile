package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcSend: ImageVector
    get() {
        if (_IcSend != null) {
            return _IcSend!!
        }
        _IcSend = ImageVector.Builder(
            name = "IcSend",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(15.854f, 0.146f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.11f, 0.54f)
                lineToRelative(-5.819f, 14.547f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.329f, 0.124f)
                lineToRelative(-3.178f, -4.995f)
                lineTo(0.643f, 7.184f)
                arcToRelative(0.75f, 0.75f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.124f, -1.33f)
                lineTo(15.314f, 0.037f)
                arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.54f, 0.11f)
                close()
                moveTo(6.636f, 10.07f)
                lineToRelative(2.761f, 4.338f)
                lineTo(14.13f, 2.576f)
                close()
                moveToRelative(6.787f, -8.201f)
                lineTo(1.591f, 6.602f)
                lineToRelative(4.339f, 2.76f)
                close()
            }
        }.build()

        return _IcSend!!
    }

@Suppress("ObjectPropertyName")
private var _IcSend: ImageVector? = null
