package com.hopcape.odo.core.triptracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Checks the five permissions [TrackingReadiness] names — requesting them is `:feature:auto-odometer`'s job. */
internal class AndroidTrackingPreconditions(private val context: Context) : TrackingPreconditions {

    override fun status(): TrackingReadiness {
        val fineLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        return TrackingReadiness(
            fineLocation = fineLocation,
            // Below Android 10 the separate background permission does not exist — fine
            // location covers the background, and checking the platform-unknown permission
            // would read denied forever, deadlocking canTrack on those phones (minSdk 26).
            backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                fineLocation
            },
            activityRecognition = context.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION),
            // BLUETOOTH_CONNECT does not exist before Android 12 either — same deadlock as
            // backgroundLocation above, covered instead by the legacy BLUETOOTH permission
            // (maxSdkVersion 30), which is granted at install with nothing to check at runtime.
            bluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                true
            },
            notifications = context.hasPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    private fun Context.hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
