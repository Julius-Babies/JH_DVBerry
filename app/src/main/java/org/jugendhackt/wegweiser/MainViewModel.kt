package org.jugendhackt.wegweiser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalTime

class MainViewModel : ViewModel() {
    var latitude: Double by mutableDoubleStateOf(0.0)
        private set
    var longitude: Double by mutableDoubleStateOf(0.0)
        private set

    fun onEvent(event: MainEvent) {
        viewModelScope.launch {
            when (event) {
                is MainEvent.LocationUpdate -> {
                    latitude = event.latitude
                    longitude = event.longitude
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
    val name: String,
    val distance: Int,
    val departures: List<Departure>,
    val lines: List<String>
)

data class Departure(
    val line: String,
    val destination: String,
    val time: LocalTime,
)