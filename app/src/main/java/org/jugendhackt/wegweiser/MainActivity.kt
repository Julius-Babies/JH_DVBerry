package org.jugendhackt.wegweiser

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import org.jugendhackt.wegweiser.app.checkPermission
import org.jugendhackt.wegweiser.language.language
import org.jugendhackt.wegweiser.sensors.shake.ShakeSensor
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import org.jugendhackt.wegweiser.MainEvent

class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModel()
    var hasLocationPermissionRequested = false

    private lateinit var locationManager: LocationManager
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            viewModel.onEvent(MainEvent.LocationUpdate(location.latitude, location.longitude))
        }

        override fun onProviderDisabled(provider: String) {
            Log.w("Location", "Provider deaktiviert: $provider")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        enableEdgeToEdge()
        if (checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            startLocationUpdates(true)
        } else {
            requestPermissions()
        }

        setContent {
            KoinAndroidContext {
                val shakeSensor = koinInject<ShakeSensor>()
                LaunchedEffect(42) {
                    var timeThreshold = 0L
                    shakeSensor.add {
                        if (System.nanoTime() - timeThreshold < 1500000000L) return@add
                        if (viewModel.nearestStops == null) return@add
                        timeThreshold = System.nanoTime()
                        viewModel.onEvent(MainEvent.TogglePlayPause)
                        Log.d("ACC", "ButtonToggle by Shaking")
                    }
                }

                val language = language(this)

                WegweiserTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                AnimatedContent(
                                    targetState = viewModel.nearestStops == null
                                ) { isLoading ->
                                    if (isLoading) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier
                                                    .padding(24.dp)
                                                    .fillMaxSize(),
                                                strokeWidth = 16.dp
                                            )
                                        }
                                        return@AnimatedContent
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp)
                                    ) {
                                        viewModel.nearestStops?.let {
                                            Text(
                                                text = it.name,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .basicMarquee(iterations = Int.MAX_VALUE),
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.displayLarge
                                            )
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                text = "${language.getString("ui.next_departures")}: ",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                            Text(
                                                text = buildString {
                                                    it.departures.forEachIndexed { i, departure ->
                                                        if (i > 0) append("\n")
                                                        append(departure.line)
                                                        append(": ")
                                                        append(departure.destination)
                                                        append(" (")
                                                        append(departure.time)
                                                        append(") ${language.getString("ui.at")} ${when (departure.platformType) {
                                                            "Platform", "Tram" -> language.getString("ui.platform")
                                                            "Railtrack" -> language.getString("ui.railtrack")
                                                            else -> departure.platformType
                                                        }
                                                        } ${departure.platformName}")
                                                        if (departure.isCancelled) append(" ${language.getString("ui.isCancelled")}") else if (departure.delayInMinutes > 0) append(
                                                            " +${departure.delayInMinutes}${language.getString("ui.abbreviation_minutes")}"
                                                        ) else if (departure.delayInMinutes < 0) append(
                                                            " ${departure.delayInMinutes}${language.getString("ui.abbreviation_minutes")}"
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            PlayPauseButton(
                                viewModel.isPlaying,
                                viewModel.canPlay,
                                language = language
                            ) { viewModel.onEvent(MainEvent.TogglePlayPause) }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun startLocationUpdates(highAccuracy: Boolean) {
        try {
            val provider = if (highAccuracy) {
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.NETWORK_PROVIDER
            }

            locationManager.requestLocationUpdates(
                provider,
                1000L,
                1f,
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("Location", "Berechtigungsfehler", e)
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)
    }

    override fun onResume() {
        super.onResume()
        if (checkPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            startLocationUpdates(true)
        } else {
            requestPermissions()
        }
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Standortzugriff benötigt")
            .setMessage("Diese App benötigt den Standortzugriff, um die nächsten Abfahrten anzuzeigen.")
            .setPositiveButton("Erneut versuchen") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    fun requestPermissions() {
        if (hasLocationPermissionRequested) return
        hasLocationPermissionRequested = true

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            when {
                isGranted -> startLocationUpdates(true)
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) -> showRationaleDialog()
                else -> viewModel.onEvent(MainEvent.PermissionPermanentlyDenied)
            }
        }

        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        ) { isPlaying ->
            if (isPlaying && canPlay) {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = language.getString("contentDescription.stop"),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize()
                )
            } else if ((!isPlaying) && canPlay) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = language.getString("contentDescription.play"),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = language.getString("contentDescription.loading"),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxSize()
                )
            }
        }
    }
}