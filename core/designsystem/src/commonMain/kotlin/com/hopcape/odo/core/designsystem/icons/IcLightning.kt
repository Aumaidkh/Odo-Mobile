package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A lightning bolt — the "biggest opportunity" spark. A filled zig-zag polygon.
val IcLightning: ImageVector
    get() {
        if (_IcLightning != null) return _IcLightning!!
        _IcLightning = ImageVector.Builder(
            name = "IcLightning", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9.4f, 1f)
                lineTo(3.4f, 9f)
                lineTo(7f, 9f)
                lineTo(6.2f, 15f)
                lineTo(12.6f, 6.6f)
                lineTo(8.7f, 6.6f)
                close()
            }
        }.build()
        return _IcLightning!!
    }

@Suppress("ObjectPropertyName")
private var _IcLightning: ImageVector? = null
