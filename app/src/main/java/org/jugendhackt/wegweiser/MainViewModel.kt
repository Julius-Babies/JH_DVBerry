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
import org.jugendhackt.wegweiser.language.language
import org.jugendhackt.wegweiser.tts.TTS
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes

class MainViewModel(
    private val dvbSource: DVBSource,
    private val tts: TTS,
    private val language: language
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
                            val speak = it.buildTTSSpeakableString(language)
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
    fun buildTTSSpeakableString(language: language): String {
        return buildString {
            append("${language.getString("tts.hold")} ")
            append(name)
            append(". ${language.getString("tts.next_departures")}: ")
            departures.forEach {
                append("${language.getString("tts.line")} ")
                append(it.line.replace(Regex("(?<=[A-Za-z])(?=\\d)"), " ")) // to make sure that the line is spoken correctly ("S80" -> "S 80")
                append(" ${language.getString("tts.in_direction")} ")
                append(it.destination)
                ((it.time.hour * 60 + it.time.minute).minutes - (LocalTime.now().hour * 60 + LocalTime.now().minute).minutes).inWholeMinutes.let { minutes ->
                    if (minutes == 0L) append(" ${language.getString("tts.now")}")
                    else if (minutes > 60L) append(" ${language.getString("tts.at_time")} ${it.time}")
                    else append(" ${language.getString("tts.in")} $minutes ${language.getString("tts.minutes")}")
                }
                append(" ${language.getString("tts.at")} ")
                when(it.platformType) {
                    "Platform", "Tram" -> append(language.getString("tts.platform"))
                    "Railtrack" -> append(language.getString("tts.railtrack"))
                    else -> append(it.platformType)
                }
                append(" ")
                append(it.platformName)
                if (it.isCancelled) append(" ${language.getString("tts.isCancelled")}")
                else if (it.delayInMinutes != 0) {
                    append(", ${language.getString("tts.today")} ")
                    if (abs(it.delayInMinutes) == 1) append(" ${language.getString("tts.one_minute")} ")
                    else append(" ${abs(it.delayInMinutes)} ${language.getString("tts.minutes")} ")
                    if (it.delayInMinutes > 0) append(" ${language.getString("tts.later")}")
                    else append(" ${language.getString("tts.earlier")}")
                }
                append(" . ")
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
