package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val IcInfinity: ImageVector
    get() {
        if (_IcInfinity != null) {
            return _IcInfinity!!
        }
        _IcInfinity = ImageVector.Builder(
            name = "IcInfinity",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.68f, 5.792f)
                lineTo(7.345f, 7.75f)
                lineTo(5.681f, 9.708f)
                arcToRelative(2.75f, 2.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -3.916f)
                close()
                moveTo(8f, 6.978f)
                lineTo(6.416f, 5.113f)
                lineToRelative(-0.014f, -0.015f)
                arcToRelative(3.75f, 3.75f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, 5.304f)
                lineToRelative(0.014f, -0.015f)
                lineTo(8f, 8.522f)
                lineToRelative(1.584f, 1.865f)
                lineToRelative(0.014f, 0.015f)
                arcToRelative(3.75f, 3.75f, 0f, isMoreThanHalf = true, isPositiveArc = false, 0f, -5.304f)
                lineToRelative(-0.014f, 0.015f)
                close()
                moveTo(8.656f, 7.75f)
                lineTo(10.319f, 5.792f)
                arcToRelative(2.75f, 2.75f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 3.916f)
                close()
            }
        }.build()

        return _IcInfinity!!
    }

@Suppress("ObjectPropertyName")
private var _IcInfinity: ImageVector? = null