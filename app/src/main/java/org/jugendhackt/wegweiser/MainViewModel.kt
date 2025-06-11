package org.jugendhackt.wegweiser

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jugendhackt.wegweiser.dvb.DVBSource
import org.jugendhackt.wegweiser.language.language
import org.jugendhackt.wegweiser.tts.TTS
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MainViewModel(
    private val dvbSource: DVBSource,
    private val tts: TTS,
    private val language: language
) : ViewModel() {
    private val TAG = "MainViewModel"

    var latitude by mutableDoubleStateOf(0.0)
    var longitude by mutableDoubleStateOf(0.0)
    var isPlaying by mutableStateOf(false)
    var canPlay by mutableStateOf(false)
    var nearestStops by mutableStateOf<Station?>(null)

    private val stops: List<Station> by lazy { 
        Log.d(TAG, "Initializing stops list")
        try {
            val stations = dvbSource.getStations()
            Log.d(TAG, "Loaded ${stations.size} stations")
            if (stations.isEmpty()) {
                Log.e(TAG, "No stations loaded from DVB source")
            }
            stations
        } catch (e: Exception) {
            Log.e(TAG, "Error loading stations", e)
            emptyList()
        }
    }

    fun onEvent(event: MainEvent) {
        Log.d(TAG, "Received event: ${event.javaClass.simpleName}")
        viewModelScope.launch {
            when (event) {
                is MainEvent.LocationUpdate -> handleLocationUpdate(event)
                MainEvent.TogglePlayPause -> togglePlayState()
                MainEvent.PermissionDenied -> handlePermissionDenied()
                MainEvent.PermissionPermanentlyDenied -> handlePermanentDenial()
            }
        }
    }

    private suspend fun handleLocationUpdate(event: MainEvent.LocationUpdate) {
        withContext(Dispatchers.Default) {
            Log.d(TAG, "Handling location update: lat=${event.latitude}, lon=${event.longitude}")
            if (stops.isEmpty()) {
                Log.e(TAG, "No stations available for distance calculation")
                withContext(Dispatchers.Main) {
                    updateUI(null)
                }
                return@withContext
            }
            
            try {
                val nearest = stops.minByOrNull { station ->
                    calculateDistance(event.latitude, event.longitude, station)
                }
                Log.d(TAG, "Found nearest station: ${nearest?.name ?: "none"}")

                if (nearest != null) {
                    val distance = calculateDistance(event.latitude, event.longitude, nearest)
                    // Increased distance threshold to 250 meters for better user experience
                    if (distance <= 250.0) {
                        try {
                            val stationWithDepartures = dvbSource.departureMonitor(nearest.id, 10)
                            withContext(Dispatchers.Main) {
                                updateUI(stationWithDepartures)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching departures", e)
                            withContext(Dispatchers.Main) {
                                updateUI(null)
                            }
                        }
                    } else {
                        Log.d(TAG, "Too far from nearest station: $distance meters")
                        withContext(Dispatchers.Main) {
                            updateUI(null)
                        }
                    }
                } else {
                withContext(Dispatchers.Main) {
                        updateUI(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing location update", e)
                withContext(Dispatchers.Main) {
                    updateUI(null)
                }
            }
        }
    }

    private fun calculateDistance(lat: Double, lon: Double, station: Station): Double {
        try {
            val R = 6371000.0 // Earth's radius in meters
            val lat1 = Math.toRadians(lat)
            val lat2 = Math.toRadians(station.latitude)
            val deltaLat = Math.toRadians(station.latitude - lat)
            val deltaLon = Math.toRadians(station.longitude - lon)

            val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                    Math.cos(lat1) * Math.cos(lat2) *
                    Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            val distance = R * c

            Log.d(TAG, "Calculated distance to ${station.name}: $distance meters")
            return distance
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating distance to ${station.name}", e)
            return Double.MAX_VALUE
        }
    }

    private fun updateUI(nearest: Station?) {
        Log.d(TAG, "Updating UI with station: ${nearest?.name ?: "none"}")
        if (nearest != null) {
            nearestStops = nearest
            canPlay = true
        } else {
            nearestStops = null
            canPlay = false
        }
    }


    private fun togglePlayState() {
        Log.d(TAG, "Toggling play state. Current: $isPlaying")
        isPlaying = !isPlaying
        if (isPlaying) {
            nearestStops?.let {
                tts.speak(it.buildTTSSpeakableString(language)) { isPlaying = false }
            }
        } else {
            tts.stop()
        }
    }

    private fun handlePermissionDenied() {
        Log.w(TAG, "Location permission denied")
        canPlay = false
        isPlaying = false
    }

    private fun handlePermanentDenial() {
        Log.e(TAG, "Location permission permanently denied")
        canPlay = false
        isPlaying = false
    }
}

sealed class MainEvent {
    data class LocationUpdate(val latitude: Double, val longitude: Double) : MainEvent()
    object TogglePlayPause : MainEvent()
    object PermissionDenied : MainEvent()
    object PermissionPermanentlyDenied : MainEvent()
}

data class Station(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val departures: List<Departure>
) {
    fun buildTTSSpeakableString(language: language): String {
        // Get only the displayed departures (grouped by line and direction, sorted by time)
        val displayedDepartures = departures
            .groupBy { "${it.line} ${it.destination}" }
            .mapValues { (_, departures) -> departures.minByOrNull { it.time } }
            .values
            .filterNotNull()
            .sortedBy { it.time }
            .take(5)

        return buildString {
            append("${language.getString("tts.hold")} ")
            append(name.replace("S-Bf.", "S Bahnhof"))
            append(". ${language.getString("tts.next_departures")}: ")

            displayedDepartures.forEachIndexed { index, departure ->
                if (index > 0) append(". ")

                // Handle line announcement based on line type
                when {
                    departure.line.startsWith("S") -> {
                        // For S-Bahn lines, just announce the line number
                        append(departure.line)
                    }
                    departure.platformType == "Railtrack" -> {
                        // For trains, announce with "Gleis"
                        append("${language.getString("tts.railtrack")} ${departure.line}")
                    }
                    else -> {
                        // For other lines (bus, tram), announce with "Linie"
                        append("${language.getString("tts.line")} ${departure.line}")
                    }
                }

                append(" ${language.getString("tts.in_direction")} ")
                append(departure.destination.replace(" Bahnhof", "").replace("S-Bf.", "S Bahnhof"))

                val minutes = ((departure.time.hour * 60 + departure.time.minute) -
                        (LocalTime.now().hour * 60 + LocalTime.now().minute))

                when {
                    minutes <= 0 -> append(" ${language.getString("tts.now")}")
                    minutes > 60 -> append(" ${language.getString("tts.at_time")} ${departure.time}")
                    else -> {
                        append(" ${language.getString("tts.in")} ")
                        when (minutes) {
                            1 -> append("${language.getString("tts.one_minute")}")
                            else -> append("$minutes ${language.getString("tts.minutes")}")
                        }
                    }
                }

                append(" ${language.getString("tts.at")} ")
                when (departure.platformType) {
                    "Platform", "Tram" -> append(language.getString("tts.platform"))
                    "Railtrack" -> append(language.getString("tts.railtrack"))
                    else -> append(departure.platformType)
                }
                // Handle platform names for different line types
                val platformName = when {
                    // For S-Bahn lines, just use the platform number
                    departure.line.startsWith("S") -> departure.platformName.replace(Regex("^(Gleis|Steig)\\s*"), "")
                    // For other lines, remove "Gleis" prefix if present
                    else -> departure.platformName.replace(Regex("^Gleis\\s*"), "")
                }
                append(" $platformName")

                if (departure.isCancelled) {
                    append(" ${language.getString("tts.isCancelled")}")
                }
                
                if (departure.delayInMinutes != 0) {
                    append(", ${language.getString("tts.today")} ")
                    when (abs(departure.delayInMinutes)) {
                        1 -> append(language.getString("tts.one_minute"))
                        else -> append("${abs(departure.delayInMinutes)} ${language.getString("tts.minutes")}")
                    }
                    append(if (departure.delayInMinutes > 0) " ${language.getString("tts.later")}"
                    else " ${language.getString("tts.earlier")}")
                }
            }
        }
    }
}
data class Departure(
    val line: String,
    val destination: String,
    val time: LocalTime,
    val platformName: String,
    val platformType: String,
    val delayInMinutes: Int = 0,
    val isCancelled: Boolean = false
)