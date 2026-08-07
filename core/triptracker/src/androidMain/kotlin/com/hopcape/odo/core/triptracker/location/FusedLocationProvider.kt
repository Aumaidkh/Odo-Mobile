package com.hopcape.odo.core.triptracker.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hopcape.odo.core.triptracker.model.FixRequest
import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.port.LocationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

/**
 * FusedLocationProviderClient, as the [LocationProvider] port. Both methods can throw
 * [SecurityException] if the permission was revoked between [TrackingPreconditions][
 * com.hopcape.odo.core.triptracker.TrackingPreconditions] reading it and this call
 * running — the `@SuppressLint` is for the linter, not a claim the permission is always
 * held; both call sites still handle the exception.
 */
internal class FusedLocationProvider(context: android.content.Context) : LocationProvider {

    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override fun fixes(spec: FixRequest): Flow<LocationSample> = callbackFlow {
        val request = LocationRequest.Builder(spec.interval.inWholeMilliseconds)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toLocationSample()) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): LocationSample? = try {
        client.lastLocation.await()?.toLocationSample()
    } catch (e: SecurityException) {
        null
    }
}

private fun Location.toLocationSample(): LocationSample = LocationSample(
    at = Instant.fromEpochMilliseconds(time),
    elapsed = elapsedRealtimeNanos.nanoseconds,
    lat = latitude,
    lon = longitude,
    accuracyM = if (hasAccuracy()) accuracy else DEFAULT_ACCURACY_M,
    speedMps = if (hasSpeed()) speed else null,
    speedAccuracyMps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasSpeedAccuracy()) {
        speedAccuracyMetersPerSecond
    } else {
        null
    },
    bearingDeg = if (hasBearing()) bearing else null,
)

/** A fix with no accuracy figure is worse than useless — treat it as maximally uncertain. */
private const val DEFAULT_ACCURACY_M = 9_999f
