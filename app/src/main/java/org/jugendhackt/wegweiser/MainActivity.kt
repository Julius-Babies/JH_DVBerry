package org.jugendhackt.wegweiser

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jugendhackt.wegweiser.app.checkPermission
import org.jugendhackt.wegweiser.language.language
import org.jugendhackt.wegweiser.location.LocationManager
import org.jugendhackt.wegweiser.sensors.shake.ShakeSensor
import org.jugendhackt.wegweiser.ui.components.*
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    val viewModel: MainViewModel by viewModel()
    var hasLocationPermissionRequested = false
    private lateinit var locationManager: LocationManager

    companion object {
        private const val SHAKE_TIME_THRESHOLD_NANOS = 1_500_000_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        lifecycleScope.launch {
            locationManager = LocationManager(
                context = this@MainActivity,
                scope = lifecycleScope,
                onLocationUpdate = { event -> viewModel.onEvent(event) }
            )
            enableEdgeToEdge()
            checkLocationAvailability()
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
                        if (System.nanoTime() - timeThreshold < SHAKE_TIME_THRESHOLD_NANOS) return@add
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
                                isPlaying = viewModel.isPlaying,
                                canPlay = viewModel.canPlay,
                                language = language
                            ) { viewModel.onEvent(MainEvent.TogglePlayPause) }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        locationManager.stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        lifecycleScope.launch {
            checkLocationAvailability()
        }
    }

    private suspend fun checkLocationAvailability() {
        if (withContext(Dispatchers.IO) {
            checkPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        }) {
            Log.d(TAG, "Permission already granted")
            locationManager.startLocationUpdatesIfPossible()
        } else {
            Log.d(TAG, "Requesting permission")
            requestPermissions()
        }
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
            isGranted -> lifecycleScope.launch {
                locationManager.startLocationUpdatesIfPossible()
            }
            else -> viewModel.onEvent(MainEvent.PermissionPermanentlyDenied)
        }
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
                    } ?: NoStationFound(language)
                }
            }
        }
    }
}