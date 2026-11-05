package com.example.gpsphotapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val systemLocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var location: Location? = null
        private set

    var onLocationUpdate: ((Location) -> Unit)? = null

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val latest = result.lastLocation ?: return
            publish(latest, "fused")
        }
    }

    private val systemListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = publish(loc, "system:${loc.provider}")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun publish(loc: Location, source: String) {
        val prev = location
        if (prev != null && loc.accuracy > 0f && prev.accuracy > 0f &&
            loc.time < prev.time && loc.accuracy > prev.accuracy
        ) return
        location = loc
        Log.d(TAG, "fix $source acc=${loc.accuracy} lat=${loc.latitude} lon=${loc.longitude}")
        onLocationUpdate?.invoke(loc)
    }

    fun isGpsHardwareAvailable(): Boolean {
        return systemLocationManager.allProviders.contains(LocationManager.GPS_PROVIDER)
    }

    fun isGpsEnabled(): Boolean {
        return systemLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000)
            .setMinUpdateIntervalMillis(1_000)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching {
            fusedClient.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { last ->
                if (last != null) publish(last, "fused:last")
            }
        }.onFailure { Log.w(TAG, "fused start failed", it) }

        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            if (!systemLocationManager.isProviderEnabled(provider)) {
                Log.w(TAG, "provider $provider disabled")
                return@forEach
            }
            runCatching {
                systemLocationManager.requestLocationUpdates(
                    provider,
                    1_000L,
                    0f,
                    systemListener,
                    Looper.getMainLooper()
                )
                systemLocationManager.getLastKnownLocation(provider)?.let {
                    publish(it, "system:last:$provider")
                }
            }.onFailure { Log.w(TAG, "system start $provider failed", it) }
        }
    }

    fun stop() {
        runCatching { fusedClient.removeLocationUpdates(fusedCallback) }
        runCatching { systemLocationManager.removeUpdates(systemListener) }
    }

    companion object {
        private const val TAG = "LocationHelper"
    }
}
