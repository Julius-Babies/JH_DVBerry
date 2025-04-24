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
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.app.checkPermission
import org.jugendhackt.wegweiser.language.language
import org.jugendhackt.wegweiser.sensors.shake.ShakeSensor
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    val viewModel: MainViewModel by viewModel()
    var hasLocationPermissionRequested = false
    private lateinit var locationManager: LocationManager

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lifecycleScope.launch(Dispatchers.Default) {
                Log.d(TAG, "New location received")
                viewModel.onEvent(MainEvent.LocationUpdate(location.latitude, location.longitude))
            }
        }

        override fun onProviderDisabled(provider: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                Log.w(TAG, "Location provider disabled: $provider")
                checkProviderAvailability()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        enableEdgeToEdge()
        checkLocationAvailability()

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
                            MainContent(viewModel, language)
                            PlayPauseButton(
                                viewModel.isPlaying,
                                viewModel.canPlay,
                                language
                            ) { viewModel.onEvent(MainEvent.TogglePlayPause) }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
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
        try {
            val provider = when {
                highAccuracy && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> {
                    showProviderErrorDialog()
                    return
                }
            }

            locationManager.requestLocationUpdates(
                provider,
                1000L,
                1f,
                locationListener,
                mainLooper
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission error", e)
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
        locationManager.removeUpdates(locationListener)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
        ) {
            AnimatedContent(
                targetState = viewModel.nearestStops == null
            ) { isLoading ->
                if (isLoading) {
                    LoadingIndicator()
                } else {
                    viewModel.nearestStops?.let {
                        StationInfo(it, language)
                    } ?: Text("Keine Station gefunden")
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
            Text(
                text = station.departures.joinToString("\n") { departure ->
                    "${departure.line}: ${departure.destination} (${departure.time})"
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
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(16.dp))
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
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    !targetState && canPlay -> {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = language.getString("contentDescription.play"),
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = language.getString("contentDescription.loading"),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}