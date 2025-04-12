package org.jugendhackt.wegweiser.dvb

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Serializable
data class Departure(val line: String, val direction: String, val arrival: Int)

@Serializable
data class StopResult(val name: String, val city: String? = null, val coords: List<Double>? = null)

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
                if (!response.isSuccessful) {
                    println("Fehler: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val raw = json.decodeFromString<List<List<String>>>(body)
                raw.map {
                    Departure(
                        line = it[0],
                        direction = it[1],
                        arrival = it[2].toIntOrNull() ?: 0
                    )
                }
            }
        } catch (e: IOException) {
            println("Fehler beim Abrufen der Daten: $e")
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
                if (!response.isSuccessful) {
                    println("Fehler: ${response.code}")
                    return null
                }
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

                    StopResult(name = name, city = city, coords = coords)
                }
            }
        } catch (e: IOException) {
            println("Fehler bei der Anfrage: $e")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun timestampToDateTime(unix: Long): LocalDateTime {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(unix), ZoneId.systemDefault())
    }

    // Mehr Funktionen wie `route()`, `address()`, `pins()` usw. können ebenfalls eingebaut werden.
}
