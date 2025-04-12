package org.jugendhackt.wegweiser

import androidx.lifecycle.ViewModel
import java.time.LocalTime

class MainViewModel : ViewModel() {
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