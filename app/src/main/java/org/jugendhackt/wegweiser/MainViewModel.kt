package org.jugendhackt.wegweiser

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jugendhackt.wegweiser.dvb.Dvb
import org.jugendhackt.wegweiser.dvb.FeatureCollection
import org.jugendhackt.wegweiser.tts.TTS
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalTime
import kotlin.math.sqrt

class MainViewModel : ViewModel() {

    var latitude: Double by mutableDoubleStateOf(0.0)
        private set

    var longitude: Double by mutableDoubleStateOf(0.0)
        private set

    private val stops = mutableListOf<Station>()
    val nearestStops = mutableStateListOf<Station>()

    private var tts: TTS? = null

    fun init(context: Context) {
        try {
            val inputStream = context.resources.openRawResource(R.raw.stops)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = Json { ignoreUnknownKeys = true }
            stops.clear()
            reader.useLines { lines ->
                lines.joinToString("").let {
                    val featureCollection = json.decodeFromString<FeatureCollection>(it)
                    featureCollection.features.forEach { feature ->
                        val coords = feature.geometry.coordinates
                        stops.add(
                            Station(
                                id = feature.properties.number,
                                name = feature.properties.name,
                                longitude = coords[0],
                                latitude = coords[1],
                                departures = emptyList()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Fehler beim Laden der Haltestellen: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onEvent(event: MainEvent) {
        viewModelScope.launch {
            when (event) {
                is MainEvent.LocationUpdate -> {
                    latitude = event.latitude
                    longitude = event.longitude

                    val nearest = stops.sortedBy {
                        distance(it.latitude, it.longitude, latitude, longitude)
                    }.take(10)

                    if (nearestStops.map { it.id } != nearest.map { it.id }) {
                        nearestStops.clear()
                        nearestStops.addAll(nearest)

                        try {
                            val enrichedStation = nearest[0].copy(
                                departures = Dvb.departureMonitor(nearest[0].id, 5)
                                    .departures
                                    .distinctBy { it.line + it.destination + it.platformName + it.platformType }
                            )
                            nearestStops[0] = enrichedStation

                            // Jetzt wird nur noch dynamicSpeakChanges verwendet
                            dynamicSpeakChanges(enrichedStation.departures)
                        } catch (e: Exception) {
                            Log.e("MainViewModel", "Fehler beim Abrufen der Abfahrten: ${e.message}")
                        }
                    } else {
                        Log.d("MainViewModel", "Keine Änderungen an nächstgelegenen Haltestellen")
                    }
                }
            }
        }
    }

    fun dynamicSpeakChanges(changes: List<Departure>) {
        val message = changes.joinToString(". ") {
            when {
                it.isCancelled -> "Die Linie ${it.line} nach ${it.destination} fällt aus"
                it.delayInMinutes > 0 -> "Die Linie ${it.line} nach ${it.destination} hat ${it.delayInMinutes} Minuten Verspätung"
                else -> "Änderung bei der Linie ${it.line} nach ${it.destination}"
            }
        }.ifBlank { "Keine aktuellen Änderungen im Fahrplan." }

        if (tts == null) {
            tts = TTS(this).also { it.initialize() }
        }

        viewModelScope.launch {
            tts?.speak(message)
        }
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return sqrt((lon2 - lon1) * (lon2 - lon1) + (lat2 - lat1) * (lat2 - lat1))
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }
}

sealed class MainEvent {
    data class LocationUpdate(val latitude: Double, val longitude: Double) : MainEvent()
}

/**
 * @param distance in meters
 */
data class Station(
    val id: String,
    val name: String,
    val longitude: Double,
    val latitude: Double,
    val departures: List<Departure>
)

data class Departure(
    val line: String,
    val destination: String,
    val time: LocalTime,
    val platformName: String,
    val platformType: String,
    val delayInMinutes: Int = 0,
    val isCancelled: Boolean = false
)