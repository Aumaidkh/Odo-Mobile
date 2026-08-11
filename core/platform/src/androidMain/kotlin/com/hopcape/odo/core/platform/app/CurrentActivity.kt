package com.hopcape.odo.core.platform.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The Activity currently in front of the owner, or null when none is.
 *
 * Almost nothing in this app needs one — Compose reaches the Activity through
 * `LocalActivity`, and every other platform seam here takes the application `Context`. This
 * exists for the callers that are neither: code running outside composition that a vendor SDK
 * insists on handing an Activity. Firebase's `PhoneAuthProvider.verifyPhoneNumber` is the
 * first, and it has no Activity-free overload — Play Integrity and the reCAPTCHA fallback
 * both need a window to attach to.
 *
 * Null is an ordinary answer. The process can be alive with no Activity resumed (a
 * background sync, a notification action), and a caller that needs one has to cope with not
 * getting it rather than assume.
 */
interface CurrentActivity {

    /** The resumed Activity, or null if none is up or the last one is going away. */
    fun get(): Activity?
}

/**
 * Tracks the resumed Activity by registering with the [Application].
 *
 * Registers in its constructor, which is why `corePlatformAndroidModule` builds it with
 * `createdAtStart = true`: resolved lazily it would start watching only at the first call
 * site, by which point the Activity it is being asked about has long since resumed and its
 * `onActivityResumed` has already gone by unheard.
 *
 * Held weakly, and never cleared on pause. Weakly because a strong reference from a process
 * singleton to an Activity is the textbook leak; not cleared on pause because an Activity is
 * paused by anything that draws over it — a permission dialog, a reCAPTCHA sheet — and
 * answering null in the middle of a flow that is still on screen would be wrong. The
 * finishing/destroyed check is what keeps a stale one from being handed out.
 */
internal class ActivityTracker(
    application: Application,
) : CurrentActivity, Application.ActivityLifecycleCallbacks {

    private var resumed: WeakReference<Activity>? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun get(): Activity? =
        resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
