package org.jugendhackt.wegweiser.dvb

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.time.*
import java.time.format.DateTimeFormatter

@Serializable
data class Departure(val line: String, val direction: String, val arrival: Int)

@Serializable
data class StopResult(val name: String, val city: String? = null, val coords: List<Double>? = null)

@Serializable
data class AddressResult(val city: String, val address: String)

@Serializable
data class Pin(val id: String, val name: String, val lat: Double, val lng: Double, val connections: String? = null)

@Serializable
data class RouteLeg(
    val mode: String,
    val line: String,
    val direction: String,
    val departure: RoutePoint,
    val arrival: RoutePoint
)

@Serializable
data class RoutePoint(
    val stop: String,
    val time: String,
    val coords: String
)

@Serializable
data class TripResult(
    val origin: String,
    val destination: String,
    val trips: List<JsonObject>
)

class Dvb {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun monitor(stop: String, offset: Int = 0, limit: Int = 10, city: String = "Dresden"): List<Departure>? {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("widgets.vvo-online.de")
            .addPathSegments("abfahrtsmonitor/Abfahrten.do")
            .addQueryParameter("ort", city)
            .addQueryParameter("hst", stop)
            .addQueryParameter("vz", offset.toString())
            .addQueryParameter("lim", limit.toString())
            .build()

        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val raw = json.decodeFromString<List<List<String>>>(body)
                raw.map {
                    Departure(it[0], it[1], it[2].toIntOrNull() ?: 0)
                }
            }
        } catch (e: IOException) {
            println("Fehler beim Monitor: $e")
            null
        }
    }

    suspend fun find(search: String): List<StopResult>? {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("efa.vvo-online.de")
            .addPathSegments("dvb/XML_STOPFINDER_REQUEST")
            .addQueryParameter("locationServerActive", "1")
            .addQueryParameter("outputFormat", "JSON")
            .addQueryParameter("type_sf", "any")
            .addQueryParameter("name_sf", search)
            .addQueryParameter("coordOutputFormat", "WGS84")
            .addQueryParameter("coordOutputFormatTail", "0")
            .build()

        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                val jsonTree = json.parseToJsonElement(body).jsonObject
                val points = jsonTree["stopFinder"]?.jsonObject?.get("points") ?: return null

                val results = if (points.jsonObject.containsKey("point")) {
                    listOf(points.jsonObject["point"]!!)
                } else {
                    points.jsonArray
                }

                results.mapNotNull { element ->
                    val obj = element.jsonObject
                    val name = obj["object"]?.jsonPrimitive?.content
                        ?: obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val city = obj["posttown"]?.jsonPrimitive?.content
                    val coordsRaw = obj["ref"]?.jsonObject?.get("coords")?.jsonPrimitive?.content
                    val coords = coordsRaw?.split(",")?.mapNotNull { it.toIntOrNull()?.div(1_000_000.0) }

                    StopResult(name, city, coords)
                }
            }
        } catch (e: IOException) {
            println("Fehler bei Find: $e")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun route(origin: String, destination: String, cityOrigin: String = "Dresden", cityDestination: String = "Dresden", time: LocalDateTime = LocalDateTime.now(), deparr: String = "dep"): TripResult? {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("efa.vvo-online.de")
            .port(8080)
            .addPathSegments("dvb/XML_TRIP_REQUEST2")
            .addQueryParameter("sessionID", "0")
            .addQueryParameter("requestID", "0")
            .addQueryParameter("language", "de")
            .addQueryParameter("execInst", "normal")
            .addQueryParameter("command", "")
            .addQueryParameter("ptOptionsActive", "-1")
            .addQueryParameter("itDateDay", time.dayOfMonth.toString())
            .addQueryParameter("itDateMonth", time.monthValue.toString())
            .addQueryParameter("itDateYear", time.year.toString())
            .addQueryParameter("itdTimeHour", time.hour.toString())
            .addQueryParameter("idtTimeMinute", time.minute.toString())
            .addQueryParameter("place_origin", cityOrigin)
            .addQueryParameter("name_origin", origin)
            .addQueryParameter("place_destination", cityDestination)
            .addQueryParameter("name_destination", destination)
            .addQueryParameter("itdTripDateTimeDepArr", deparr)
            .addQueryParameter("outputFormat", "JSON")
            .addQueryParameter("coordOutputFormat", "WGS84")
            .addQueryParameter("coordOutputFormatTail", "0")
            .build()

        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                val tree = json.parseToJsonElement(body).jsonObject
                TripResult(
                    origin = tree["origin"]?.jsonObject?.get("points")?.jsonObject?.get("point")?.jsonObject?.get("name")?.jsonPrimitive?.content
                        ?: "Unbekannt",
                    destination = tree["destination"]?.jsonObject?.get("points")?.jsonObject?.get("point")?.jsonObject?.get("name")?.jsonPrimitive?.content
                        ?: "Unbekannt",
                    trips = tree["trips"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
                )
            }
        } catch (e: IOException) {
            println("Fehler bei Route: $e")
            null
        }
    }

    suspend fun pins(swlat: Double, swlng: Double, nelat: Double, nelng: Double, pintypes: String = "stop"): List<Pin>? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.dvb.de")
            .addPathSegments("apps/map/pins")
            .addQueryParameter("showlines", "true")
            .addQueryParameter("swlat", swlat.toInt().toString())
            .addQueryParameter("swlng", swlng.toInt().toString())
            .addQueryParameter("nelat", nelat.toInt().toString())
            .addQueryParameter("nelng", nelng.toInt().toString())
            .addQueryParameter("pintypes", pintypes)
            .build()

        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                body.split("\n").mapNotNull { line ->
                    val parts = line.split("||")
                    if (parts.size < 2) return@mapNotNull null
                    val id = parts[0].split("|||")[0]
                    val data = parts[1].split("|")
                    val name = data[1]
                    val lat = data[2].toIntOrNull()?.div(1_000_000.0) ?: return@mapNotNull null
                    val lng = data[3].toIntOrNull()?.div(1_000_000.0) ?: return@mapNotNull null
                    val connections = parts.getOrNull(2)
                    Pin(id, name, lat, lng, connections)
                }
            }
        } catch (e: IOException) {
            println("Fehler bei Pins: $e")
            null
        }
    }

    suspend fun address(lat: Double, lng: Double): AddressResult? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.dvb.de")
            .addPathSegments("apps/map/address")
            .addQueryParameter("lat", lat.toInt().toString())
            .addQueryParameter("lng", lng.toInt().toString())
            .build()

        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                val parts = body.split("|")
                if (parts.size >= 2) AddressResult(parts[0], parts[1]) else null
            }
        } catch (e: IOException) {
            println("Fehler bei Adresse: $e")
            null
        }
    }

    suspend fun poiCoords(poiId: String): Pair<Double, Double>? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("www.dvb.de")
            .addPathSegments("apps/map/coordinates")
            .addQueryParameter("id", poiId)
            .build()

        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                val coords = body.split("|").mapNotNull { it.toIntOrNull() }
                if (coords.size >= 2) {
                    Pair(coords[0] / 1_000_000.0, coords[1] / 1_000_000.0)
                } else null
            }
        } catch (e: IOException) {
            println("Fehler bei POI-Koordinaten: $e")
            null
        }
    }
}
