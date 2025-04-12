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

    fun init(context: Context) {
        val inputStream = context.resources.openRawResource(R.raw.stops)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val json = Json { ignoreUnknownKeys = true }
        stops.clear()
        reader.useLines { lines ->
            lines.joinToString("").let {
                val json = json.decodeFromString<FeatureCollection>(it)
                json.features.forEach {
                    stops.add(Station(it.properties.number, it.properties.name, it.geometry.coordinates[0], it.geometry.coordinates[1], emptyList()))
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
                                departures = Dvb.departureMonitor(stop.id, 5).departures
                            )
                        }
                    } else {
                        Log.d("MainViewModel", "Location update ignored")
                    }
                }
            }
        }
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
    val platformType: String
)