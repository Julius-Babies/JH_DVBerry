package org.jugendhackt.wegweiser.language

class en {

    private val language = mapOf(
        "ui.next_departures" to "Next departures",
        "ui.isCancelled" to "Cancelled",
        "ui.at" to "at",
        "ui.abbreviation_minutes" to "min",
        "ui.railtrack" to "Track",
        "ui.platform" to "Platform",
        "ui.no_station_found" to "No station found",
        "ui.move_closer" to "Please move closer to a station",
        "tts.hold" to "Stop",
        "tts.next_departures" to "Next departures",
        "tts.line" to "Line",
        "tts.in_direction" to "towards",
        "tts.now" to "now",
        "tts.at_time" to "at",
        "tts.in" to "in",
        "tts.minute" to "minute",
        "tts.minutes" to "minutes",
        "tts.at" to "at",
        "tts.railtrack" to "track",
        "tts.platform" to "platform",
        "tts.isCancelled" to "is cancelled today",
        "tts.today" to "today",
        "tts.one_minute" to "one minute",
        "tts.later" to "later",
        "tts.earlier" to "earlier",
        "contentDescription.play" to "Play",
        "contentDescription.stop" to "Stop",
        "contentDescription.loading" to "Loading",
    )

    fun getString(key: String): String {
        return language[key] ?: key
    }
}