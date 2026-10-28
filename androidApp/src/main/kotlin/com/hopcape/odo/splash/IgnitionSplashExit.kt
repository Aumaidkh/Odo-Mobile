package com.hopcape.odo.splash

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreenViewProvider
import com.hopcape.logging.api.HLogger
import com.hopcape.odo.R

/**
 * Lets the Ignition Sweep finish, then fades the splash out. Android 12+ plays the sweep
 * itself; on 8-11 the library shows a still first frame, so the sweep is started here.
 * With animations turned off in system settings it exits at once.
 */
internal object IgnitionSplashExit {

    /** [onRemoved] runs once the splash is gone, so the caller can restore its window setup. */
    fun play(splash: SplashScreenViewProvider, onRemoved: () -> Unit) {
        val wait = when {
            !ValueAnimator.areAnimatorsEnabled() -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                splash.iconAnimationStartMillis + SWEEP_MS - System.currentTimeMillis() + ODO_HOLD_MS
            else -> startCompatSweep(splash.iconView).let { if (it > 0) it + ODO_HOLD_MS else 0L }
        }.coerceIn(0L, SWEEP_MS + ODO_HOLD_MS)

        splash.view.animate()
            .alpha(0f)
            .scaleX(EXIT_SCALE)
            .scaleY(EXIT_SCALE)
            .setStartDelay(wait)
            .setDuration(FADE_MS)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .withEndAction {
                splash.remove()
                onRemoved()
            }
            .start()
    }

    /** Swaps in a fresh copy of the sweep, since the library's masked icon never redraws. */
    private fun startCompatSweep(icon: View): Long {
        val image = icon as? ImageView
        val sweep = image?.let { ContextCompat.getDrawable(it.context, R.drawable.avd_splash_ignition) }
        if (image == null || sweep !is Animatable) {
            // The splash still exits; only the animation is lost.
            HLogger.tag(TAG).w("splash_compat_sweep_unavailable", mapOf("icon" to icon::class.simpleName))
            return 0L
        }
        image.setImageDrawable(sweep as Drawable)
        sweep.start()
        return SWEEP_MS
    }

    /** The drawable's timeline: sweep, hold, fall, then ODO and the reading fade in. */
    private const val SWEEP_MS = 1_680L

    /** How long ODO stays readable before the fade, when the app was ready early. */
    private const val ODO_HOLD_MS = 400L
    private const val FADE_MS = 250L
    private const val EXIT_SCALE = 1.02f
    private const val TAG = "SPLASH"
}
