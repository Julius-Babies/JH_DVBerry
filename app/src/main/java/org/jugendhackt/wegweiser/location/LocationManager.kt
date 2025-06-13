package org.jugendhackt.wegweiser.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jugendhackt.wegweiser.MainEvent
import org.jugendhackt.wegweiser.app.checkPermission

class LocationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLocationUpdate: (MainEvent.LocationUpdate) -> Unit
) {
    private val TAG = "LocationManager"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastKnownLocation: Location? = null
    private var lastUpdateTime = 0L
    private var locationUpdateAttempts = 0
    private var isLocationListenerRegistered = false

    companion object {
        private const val MIN_DISTANCE_CHANGE_METERS = 20
        private const val LOCATION_ACCURACY_THRESHOLD_METERS = 200
        private const val LOCATION_AGE_THRESHOLD_MILLIS = 60000L
        private const val GPS_MIN_TIME_MILLIS = 10000L
        private const val NETWORK_MIN_TIME_MILLIS = 15000L
        private const val PASSIVE_MIN_TIME_MILLIS = 30000L
        private const val GPS_MIN_DISTANCE_METERS = 20f
        private const val NETWORK_MIN_DISTANCE_METERS = 30f
        private const val PASSIVE_MIN_DISTANCE_METERS = 50f
        private const val MIN_UPDATE_INTERVAL = 10000L
        private const val MAX_UPDATE_ATTEMPTS = 3
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!isLocationListenerRegistered) {
                Log.w(TAG, "Received location update but listener is not registered")
                return
            }

            scope.launch(Dispatchers.Default) {
                try {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL) {
                        Log.d(TAG, "Skipping location update - too soon")
                        return@launch
                    }

                    if (!isValidLocation(location)) {
                        Log.w(TAG, "Invalid location received: $location")
                        return@launch
                    }

                    Log.d(TAG, "New location received from ${location.provider}: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m")
                    
                    locationUpdateAttempts = 0
                    
                    val significantChange = lastKnownLocation?.let { last ->
                        val distance = calculateDistance(
                            last.latitude, last.longitude,
                            location.latitude, location.longitude
                        )
                        distance > MIN_DISTANCE_CHANGE_METERS
                    } ?: true

                    val shouldUpdate = if (significantChange) {
                        if (location.accuracy <= LOCATION_ACCURACY_THRESHOLD_METERS) {
                            true
                        } else if (lastKnownLocation == null) {
                            Log.d(TAG, "Accepting less accurate location as first location")
                            true
                        } else if (location.accuracy < lastKnownLocation!!.accuracy) {
                            Log.d(TAG, "Accepting less accurate location as it's better than current")
                            true
                        } else {
                            Log.d(TAG, "Ignoring location update - no significant change or poor accuracy")
                            false
                        }
                    } else {
                        Log.d(TAG, "Ignoring location update - no significant change")
                        false
                    }

                    if (shouldUpdate) {
                        lastKnownLocation = location
                        lastUpdateTime = currentTime
                        withContext(Dispatchers.Main) {
                            onLocationUpdate(MainEvent.LocationUpdate(location.latitude, location.longitude))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing location update", e)
                    handleLocationError()
                }
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (!isLocationListenerRegistered) return
            Log.w(TAG, "Location provider disabled: $provider")
            handleLocationError()
        }

        override fun onProviderEnabled(provider: String) {
            if (!isLocationListenerRegistered) return
            Log.d(TAG, "Location provider enabled: $provider")
            locationUpdateAttempts = 0
            startLocationUpdatesIfPossible()
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            if (!isLocationListenerRegistered) return
            Log.d(TAG, "Location provider status changed: provider=$provider, status=$status")
        }
    }

    private fun isValidLocation(location: Location): Boolean {
        return try {
            location.latitude != 0.0 && 
            location.longitude != 0.0 && 
            location.latitude >= -90.0 && 
            location.latitude <= 90.0 && 
            location.longitude >= -180.0 && 
            location.longitude <= 180.0
        } catch (e: Exception) {
            Log.e(TAG, "Error validating location", e)
            false
        }
    }

    private fun handleLocationError() {
        locationUpdateAttempts++
        if (locationUpdateAttempts >= MAX_UPDATE_ATTEMPTS) {
            Log.e(TAG, "Too many location update failures, stopping updates")
            stopLocationUpdates()
        }
    }

    fun stopLocationUpdates() {
        try {
            if (isLocationListenerRegistered) {
                locationManager.removeUpdates(locationListener)
                isLocationListenerRegistered = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    @SuppressLint("MissingPermission")
    @androidx.annotation.RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startLocationUpdates(highAccuracy: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                stopLocationUpdates()

                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).filter { locationManager.isProviderEnabled(it) }

                if (providers.isEmpty()) {
                    Log.e(TAG, "No location providers available")
                    return@launch
                }

                Log.d(TAG, "Available providers: ${providers.joinToString()}")
                
                var bestLocation: Location? = null
                var bestAccuracy = Float.MAX_VALUE
                var bestTime = 0L

                for (provider in providers) {
                    try {
                        val location = locationManager.getLastKnownLocation(provider)
                        if (location != null && isValidLocation(location)) {
                            val accuracy = location.accuracy
                            val time = location.time
                            
                            if (accuracy < bestAccuracy || (accuracy == bestAccuracy && time > bestTime)) {
                                bestLocation = location
                                bestAccuracy = accuracy
                                bestTime = time
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting last known location from $provider", e)
                    }
                }

                if (bestLocation != null) {
                    val locationAge = System.currentTimeMillis() - bestLocation.time
                    if (locationAge < LOCATION_AGE_THRESHOLD_MILLIS && bestLocation.accuracy <= LOCATION_ACCURACY_THRESHOLD_METERS) {
                        Log.d(TAG, "Using best last known location from ${bestLocation.provider}: lat=${bestLocation.latitude}, lon=${bestLocation.longitude}, accuracy=${bestLocation.accuracy}m")
                        lastKnownLocation = bestLocation
                        lastUpdateTime = System.currentTimeMillis()
                        withContext(Dispatchers.Main) {
                            onLocationUpdate(MainEvent.LocationUpdate(bestLocation.latitude, bestLocation.longitude))
                        }
                    } else {
                        Log.d(TAG, "Last known location is too old (${locationAge}ms) or inaccurate (${bestLocation.accuracy}m), waiting for fresh update")
                    }
                }

                withContext(Dispatchers.Main) {
                    for (provider in providers) {
                        try {
                            val minTime = when (provider) {
                                LocationManager.GPS_PROVIDER -> GPS_MIN_TIME_MILLIS
                                LocationManager.NETWORK_PROVIDER -> NETWORK_MIN_TIME_MILLIS
                                else -> PASSIVE_MIN_TIME_MILLIS
                            }
                            val minDistance = when (provider) {
                                LocationManager.GPS_PROVIDER -> GPS_MIN_DISTANCE_METERS
                                LocationManager.NETWORK_PROVIDER -> NETWORK_MIN_DISTANCE_METERS
                                else -> PASSIVE_MIN_DISTANCE_METERS
                            }
                            
                            locationManager.requestLocationUpdates(
                                provider,
                                minTime,
                                minDistance,
                                locationListener,
                                context.mainLooper
                            )
                            isLocationListenerRegistered = true
                            Log.d(TAG, "Requested location updates from provider: $provider (minTime=$minTime, minDistance=$minDistance)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error requesting location updates from $provider", e)
                            handleLocationError()
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location permission error", e)
                handleLocationError()
            } catch (e: Exception) {
                Log.e(TAG, "Error in startLocationUpdates", e)
                handleLocationError()
            }
        }
    }

    fun startLocationUpdatesIfPossible() {
        scope.launch {
            withContext(Dispatchers.Main) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Location permission not granted")
                    return@withContext
                }

                try {
                    // Check if GPS is enabled
                    val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    if (!isGpsEnabled) {
                        Log.w(TAG, "GPS is not enabled")
                        return@withContext
                    }

                    // Request last known location first
                    val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (lastKnownLocation != null) {
                        Log.d(TAG, "Using last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                        onLocationUpdate(MainEvent.LocationUpdate(lastKnownLocation.latitude, lastKnownLocation.longitude))
                    }

                    // Start location updates
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000L, // Update every 5 seconds
                        10f, // Update if moved 10 meters
                        locationListener,
                        Looper.getMainLooper()
                    )
                    isLocationListenerRegistered = true
                    Log.d(TAG, "Location updates started")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting location updates", e)
                }
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth's radius in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
} 