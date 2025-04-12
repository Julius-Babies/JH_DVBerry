package org.jugendhackt.wegweiser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.ui.theme.WegweiserTheme
import org.jugendhackt.wegweiser.tts.TTS

class MainActivity : ComponentActivity() {
    private lateinit var tts: TTS
    val viewModel: MainViewModel by viewModels()
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisiere Text-to-Speech (TTS)
        tts = TTS(this)
        tts.initialize() // Initialisiere das TTS

        enableEdgeToEdge()
        requestPermissions()
        MainScope().launch {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startLocationUpdates()
            }
        }
        setContent {
            LaunchedEffect(Unit) {
                viewModel.init(applicationContext)
            }
            WegweiserTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val viewModel by viewModels<MainViewModel>()
                    var counter by remember { mutableStateOf(0) }

                    Column {
                        Text(viewModel.testText.value)

                        val scope = rememberCoroutineScope()

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest().apply {
            interval = 1000
            fastestInterval = 5000
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            Log.d("Location", "onLocationResult")
            Log.d("Location", "Location: ${locationResult.lastLocation}")
            locationResult.lastLocation?.let { location ->
                // Update UI with the new location
                viewModel.onEvent(MainEvent.LocationUpdate(location.latitude, location.longitude))
                        Button(onClick = {
                            scope.launch {
                                tts.speak("Hallo Welt")
                            }
                        }) {
                            Text("SAY TEST text")
                        }
                    }
                }
            }
        }
    }

    fun requestPermissions() {
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    false
                ) -> {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return@registerForActivityResult
                    }
                    startLocationUpdates()
                }

                permissions.getOrDefault(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    false
                ) -> {
                    // Only approximate location access granted.
                }

                else -> {
                    // No location access granted.
                }
            }
        }

        // Before you perform the actual permission request, check whether your app
        // already has the permissions, and whether your app needs to show a permission
        // rationale dialog. For more details, see Request permissions:
        // https://developer.android.com/training/permissions/requesting#request-permission
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

@Composable
fun PlayPauseButton() {
    var isPlaying by remember { mutableStateOf(false) }
    // Erhalte die aktuelle Bildschirmbreite
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    // Setze die Button-Größe auf 40% der Bildschirmbreite
    val buttonSize = screenWidth * 0.4f
    // Wähle die Icon-Größe als 75% des Button-Durchmessers
    val iconSize = buttonSize * 0.75f

    IconButton(
        onClick = { isPlaying = !isPlaying },
        modifier = Modifier.size(buttonSize)
    ) {
        if (isPlaying) {
            Icon(
                imageVector = Icons.Outlined.Pause,
                contentDescription = "Pause",
                modifier = Modifier.size(iconSize)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}