package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A house outline — roof, walls and a door — for the Home bottom-nav tab.
val IcHouse: ImageVector
    get() {
        if (_IcHouse != null) return _IcHouse!!
        _IcHouse = ImageVector.Builder(
            name = "IcHouse", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                // Roof: left eave → apex → right eave.
                moveTo(2.2f, 7.4f)
                lineTo(8f, 2.6f)
                lineTo(13.8f, 7.4f)
                // Walls: down the left, across the floor, up the right.
                moveTo(3.6f, 6.4f)
                lineTo(3.6f, 13.2f)
                lineTo(12.4f, 13.2f)
                lineTo(12.4f, 6.4f)
                // Door.
                moveTo(6.5f, 13.2f)
                lineTo(6.5f, 9.3f)
                lineTo(9.5f, 9.3f)
                lineTo(9.5f, 13.2f)
            }
        }.build()
        return _IcHouse!!
    }

@Suppress("ObjectPropertyName")
private var _IcHouse: ImageVector? = null
