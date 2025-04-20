package org.jugendhackt.wegweiser.dvb

import android.content.Context
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
import java.util.Locale
import org.jugendhackt.wegweiser.language.language

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
    private val json = Json { ignoreUnknownKeys = true }
    private val language = language(context)

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
                    platformType = when (it.platform.type.lowercase(Locale.ROOT)) {
                        "platform", "tram" -> language.getString("dvb.platform")
                        "railtrack" -> language.getString("dvb.railtrack")
                        else -> it.platform.type
                    },
                    delayInMinutes = it.realTime?.let { realTime ->
                        val plannedTime = extractLocalTimeFromDateString(it.scheduleTime)
                        val actualTime = extractLocalTimeFromDateString(realTime)
                        (plannedTime.hour - actualTime.hour) * 60 + (plannedTime.minute - actualTime.minute)
                    } ?: 0,
                    isCancelled = it.state == "Cancelled"
                )
            )
        }
        val station = Station(stopID, stationResponse.name, 0.0, 0.0, departures)
        return station
    }

    fun getStations(): List<Station> {
        val inputStream = context.resources.openRawResource(R.raw.stops)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val json = Json { ignoreUnknownKeys = true }
        return reader.useLines { lines ->
            lines.joinToString("").let {
                val json = json.decodeFromString<FeatureCollection>(it)
                json.features.map {
                    Station(it.properties.number, it.properties.name, it.geometry.coordinates[0], it.geometry.coordinates[1], emptyList())
                }
            }
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
    val features: List<Feature>
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
    val coordinates: List<Double>
)