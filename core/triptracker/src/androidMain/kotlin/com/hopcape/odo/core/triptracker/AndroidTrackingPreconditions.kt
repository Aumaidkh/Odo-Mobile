package com.hopcape.odo.core.triptracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/** Checks the five permissions [TrackingReadiness] names — requesting them is `:feature:auto-odometer`'s job. */
internal class AndroidTrackingPreconditions(private val context: Context) : TrackingPreconditions {

    override fun status(): TrackingReadiness = TrackingReadiness(
        fineLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        activityRecognition = context.hasPermission(Manifest.permission.ACTIVITY_RECOGNITION),
        bluetoothConnect = context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT),
        notifications = context.hasPermission(Manifest.permission.POST_NOTIFICATIONS),
    )

    private fun Context.hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
