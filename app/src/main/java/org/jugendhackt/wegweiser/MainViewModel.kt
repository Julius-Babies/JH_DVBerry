package org.jugendhackt.wegweiser

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.dvb.DVBSource
import org.jugendhackt.wegweiser.tts.TTS
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

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

    var canPlay by mutableStateOf(false)
        private set

    private val stops: List<Station> = dvbSource.getStations()
    var nearestStops by mutableStateOf<Station?>(null)
        private set

    fun onEvent(event: MainEvent) {
        viewModelScope.launch {
            when (event) {
                is MainEvent.LocationUpdate -> {
                    latitude = event.latitude
                    longitude = event.longitude
                    val nearestStation = stops.minByOrNull {
                        sqrt((longitude - it.longitude) * (longitude - it.longitude) + (latitude - it.latitude) * (latitude - it.latitude))
                    }
                    if (nearestStation?.id != nearestStops?.id) {
                        nearestStops = nearestStation?.copy(
                            departures = dvbSource.departureMonitor(nearestStation.id, 5)
                                ?.departures
                                ?.distinctBy { it.line + it.destination + it.platformName + it.platformType }
                                .orEmpty()
                        )
                        canPlay = nearestStops != null
                    } else {
                        Log.d("MainViewModel", "No changes to nearest stops")
                    }
                }
                is MainEvent.TogglePlayPause -> {
                    isPlaying = !isPlaying
                    if (isPlaying) {
                        nearestStops?.let {
                            val speak = it.buildTTSSpeakableString()
                            tts.speak(speak) { isPlaying = false }
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
                ((it.time.hour * 60 + it.time.minute).minutes - (LocalTime.now().hour * 60 + LocalTime.now().minute).minutes).inWholeMinutes.let { minutes ->
                    if (minutes == 0L) append(" jetzt")
                    else if (minutes > 60L) append(" um ${it.time}")
                    else append(" in $minutes Minuten")
                }
                append(" an ")
                when(it.platformType) {
                    "Platform", "Tram" -> append("Steig")
                    "Railtrack" -> append("Gleis")
                    else -> append(it.platformType)
                }
                append(" ")
                append(it.platformName)
                if (it.isCancelled) append(" fällt heute aus")
                else if (it.delayInMinutes != 0) {
                    append(", heute ")
                    if (abs(it.delayInMinutes) == 1) append(" eine Minute ")
                    else append(" ${abs(it.delayInMinutes)} Minuten ")
                    if (it.delayInMinutes > 0) append(" später")
                    else append(" früher")
                }
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
    val platformType: String,
    val delayInMinutes: Int = 0,
    val isCancelled: Boolean = false
)
