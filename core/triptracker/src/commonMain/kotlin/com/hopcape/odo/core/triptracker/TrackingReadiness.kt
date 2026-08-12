package com.hopcape.odo.core.triptracker

/** Which prerequisites are present. The feature module renders and fixes the gaps. */
data class TrackingReadiness(
    val fineLocation: Boolean,
    val backgroundLocation: Boolean,
    val activityRecognition: Boolean,
    val bluetoothConnect: Boolean,
    val notifications: Boolean,
) {
    val canTrack: Boolean get() = fineLocation && backgroundLocation && activityRecognition
}
