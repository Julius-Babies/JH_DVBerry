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

    private val stops: List<Station> by lazy { dvbSource.getStations() }

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
            Log.d(TAG, "Updating location: ${event.latitude}, ${event.longitude}")
            latitude = event.latitude
            longitude = event.longitude

            val nearest = stops.minByOrNull { station ->
                sqrt(
                    (longitude - station.longitude).pow(2) +
                            (latitude - station.latitude).pow(2)
                )
            }

            nearest?.let { station ->
                val departures = withContext(Dispatchers.IO) {
                    dvbSource.departureMonitor(station.id, 5)?.departures.orEmpty()
                }

                withContext(Dispatchers.Main) {
                    if (station.id != nearestStops?.id) {
                        Log.d(TAG, "New nearest station: ${station.name}")
                        nearestStops = station.copy(departures = departures)
                        canPlay = true
                    }
                }
            } ?: run {
                withContext(Dispatchers.Main) {
                    nearestStops = null
                    canPlay = false
                }
            }
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
        return buildString {
            append("${language.getString("tts.hold")} ")
            append(name)
            append(". ${language.getString("tts.next_departures")}: ")

            departures.forEachIndexed { index, departure ->
                if (index > 0) append(". ")

                when (departure.platformType) {
                    "Platform", "Tram" -> append("${language.getString("tts.line")} ")
                    "Railtrack" -> append("${language.getString("tts.railtrack")} ")
                    else -> append("")
                }

                append(departure.line.replace(Regex("(?<=[A-Za-z])(?=\\d)"), " "))
                append(" ${language.getString("tts.in_direction")} ")
                append(departure.destination.replace(" Bahnhof", ""))

                val minutes = ((departure.time.hour * 60 + departure.time.minute) -
                        (LocalTime.now().hour * 60 + LocalTime.now().minute))

                when {
                    minutes <= 0 -> append(" ${language.getString("tts.now")}")
                    minutes > 60 -> append(" ${language.getString("tts.at_time")} ${departure.time}")
                    else -> append(" ${language.getString("tts.in")} $minutes ${language.getString("tts.minutes")}")
                }

                append(" ${language.getString("tts.at")} ")
                when (departure.platformType) {
                    "Platform", "Tram" -> append(language.getString("tts.platform"))
                    "Railtrack" -> append(language.getString("tts.railtrack"))
                    else -> append(departure.platformType)
                }
                append(" ${departure.platformName}")

                if (departure.isCancelled) {
                    append(" ${language.getString("tts.isCancelled")}")
                } else if (departure.delayInMinutes != 0) {
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