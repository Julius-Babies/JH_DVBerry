package org.jugendhackt.wegweiser.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jugendhackt.wegweiser.Station
import org.jugendhackt.wegweiser.language.language

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.padding(24.dp),
            strokeWidth = 16.dp
        )
    }
}

@Composable
fun StationInfo(station: Station, language: language) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = station.name,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "${language.getString("ui.next_departures")}:",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        
        // Group departures by line and destination, and show only the next one for each
        station.departures
            .groupBy { "${it.line} ${it.destination}" }
            .mapValues { (_, departures) -> departures.minByOrNull { it.time } }
            .values
            .filterNotNull()
            .sortedBy { it.time }
            .forEach { departure ->
                val timeString = String.format("%02d:%02d", departure.time.hour, departure.time.minute)
                val delayString = when {
                    departure.delayInMinutes > 0 -> " +${departure.delayInMinutes}min"
                    departure.delayInMinutes < 0 -> " ${departure.delayInMinutes}min"
                    else -> ""
                }
                val cancelledString = if (departure.isCancelled) " ${language.getString("ui.cancelled")}" else ""
                
                Text(
                    text = "${departure.line}: ${departure.destination} ($timeString)$delayString$cancelledString",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
    }
}

@Composable
fun NoStationFound(language: language) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = language.getString("ui.no_station_found"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = language.getString("ui.move_closer"),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
} 