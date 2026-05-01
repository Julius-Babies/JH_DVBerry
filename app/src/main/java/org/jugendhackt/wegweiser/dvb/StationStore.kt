package org.jugendhackt.wegweiser.dvb

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jugendhackt.wegweiser.R
import org.jugendhackt.wegweiser.Station
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class StationStore(
    private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        prettyPrint = true
    }

    private val stationsRef = AtomicReference<List<Station>>(emptyList())
    private val stationsFlow = MutableStateFlow(emptyList<Station>())

    init {
        // Start from disk if possible so the app has data before any network refresh runs.
        val initialData = loadInitialStations()
        stationsRef.set(initialData)
        stationsFlow.value = initialData
    }

    fun getStations(): List<Station> = stationsRef.get()

    fun observeStations(): StateFlow<List<Station>> = stationsFlow.asStateFlow()

    suspend fun refreshIfNeeded(client: HttpClient) {
        val cacheExists = cacheFile().exists()
        if (cacheExists && !shouldRefresh()) {
            Log.d(TAG, "Skipping station refresh because cache is newer than 1 day")
            return
        }

        runCatching {
            val remoteStops = downloadRemoteStops(client)
            // Normalize the remote VVO format into the GeoJSON structure already used by the app.
            val featureCollection = FeatureCollection(
                type = "FeatureCollection",
                features = remoteStops.mapNotNull(::toFeature)
            )

            cacheFile().writeText(json.encodeToString(FeatureCollection.serializer(), featureCollection))
            preferences().edit().putLong(PREF_LAST_REFRESH_AT, System.currentTimeMillis()).apply()

            val refreshedStations = featureCollection.features.mapNotNull(::toStation)
            publishStations(refreshedStations)
            Log.d(TAG, "Refreshed ${refreshedStations.size} stations")
        }.onFailure { error ->
            Log.e(TAG, "Failed to refresh station cache", error)
            if (stationsRef.get().isEmpty()) {
                publishStations(loadInitialStations())
            }
        }
    }

    private fun shouldRefresh(): Boolean {
        val lastRefreshAt = preferences().getLong(PREF_LAST_REFRESH_AT, 0L)
        return lastRefreshAt == 0L || System.currentTimeMillis() - lastRefreshAt >= REFRESH_INTERVAL_MS
    }

    private fun loadStationsFromDisk(): List<Station> {
        val file = cacheFile()
        if (!file.exists()) {
            Log.w(TAG, "Station cache file does not exist")
            return emptyList()
        }

        return runCatching {
            val featureCollection = json.decodeFromString(
                FeatureCollection.serializer(),
                file.readText()
            )
            featureCollection.features.mapNotNull(::toStation)
        }.onFailure { error ->
            Log.e(TAG, "Failed to read station cache", error)
        }.getOrDefault(emptyList())
    }

    private fun loadInitialStations(): List<Station> {
        val cachedStations = loadStationsFromDisk()
        if (cachedStations.isNotEmpty()) return cachedStations

        // Fall back to the bundled snapshot when there is no usable on-device cache yet.
        val bundledStations = loadBundledStations()
        if (bundledStations.isNotEmpty()) {
            Log.d(TAG, "Using bundled fallback stops until refresh completes")
        }
        return bundledStations
    }

    private suspend fun downloadRemoteStops(client: HttpClient): List<VvoStop> {
        val response = client.get("https://www.vvo-online.de/open_data/VVO_STOPS.JSON")
        check(response.status.isSuccess()) { "Station refresh failed with HTTP ${response.status.value}" }
        return json.decodeFromString(response.bodyAsText())
    }

    private fun loadBundledStations(): List<Station> {
        return runCatching {
            context.resources.openRawResource(R.raw.stops).bufferedReader().use { reader ->
                val featureCollection = json.decodeFromString(
                    FeatureCollection.serializer(),
                    reader.readText()
                )
                featureCollection.features.mapNotNull(::toStation)
            }
        }.onFailure { error ->
            Log.e(TAG, "Failed to read bundled fallback stops", error)
        }.getOrDefault(emptyList())
    }

    private fun toFeature(stop: VvoStop): Feature? {
        val x = stop.x?.toDoubleOrNull()
        val y = stop.y?.toDoubleOrNull()
        if (x == null || y == null) {
            Log.w(TAG, "Skipping stop ${stop.gid ?: "unknown"} because coordinates are invalid")
            return null
        }

        return Feature(
            type = "Feature",
            properties = Properties(
                number = stop.gid.orEmpty(),
                nameWithCity = listOfNotNull(
                    stop.place?.takeIf { it.isNotBlank() },
                    stop.name?.takeIf { it.isNotBlank() }
                ).joinToString(" "),
                name = stop.name.orEmpty(),
                city = stop.place.orEmpty(),
                tariffZone1 = "",
                tariffZone2 = "",
                tariffZone3 = ""
            ),
            geometry = Geometry(
                type = "Point",
                coordinates = listOf(x, y)
            )
        )
    }

    private fun toStation(feature: Feature): Station? {
        if (feature.geometry.coordinates.size < 2) {
            Log.w(TAG, "Skipping feature ${feature.properties.name} because coordinates are incomplete")
            return null
        }

        // Station objects are cached without departures; live departures are resolved separately.
        return Station(
            id = feature.properties.number,
            name = feature.properties.name,
            longitude = feature.geometry.coordinates[0],
            latitude = feature.geometry.coordinates[1],
            departures = emptyList()
        )
    }

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE_NAME)

    private fun preferences() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun publishStations(stations: List<Station>) {
        stationsRef.set(stations)
        stationsFlow.value = stations
    }

    companion object {
        private const val TAG = "StationStore"
        private const val CACHE_FILE_NAME = "stops.json"
        private const val PREFS_NAME = "station_store"
        private const val PREF_LAST_REFRESH_AT = "last_refresh_at"
        private const val REFRESH_INTERVAL_MS = 1L * 24L * 60L * 60L * 1000L
    }
}

@Serializable
private data class VvoStop(
    @SerialName("gid") val gid: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("place") val place: String? = null,
    @SerialName("x") val x: String? = null,
    @SerialName("y") val y: String? = null
)
