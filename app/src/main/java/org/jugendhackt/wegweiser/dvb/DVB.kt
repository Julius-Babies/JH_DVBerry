package org.jugendhackt.wegweiser.dvb

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jugendhackt.wegweiser.Departure
import org.jugendhackt.wegweiser.Station
import kotlin.time.Duration.Companion.milliseconds

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
    private val stationStore: StationStore,
    private val client: HttpClient
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun departureMonitor(stopID: String, limit: Int): Station? {
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
                        val plannedTime = extractInstantFromDateString(it.scheduleTime)
                        val actualTime = extractInstantFromDateString(realTime)
                        (actualTime.toEpochMilliseconds() - plannedTime.toEpochMilliseconds())
                            .milliseconds
                            .inWholeMinutes
                            .toInt()
                    } ?: 0,
                    isCancelled = it.state == "Cancelled"
                )
            )
        }
        val station = Station(stopID, stationResponse.name, 0.0, 0.0, departures)
        return station
    }

    fun observeStations(): StateFlow<List<Station>> = stationStore.observeStations()

    suspend fun refreshStationsIfNeeded() {
        stationStore.refreshIfNeeded()
    }

    private fun extractLocalTimeFromDateString(dateString: String): LocalTime {
        return extractInstantFromDateString(dateString)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .time
    }

    private fun extractInstantFromDateString(dateString: String): Instant {
        val millis = dateString.substringAfter("(").substringBefore("+").substringBefore("-").toLong()
        return Instant.fromEpochMilliseconds(millis)
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
