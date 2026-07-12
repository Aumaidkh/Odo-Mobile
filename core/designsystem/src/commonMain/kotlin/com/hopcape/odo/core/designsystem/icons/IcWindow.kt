package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A simple "app window" placeholder (title bar + frame) — stands in for DigiLocker.
val IcWindow: ImageVector
    get() {
        if (_IcWindow != null) {
            return _IcWindow!!
        }
        _IcWindow = ImageVector.Builder(
            name = "IcWindow",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                moveTo(1.5f, 2.5f)
                lineTo(14.5f, 2.5f)
                lineTo(14.5f, 13.5f)
                lineTo(1.5f, 13.5f)
                close()
                moveTo(3f, 5.5f)
                lineTo(13f, 5.5f)
                lineTo(13f, 12f)
                lineTo(3f, 12f)
                close()
            }
        }.build()

        return _IcWindow!!
    }

@Suppress("ObjectPropertyName")
private var _IcWindow: ImageVector? = null
