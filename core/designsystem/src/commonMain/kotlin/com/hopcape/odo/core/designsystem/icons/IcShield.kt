package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcShield: ImageVector
    get() {
        if (_IcShield != null) return _IcShield!!
        _IcShield = ImageVector.Builder(
            name = "IcShield", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 1.5f)
                lineToRelative(5f, 1.8f)
                lineToRelative(0f, 4.7f)
                curveToRelative(0f, 3.2f, -2.2f, 5.4f, -5f, 6.5f)
                curveToRelative(-2.8f, -1.1f, -5f, -3.3f, -5f, -6.5f)
                lineToRelative(0f, -4.7f)
                close()
            }
        }.build()
        return _IcShield!!
    }

@Suppress("ObjectPropertyName")
private var _IcShield: ImageVector? = null
