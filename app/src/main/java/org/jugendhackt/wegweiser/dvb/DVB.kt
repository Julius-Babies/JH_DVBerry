package org.jugendhackt.wegweiser.dvb

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jugendhackt.wegweiser.Departure
import org.jugendhackt.wegweiser.Station
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
data class Haltestelle(
    @SerialName("Name") val name: String,
    @SerialName("Departures") val departures: List<Departure>
) {
    @Serializable
    data class Departure(
        @SerialName("LineName") val lineName: String,
        @SerialName("Direction") val direction: String,
        @SerialName("Platform") val platform: Platform,
        @SerialName("RealTime") val time: String
    ) {
        @Serializable
        data class Platform(
            @SerialName("Name") val name: String,
            @SerialName("Type") val type: String
        )
    }
}

object Dvb {
    private var baseUrl = "https://webapi.vvo-online.de"

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun departureMonitor(stopID: String, limit: Int): Station {
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
        val body = response.bodyAsText()
        Log.d("KTor-Response", body)
        val haltestelle = Json {ignoreUnknownKeys = true }.decodeFromString<Haltestelle>(body)
        val departures = ArrayList<Departure>()
        haltestelle.departures.forEach {
            departures.add(
                Departure(
                    it.lineName,
                    it.direction,
                    extractLocalTimeFromDateString(it.time),
                    it.platform.name,
                    it.platform.type
                )
            )
        }
        val station = Station(haltestelle.name, -1, departures)
        return station
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun extractLocalTimeFromDateString(dateString: String): LocalTime {
        val millis = dateString.substringAfter("(").substringBefore("+").substringBefore("-").toLong()
        return ZonedDateTime.from(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())).toLocalTime()
    }
}
