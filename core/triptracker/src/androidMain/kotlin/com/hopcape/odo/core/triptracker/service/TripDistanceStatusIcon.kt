package com.hopcape.odo.core.triptracker.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.hopcape.odo.core.triptracker.R
import kotlin.math.roundToInt

/**
 * The distance so far, drawn as the notification's small icon so it appears in the status
 * bar beside the clock and the battery.
 *
 * Android gives an app no way to write text into the status bar. The only thing it draws
 * there is a notification's small icon, and the only way to make that icon say something
 * that changes is to draw the text into a bitmap and hand that over as the icon — which is
 * what this does. The system then reduces it to a silhouette (it keeps the alpha channel
 * and re-tints the rest), so the glyph is drawn in opaque white on transparent: what
 * survives that reduction is exactly the digits.
 *
 * Cheap enough to do on every update because there are so few of them —
 * [TripTrackingService] throttles the notification to one refresh per 500 m or 30 s, so
 * this runs a couple of times a minute at motorway speed, not per location fix.
 */
internal object TripDistanceStatusIcon {

    /** Below this there is no whole kilometre to show, so the mark keeps the slot. */
    const val MIN_DISTANCE_METERS = 1_000L

    /**
     * @param distanceMeters metres covered so far; at least [MIN_DISTANCE_METERS].
     * @return an icon showing the distance in whole kilometres, sized for the status bar.
     */
    fun of(context: Context, distanceMeters: Long): IconCompat {
        val label = label(context, distanceMeters)
        val sizePx = (SIZE_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(MIN_SIZE_PX)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GLYPH_COLOR
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = fittedTextSize(this, label, sizePx)
        }

        // Centred on the text's own bounds rather than on the baseline: "12" and "4.2" have
        // different heights, and centring on the baseline would make the shorter one sit low.
        val metrics = paint.fontMetrics
        val baseline = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, sizePx / 2f, baseline, paint)

        return IconCompat.createWithBitmap(bitmap)
    }

    /**
     * The largest text size at which [label] still fits, found by measuring rather than
     * guessing — "4.2" and "104" need different sizes to fill the same box, and a fixed size
     * would either clip the wider one or waste the narrower one.
     */
    private fun fittedTextSize(paint: Paint, label: String, sizePx: Int): Float {
        val available = sizePx * (1f - 2 * PADDING_FRACTION)
        paint.textSize = sizePx.toFloat()
        val widthAtFullSize = paint.measureText(label)
        if (widthAtFullSize <= 0f) return sizePx.toFloat()
        return (sizePx * available / widthAtFullSize).coerceAtMost(sizePx * MAX_TEXT_FRACTION)
    }

    /**
     * Whole kilometres, never a decimal.
     *
     * A status-bar icon is about 5 mm across. "4.2" is three characters in that space and
     * ends up too small to read at a glance, which is the only way anyone reads a status
     * bar; "4" is one character and legible in the corner of an eye. The tenth would be
     * false precision anyway — [TripTrackingService] refreshes this every 500 m, so the
     * decimal could only ever be .0 or .5.
     */
    private fun label(context: Context, distanceMeters: Long): String =
        context.getString(R.string.trip_tracking_status_icon_km_whole, (distanceMeters / 1000.0).roundToInt())

    private const val SIZE_DP = 24
    private const val MIN_SIZE_PX = 48
    private const val GLYPH_COLOR = 0xFFFFFFFF.toInt()

    /**
     * Breathing room on each side. Generous on purpose: the system adds padding of its own
     * around a status-bar icon and will happily clip whatever is flush against the edge, and
     * digits touching the boundary read as cramped even when they survive.
     */
    private const val PADDING_FRACTION = 0.12f

    /** Caps how tall a single short label may grow — "4" should not fill the box edge to edge. */
    private const val MAX_TEXT_FRACTION = 0.78f
}
