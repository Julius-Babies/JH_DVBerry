package org.jugendhackt.wegweiser

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.app.checkPermission
import org.jugendhackt.wegweiser.language.language
import org.jugendhackt.wegweiser.sensors.shake.ShakeSensor
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    val viewModel: MainViewModel by viewModel()
    var hasLocationPermissionRequested = false
    private lateinit var locationManager: LocationManager
    private var lastKnownLocation: Location? = null
    private var lastUpdateTime = 0L
    private val MIN_UPDATE_INTERVAL = 10000L // 10 seconds minimum between updates
    private var locationUpdateAttempts = 0
    private val MAX_UPDATE_ATTEMPTS = 3
    private var isLocationListenerRegistered = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!isLocationListenerRegistered) {
                Log.w(TAG, "Received location update but listener is not registered")
                return
            }

            lifecycleScope.launch(Dispatchers.Default) {
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
                    
                    // Reset attempts counter on successful update
                    locationUpdateAttempts = 0
                    
                    // Check if the location has changed significantly
                    val significantChange = lastKnownLocation?.let { last ->
                        val distance = calculateDistance(
                            last.latitude, last.longitude,
                            location.latitude, location.longitude
                        )
                        distance > 20 // Only update if moved more than 20 meters
                    } ?: true

                    // Accept location if:
                    // 1. It's significantly different AND accurate enough (<= 200m)
                    // 2. OR we don't have a last known location
                    // 3. OR the new location is more accurate than the last known one
                    val shouldUpdate = if (significantChange) {
                        if (location.accuracy <= 200) {
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
                            viewModel.onEvent(MainEvent.LocationUpdate(location.latitude, location.longitude))
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
            
            lifecycleScope.launch(Dispatchers.Main) {
                Log.w(TAG, "Location provider disabled: $provider")
                handleLocationError()
                checkProviderAvailability()
            }
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
            when (status) {
                android.location.LocationProvider.AVAILABLE -> {
                    Log.d(TAG, "Provider $provider is available")
                    locationUpdateAttempts = 0
                }
                android.location.LocationProvider.OUT_OF_SERVICE -> {
                    Log.w(TAG, "Provider $provider is out of service")
                    handleLocationError()
                }
                android.location.LocationProvider.TEMPORARILY_UNAVAILABLE -> {
                    Log.w(TAG, "Provider $provider is temporarily unavailable")
                    handleLocationError()
                }
            }
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
            lifecycleScope.launch(Dispatchers.Main) {
                showProviderErrorDialog()
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            if (isLocationListenerRegistered) {
                locationManager.removeUpdates(locationListener)
                isLocationListenerRegistered = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing location updates", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        lifecycleScope.launch(Dispatchers.IO) {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            withContext(Dispatchers.Main) {
                enableEdgeToEdge()
                checkLocationAvailability()
            }
        }

        // Restore last known location if available
        savedInstanceState?.let {
            val lat = it.getDouble("last_lat", 0.0)
            val lon = it.getDouble("last_lon", 0.0)
            val accuracy = it.getFloat("last_accuracy", Float.MAX_VALUE)
            if (lat != 0.0 && lon != 0.0 && accuracy <= 200) {
                lastKnownLocation = Location("saved").apply {
                    latitude = lat
                    longitude = lon
                    setAccuracy(accuracy)
                }
                viewModel.onEvent(MainEvent.LocationUpdate(lat, lon))
            }
        }

        setContent {
            KoinAndroidContext {
                val shakeSensor = koinInject<ShakeSensor>()
                val context = LocalContext.current
                val language = language(context)

                LaunchedEffect(Unit) {
                    Log.d(TAG, "Shake sensor initialized")
                    var timeThreshold = 0L
                    shakeSensor.add {
                        if (System.nanoTime() - timeThreshold < 1_500_000_000) return@add
                        timeThreshold = System.nanoTime()
                        viewModel.onEvent(MainEvent.TogglePlayPause)
                    }
                }

                WegweiserTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.5f)
                            ) {
                                MainContent(viewModel, language)
                            }
                            PlayPauseButton(
                                viewModel.isPlaying,
                                viewModel.canPlay,
                                language
                            ) { viewModel.onEvent(MainEvent.TogglePlayPause) }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastKnownLocation?.let { location ->
            outState.putDouble("last_lat", location.latitude)
            outState.putDouble("last_lon", location.longitude)
            outState.putFloat("last_accuracy", location.accuracy)
        }
    }

    private fun checkLocationAvailability() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (checkPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.d(TAG, "Permission already granted")
                startLocationUpdatesIfPossible()
            } else {
                Log.d(TAG, "Requesting permission")
                requestPermissions()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun startLocationUpdates(highAccuracy: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                stopLocationUpdates() // Stop any existing updates first

                // Try to get location from all available providers
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).filter { locationManager.isProviderEnabled(it) }

                if (providers.isEmpty()) {
                    Log.e(TAG, "No location providers available")
                    withContext(Dispatchers.Main) {
                        showProviderErrorDialog()
                    }
                    return@launch
                }

                Log.d(TAG, "Available providers: ${providers.joinToString()}")
                
                // Try to get last known location from all providers
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
                    if (locationAge < 60000 && bestLocation.accuracy <= 200) { // 1 minute and accuracy <= 200m
                        Log.d(TAG, "Using best last known location from ${bestLocation.provider}: lat=${bestLocation.latitude}, lon=${bestLocation.longitude}, accuracy=${bestLocation.accuracy}m")
                        lastKnownLocation = bestLocation
                        lastUpdateTime = System.currentTimeMillis()
                        withContext(Dispatchers.Main) {
                            viewModel.onEvent(MainEvent.LocationUpdate(bestLocation.latitude, bestLocation.longitude))
                        }
                    } else {
                        Log.d(TAG, "Last known location is too old (${locationAge}ms) or inaccurate (${bestLocation.accuracy}m), waiting for fresh update")
                    }
                }

                // Request updates from all available providers with different parameters
                withContext(Dispatchers.Main) {
                    for (provider in providers) {
                        try {
                            val minTime = when (provider) {
                                LocationManager.GPS_PROVIDER -> 10000L // 10 seconds for GPS
                                LocationManager.NETWORK_PROVIDER -> 15000L // 15 seconds for network
                                else -> 30000L // 30 seconds for passive
                            }
                            val minDistance = when (provider) {
                                LocationManager.GPS_PROVIDER -> 20f // 20 meters for GPS
                                LocationManager.NETWORK_PROVIDER -> 30f // 30 meters for network
                                else -> 50f // 50 meters for passive
                            }
                            
                            locationManager.requestLocationUpdates(
                                provider,
                                minTime,
                                minDistance,
                                locationListener,
                                mainLooper
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

    private fun startLocationUpdatesIfPossible() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            when {
                isGpsEnabled -> startLocationUpdates(true)
                isNetworkEnabled -> startLocationUpdates(false)
                else -> showProviderErrorDialog()
            }
        } else {
            requestPermissions()
        }
    }

    private fun checkProviderAvailability() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            showProviderErrorDialog()
        }
    }

    private fun showProviderErrorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Standortdienst benötigt")
            .setMessage("Bitte aktivieren Sie GPS oder Netzwerk-basierte Standortdienste.")
            .setPositiveButton("Einstellungen öffnen") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // If we have a last known location, use it immediately
        lastKnownLocation?.let { location ->
            if (location.accuracy <= 200) {
                viewModel.onEvent(MainEvent.LocationUpdate(location.latitude, location.longitude))
            }
        }
        checkLocationAvailability()
    }

    private fun requestPermissions() {
        if (hasLocationPermissionRequested) return
        hasLocationPermissionRequested = true
        Log.d(TAG, "Requesting location permission")

        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Permission result: $isGranted")
        when {
            isGranted -> startLocationUpdatesIfPossible()
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> showRationaleDialog()
            else -> viewModel.onEvent(MainEvent.PermissionPermanentlyDenied)
        }
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Standort benötigt")
            .setMessage("Wir benötigen den Standortzugriff um die nächsten Abfahrten anzuzeigen.")
            .setPositiveButton("Erneut versuchen") { _, _ -> requestPermissions() }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    @Composable
    private fun MainContent(viewModel: MainViewModel, language: language) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = viewModel.nearestStops == null
            ) { isLoading ->
                if (isLoading) {
                    LoadingIndicator()
                } else {
                    viewModel.nearestStops?.let {
                        StationInfo(it, language)
                    } ?: Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = language.getString("ui.no_station_found"),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = language.getString("ui.move_closer"),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LoadingIndicator() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.padding(24.dp),
                strokeWidth = 16.dp
            )
        }
    }

    @Composable
    private fun StationInfo(station: Station, language: language) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = station.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${language.getString("ui.next_departures")}:",
                style = MaterialTheme.typography.titleLarge
            )
            // Group departures by line and direction
            val groupedDepartures = station.departures
                .groupBy { "${it.line} ${it.destination}" }
                .mapValues { (_, departures) -> departures.minByOrNull { it.time } }
                .values
                .filterNotNull() // Remove any null values
                .sortedBy { it.time }
                .take(5) // Show only the next 5 unique departures

            Text(
                text = groupedDepartures.joinToString("\n") { departure ->
                    val timeString = String.format("%02d:%02d", departure.time.hour, departure.time.minute)
                    val delayString = when {
                        departure.delayInMinutes > 0 -> " +${departure.delayInMinutes}min"
                        departure.delayInMinutes < 0 -> " ${departure.delayInMinutes}min"
                        else -> ""
                    }
                    "${departure.line}: ${departure.destination} ($timeString)$delayString"
                }
            )
        }
    }

    @Composable
    fun ColumnScope.PlayPauseButton(
        isPlaying: Boolean,
        canPlay: Boolean,
        language: language,
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .clickable(onClick = onClick, enabled = canPlay),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                label = "PlayPauseAnimation"
            ) { targetState ->
                when {
                    targetState && canPlay -> {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = language.getString("contentDescription.stop"),
                            modifier = Modifier
                                .size(512.dp)
                                .padding(16.dp)
                        )
                    }

                    !targetState && canPlay -> {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = language.getString("contentDescription.play"),
                            modifier = Modifier
                                .size(512.dp)
                                .padding(16.dp)
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = language.getString("contentDescription.loading"),
                            modifier = Modifier
                                .size(512.dp)
                                .padding(16.dp)
                        )
                    }
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