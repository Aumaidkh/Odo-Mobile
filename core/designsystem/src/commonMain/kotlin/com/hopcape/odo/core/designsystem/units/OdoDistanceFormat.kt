package com.hopcape.odo.core.designsystem.units

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.hopcape.odo.core.designsystem.component.OdoDistanceUnit

/**
 * How distances are written and read on screen, for whichever unit the owner chose.
 *
 * Every stored reading is in kilometres; this is the one place the app turns that into
 * what a screen shows and back into what it stores. It is an interface rather than a
 * `when` in each screen because the conversion and the rounding have to agree everywhere —
 * an odometer shown in miles and typed back in miles must not drift by a kilometre a time.
 *
 * Deliberately built on `Int` kilometres and `Long` paise rather than the domain's value
 * objects: the design system stays free of domain types (the same reason
 * [OdoDistanceUnit] exists next to the domain's own unit enum). The app provides the real
 * implementation, which is backed by the domain kernel and its tests.
 */
@Immutable
interface OdoDistanceFormat {

    /** The unit itself, for controls that toggle or label it. */
    val unit: OdoDistanceUnit

    /** The short label a number is followed by: "km" or "mi". */
    val suffix: String

    /** The whole number to show for a stored reading. */
    fun display(km: Int): Int

    /**
     * The kilometres to store for a number the owner typed. [currentKm] is the reading
     * already on file, so re-typing the number on screen stores what is already there
     * rather than a value a rounding step below it.
     */
    fun store(displayed: Int, currentKm: Int? = null): Int

    /** A stored reading as display text — "54,000 km" / "33,554 mi". */
    fun format(km: Int): String

    /** A per-kilometre money rate restated per shown unit, in paise. */
    fun ratePaise(perKmPaise: Long): Long
}

/**
 * Kilometres, with no conversion — the value a composition gets when nothing provided one.
 *
 * Real screens run under the app, which provides the domain-backed implementation. This
 * exists so previews and tests render something sensible instead of crashing, so its
 * grouping is plain rather than the app's Indian grouping.
 */
object OdoKilometreFormat : OdoDistanceFormat {
    override val unit: OdoDistanceUnit = OdoDistanceUnit.KM
    override val suffix: String = "km"
    override fun display(km: Int): Int = km
    override fun store(displayed: Int, currentKm: Int?): Int = displayed
    override fun format(km: Int): String = "$km $suffix"
    override fun ratePaise(perKmPaise: Long): Long = perKmPaise
}

/** The distance unit in force, provided once by the app from the owner's settings. */
val LocalOdoDistanceFormat = staticCompositionLocalOf<OdoDistanceFormat> { OdoKilometreFormat }
