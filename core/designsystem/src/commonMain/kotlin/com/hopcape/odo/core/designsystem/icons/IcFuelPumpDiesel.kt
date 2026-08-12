package com.hopcape.odo.core.designsystem.icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// The diesel pump — the same dispenser as IcFuelPump, marked with grille bars
// instead of a droplet so the two fuels read apart at a glance.
val IcFuelPumpDiesel: ImageVector
    get() {
        if (_IcFuelPumpDiesel != null) return _IcFuelPumpDiesel!!
        _IcFuelPumpDiesel = ImageVector.Builder(
            name = "IcFuelPumpDiesel", defaultWidth = 16.dp, defaultHeight = 16.dp,
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
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
            ) {
                // Grille bars on the pump's face.
                moveTo(3.8f, 9.6f)
                horizontalLineTo(7.7f)
                moveTo(3.8f, 12.1f)
                horizontalLineTo(6.4f)
            }
        }.build()
        return _IcFuelPumpDiesel!!
    }

@Suppress("ObjectPropertyName")
private var _IcFuelPumpDiesel: ImageVector? = null
