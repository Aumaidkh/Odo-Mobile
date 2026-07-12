package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A price tag — a pointed body with a punched hole (even-odd fill).
val IcTag: ImageVector
    get() {
        if (_IcTag != null) return _IcTag!!
        _IcTag = ImageVector.Builder(
            name = "IcTag", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(6f, 2f)
                lineTo(14f, 2f)
                lineTo(14f, 14f)
                lineTo(6f, 14f)
                lineTo(1.5f, 8f)
                close()
                moveTo(4.4f, 5.5f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.2f, 0f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.2f, 0f)
                close()
            }
        }.build()
        return _IcTag!!
    }

@Suppress("ObjectPropertyName")
private var _IcTag: ImageVector? = null
