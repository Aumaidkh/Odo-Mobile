package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcCaretUp: ImageVector
    get() {
        if (_IcCaretUp != null) {
            return _IcCaretUp!!
        }
        _IcCaretUp = ImageVector.Builder(
            name = "IcCaretUp",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(7.247f, 4.86f)
                lineToRelative(-4.796f, 5.481f)
                curveToRelative(-0.566f, 0.647f, -0.106f, 1.659f, 0.753f, 1.659f)
                horizontalLineToRelative(9.592f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.753f, -1.659f)
                lineToRelative(-4.796f, -5.48f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.506f, 0f)
                close()
            }
        }.build()

        return _IcCaretUp!!
    }

@Suppress("ObjectPropertyName")
private var _IcCaretUp: ImageVector? = null
