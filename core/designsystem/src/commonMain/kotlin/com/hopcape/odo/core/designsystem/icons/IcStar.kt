package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A filled five-point star (Bootstrap "star-fill").
val IcStar: ImageVector
    get() {
        if (_IcStar != null) return _IcStar!!
        _IcStar = ImageVector.Builder(
            name = "IcStar", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3.612f, 15.443f)
                curveToRelative(-0.386f, 0.198f, -0.824f, -0.149f, -0.746f, -0.592f)
                lineToRelative(0.83f, -4.73f)
                lineTo(0.173f, 6.765f)
                curveToRelative(-0.329f, -0.314f, -0.158f, -0.888f, 0.283f, -0.95f)
                lineToRelative(4.898f, -0.696f)
                lineTo(7.538f, 0.792f)
                curveToRelative(0.197f, -0.39f, 0.73f, -0.39f, 0.927f, 0f)
                lineToRelative(2.184f, 4.327f)
                lineToRelative(4.898f, 0.696f)
                curveToRelative(0.441f, 0.062f, 0.612f, 0.636f, 0.283f, 0.95f)
                lineToRelative(-3.523f, 3.356f)
                lineToRelative(0.83f, 4.73f)
                curveToRelative(0.078f, 0.443f, -0.36f, 0.79f, -0.746f, 0.592f)
                lineTo(8f, 13.187f)
                close()
            }
        }.build()
        return _IcStar!!
    }

@Suppress("ObjectPropertyName")
private var _IcStar: ImageVector? = null
