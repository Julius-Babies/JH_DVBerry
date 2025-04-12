package org.jugendhackt.wegweiser

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.jugendhackt.wegweiser.dvb.Dvb
import java.time.LocalTime

@RequiresApi(Build.VERSION_CODES.O)
class MainViewModel : ViewModel() {
    val testText = mutableStateOf("")
    init {
        viewModelScope.launch { testText.value = Dvb.departureMonitor("de:14612:65", 10).toString() }
    }
}

/**
 * @param distance in meters
 */
data class Station(
    val name: String,
    val distance: Int, //TODO
    val departures: List<Departure>
)

/**
 * @param time LocalTime
 */
data class Departure(
    val line: String,
    val direction: String,
    val time: LocalTime,
    val platformName: String,
    val platformType: String
)