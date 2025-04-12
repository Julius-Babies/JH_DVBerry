package org.jugendhackt.wegweiser

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.dvb.DVBSource
import org.jugendhackt.wegweiser.tts.TTS
import java.time.LocalTime
import kotlin.math.sqrt

class MainViewModel(
    private val dvbSource: DVBSource,
    private val tts: TTS
) : ViewModel() {
    var latitude: Double by mutableDoubleStateOf(0.0)
        private set
    var longitude: Double by mutableDoubleStateOf(0.0)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    private val stops: List<Station> = dvbSource.getStations()
    val nearestStops = mutableStateListOf<Station>()

    fun onEvent(event: MainEvent) {
        viewModelScope.launch {
            when (event) {
                is MainEvent.LocationUpdate -> {
                    latitude = event.latitude
                    longitude = event.longitude
                    val nearestStation = stops.sortedBy {
                        sqrt((longitude - it.longitude) * (longitude - it.longitude) + (latitude - it.latitude) * (latitude - it.latitude))
                    }.take(10)
                    if (nearestStation != nearestStops) {
                        nearestStops.clear()
                        nearestStops.addAll(nearestStation)
                        nearestStops[0] = nearestStops[0].let { stop ->
                            stop.copy(
                                departures = dvbSource.departureMonitor(stop.id, 5)
                                    ?.departures
                                    ?.distinctBy { it.line + it.destination + it.platformName + it.platformType }
                                    .orEmpty()
                            )
                        }
                    } else {
                        Log.d("MainViewModel", "Location update ignored")
                    }
                }
                is MainEvent.TogglePlayPause -> {
                    isPlaying = !isPlaying
                    if (isPlaying) {
                        nearestStops.firstOrNull()?.let {
                            val speak = it.buildTTSSpeakableString()
                            tts.speak(speak)
                        }
                    } else {
                        tts.stop()
                    }
                }
            }
        }
    }
}

sealed class MainEvent {
    data class LocationUpdate(val latitude: Double, val longitude: Double) : MainEvent()
    data object TogglePlayPause : MainEvent()
}

data class Station(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val departures: List<Departure>
) {
    fun buildTTSSpeakableString(): String {
        return buildString {
            append("Haltestelle ")
            append(name)
            append(". Nächste Abfahrten: ")
            departures.forEach {
                append("Linie ")
                append(it.line)
                append(" in Richtung ")
                append(it.destination)
                append(" um ")
                append(it.time)
                append(" an ")
                append(it.platformType)
                append(" ")
                append(it.platformName)
                append(". ")
            }
        }
    }
}

data class Departure(
    val line: String,
    val destination: String,
    val time: LocalTime,
    val platformName: String,
    val platformType: String
)