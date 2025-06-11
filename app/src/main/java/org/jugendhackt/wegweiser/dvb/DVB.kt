package org.jugendhackt.wegweiser.dvb

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jugendhackt.wegweiser.Departure
import org.jugendhackt.wegweiser.R
import org.jugendhackt.wegweiser.Station
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
data class Haltestelle(
    @SerialName("Name") val name: String,
    @SerialName("Departures") val departures: List<Departure> = emptyList()
) {
    @Serializable
    data class Departure(
        @SerialName("LineName") val lineName: String,
        @SerialName("Direction") val direction: String,
        @SerialName("Platform") val platform: Platform,
        @SerialName("RealTime") val realTime: String? = null,
        @SerialName("ScheduledTime") val scheduleTime: String,
        @SerialName("State") val state: String? = null,
    ) {
        @Serializable
        data class Platform(
            @SerialName("Name") val name: String,
            @SerialName("Type") val type: String
        )
    }
}

class DVBSource(
    private val context: Context
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun departureMonitor(stopID: String, limit: Int): Station? {
        val client = HttpClient(CIO)
        val response: HttpResponse = client.get {
            url {
                protocol = URLProtocol.HTTPS
                host = "webapi.vvo-online.de"
                pathSegments = listOf("dm")
                parameter("stopID", stopID)
                parameter("limit", limit)
            }
        }
        if (!response.status.isSuccess()) return null
        val body = response.bodyAsText()
        val stationResponse = json.decodeFromString<Haltestelle>(body)
        val departures = mutableListOf<Departure>()
        stationResponse.departures.forEach {
            departures.add(
                Departure(
                    line = it.lineName,
                    destination = it.direction,
                    time = extractLocalTimeFromDateString(it.realTime ?: it.scheduleTime),
                    platformName = it.platform.name,
                    platformType = it.platform.type,
                    delayInMinutes = it.realTime?.let { realTime ->
                        val plannedTime = extractLocalTimeFromDateString(it.scheduleTime)
                        val actualTime = extractLocalTimeFromDateString(realTime)
                        (actualTime.hour - plannedTime.hour) * 60 + (actualTime.minute - plannedTime.minute)
                    } ?: 0,
                    isCancelled = it.state == "Cancelled"
                )
            )
        }
        val station = Station(stopID, stationResponse.name, 0.0, 0.0, departures)
        return station
    }

    fun getStations(): List<Station> {
        Log.d("DVBSource", "Starting to load stations from raw resource")
        return try {
            val inputStream = context.resources.openRawResource(R.raw.stops)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            reader.useLines { lines ->
                Log.d("DVBSource", "Reading stations from JSON")
                try {
                    val content = lines.joinToString("")
                    if (content.isBlank()) {
                        Log.e("DVBSource", "Empty JSON content")
                        emptyList<Station>()
                    } else {
                        Log.d("DVBSource", "JSON content length: ${content.length}")
                        Log.d("DVBSource", "First 100 characters: ${content.take(100)}")
                        
                        try {
                            val jsonData = json.decodeFromString<FeatureCollection>(content)
                            Log.d("DVBSource", "Successfully parsed ${jsonData.features.size} stations")
                            
                            jsonData.features.mapNotNull { feature ->
                                try {
                                    if (feature.geometry.coordinates.size < 2) {
                                        Log.e("DVBSource", "Invalid coordinates for station: ${feature.properties.name}")
                                        null
                                    } else {
                                        Station(
                                            feature.properties.number,
                                            feature.properties.name,
                                            feature.geometry.coordinates[0],
                                            feature.geometry.coordinates[1],
                                            emptyList()
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("DVBSource", "Error creating station from feature: ${feature.properties.name}", e)
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DVBSource", "Error parsing JSON content", e)
                            Log.e("DVBSource", "JSON content sample: ${content.take(500)}")
                            emptyList<Station>()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DVBSource", "Error reading JSON content", e)
                    emptyList<Station>()
                }
            }
        } catch (e: Exception) {
            Log.e("DVBSource", "Error loading stations resource", e)
            emptyList<Station>()
        }
    }

    private fun extractLocalTimeFromDateString(dateString: String): LocalTime {
        val millis = dateString.substringAfter("(").substringBefore("+").substringBefore("-").toLong()
        return ZonedDateTime.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())).toLocalTime()
    }
}

@Serializable
data class FeatureCollection(
    val type: String,
    val features: List<Feature> = emptyList()
)

@Serializable
data class Feature(
    val type: String,
    val properties: Properties,
    val geometry: Geometry
)

@Serializable
data class Properties(
    val number: String,
    val nameWithCity: String,
    val name: String,
    val city: String,
    val tariffZone1: String,
    val tariffZone2: String,
    val tariffZone3: String
)

@Serializable
data class Geometry(
    val type: String,
    val coordinates: List<Double> = emptyList()
)