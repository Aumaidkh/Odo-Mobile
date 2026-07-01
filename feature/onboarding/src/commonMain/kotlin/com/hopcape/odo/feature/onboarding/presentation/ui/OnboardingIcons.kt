package com.hopcape.odo.feature.onboarding.presentation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.unit.dp

/**
 * The handful of line-art glyphs the onboarding flow needs (scan camera, the three
 * goal icons, and the printed/handwritten bullet marks). Authored as [ImageVector]s
 * rather than pulling in `material-icons-*`, keeping onboarding consistent with the
 * design system's zero-icons-dependency stance (it hand-draws its chevron too).
 *
 * All are stroke-only 24dp outlines whose colour is white — [OdoIcon]/Material's
 * `Icon` recolours them via its `tint`, so the caller decides the ink.
 */
internal object OnboardingIcons {

    /** Camera with a viewfinder hump and a lens — the "Tap to scan a bill" affordance. */
    val Camera: ImageVector by lazy {
        icon {
            stroke {
                moveTo(3f, 8.5f)
                lineTo(7f, 8.5f)
                lineTo(8.5f, 6f)
                lineTo(15.5f, 6f)
                lineTo(17f, 8.5f)
                lineTo(21f, 8.5f)
                lineTo(21f, 18f)
                lineTo(3f, 18f)
                close()
            }
            stroke {
                moveTo(15f, 13f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6f, 0f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6f, 0f)
            }
        }
    }

    /** Wallet — the "Sell my car / Resale Passport" goal. */
    val Wallet: ImageVector by lazy {
        icon {
            stroke {
                moveTo(3.5f, 7f)
                lineTo(20.5f, 7f)
                lineTo(20.5f, 18f)
                lineTo(3.5f, 18f)
                close()
            }
            stroke {
                moveTo(3.5f, 10.5f)
                lineTo(20.5f, 10.5f)
            }
            fill {
                moveTo(16.8f, 14.2f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2f, 0f)
                arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2f, 0f)
            }
        }
    }

    /** Receipt with a torn edge — the "Track service costs / catch overcharging" goal. */
    val Receipt: ImageVector by lazy {
        icon {
            stroke {
                moveTo(6f, 3.5f)
                lineTo(18f, 3.5f)
                lineTo(18f, 20.5f)
                lineTo(16f, 19f)
                lineTo(14f, 20.5f)
                lineTo(12f, 19f)
                lineTo(10f, 20.5f)
                lineTo(8f, 19f)
                lineTo(6f, 20.5f)
                close()
            }
            stroke {
                moveTo(9f, 8.5f)
                lineTo(15f, 8.5f)
            }
            stroke {
                moveTo(9f, 11.5f)
                lineTo(15f, 11.5f)
            }
            stroke {
                moveTo(9f, 14.5f)
                lineTo(13f, 14.5f)
            }
        }
    }

    /** Bell — the "Never miss insurance / PUC / smart reminders" goal. */
    val Bell: ImageVector by lazy {
        icon {
            stroke {
                moveTo(7f, 17f)
                curveTo(8f, 16f, 8.5f, 15f, 8.5f, 13f)
                lineTo(8.5f, 11f)
                curveTo(8.5f, 8.8f, 10f, 7.2f, 12f, 7.2f)
                curveTo(14f, 7.2f, 15.5f, 8.8f, 15.5f, 11f)
                lineTo(15.5f, 13f)
                curveTo(15.5f, 15f, 16f, 16f, 17f, 17f)
                close()
            }
            stroke {
                moveTo(12f, 7.2f)
                lineTo(12f, 5f)
            }
            stroke {
                moveTo(10.4f, 17.5f)
                arcToRelative(1.6f, 1.6f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.2f, 0f)
            }
        }
    }

    /** Check mark — the green "printed bills: best results" bullet. */
    val Check: ImageVector by lazy {
        icon {
            stroke {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 7f)
            }
        }
    }

    /** Warning triangle — the amber "handwritten: double-check" bullet. */
    val Warning: ImageVector by lazy {
        icon {
            stroke {
                moveTo(12f, 4.5f)
                lineTo(21f, 19.5f)
                lineTo(3f, 19.5f)
                close()
            }
            stroke {
                moveTo(12f, 10f)
                lineTo(12f, 14f)
            }
            fill {
                moveTo(12.9f, 16.4f)
                arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = true, isPositiveArc = true, -1.8f, 0f)
                arcToRelative(0.9f, 0.9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 1.8f, 0f)
            }
        }
    }

    /** Right chevron — the trailing "go" affordance on each goal card. */
    val ChevronRight: ImageVector by lazy {
        icon {
            stroke {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }
    }
}

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 1.9f

private fun icon(build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).apply(build).build()

/** A stroked (outline) sub-path — the default look for these line-art glyphs. */
private fun ImageVector.Builder.stroke(pathBuilder: PathBuilder.() -> Unit) {
    addPath(
        pathData = PathData(pathBuilder),
        stroke = SolidColor(Color.White),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** A filled sub-path — for the small solid dots (wallet clasp, warning dot). */
private fun ImageVector.Builder.fill(pathBuilder: PathBuilder.() -> Unit) {
    addPath(pathData = PathData(pathBuilder), fill = SolidColor(Color.White))
}
