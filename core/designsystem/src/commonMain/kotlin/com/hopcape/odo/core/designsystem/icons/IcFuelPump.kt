package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// A petrol pump — the dispenser body with its display window and nozzle arm, and a
// filled droplet on the front. Pairs with IcFuelPumpDiesel, which shares the body.
val IcFuelPump: ImageVector
    get() {
        if (_IcFuelPump != null) return _IcFuelPump!!
        _IcFuelPump = ImageVector.Builder(
            name = "IcFuelPump", defaultWidth = 16.dp, defaultHeight = 16.dp,
            viewportWidth = 16f, viewportHeight = 16f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                // Dispenser body.
                moveTo(3.4f, 1.6f)
                horizontalLineTo(8.1f)
                arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.4f, 1.4f)
                verticalLineTo(14.4f)
                horizontalLineTo(2f)
                verticalLineTo(3f)
                arcToRelative(1.4f, 1.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.4f, -1.4f)
                close()
                // Forecourt base it stands on.
                moveTo(1f, 14.4f)
                horizontalLineTo(10.5f)
                // Price display window.
                moveTo(3.6f, 3.7f)
                horizontalLineTo(7.9f)
                verticalLineTo(6.4f)
                horizontalLineTo(3.6f)
                close()
                // Hose arm, rising rod, and the spout tip.
                moveTo(9.5f, 9.4f)
                horizontalLineTo(12.3f)
                arcToRelative(1.2f, 1.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.2f, -1.2f)
                verticalLineTo(5.2f)
                lineTo(12.2f, 3.9f)
            }
            path(fill = SolidColor(Color.Black)) {
                // Fuel droplet on the pump's face.
                moveTo(5.75f, 12.8f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
                curveToRelative(0f, -0.9f, -1f, -1.9f, -2f, -3.2f)
                curveToRelative(-1f, 1.3f, -2f, 2.3f, -2f, 3.2f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
                close()
            }
        }.build()
        return _IcFuelPump!!
    }

@Suppress("ObjectPropertyName")
private var _IcFuelPump: ImageVector? = null
