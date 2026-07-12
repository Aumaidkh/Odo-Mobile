package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcDroplet: ImageVector
    get() {
        if (_IcDroplet != null) return _IcDroplet!!
        _IcDroplet = ImageVector.Builder(
            name = "IcDroplet", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 16f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, -6f)
                curveToRelative(0f, -1.655f, -1.122f, -2.904f, -2.432f, -4.362f)
                curveTo(10.254f, 4.176f, 8.75f, 2.503f, 8f, 0f)
                curveToRelative(0f, 0f, -6f, 5.686f, -6f, 10f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 6f, 6f)
                close()
            }
        }.build()
        return _IcDroplet!!
    }

@Suppress("ObjectPropertyName")
private var _IcDroplet: ImageVector? = null
