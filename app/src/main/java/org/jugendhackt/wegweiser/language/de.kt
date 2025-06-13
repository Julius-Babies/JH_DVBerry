package org.jugendhackt.wegweiser.language

class de {

    private val language = mapOf(
        "ui.next_departures" to "Nächsten Abfahrten",
        "ui.isCancelled" to "Entfällt",
        "ui.at" to "auf",
        "ui.abbreviation_minutes" to "min",
        "ui.railtrack" to "Gleis",
        "ui.platform" to "Steig",
        "ui.no_station_found" to "Keine Station gefunden",
        "ui.move_closer" to "Bitte bewegen Sie sich näher an eine Haltestelle",
        "tts.hold" to "Haltestelle",
        "tts.next_departures" to "Nächste Abfahrten",
        "tts.line" to "Linie",
        "tts.in_direction" to "in Richtung",
        "tts.now" to "jetzt",
        "tts.at_time" to "um",
        "tts.in" to "in",
        "tts.minute" to "Minute",
        "tts.minutes" to "Minuten",
        "tts.at" to "an",
        "tts.railtrack" to "Gleis",
        "tts.platform" to "Steig",
        "tts.isCancelled" to "fällt heute aus",
        "tts.today" to "heute",
        "tts.one_minute" to "eine Minute",
        "tts.later" to "später",
        "tts.earlier" to "früher",
        "contentDescription.play" to "Abspielen",
        "contentDescription.stop" to "Stop",
        "contentDescription.loading" to "Laden",
    )

    fun getString(key: String): String {
        return language[key] ?: key
    }
}
