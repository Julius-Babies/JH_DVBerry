package org.jugendhackt.wegweiser

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.dvb.Dvb
import java.time.LocalTime

class MainViewModel : ViewModel() {
    val testText = mutableStateOf("")
    init {
        viewModelScope.launch { testText.value = Dvb.departureMonitor(65, 10) }
    }
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